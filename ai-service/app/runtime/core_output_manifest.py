"""Machine-readable version requirements for core AI outputs."""

from __future__ import annotations

from dataclasses import asdict, dataclass

from app.runtime.artifact_versions import FAMILY_CHAT_PROMPT_VERSION
from app.runtime.skill_manifest import (
    ORGANIZE_DRAFT_MANIFEST,
    PERSONA_MATERIAL_DRAFT_MANIFEST,
    SAVE_MEMORY_PLAN_MANIFEST,
    SkillManifest,
)

BACKEND_RECALL_ALGORITHM_VERSION = "authorized-memory-recall.v1"


@dataclass(frozen=True)
class CoreOutputManifestItem:
    capability: str
    owner: str
    skill_version: str | None
    prompt_version: str | None
    schema_version: str | None
    algorithm_version: str | None
    provider_observation_required_when_external: bool
    eval_binding: str

    def as_dict(self) -> dict[str, object]:
        return asdict(self)


def _skill_manifest_item(
    capability: str,
    manifest: SkillManifest,
    eval_binding: str,
) -> CoreOutputManifestItem:
    return CoreOutputManifestItem(
        capability=capability,
        owner="ai-service",
        skill_version=manifest.version,
        prompt_version=manifest.prompt_version,
        schema_version=manifest.schema_version,
        algorithm_version=None,
        provider_observation_required_when_external=True,
        eval_binding=eval_binding,
    )


CORE_OUTPUT_MANIFEST = (
    CoreOutputManifestItem(
        capability="family_chat",
        owner="ai-service",
        skill_version=None,
        prompt_version=FAMILY_CHAT_PROMPT_VERSION,
        schema_version=None,
        algorithm_version=None,
        provider_observation_required_when_external=True,
        eval_binding="family-chat-stream-contract",
    ),
    _skill_manifest_item("save_memory_plan", SAVE_MEMORY_PLAN_MANIFEST, "save-memory-plan-eval"),
    _skill_manifest_item("organize_draft", ORGANIZE_DRAFT_MANIFEST, "organize-draft-eval"),
    _skill_manifest_item(
        "persona_material_draft",
        PERSONA_MATERIAL_DRAFT_MANIFEST,
        "persona-material-draft-eval",
    ),
    CoreOutputManifestItem(
        capability="memory_recall_ranking",
        owner="backend",
        skill_version=None,
        prompt_version=None,
        schema_version=None,
        algorithm_version=BACKEND_RECALL_ALGORITHM_VERSION,
        provider_observation_required_when_external=True,
        eval_binding="memory-recall-quality-eval",
    ),
)


def core_output_manifest() -> list[dict[str, object]]:
    return [item.as_dict() for item in CORE_OUTPUT_MANIFEST]
