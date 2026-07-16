"""Application use case for the persona-material-draft skill."""

import logging
from typing import Any

from app.api.memory_contracts import PERSONA_MATERIAL_DRAFT_SCHEMA
from app.api.memory_models import PersonaMaterialDraftRequest
from app.llm.client import LLMClient
from app.runtime.draft_output_parser import PersonaMaterialOutputParser
from app.runtime.draft_prompt_renderer import PersonaMaterialPromptRenderer
from app.runtime.output_parser import SkillOutputParseError
from app.runtime.skill_error import SkillErrorCode
from app.runtime.skill_registry import SkillRuntime
from app.runtime.skill_response import skill_failure

logger = logging.getLogger("familyagent.ai.use_cases.persona_material_draft")


class PersonaMaterialDraftUseCase:
    def __init__(
        self,
        skill_runtime: SkillRuntime,
        prompt_renderer: PersonaMaterialPromptRenderer,
        output_parser: PersonaMaterialOutputParser,
    ):
        self._skill_runtime = skill_runtime
        self._prompt_renderer = prompt_renderer
        self._output_parser = output_parser

    async def execute(
        self,
        request: PersonaMaterialDraftRequest,
        llm_client: LLMClient,
    ) -> dict[str, Any]:
        try:
            return await self._skill_runtime.execute(lambda: self._organize(request, llm_client))
        except TimeoutError:
            logger.warning("Persona-material skill timed out")
            return skill_failure(SkillErrorCode.TIMEOUT, "Persona material organization unavailable")

    async def _organize(
        self,
        request: PersonaMaterialDraftRequest,
        llm_client: LLMClient,
    ) -> dict[str, Any]:
        content = request.content.strip()
        profile = request.profile.model_dump()
        try:
            raw = await llm_client.chat(
                messages=self._prompt_renderer.render(
                    profile=profile,
                    family_context=request.family_context,
                    content=content,
                ),
                temperature=0.12,
                max_tokens=1600,
                response_format=PERSONA_MATERIAL_DRAFT_SCHEMA,
            )
        except Exception as error:
            logger.error("Persona-material provider failed: errorType=%s", type(error).__name__)
            return skill_failure(
                SkillErrorCode.PROVIDER_ERROR,
                "Persona material organization unavailable",
            )

        try:
            data = self._output_parser.parse(
                raw,
                fallback_profile=profile,
                fallback_content=content,
            )
            return {"success": True, "data": data}
        except SkillOutputParseError as error:
            logger.error("Persona-material output invalid: errorType=%s", type(error).__name__)
            return skill_failure(
                SkillErrorCode.INVALID_RESPONSE,
                "Persona material organization unavailable",
            )
