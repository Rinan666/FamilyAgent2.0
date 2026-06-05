"""
批改Agent — 步骤级评分与错误分析
"""
from app.agents.base import BaseAgent
from app.engine.math_executor import math_sandbox
from app.llm.prompts import grader as grader_prompts
from app.llm.schemas import GRADE_RESULT_SCHEMA


class GraderAgent(BaseAgent):
    """批改Agent"""

    def __init__(self):
        super().__init__(
            name="GraderAgent",
            system_prompt=grader_prompts.SYSTEM_PROMPT,
        )

    def _has_working_steps(self, student_answer: str) -> bool:
        separators = ["\n", "=>", "->", "所以", "因为", "先", "再"]
        return any(separator in student_answer for separator in separators)

    def _basic_grade_result(
        self,
        student_answer: str,
        verification: dict,
        *,
        uncertain: bool = False,
    ) -> dict:
        if uncertain:
            return {
                "overall_score": 0,
                "is_correct": False,
                "step_grades": [{
                    "step_number": 1,
                    "step_name": "快速判分",
                    "student_work": student_answer,
                    "is_correct": False,
                    "score": 0,
                    "max_score": 100,
                    "error_type": "理解偏差",
                    "feedback": "快速判分无法确认答案是否正确，建议查看详细分析或人工复核。",
                }],
                "error_analysis": {
                    "primary_error_type": "需要复核",
                    "knowledge_gaps": [],
                    "suggestion": "当前答案无法通过规则或数学等价性直接判断，建议使用详细 AI 分析。",
                },
                "overall_feedback": "快速判分暂时无法确认结果，请查看详细分析。",
                "math_verification": verification,
                "grading_mode": "quick",
                "needs_ai_review": True,
            }

        if verification.get("is_correct"):
            score = 95 if not self._has_working_steps(student_answer) else 100
            feedback = (
                "答案正确。当前只写了最终答案，建议补充关键步骤。"
                if score < 100
                else "答案正确，关键步骤也比较完整。"
            )
            return {
                "overall_score": score,
                "is_correct": True,
                "step_grades": [{
                    "step_number": 1,
                    "step_name": "最终答案",
                    "student_work": student_answer,
                    "is_correct": True,
                    "score": score,
                    "max_score": 100,
                    "error_type": "无" if score == 100 else "步骤遗漏",
                    "feedback": feedback,
                }],
                "error_analysis": {
                    "primary_error_type": "无",
                    "knowledge_gaps": [],
                    "suggestion": "继续保持。正式测试中建议保留关键步骤，便于复盘。",
                },
                "overall_feedback": feedback,
                "math_verification": verification,
                "grading_mode": "quick",
                "needs_ai_review": False,
            }

        return {
            "overall_score": 0,
            "is_correct": False,
            "step_grades": [{
                "step_number": 1,
                "step_name": "最终答案",
                "student_work": student_answer,
                "is_correct": False,
                "score": 0,
                "max_score": 100,
                "error_type": "答案不符",
                "feedback": "答案与标准答案不一致。若你写了完整过程，可以查看详细分析定位具体错误。",
            }],
            "error_analysis": {
                "primary_error_type": "答案不符",
                "knowledge_gaps": [],
                "suggestion": "先核对最终答案；若过程复杂，再查看详细 AI 分析。",
            },
            "overall_feedback": "答案暂未通过快速判分。",
            "math_verification": verification,
            "grading_mode": "quick",
            "needs_ai_review": True,
        }

    def _apply_math_verification(
        self,
        result: dict,
        question_content: str,
        answer: str,
        student_answer: str,
    ) -> dict:
        verification = math_sandbox.verify_answer(
            question_expr=question_content,
            student_answer=student_answer,
            expected_answer=answer,
        )
        result["math_verification"] = verification

        if not verification.get("is_correct"):
            return result

        result["is_correct"] = True
        result["overall_score"] = max(float(result.get("overall_score", 0) or 0), 90.0)
        result["error_analysis"] = {
            "primary_error_type": "无",
            "knowledge_gaps": [],
            "suggestion": "最终答案正确。若是正式测试，建议补充关键步骤，方便判断思路。",
        }

        if not self._has_working_steps(student_answer):
            result["overall_score"] = min(float(result["overall_score"]), 95.0)
            result["step_grades"] = [{
                "step_number": 1,
                "step_name": "最终答案",
                "student_work": student_answer,
                "is_correct": True,
                "score": result["overall_score"],
                "max_score": 100,
                "error_type": "步骤遗漏",
                "feedback": "最终答案正确，但只写了答案，建议补上关键计算步骤。",
            }]
            result["overall_feedback"] = "答案正确。当前作答只展示了最终答案，建议补充关键步骤，让解题思路更完整。"
        else:
            result["overall_feedback"] = "答案正确，主要思路可以认可。继续保持，并注意书写关键步骤。"

        return result

    async def grade(
        self,
        question_content: str,
        answer: str,
        steps: str,
        student_answer: str,
        subject: str = "数学",
        grade: str = "初中",
    ) -> dict:
        """
        批改学生答案

        Returns:
            dict: {
                overall_score, is_correct, step_grades,
                error_analysis, overall_feedback
            }
        """
        user_message = grader_prompts.SYSTEM_PROMPT.format(
            subject=subject,
            grade=grade,
            question_content=question_content,
            answer=answer,
            steps=steps,
            student_answer=student_answer,
        )

        messages = [
            {"role": "system", "content": "你是一位严谨而温和的AI批改老师。"},
            {"role": "user", "content": user_message},
        ]

        from app.llm.client import llm_client
        import json

        result = await llm_client.chat(
            messages,
            temperature=0.3,  # 低温度，保证批改一致性
            max_tokens=4096,
            response_format=GRADE_RESULT_SCHEMA,
        )

        try:
            parsed = json.loads(result)
            return self._apply_math_verification(parsed, question_content, answer, student_answer)
        except json.JSONDecodeError:
            verification = math_sandbox.verify_answer(
                question_expr=question_content,
                student_answer=student_answer,
                expected_answer=answer,
            )
            if verification.get("is_correct"):
                return {
                    "overall_score": 95,
                    "is_correct": True,
                    "step_grades": [{
                        "step_number": 1,
                        "step_name": "最终答案",
                        "student_work": student_answer,
                        "is_correct": True,
                        "score": 95,
                        "max_score": 100,
                        "error_type": "步骤遗漏",
                        "feedback": "最终答案正确，但建议补充关键步骤。",
                    }],
                    "error_analysis": {
                        "primary_error_type": "无",
                        "knowledge_gaps": [],
                        "suggestion": "补充关键步骤，方便复盘解题思路。",
                    },
                    "overall_feedback": "答案正确。当前只写了最终答案，建议补充关键步骤。",
                    "math_verification": verification,
                }

            # 降级：返回基础评分
            return {
                "overall_score": 0,
                "is_correct": False,
                "step_grades": [],
                "error_analysis": {
                    "primary_error_type": "系统错误",
                    "knowledge_gaps": [],
                    "suggestion": "批改解析异常，请重试",
                },
                "overall_feedback": "批改结果解析失败，请重新提交。",
            }

    async def quick_grade(
        self,
        question: str,
        answer: str,
        student_answer: str,
    ) -> dict:
        """快速批改（规则 + sympy，不调用LLM）"""
        if not student_answer.strip():
            return self._basic_grade_result(
                student_answer,
                {"is_correct": False, "method": "empty_answer"},
                uncertain=False,
            )

        verification = math_sandbox.verify_answer(
            question_expr=question,
            student_answer=student_answer,
            expected_answer=answer,
        )

        return self._basic_grade_result(
            student_answer,
            verification,
            uncertain=verification.get("method") == "math_or_string_compare" and not verification.get("is_correct"),
        )


# 全局单例
grader_agent = GraderAgent()
