"""Typed declarations for executable AI skills."""

from dataclasses import asdict, dataclass

from app.config import settings
from app.runtime.artifact_versions import (
    ORGANIZED_DRAFT_SCHEMA_VERSION,
    ORGANIZE_DRAFT_PROMPT_VERSION,
    PERSONA_MATERIAL_DRAFT_SCHEMA_VERSION,
    PERSONA_MATERIAL_PROMPT_VERSION,
    SAVE_MEMORY_PROMPT_VERSION,
    SAVE_TOOL_PLAN_SCHEMA_VERSION,
)


@dataclass(frozen=True)
class SkillManifest:
    name: str
    version: str
    description: str
    input_schema: str
    output_schema: str
    prompt_version: str
    schema_version: str
    reads: tuple[str, ...]
    writes: tuple[str, ...]
    requires_confirmation: bool
    timeout_seconds: float
    privacy_level: str

    def as_dict(self) -> dict[str, object]:
        payload = asdict(self)
        payload["reads"] = list(self.reads)
        payload["writes"] = list(self.writes)
        return payload


SAVE_MEMORY_PLAN_MANIFEST = SkillManifest(
    name="save_memory",
    version="1.0.0",
    description="Plan whether a conversation fragment should become a family record.",
    input_schema="save_tool_plan.request.v1",
    output_schema="save_tool_plan.response.v1",
    prompt_version=SAVE_MEMORY_PROMPT_VERSION,
    schema_version=SAVE_TOOL_PLAN_SCHEMA_VERSION,
    reads=("L0", "L4"),
    writes=("L0", "L1", "L4"),
    requires_confirmation=True,
    timeout_seconds=settings.ai_hard_timeout_seconds,
    privacy_level="FAMILY_DATA",
)

ORGANIZE_DRAFT_MANIFEST = SkillManifest(
    name="organize_draft",
    version="1.0.0",
    description="Organize family source text into an editable structured draft.",
    input_schema="organize_draft.request.v1",
    output_schema="organize_draft.response.v1",
    prompt_version=ORGANIZE_DRAFT_PROMPT_VERSION,
    schema_version=ORGANIZED_DRAFT_SCHEMA_VERSION,
    reads=("L0",),
    writes=("L1",),
    requires_confirmation=True,
    timeout_seconds=settings.ai_hard_timeout_seconds,
    privacy_level="FAMILY_DATA",
)

PERSONA_MATERIAL_DRAFT_MANIFEST = SkillManifest(
    name="persona_material_draft",
    version="1.0.0",
    description="Organize persona source text into profile suggestions and material cards.",
    input_schema="persona_material_draft.request.v1",
    output_schema="persona_material_draft.response.v1",
    prompt_version=PERSONA_MATERIAL_PROMPT_VERSION,
    schema_version=PERSONA_MATERIAL_DRAFT_SCHEMA_VERSION,
    reads=("L0",),
    writes=("L1",),
    requires_confirmation=True,
    timeout_seconds=settings.ai_hard_timeout_seconds,
    privacy_level="FAMILY_DATA",
)
