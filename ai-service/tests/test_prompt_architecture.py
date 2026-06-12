import json

import pytest

from app.agents.family_agent import family_agent
from app.api import growth
from app.llm.prompts.growth import WEEKLY_REPORT_SYSTEM_PROMPT


class _WebSearchContext:
    def __init__(self):
        self.needed = False
        self.results = []
        self.prompt_context = "未触发联网搜索。"


@pytest.mark.asyncio
async def test_family_agent_chat_stream_builds_messages_without_behavior_drift(monkeypatch):
    captured: dict = {}

    async def fake_build_web_search_context(message: str, response_mode: str = "think"):
        captured["member_message"] = message
        captured["response_mode"] = response_mode
        return _WebSearchContext()

    async def fake_chat_stream(messages, temperature=0.7):
        captured["messages"] = messages
        captured["temperature"] = temperature
        yield "第一段"
        yield "第二段"

    monkeypatch.setattr(
        "app.agents.family_agent.build_web_search_context",
        fake_build_web_search_context,
    )
    monkeypatch.setattr("app.agents.family_agent.llm_client.chat_stream", fake_chat_stream)

    chunks = []
    async for chunk in family_agent.chat_stream(
        member_message="请帮我分析这段家庭冲突",
        history=[{"role": "assistant", "content": "之前的上下文"}],
        subject="MirrorAgent",
        context_label="mirror_agent",
        memory_context="授权上下文",
        viewer_role="PARENT",
        target_role="MEMBER",
        client_timestamp="2026-06-12T09:00:00+08:00",
        client_timezone="Asia/Shanghai",
    ):
        chunks.append(chunk)

    assert chunks[0]["type"] == "metadata"
    assert chunks[0]["response_mode"] == "think"
    assert chunks[0]["thinking_summary"]
    assert chunks[1] == {
        "type": "metadata",
        "response_mode": "think",
        "web_search": {
            "needed": False,
            "used": False,
            "pending": False,
            "result_count": 0,
            "sources": [],
        },
    }
    assert chunks[2] == {"type": "content", "content": "第一段"}
    assert chunks[3] == {"type": "content", "content": "第二段"}
    assert captured["member_message"] == "请帮我分析这段家庭冲突"
    assert captured["response_mode"] == "think"
    assert captured["temperature"] == 0.7
    assert captured["messages"][0]["role"] == "system"
    assert "MirrorAgent" in captured["messages"][0]["content"]
    assert "授权上下文" in captured["messages"][0]["content"]
    assert captured["messages"][1] == {"role": "assistant", "content": "之前的上下文"}
    assert captured["messages"][2] == {"role": "user", "content": "请帮我分析这段家庭冲突"}


@pytest.mark.asyncio
async def test_weekly_report_uses_prompt_module_and_returns_existing_shape(monkeypatch):
    captured: dict = {}

    async def fake_chat(*, messages, temperature, max_tokens, response_format):
        captured["messages"] = messages
        captured["temperature"] = temperature
        captured["max_tokens"] = max_tokens
        captured["response_format"] = response_format
        return json.dumps(
            {
                "title": "成长守护摘要",
                "summary": "最近孩子在作息上有波动，但家人已经开始留意。",
                "affirmations": ["家人开始持续观察作息变化。"],
                "concerns": ["最近入睡时间偏晚。"],
                "signals": ["近两周睡前刷屏时间增加。"],
                "uncertainty_notes": ["目前主要来自家长观察，缺少本人反馈。"],
                "family_experience_refs": ["家里已有提醒晚间减少屏幕时间的经验。"],
                "suggested_actions": ["本周先连续记录 3 天睡前状态。"],
                "follow_up_questions": ["晚睡通常发生在什么情境下？"],
                "safety_note": "这是一份照护者可见的成长观察摘要，不构成医疗或心理诊断。",
            },
            ensure_ascii=False,
        )

    monkeypatch.setattr(growth.llm_client, "chat", fake_chat)

    response = await growth.weekly_report(
        growth.WeeklyReportRequest(
            family_name="测试家庭",
            target="孩子",
            records=[{"category": "SLEEP", "content": "最近入睡偏晚"}],
            memories=[{"type": "HEALTH_REMINDER", "content": "晚上少看屏幕"}],
        )
    )

    assert response["success"] is True
    assert response["data"]["title"] == "成长守护摘要"
    assert captured["messages"][0] == {"role": "system", "content": WEEKLY_REPORT_SYSTEM_PROMPT}
    assert captured["messages"][1]["role"] == "user"
    assert "测试家庭" in captured["messages"][1]["content"]
    assert captured["temperature"] == 0.2
    assert captured["max_tokens"] == 1000
    assert captured["response_format"] == growth.WEEKLY_REPORT_SCHEMA
