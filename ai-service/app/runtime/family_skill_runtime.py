"""Registered FamilyAgent skill runtimes."""

from .skill_executor import SkillExecutor
from .skill_manifest import (
    ORGANIZE_DRAFT_MANIFEST,
    PERSONA_MATERIAL_DRAFT_MANIFEST,
    SAVE_MEMORY_PLAN_MANIFEST,
)
from .skill_registry import SkillRuntimeRegistry

family_skill_runtime_registry = SkillRuntimeRegistry()
save_memory_skill_runtime = family_skill_runtime_registry.register(
    SAVE_MEMORY_PLAN_MANIFEST,
    SkillExecutor(),
)
organize_draft_skill_runtime = family_skill_runtime_registry.register(
    ORGANIZE_DRAFT_MANIFEST,
    SkillExecutor(),
)
persona_material_draft_skill_runtime = family_skill_runtime_registry.register(
    PERSONA_MATERIAL_DRAFT_MANIFEST,
    SkillExecutor(),
)
