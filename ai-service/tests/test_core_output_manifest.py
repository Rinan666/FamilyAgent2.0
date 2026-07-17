from pathlib import Path

import pytest

from app.runtime.core_output_manifest import (
    BACKEND_RECALL_ALGORITHM_VERSION,
    CORE_OUTPUT_MANIFEST,
    core_output_manifest,
)


def test_core_outputs_declare_applicable_versions_and_eval_bindings():
    items = {item.capability: item for item in CORE_OUTPUT_MANIFEST}

    assert set(items) == {
        "family_chat",
        "save_memory_plan",
        "organize_draft",
        "persona_material_draft",
        "memory_recall_ranking",
    }
    assert items["family_chat"].prompt_version
    for capability in ("save_memory_plan", "organize_draft", "persona_material_draft"):
        item = items[capability]
        assert item.skill_version
        assert item.prompt_version
        assert item.schema_version
    assert items["memory_recall_ranking"].algorithm_version
    assert all(item.provider_observation_required_when_external for item in items.values())
    assert all(item.eval_binding for item in items.values())

    payload = core_output_manifest()
    assert all("family_content" not in item for item in payload)
    assert all("model_output" not in item for item in payload)


def test_backend_recall_algorithm_version_matches_cross_service_contract():
    repository_root = Path(__file__).resolve().parents[2]
    ranking_source = repository_root / (
        "backend/src/main/java/com/familyagent/module/memory/service/"
        "AuthorizedMemoryRecallRankingService.java"
    )
    if not ranking_source.exists():
        pytest.skip("Backend source is not mounted in the AI Service test container")

    assert f'ALGORITHM_VERSION = "{BACKEND_RECALL_ALGORITHM_VERSION}"' in ranking_source.read_text(
        encoding="utf-8"
    )
