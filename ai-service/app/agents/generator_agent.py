"""
出题Agent — AI批量生成题目
"""
import json
import logging
from typing import Optional

from app.agents.base import BaseAgent
from app.llm.client import llm_client
from app.llm.prompts import generator as generator_prompts
from app.llm.schemas import GENERATE_QUESTIONS_SCHEMA

logger = logging.getLogger("familyagent.ai.generator")


def _text(value, default: str = "") -> str:
    if value is None:
        return default
    if isinstance(value, str):
        return value.strip() or default
    if isinstance(value, (int, float, bool)):
        return str(value)
    if isinstance(value, dict):
        for key in (
            "value",
            "text",
            "stem",
            "content",
            "question",
            "title",
            "answer",
            "final_answer",
            "finalAnswer",
            "standard_answer",
            "standardAnswer",
            "result",
        ):
            text = _text(value.get(key))
            if text:
                return text
    return default


def _string_list(value) -> list[str]:
    if isinstance(value, list):
        return [_text(item) for item in value if _text(item)]
    if isinstance(value, str):
        return [item.strip() for item in value.splitlines() if item.strip()]
    return []


def _int_between(value, default: int, minimum: int, maximum: int) -> int:
    try:
        number = int(value)
    except (TypeError, ValueError):
        return default
    return min(max(number, minimum), maximum)


class GeneratorAgent(BaseAgent):
    """出题Agent"""

    def __init__(self):
        super().__init__(
            name="GeneratorAgent",
            system_prompt="你是一位经验丰富的题目设计专家。",
        )

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
        user_message = generator_prompts.SYSTEM_PROMPT.format(
            subject=subject,
            grade=grade,
            knowledge_point=knowledge_point,
            question_type=question_type,
            difficulty=difficulty,
            count=min(count, 10),
            additional_requirements=additional_requirements,
        )

        # 使用结构化输出（JSON Schema），基类 run() 不支持 response_format
        messages = self._build_messages(user_message)
        result = await llm_client.chat(
            messages,
            temperature=0.8,
            max_tokens=8192,
            response_format=GENERATE_QUESTIONS_SCHEMA,
        )

        try:
            data = json.loads(result)
            questions = data.get("questions", [])
            normalized = [
                self._normalize_question(
                    question,
                    subject=subject,
                    grade=grade,
                    question_type=question_type,
                    difficulty=difficulty,
                    knowledge_point=knowledge_point,
                )
                for question in questions
                if isinstance(question, dict)
            ]
            logger.info(f"[{self.name}] 生成 {len(normalized)} 道题目")
            return normalized
        except json.JSONDecodeError:
            logger.error(f"[{self.name}] 出题结果JSON解析失败")
            return []

    def _normalize_question(
        self,
        raw: dict,
        *,
        subject: str,
        grade: str,
        question_type: str,
        difficulty: int,
        knowledge_point: str,
    ) -> dict:
        content = raw.get("content") if isinstance(raw.get("content"), dict) else {}
        answer = raw.get("answer") if isinstance(raw.get("answer"), dict) else {}

        answer_value = next(
            (
                _text(value)
                for value in [
                    answer.get("value"),
                    answer.get("answer"),
                    answer.get("final_answer"),
                    answer.get("finalAnswer"),
                    answer.get("standard_answer"),
                    answer.get("standardAnswer"),
                    answer.get("result"),
                    raw.get("answer_value"),
                    raw.get("answerValue"),
                    raw.get("final_answer"),
                    raw.get("finalAnswer"),
                    raw.get("standard_answer"),
                    raw.get("standardAnswer"),
                    raw.get("result"),
                    raw.get("answer") if isinstance(raw.get("answer"), str) else None,
                ]
                if _text(value)
            ),
            "",
        )
        steps = (
            _string_list(answer.get("steps"))
            or _string_list(answer.get("solution_steps"))
            or _string_list(raw.get("steps"))
            or _string_list(raw.get("solution_steps"))
            or _string_list(raw.get("solutionSteps"))
        )
        explanation = next(
            (
                _text(value)
                for value in [
                    answer.get("explanation"),
                    answer.get("analysis"),
                    answer.get("solution"),
                    raw.get("explanation"),
                    raw.get("analysis"),
                    raw.get("solution"),
                ]
                if _text(value)
            ),
            "",
        )

        question = {
            "subject": _text(raw.get("subject"), subject),
            "grade": _text(raw.get("grade"), grade),
            "type": _text(raw.get("type"), question_type).upper(),
            "difficulty": _int_between(raw.get("difficulty"), difficulty, 1, 5),
            "content": {
                "stem": (
                    _text(content.get("stem"))
                    or _text(content.get("value"))
                    or _text(content.get("text"))
                    or _text(raw.get("stem"))
                    or _text(raw.get("question"))
                    or _text(raw.get("title"))
                    or _text(raw.get("content"))
                ),
                "options": _string_list(content.get("options")) or _string_list(raw.get("options")),
                "figures": _string_list(content.get("figures")) or _string_list(raw.get("figures")),
            },
            "answer": {
                "value": answer_value,
                "steps": steps,
                "explanation": explanation,
            },
            "tags": list(dict.fromkeys([
                knowledge_point,
                *_string_list(raw.get("tags")),
            ])),
        }

        kp_id = raw.get("kp_id", raw.get("kpId"))
        if kp_id is not None:
            question["kp_id"] = _int_between(kp_id, 0, 0, 2_147_483_647)

        if question["type"] not in {"CHOICE", "FILL", "CALCULATION", "PROOF"}:
            question["type"] = question_type

        return question

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
        prompt = generator_prompts.VARIATION_PROMPT.format(
            original_question=original_question,
            original_difficulty=original_difficulty,
            target_difficulty=target_difficulty,
        )

        result = await self.run(
            user_message=prompt,
            temperature=0.7,
            max_tokens=4096,
        )
        return {"variation": result}


# 全局单例
generator_agent = GeneratorAgent()
