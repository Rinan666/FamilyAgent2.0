"""Output parsers for executable skills."""

import json

from app.api.memory_helpers import _sanitize_save_tool_plan
from app.api.memory_models import SaveToolPlanData


class SkillOutputParseError(ValueError):
    """Raised when a skill provider returns an invalid structured payload."""


class SaveMemoryOutputParser:
    """Parse and apply the deterministic value guard to save plans."""

    def parse(self, raw: str) -> SaveToolPlanData:
        try:
            return SaveToolPlanData.model_validate(
                _sanitize_save_tool_plan(json.loads(raw))
            )
        except (json.JSONDecodeError, TypeError, ValueError) as exc:
            raise SkillOutputParseError("Invalid save-memory plan output") from exc
