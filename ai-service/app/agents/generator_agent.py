"""
出题Agent — AI批量生成题目
"""
import json
import logging
from typing import Optional

from app.llm.prompts import generator as generator_prompts
from app.llm.schemas import GENERATE_QUESTIONS_SCHEMA

logger = logging.getLogger("familyagent.ai.generator")


class GeneratorAgent:
    """出题Agent"""

    def __init__(self):
        self.name = "GeneratorAgent"

    async def generate(
        self,
        subject: str,
        grade: str,
        knowledge_point: str,
        question_type: str = "CALCULATION",
        difficulty: int = 3,
        count: int = 5,
        additional_requirements: str = "无",
    ) -> list[dict]:
        """
        批量生成题目

        Args:
            subject: 学科
            grade: 年级
            knowledge_point: 知识点
            question_type: 题型 (CHOICE/FILL/CALCULATION/PROOF)
            difficulty: 难度 (1-5)
            count: 生成数量
            additional_requirements: 附加要求

        Returns:
            list[dict]: 题目列表
        """
        from app.llm.client import llm_client

        user_message = generator_prompts.SYSTEM_PROMPT.format(
            subject=subject,
            grade=grade,
            knowledge_point=knowledge_point,
            question_type=question_type,
            difficulty=difficulty,
            count=min(count, 10),  # 一次最多10道
            additional_requirements=additional_requirements,
        )

        messages = [
            {"role": "system", "content": "你是一位经验丰富的题目设计专家。"},
            {"role": "user", "content": user_message},
        ]

        result = await llm_client.chat(
            messages,
            temperature=0.8,  # 较高温度增加题目多样性
            max_tokens=8192,
            response_format=GENERATE_QUESTIONS_SCHEMA,
        )

        try:
            data = json.loads(result)
            questions = data.get("questions", [])
            logger.info(f"[{self.name}] 生成 {len(questions)} 道题目")
            return questions
        except json.JSONDecodeError:
            logger.error(f"[{self.name}] 出题结果JSON解析失败")
            return []

    async def generate_variation(
        self,
        original_question: str,
        original_difficulty: int,
        target_difficulty: int,
    ) -> Optional[dict]:
        """
        生成变式题（基于原题修改）

        改变数字或条件，但考察同一知识点
        """
        from app.llm.client import llm_client

        prompt = generator_prompts.VARIATION_PROMPT.format(
            original_question=original_question,
            original_difficulty=original_difficulty,
            target_difficulty=target_difficulty,
        )

        result = await llm_client.chat(
            [{"role": "user", "content": prompt}],
            temperature=0.7,
            max_tokens=4096,
        )
        return {"variation": result}


# 全局单例
generator_agent = GeneratorAgent()
