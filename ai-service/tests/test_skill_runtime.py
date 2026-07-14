import asyncio

import pytest

from app.agents.family_skill_registry import get_family_skill
from app.runtime.skill_executor import SkillExecutor
from app.runtime.skill_manifest import SAVE_MEMORY_PLAN_MANIFEST, SkillManifest


def test_save_memory_manifest_is_versioned_and_exposed_in_registry():
    skill = get_family_skill("save_memory")

    assert skill is not None
    assert skill["version"] == SAVE_MEMORY_PLAN_MANIFEST.version
    assert skill["input_schema"] == "save_tool_plan.request.v1"
    assert skill["output_schema"] == "save_tool_plan.response.v1"
    assert skill["requires_confirmation"] is True
    assert skill["privacy_level"] == "FAMILY_DATA"


@pytest.mark.asyncio
async def test_skill_executor_enforces_declared_timeout():
    manifest = SkillManifest(
        name="test_skill",
        version="1.0.0",
        description="test",
        input_schema="test.input.v1",
        output_schema="test.output.v1",
        reads=(),
        writes=(),
        requires_confirmation=False,
        timeout_seconds=0.01,
        privacy_level="NON_SENSITIVE",
    )

    async def slow_operation():
        await asyncio.sleep(0.1)

    with pytest.raises(TimeoutError, match="test_skill"):
        await SkillExecutor().execute(manifest, slow_operation)
