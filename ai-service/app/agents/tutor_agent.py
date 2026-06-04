"""
讲题Agent — 苏格拉底式引导教学
"""
from typing import AsyncIterator, Optional

from app.agents.base import BaseAgent
from app.llm.prompts import tutor as tutor_prompts


class TutorAgent(BaseAgent):
    """讲题Agent"""

    def __init__(self):
        super().__init__(
            name="TutorAgent",
            system_prompt=tutor_prompts.SYSTEM_PROMPT,
        )

    def _build_context(
        self,
        question_content: str,
        answer: str,
        steps: str,
        grade: str = "初中",
        subject: str = "数学",
        knowledge_point: str = "未知",
        mastery_level: str = "中",
        common_errors: str = "无历史数据",
    ) -> str:
        """构建讲题上下文"""
        return self.system_prompt.format(
            grade=grade,
            subject=subject,
            knowledge_point=knowledge_point,
            mastery_level=mastery_level,
            common_errors=common_errors,
            question_content=question_content,
            answer=answer,
            steps=steps,
        )

    async def explain(
        self,
        question_content: str,
        answer: str,
        steps: str,
        student_message: str,
        history: Optional[list[dict]] = None,
        grade: str = "初中",
        subject: str = "数学",
        knowledge_point: str = "未知",
        mastery_level: str = "中",
        common_errors: str = "无历史数据",
    ) -> str:
        """
        讲题（非流式）

        Args:
            question_content: 题目内容
            answer: 标准答案
            steps: 解题步骤
            student_message: 学生当前消息
            history: 对话历史
            grade: 年级
            subject: 学科
            knowledge_point: 知识点
            mastery_level: 掌握程度
            common_errors: 常见错误类型
        """
        # 首次对话：设置完整的系统prompt
        if not history:
            context = self._build_context(
                question_content, answer, steps,
                grade, subject, knowledge_point,
                mastery_level, common_errors,
            )
            messages = [
                {"role": "system", "content": context},
                {"role": "user", "content": student_message},
            ]
        else:
            messages = [
                {"role": "system", "content": self.system_prompt},
                *history,
                {"role": "user", "content": student_message},
            ]

        from app.llm.client import llm_client
        return await llm_client.chat(messages, temperature=0.7)

    async def explain_stream(
        self,
        question_content: str,
        answer: str,
        steps: str,
        student_message: str,
        history: Optional[list[dict]] = None,
        grade: str = "初中",
        subject: str = "数学",
        knowledge_point: str = "未知",
        mastery_level: str = "中",
        common_errors: str = "无历史数据",
    ) -> AsyncIterator[str]:
        """
        讲题（流式输出）

        使用SSE推送，前端实现打字机效果
        """
        if not history:
            context = self._build_context(
                question_content, answer, steps,
                grade, subject, knowledge_point,
                mastery_level, common_errors,
            )
            messages = [
                {"role": "system", "content": context},
                {"role": "user", "content": student_message},
            ]
        else:
            messages = [
                {"role": "system", "content": self.system_prompt},
                *history,
                {"role": "user", "content": student_message},
            ]

        from app.llm.client import llm_client
        async for chunk in llm_client.chat_stream(messages, temperature=0.7):
            yield chunk


# 全局单例
tutor_agent = TutorAgent()
