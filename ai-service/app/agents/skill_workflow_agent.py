"""
Skill workflow agent.

Adapts selected education skills into FamilyAgent-native AI workflows.
"""
import json
import logging
from typing import Any

from app.agents.base import BaseAgent
from app.llm.client import llm_client
from app.llm.prompts import skill_workflows
from app.llm.schemas import (
    DAILY_PRACTICE_SCHEMA,
    EXAM_REVIEW_SCHEMA,
    MISTAKE_REVIEW_SCHEMA,
    STUDY_PLAN_SCHEMA,
)

logger = logging.getLogger("familyagent.ai.skill_workflow")


class SkillWorkflowAgent(BaseAgent):
    """FamilyAgent skill workflow agent."""

    def __init__(self):
        super().__init__(
            name="SkillWorkflowAgent",
            system_prompt=skill_workflows.SKILL_SYSTEM_PROMPT,
        )

    async def _run_structured(
        self,
        prompt: str,
        response_format: dict,
        *,
        temperature: float = 0.5,
        max_tokens: int = 8192,
    ) -> dict[str, Any]:
        messages = self._build_messages(prompt)
        result = await llm_client.chat(
            messages,
            temperature=temperature,
            max_tokens=max_tokens,
            response_format=response_format,
        )
        try:
            parsed = json.loads(result)
            logger.info("[%s] workflow generated keys=%s", self.name, list(parsed.keys()))
            return parsed
        except json.JSONDecodeError:
            logger.error("[%s] workflow JSON parse failed", self.name)
            return {
                "raw_content": result,
                "missing_info": ["AI 返回内容不是有效 JSON，请重试。"],
            }

    def _list(self, value: Any) -> list:
        return value if isinstance(value, list) else []

    def _text(self, value: Any, fallback: str = "") -> str:
        return value if isinstance(value, str) and value.strip() else fallback

    def _difficulty(self, value: Any, fallback: int = 3) -> int:
        if isinstance(value, int):
            return min(5, max(1, value))
        if isinstance(value, float):
            return min(5, max(1, int(round(value))))
        if isinstance(value, str):
            value = value.strip()
            if value.isdigit():
                return min(5, max(1, int(value)))
            labels = {
                "基础": 1,
                "简单": 1,
                "入门": 1,
                "标准": 3,
                "中等": 3,
                "普通": 3,
                "提高": 4,
                "较难": 4,
                "困难": 5,
            }
            return labels.get(value, fallback)
        return fallback

    def _string_list(self, value: Any) -> list[str]:
        items = []
        for item in self._list(value):
            if isinstance(item, str):
                items.append(item)
            elif isinstance(item, dict):
                text = item.get("text") or item.get("content") or item.get("action") or item.get("object")
                items.append(str(text) if text is not None else json.dumps(item, ensure_ascii=False))
            else:
                items.append(str(item))
        return items

    def _normalize_daily_practice(self, data: dict[str, Any]) -> dict[str, Any]:
        questions = []
        for item in self._list(data.get("questions")):
            if not isinstance(item, dict):
                continue
            questions.append({
                "stem": self._text(item.get("stem"), "题目生成不完整，请重新生成。"),
                "answer": self._text(item.get("answer"), "暂无"),
                "explanation": self._text(item.get("explanation"), "暂无解析"),
                "difficulty": self._difficulty(item.get("difficulty")),
                "error_tags": [str(tag) for tag in self._list(item.get("error_tags"))],
            })
        return {
            "daily_goal": self._text(data.get("daily_goal"), "完成一次 10-15 分钟数学短练"),
            "warmup_prompt": self._text(data.get("warmup_prompt"), "先回想今天要练的核心方法。"),
            "questions": questions,
            "self_check": self._string_list(data.get("self_check")),
            "next_review_action": self._text(data.get("next_review_action"), "明天复做 2-3 道同类题。"),
            "missing_info": self._string_list(data.get("missing_info")),
        }

    def _normalize_mistake_review(self, data: dict[str, Any]) -> dict[str, Any]:
        review_plan = []
        for item in self._list(data.get("spaced_review_plan")):
            if isinstance(item, dict):
                review_plan.append({
                    "day_offset": int(item.get("day_offset") or 1),
                    "action": self._text(item.get("action"), "复做本题并口头复述思路。"),
                })
        return {
            "error_category": self._text(data.get("error_category"), "待复核"),
            "correct_solution_summary": self._text(data.get("correct_solution_summary"), "暂无完整解法摘要"),
            "correction_note": self._text(data.get("correction_note"), "先订正本题，再做一道同类题。"),
            "error_pattern": self._text(data.get("error_pattern"), "暂未形成稳定错误模式。"),
            "similar_question_suggestions": self._string_list(data.get("similar_question_suggestions")),
            "spaced_review_plan": review_plan,
            "parent_explanation": self._text(data.get("parent_explanation"), "建议结合学习者原答案复盘关键步骤。"),
            "missing_info": self._string_list(data.get("missing_info")),
        }

    def _normalize_exam_review(self, data: dict[str, Any]) -> dict[str, Any]:
        weak_points = []
        for item in self._list(data.get("priority_weak_points")):
            if isinstance(item, dict):
                weak_points.append({
                    "knowledge_point": self._text(item.get("knowledge_point"), "未命名知识点"),
                    "priority": self._text(item.get("priority"), "中"),
                    "reason": self._text(item.get("reason"), "需要结合最近测试继续观察。"),
                })
        daily_plan = []
        for item in self._list(data.get("daily_plan")):
            if isinstance(item, dict):
                daily_plan.append({
                    "day": int(item.get("day") or 1),
                    "focus": self._text(item.get("focus"), "复习薄弱点"),
                    "tasks": [str(task) for task in self._list(item.get("tasks"))],
                })
        return {
            "diagnosis": self._text(data.get("diagnosis"), "当前数据不足，建议先完成一次诊断后再生成完整建议。"),
            "priority_weak_points": weak_points,
            "daily_plan": daily_plan,
            "timed_practice": self._string_list(data.get("timed_practice")),
            "mistake_review_actions": self._string_list(data.get("mistake_review_actions")),
            "next_retest": self._text(data.get("next_retest"), "完成 3-5 天练习后复测。"),
            "risks": self._string_list(data.get("risks")),
            "missing_info": self._string_list(data.get("missing_info")),
        }

    def _normalize_study_plan(self, data: dict[str, Any]) -> dict[str, Any]:
        priorities = []
        for item in self._list(data.get("priorities")):
            if isinstance(item, dict):
                priorities.append({
                    "item": self._text(item.get("item"), "薄弱知识点"),
                    "priority": self._text(item.get("priority"), "中"),
                    "reason": self._text(item.get("reason"), "需要持续练习确认掌握。"),
                })
        daily_tasks = []
        for item in self._list(data.get("daily_tasks")):
            if isinstance(item, dict):
                daily_tasks.append({
                    "day": int(item.get("day") or 1),
                    "focus": self._text(item.get("focus"), "学习任务"),
                    "tasks": [str(task) for task in self._list(item.get("tasks"))],
                    "check_method": self._text(item.get("check_method"), "完成后复述方法并订正错题。"),
                })
        return {
            "plan_goal": self._text(data.get("plan_goal"), "完成一轮短周期数学学习计划"),
            "priorities": priorities,
            "daily_tasks": daily_tasks,
            "review_questions": self._string_list(data.get("review_questions")),
            "parent_support": self._string_list(data.get("parent_support")),
            "missing_info": self._string_list(data.get("missing_info")),
        }

    async def mistake_review(
        self,
        *,
        question_content: str,
        answer: str,
        student_answer: str,
        steps: str = "",
        grade_result: Any = None,
        grade: str = "初中",
        subject: str = "数学",
        knowledge_point: str = "未知",
        weak_points: Any = None,
    ) -> dict[str, Any]:
        prompt = skill_workflows.MISTAKE_REVIEW_PROMPT.format(
            grade=grade,
            subject=subject,
            knowledge_point=knowledge_point,
            question_content=question_content,
            answer=answer,
            steps=steps or "暂无",
            student_answer=student_answer,
            grade_result=json.dumps(grade_result or {}, ensure_ascii=False),
            weak_points=json.dumps(weak_points or [], ensure_ascii=False),
        )
        data = await self._run_structured(prompt, MISTAKE_REVIEW_SCHEMA, temperature=0.4)
        return self._normalize_mistake_review(data)

    async def daily_practice(
        self,
        *,
        knowledge_point: str,
        grade: str = "初中",
        subject: str = "数学",
        mastery_level: str = "中",
        available_minutes: int = 15,
        difficulty: str = "标准",
        question_count: int = 5,
        weak_points: Any = None,
        scenario: str = "学生自练",
    ) -> dict[str, Any]:
        prompt = skill_workflows.DAILY_PRACTICE_PROMPT.format(
            grade=grade,
            subject=subject,
            knowledge_point=knowledge_point,
            mastery_level=mastery_level,
            available_minutes=available_minutes,
            difficulty=difficulty,
            question_count=question_count,
            weak_points=json.dumps(weak_points or [], ensure_ascii=False),
            scenario=scenario,
        )
        data = await self._run_structured(prompt, DAILY_PRACTICE_SCHEMA, temperature=0.7)
        return self._normalize_daily_practice(data)

    async def exam_review(
        self,
        *,
        exam_goal: str,
        score_summary: str,
        grade: str = "初中",
        subject: str = "数学",
        profiles: Any = None,
        weak_points: Any = None,
        recent_mistakes: Any = None,
        available_minutes: int = 30,
        review_days: int = 7,
    ) -> dict[str, Any]:
        prompt = skill_workflows.EXAM_REVIEW_PROMPT.format(
            grade=grade,
            subject=subject,
            exam_goal=exam_goal,
            score_summary=score_summary,
            profiles=json.dumps(profiles or {}, ensure_ascii=False),
            weak_points=json.dumps(weak_points or [], ensure_ascii=False),
            recent_mistakes=json.dumps(recent_mistakes or [], ensure_ascii=False),
            available_minutes=available_minutes,
            review_days=review_days,
        )
        data = await self._run_structured(prompt, EXAM_REVIEW_SCHEMA, temperature=0.45)
        return self._normalize_exam_review(data)

    async def study_plan(
        self,
        *,
        learning_goal: str,
        grade: str = "初中",
        subject: str = "数学",
        profiles: Any = None,
        weak_points: Any = None,
        available_minutes: int = 30,
        plan_days: int = 7,
        constraints: str = "无",
    ) -> dict[str, Any]:
        prompt = skill_workflows.STUDY_PLAN_PROMPT.format(
            grade=grade,
            subject=subject,
            learning_goal=learning_goal,
            profiles=json.dumps(profiles or {}, ensure_ascii=False),
            weak_points=json.dumps(weak_points or [], ensure_ascii=False),
            available_minutes=available_minutes,
            plan_days=plan_days,
            constraints=constraints or "无",
        )
        data = await self._run_structured(prompt, STUDY_PLAN_SCHEMA, temperature=0.45)
        return self._normalize_study_plan(data)


skill_workflow_agent = SkillWorkflowAgent()
