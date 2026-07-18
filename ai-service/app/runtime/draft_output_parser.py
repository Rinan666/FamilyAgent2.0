"""Output parsers for draft-only family skills."""

import json
from typing import Any

from app.api.memory_generation_helpers import (
    _sanitize_organized_draft,
    _sanitize_persona_material_draft,
)
from app.api.memory_models import OrganizedDraftData

from .output_parser import SkillOutputParseError


class OrganizeDraftOutputParser:
    def parse(self, raw: str, *, scene: str, fallback_content: str) -> OrganizedDraftData:
        try:
            data = json.loads(raw)
            if not isinstance(data, dict):
                raise TypeError("Draft output must be an object")
            return OrganizedDraftData.model_validate(
                _sanitize_organized_draft(data, scene, fallback_content)
            )
        except (json.JSONDecodeError, TypeError, ValueError) as exc:
            raise SkillOutputParseError("Invalid organize-draft output") from exc


class PersonaMaterialOutputParser:
    def parse(
        self,
        raw: str,
        *,
        fallback_profile: dict[str, str],
        fallback_content: str,
    ) -> dict[str, Any]:
        try:
            data = json.loads(raw)
            if not isinstance(data, dict):
                raise TypeError("Persona material output must be an object")
            return _sanitize_persona_material_draft(data, fallback_profile, fallback_content)
        except (json.JSONDecodeError, TypeError, ValueError) as exc:
            raise SkillOutputParseError("Invalid persona-material output") from exc
