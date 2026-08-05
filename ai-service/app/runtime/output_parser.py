"""Output parsers for executable skills."""

import json

from app.api.memory_helpers import _sanitize_memory_save_plan
from app.api.memory_models import MemorySavePlanData


class SkillOutputParseError(ValueError):
    """Raised when a skill provider returns an invalid structured payload."""


class SaveMemoryOutputParser:
    """Parse and apply the deterministic value guard to save plans."""

    def parse(self, raw: str) -> MemorySavePlanData:
        try:
            return MemorySavePlanData.model_validate(
                _sanitize_memory_save_plan(json.loads(raw))
            )
        except (json.JSONDecodeError, TypeError, ValueError) as exc:
            raise SkillOutputParseError("Invalid save-memory plan output") from exc
