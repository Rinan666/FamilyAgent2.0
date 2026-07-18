import json

import pytest

from app.api import memory
from app.api.memory_models import (
    OrganizeDraftRequest,
    PersonaMaterialDraftRequest,
    PersonaProfileInput,
)
from app.runtime.draft_output_parser import OrganizeDraftOutputParser, PersonaMaterialOutputParser
from app.runtime.draft_prompt_renderer import OrganizeDraftPromptRenderer, PersonaMaterialPromptRenderer
from app.runtime.skill_manifest import ORGANIZE_DRAFT_MANIFEST, PERSONA_MATERIAL_DRAFT_MANIFEST
from app.runtime.skill_registry import SkillRuntime
from app.use_cases.organize_draft import OrganizeDraftUseCase
from app.use_cases.persona_material_draft import PersonaMaterialDraftUseCase


@pytest.mark.asyncio
async def test_organize_draft_provider_failure_is_structured_and_private(monkeypatch, caplog):
    async def fail_chat(**_kwargs):
        raise RuntimeError("private provider outage detail")

    monkeypatch.setattr(memory.llm_client, "chat", fail_chat)

    response = await memory.organize_family_draft(
        OrganizeDraftRequest(content="今天孩子愿意先复述题意，再开始列式。")
    )

    assert response["success"] is False
    assert response["data"] is None
    assert response["errorCode"] == "AI_PROVIDER_ERROR"
    assert "private provider outage detail" not in json.dumps(response)
    assert "private provider outage detail" not in caplog.text


@pytest.mark.asyncio
async def test_organize_draft_invalid_output_is_structured(monkeypatch):
    async def invalid_chat(**_kwargs):
        return "not-json"

    monkeypatch.setattr(memory.llm_client, "chat", invalid_chat)

    response = await memory.organize_family_draft(
        OrganizeDraftRequest(content="今天孩子愿意先复述题意，再开始列式。")
    )

    assert response["success"] is False
    assert response["errorCode"] == "AI_INVALID_RESPONSE"


@pytest.mark.asyncio
async def test_persona_material_provider_failure_is_structured_and_private(monkeypatch, caplog):
    async def fail_chat(**_kwargs):
        raise RuntimeError("private persona provider detail")

    monkeypatch.setattr(memory.llm_client, "chat", fail_chat)

    response = await memory.organize_persona_material_draft(
        PersonaMaterialDraftRequest(content="外公年轻时经营小店，常提醒家里人账目要写清楚。")
    )

    assert response["success"] is False
    assert response["data"] is None
    assert response["errorCode"] == "AI_PROVIDER_ERROR"
    assert "private persona provider detail" not in json.dumps(response)
    assert "private persona provider detail" not in caplog.text


@pytest.mark.asyncio
async def test_persona_material_invalid_output_is_structured(monkeypatch):
    async def invalid_chat(**_kwargs):
        return "[]"

    monkeypatch.setattr(memory.llm_client, "chat", invalid_chat)

    response = await memory.organize_persona_material_draft(
        PersonaMaterialDraftRequest(content="外公年轻时经营小店，常提醒家里人账目要写清楚。")
    )

    assert response["success"] is False
    assert response["errorCode"] == "AI_INVALID_RESPONSE"


def test_organize_draft_prompt_renderer_redacts_ai_bound_context():
    messages = OrganizeDraftPromptRenderer().render(
        scene="DIARY",
        current_type="DAILY",
        current_visibility="PRIVATE",
        target="孩子",
        family_context="联系电话 13812345678",
        content="住址：上海市浦东新区世纪大道100号。今天聊了学习计划。",
    )

    rendered = messages[1]["content"]
    assert "13812345678" not in rendered
    assert "世纪大道100号" not in rendered


def test_persona_prompt_renderer_redacts_profile_and_content():
    messages = PersonaMaterialPromptRenderer().render(
        profile=PersonaProfileInput(
            name="外公",
            description="联系电话 13812345678",
        ).model_dump(),
        family_context="住址：上海市浦东新区世纪大道100号",
        content="材料联系电话 13912345678",
    )

    rendered = messages[1]["content"]
    assert "13812345678" not in rendered
    assert "13912345678" not in rendered
    assert "世纪大道100号" not in rendered


def test_draft_output_parsers_apply_deterministic_bounds():
    organized = OrganizeDraftOutputParser().parse(
        '{"title":"","content":"有效的家庭记录内容","diary_entry_type":"UNKNOWN"}',
        scene="DIARY",
        fallback_content="家庭记录",
    )
    persona = PersonaMaterialOutputParser().parse(
        '{"profile":{"name":""},"materials":[],"reason":""}',
        fallback_profile={"name": "外公"},
        fallback_content="家庭材料",
    )

    assert organized.title == "未命名记录"
    assert organized.diary_entry_type == "DAILY"
    assert persona.profile.name == "外公"


@pytest.mark.asyncio
async def test_organize_draft_success_serializes_stable_typed_contract(monkeypatch):
    async def valid_chat(**_kwargs):
        return json.dumps({
            "title": "Family lesson",
            "content": "A concrete family experience worth preserving.",
            "tags": ["lesson"],
            "diary_entry_type": "LESSON",
            "diary_visibility": "PRIVATE",
            "memory_type": "ELDER_ADVICE",
            "memory_scope": "CARE_VISIBLE",
            "growth_category": "OTHER",
            "growth_severity": 2,
            "scenario": "conversation",
            "reason": "Reusable family experience",
        })

    monkeypatch.setattr(memory.llm_client, "chat", valid_chat)

    response = await memory.organize_family_draft(
        OrganizeDraftRequest(content="A concrete family experience worth preserving.")
    )

    assert set(response) == {"success", "data"}
    assert response["success"] is True
    assert response["data"]["diary_entry_type"] == "LESSON"
    assert response["data"]["growth_severity"] == 2


@pytest.mark.asyncio
async def test_persona_material_success_serializes_stable_typed_contract(monkeypatch):
    async def valid_chat(**_kwargs):
        return json.dumps({
            "profile": {
                "name": "Grandfather",
                "description": "Kept careful records for the family shop.",
                "era_identity": "Shopkeeper",
                "values": "Clarity and responsibility",
                "speaking_style": "Direct and practical",
                "personality": "Patient",
            },
            "materials": [{
                "title": "Clear accounts",
                "content": "Every family expense should be recorded clearly.",
                "tags": ["responsibility"],
            }],
            "reason": "A reusable family value",
        })

    monkeypatch.setattr(memory.llm_client, "chat", valid_chat)

    response = await memory.organize_persona_material_draft(
        PersonaMaterialDraftRequest(
            content="Grandfather taught the family to keep every account clear."
        )
    )

    assert set(response) == {"success", "data"}
    assert response["success"] is True
    assert response["data"]["profile"]["name"] == "Grandfather"
    assert response["data"]["materials"][0]["title"] == "Clear accounts"


@pytest.mark.asyncio
@pytest.mark.parametrize("skill_name", ["organize", "persona"])
async def test_draft_use_cases_map_timeout_to_structured_failure(skill_name):
    class TimeoutExecutor:
        async def execute(self, manifest, operation):
            raise TimeoutError(f"Skill {manifest.name} timed out")

    if skill_name == "organize":
        use_case = OrganizeDraftUseCase(
            SkillRuntime(ORGANIZE_DRAFT_MANIFEST, TimeoutExecutor()),
            OrganizeDraftPromptRenderer(),
            OrganizeDraftOutputParser(),
        )
        request = OrganizeDraftRequest(content="今天发生了一件值得整理的家庭事情。")
    else:
        use_case = PersonaMaterialDraftUseCase(
            SkillRuntime(PERSONA_MATERIAL_DRAFT_MANIFEST, TimeoutExecutor()),
            PersonaMaterialPromptRenderer(),
            PersonaMaterialOutputParser(),
        )
        request = PersonaMaterialDraftRequest(content="外公讲过一段值得整理的家庭经验。")

    response = await use_case.execute(request, llm_client=None)

    assert response["success"] is False
    assert response["errorCode"] == "AI_TIMEOUT"
    assert response["data"] is None
