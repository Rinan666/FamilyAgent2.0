"""Common execution boundary for AI skills."""

import asyncio
from collections.abc import Awaitable, Callable
from typing import TypeVar

from .skill_manifest import SkillManifest

ResultT = TypeVar("ResultT")


class SkillExecutor:
    """Execute a skill operation within its declared timeout budget."""

    async def execute(
        self,
        manifest: SkillManifest,
        operation: Callable[[], Awaitable[ResultT]],
    ) -> ResultT:
        try:
            return await asyncio.wait_for(operation(), timeout=manifest.timeout_seconds)
        except asyncio.TimeoutError as exc:
            raise TimeoutError(f"Skill {manifest.name} timed out") from exc
