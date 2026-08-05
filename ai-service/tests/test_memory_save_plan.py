import json

import pytest

from app.api import memory
from app.api.memory import plan_agent_memory_save
from app.api.memory_helpers import _sanitize_memory_save_plan
from app.api.memory_models import MemorySavePlanRequest
from app.api.memory_save_signals import _looks_like_save_command_only


def _raw_plan(**overrides):
    plan = {
        "should_save": True,
        "memory_library": "PERSONAL",
        "memory_type": "NOTE",
        "content": "今天发生了一件值得记录的具体事情。",
        "title": "一条记录",
        "summary": "今天发生了一件值得记录的事情。",
        "visibility": "PRIVATE",
        "importance": 3,
        "tags": ["记录"],
        "reason": "按用户要求整理为草稿。",
        "confirmation_message": "草稿已准备，请确认后保存。",
    }
    plan.update(overrides)
    return plan


def test_save_to_memory_library_command_is_detected_as_command_only():
    assert _looks_like_save_command_only("保存到记忆库吧") is True


def test_memory_save_plan_uses_only_unified_library_and_database_type():
    plan = _sanitize_memory_save_plan(_raw_plan(
        memory_library="FAMILY",
        memory_type="OBSERVATION",
        content="孩子最近看黑板会眯眼，我准备继续观察并安排检查。",
        visibility="FAMILY_VISIBLE",
    ))

    assert plan["memory_library"] == "FAMILY"
    assert plan["memory_type"] == "OBSERVATION"
    assert plan["visibility"] == "CARE_VISIBLE"
    assert "tool" not in plan
    assert "entry_type" not in plan


def test_all_database_memory_types_are_preserved():
    memory_types = {
        "NOTE", "KNOWLEDGE", "INSIGHT", "EXPERIENCE",
        "OBSERVATION", "PREFERENCE", "PLAN",
    }

    assert {
        _sanitize_memory_save_plan(_raw_plan(memory_type=memory_type))["memory_type"]
        for memory_type in memory_types
    } == memory_types


def test_unknown_contract_values_are_rejected():
    with pytest.raises(ValueError, match="Unsupported memory save contract value"):
        _sanitize_memory_save_plan(_raw_plan(memory_type="UNKNOWN"))


def test_command_only_content_is_not_persistable():
    plan = _sanitize_memory_save_plan(_raw_plan(content="保存一下"))

    assert plan["should_save"] is False
    assert plan["content"] == ""
    assert plan["memory_library"] == "PERSONAL"
    assert plan["memory_type"] == "NOTE"


def test_long_content_is_bounded_without_changing_library_or_type():
    plan = _sanitize_memory_save_plan(_raw_plan(
        memory_library="FAMILY",
        memory_type="EXPERIENCE",
        content="家庭故事" * 400,
    ))

    assert len(plan["content"]) <= 1200
    assert plan["memory_library"] == "FAMILY"
    assert plan["memory_type"] == "EXPERIENCE"


@pytest.mark.asyncio
async def test_memory_save_plan_provider_failure_is_structured_and_private(monkeypatch, caplog):
    async def fail_chat(*args, **kwargs):
        raise RuntimeError("provider secret outage detail")

    monkeypatch.setattr(memory.llm_client, "chat", fail_chat)

    response = await plan_agent_memory_save(MemorySavePlanRequest(
        message="孩子最近做应用题总是没读完题就列式，我想提醒他先复述题意。"
    ))

    assert response["success"] is False
    assert response["errorCode"] == "AI_PROVIDER_ERROR"
    assert response["data"]["should_save"] is False
    assert response["data"]["memory_library"] == "PERSONAL"
    assert response["data"]["memory_type"] == "NOTE"
    assert "provider secret outage detail" not in json.dumps(response, ensure_ascii=False)
    assert "provider secret outage detail" not in caplog.text


@pytest.mark.asyncio
async def test_memory_save_plan_invalid_json_is_structured_failure(monkeypatch):
    async def invalid_json(*args, **kwargs):
        return "not json"

    monkeypatch.setattr(memory.llm_client, "chat", invalid_json)

    response = await plan_agent_memory_save(MemorySavePlanRequest(
        message="今天我和妈妈聊志愿选择，发现自己更在意长期能不能坚持。"
    ))

    assert response["success"] is False
    assert response["errorCode"] == "AI_INVALID_RESPONSE"
    assert response["data"]["should_save"] is False


@pytest.mark.asyncio
async def test_memory_save_plan_returns_unified_contract(monkeypatch):
    async def draft_chat(*args, **kwargs):
        return json.dumps(_raw_plan(
            memory_library="FAMILY",
            memory_type="KNOWLEDGE",
            content="做应用题前先复述题意，再画线段图。",
            visibility="FAMILY_VISIBLE",
        ), ensure_ascii=False)

    monkeypatch.setattr(memory.llm_client, "chat", draft_chat)

    response = await plan_agent_memory_save(MemorySavePlanRequest(
        message="把做应用题前先复述题意，再画线段图保存为家庭新知。"
    ))

    assert response["success"] is True
    assert response["data"]["memory_library"] == "FAMILY"
    assert response["data"]["memory_type"] == "KNOWLEDGE"
    assert "tool" not in response["data"]
