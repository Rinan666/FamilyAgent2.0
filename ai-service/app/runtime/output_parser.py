"""Output parsers for executable skills."""

import json
from typing import Any

from app.api.memory_helpers import _sanitize_save_tool_plan


class SkillOutputParseError(ValueError):
    """Raised when a skill provider returns an invalid structured payload."""


class SaveMemoryOutputParser:
    """Parse and apply the deterministic value guard to save plans."""

    def parse(self, raw: str) -> dict[str, Any]:
        try:
            return _sanitize_save_tool_plan(json.loads(raw))
        except (json.JSONDecodeError, TypeError, ValueError) as exc:
            raise SkillOutputParseError("Invalid save-memory plan output") from exc
