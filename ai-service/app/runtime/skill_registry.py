"""Typed registry for executable AI skill runtimes."""

from collections.abc import Awaitable, Callable
from dataclasses import dataclass
from typing import TypeVar

from .skill_executor import SkillExecutor
from .skill_manifest import SkillManifest

ResultT = TypeVar("ResultT")


@dataclass(frozen=True)
class SkillRuntime:
    manifest: SkillManifest
    executor: SkillExecutor

    async def execute(self, operation: Callable[[], Awaitable[ResultT]]) -> ResultT:
        return await self.executor.execute(self.manifest, operation)


class SkillRuntimeRegistry:
    """Associate stable skill names with their manifest and executor."""

    def __init__(self) -> None:
        self._runtimes: dict[str, SkillRuntime] = {}

    def register(self, manifest: SkillManifest, executor: SkillExecutor) -> SkillRuntime:
        normalized_name = manifest.name.strip().lower()
        if normalized_name in self._runtimes:
            raise ValueError(f"Skill runtime already registered: {manifest.name}")
        runtime = SkillRuntime(manifest=manifest, executor=executor)
        self._runtimes[normalized_name] = runtime
        return runtime

    def get(self, name: str) -> SkillRuntime:
        normalized_name = name.strip().lower()
        try:
            return self._runtimes[normalized_name]
        except KeyError as exc:
            raise KeyError(f"Skill runtime not found: {normalized_name}") from exc
