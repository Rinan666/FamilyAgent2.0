import pytest
from fastapi import HTTPException

from app.agents.family_skill_registry import (
    family_skill_registry,
    get_family_skill,
    list_family_skills,
    list_memory_layers,
)
from app.api.memory import get_family_skill_registry_item, list_family_skill_registry


def test_memory_layers_define_expected_long_term_structure():
    layers = list_memory_layers()
    layer_ids = [layer["id"] for layer in layers]

    assert layer_ids == ["L0", "L1", "L2", "L3", "L4"]
    assert layers[0]["name"] == "raw_records"
    assert "diary_entries" in layers[0]["stores"]
    assert layers[3]["name"] == "member_profiles"


def test_family_skills_include_active_memory_workflows():
    skills = list_family_skills()
    names = {skill["name"] for skill in skills}

    assert {
        "save_memory",
        "organize_draft",
        "persona_material_draft",
    }.issubset(names)

    save_memory = get_family_skill("save_memory")
    assert save_memory is not None
    assert save_memory["requires_confirmation"] is True
    assert "L0" in save_memory["writes"]
    assert "USER_CONFIRMATION" in save_memory["confirmation_policy"]

    persona_material = get_family_skill("persona_material_draft")
    assert persona_material is not None
    assert persona_material["endpoint"] == "/ai/memory/persona-material-draft"
    assert persona_material["requires_confirmation"] is True
    assert persona_material["confirmation_policy"] == "RETURNS_DRAFT_ONLY"
    assert persona_material["prompt_version"] == "persona.material_draft.v1"
    assert persona_material["schema_version"] == "persona_material_draft.schema.v1"

    organize_draft = get_family_skill("organize_draft")
    assert organize_draft is not None
    assert organize_draft["prompt_version"] == "memory.organize_draft.v1"
    assert organize_draft["schema_version"] == "organized_draft.schema.v1"


def test_family_skill_status_filter_is_case_insensitive():
    active_skills = list_family_skills("active")
    planned_skills = list_family_skills("PLANNED")

    assert active_skills
    assert all(skill["status"] == "ACTIVE" for skill in active_skills)
    assert {skill["name"] for skill in planned_skills} == {
        "member_profile_rebuild",
        "mirror_context_prepare",
    }


def test_registry_payload_is_defensive_copy():
    registry = family_skill_registry()
    registry["skills"][0]["name"] = "mutated"

    assert get_family_skill("save_memory") is not None
    assert get_family_skill("mutated") is None


def test_memory_api_registry_helpers_return_success_payloads():
    response = list_family_skill_registry(status="ACTIVE")
    data = response["data"]

    assert response["success"] is True
    assert data["memory_layers"]
    assert all(skill["status"] == "ACTIVE" for skill in data["skills"])

    item_response = get_family_skill_registry_item("save_memory")
    assert item_response["success"] is True
    assert item_response["data"]["name"] == "save_memory"


def test_memory_api_registry_item_returns_404_for_unknown_skill():
    with pytest.raises(HTTPException) as exc_info:
        get_family_skill_registry_item("unknown_skill")

    assert exc_info.value.status_code == 404
