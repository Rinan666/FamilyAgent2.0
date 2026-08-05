"""Output parsers for draft-only family skills."""

import json

from app.api.memory_generation_helpers import (
    _sanitize_organized_draft,
    _sanitize_persona_material_draft,
)
from app.api.memory_models import OrganizedDraftData, PersonaMaterialDraftData

from .output_parser import SkillOutputParseError


class OrganizeDraftOutputParser:
    def parse(
        self,
        raw: str,
        *,
        memory_library: str,
        current_memory_type: str,
        fallback_content: str,
    ) -> OrganizedDraftData:
        try:
            data = json.loads(raw)
            if not isinstance(data, dict):
                raise TypeError("Draft output must be an object")
            return OrganizedDraftData.model_validate(
                _sanitize_organized_draft(
                    data,
                    memory_library,
                    current_memory_type,
                    fallback_content,
                )
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
    ) -> PersonaMaterialDraftData:
        try:
            data = json.loads(raw)
            if not isinstance(data, dict):
                raise TypeError("Persona material output must be an object")
            return PersonaMaterialDraftData.model_validate(
                _sanitize_persona_material_draft(data, fallback_profile, fallback_content)
            )
        except (json.JSONDecodeError, TypeError, ValueError) as exc:
            raise SkillOutputParseError("Invalid persona-material output") from exc
