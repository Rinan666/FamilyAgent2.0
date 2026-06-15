import json

import pytest

from app.agents.family_agent import family_agent


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
