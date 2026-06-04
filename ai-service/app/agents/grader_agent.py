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
        """快速批改（轻量版，用于简单题目）"""
        from app.llm.client import llm_client

        prompt = grader_prompts.QUICK_GRADE_PROMPT.format(
            question=question,
            answer=answer,
            student_answer=student_answer,
        )

        result = await llm_client.chat(
            [{"role": "user", "content": prompt}],
            temperature=0.2,
            max_tokens=1024,
        )
        return {"result": result}


# 全局单例
grader_agent = GraderAgent()
