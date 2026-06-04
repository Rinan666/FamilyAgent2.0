"""
批改Agent — 步骤级评分与错误分析
"""
from typing import Optional

from app.agents.base import BaseAgent
from app.llm.prompts import grader as grader_prompts
from app.llm.schemas import GRADE_RESULT_SCHEMA


class GraderAgent(BaseAgent):
    """批改Agent"""

    def __init__(self):
        super().__init__(
            name="GraderAgent",
            system_prompt=grader_prompts.SYSTEM_PROMPT,
        )

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
            return json.loads(result)
        except json.JSONDecodeError:
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
