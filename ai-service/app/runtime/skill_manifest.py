"""Typed declarations for executable AI skills."""

from dataclasses import asdict, dataclass

from app.config import settings


@dataclass(frozen=True)
class SkillManifest:
    name: str
    version: str
    description: str
    input_schema: str
    output_schema: str
    reads: tuple[str, ...]
    writes: tuple[str, ...]
    requires_confirmation: bool
    timeout_seconds: float
    privacy_level: str

    def as_dict(self) -> dict[str, object]:
        return asdict(self)


SAVE_MEMORY_PLAN_MANIFEST = SkillManifest(
    name="save_memory",
    version="1.0.0",
    description="Plan whether a conversation fragment should become a family record.",
    input_schema="save_tool_plan.request.v1",
    output_schema="save_tool_plan.response.v1",
    reads=("L0", "L4"),
    writes=("L0", "L1", "L4"),
    requires_confirmation=True,
    timeout_seconds=settings.ai_hard_timeout_seconds,
    privacy_level="FAMILY_DATA",
)
