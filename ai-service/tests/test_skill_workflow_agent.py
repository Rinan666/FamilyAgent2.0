"""
Skill workflow agent tests.
"""
import json

import pytest

from app.agents.skill_workflow_agent import SkillWorkflowAgent
from app.llm.schemas import DAILY_PRACTICE_SCHEMA


class TestSkillWorkflowAgent:
    """Skill workflow agent."""

    def setup_method(self):
        self.agent = SkillWorkflowAgent()

    @pytest.mark.asyncio
    async def test_daily_practice_uses_structured_schema(self, monkeypatch):
        calls = {}
        payload = {
            "daily_goal": "巩固一元一次方程移项",
            "warmup_prompt": "先说说移项时符号为什么会变。",
            "questions": [{
                "stem": "解方程 2x + 3 = 9",
                "answer": "x = 3",
                "explanation": "两边同时减 3，再除以 2。",
                "difficulty": 2,
                "error_tags": ["移项", "计算"],
            }],
            "self_check": ["能说清楚每一步为什么成立"],
            "next_review_action": "明天再做 3 道同类题。",
            "missing_info": [],
        }

        async def fake_chat(messages, temperature, max_tokens, response_format):
            calls["messages"] = messages
            calls["temperature"] = temperature
            calls["max_tokens"] = max_tokens
            calls["response_format"] = response_format
            return json.dumps(payload, ensure_ascii=False)

        monkeypatch.setattr(
            "app.agents.skill_workflow_agent.llm_client.chat",
            fake_chat,
        )

        result = await self.agent.daily_practice(
            knowledge_point="一元一次方程",
            weak_points=["移项符号错误"],
        )

        assert result == payload
        assert calls["response_format"] == DAILY_PRACTICE_SCHEMA
        assert calls["messages"][0]["role"] == "system"
        assert "一元一次方程" in calls["messages"][1]["content"]
        assert "移项符号错误" in calls["messages"][1]["content"]

    @pytest.mark.asyncio
    async def test_invalid_json_returns_fallback(self, monkeypatch):
        async def fake_chat(messages, temperature, max_tokens, response_format):
            return "不是 JSON"

        monkeypatch.setattr(
            "app.agents.skill_workflow_agent.llm_client.chat",
            fake_chat,
        )

        result = await self.agent.study_plan(learning_goal="本周提升方程应用题")

        assert result["plan_goal"]
        assert result["daily_tasks"] == []
        assert result["missing_info"]
