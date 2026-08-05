"""Application use case for the organize-draft skill."""

import logging

from app.api.memory_contracts import ORGANIZED_DRAFT_SCHEMA
from app.api.memory_helpers import _choice
from app.api.memory_models import OrganizeDraftRequest
from app.llm.client import LLMClient
from app.runtime.draft_output_parser import OrganizeDraftOutputParser
from app.runtime.draft_prompt_renderer import OrganizeDraftPromptRenderer
from app.runtime.output_parser import SkillOutputParseError
from app.runtime.skill_error import SkillErrorCode
from app.runtime.skill_registry import SkillRuntime
from app.runtime.skill_response import skill_failure, skill_success

logger = logging.getLogger("familyagent.ai.use_cases.organize_draft")


class OrganizeDraftUseCase:
    def __init__(
        self,
        skill_runtime: SkillRuntime,
        prompt_renderer: OrganizeDraftPromptRenderer,
        output_parser: OrganizeDraftOutputParser,
    ):
        self._skill_runtime = skill_runtime
        self._prompt_renderer = prompt_renderer
        self._output_parser = output_parser

    async def execute(self, request: OrganizeDraftRequest, llm_client: LLMClient) -> dict[str, object]:
        try:
            return await self._skill_runtime.execute(lambda: self._organize(request, llm_client))
        except TimeoutError:
            logger.warning("Organize-draft skill timed out")
            return skill_failure(SkillErrorCode.TIMEOUT, "Draft organization unavailable")

    async def _organize(self, request: OrganizeDraftRequest, llm_client: LLMClient) -> dict[str, object]:
        content = request.content.strip()
        memory_library = _choice(request.memory_library, {"PERSONAL", "FAMILY"}, "FAMILY")
        current_memory_type = _choice(
            request.current_memory_type,
            {"NOTE", "KNOWLEDGE", "INSIGHT", "EXPERIENCE", "OBSERVATION", "PREFERENCE", "PLAN"},
            "NOTE",
        )
        try:
            raw = await llm_client.chat(
                messages=self._prompt_renderer.render(
                    memory_library=memory_library,
                    current_memory_type=current_memory_type,
                    current_visibility=request.current_visibility,
                    target=request.target,
                    family_context=request.family_context,
                    content=content,
                ),
                temperature=0.15,
                max_tokens=1200,
                response_format=ORGANIZED_DRAFT_SCHEMA,
            )
        except Exception as error:
            logger.error("Organize-draft provider failed: errorType=%s", type(error).__name__)
            return skill_failure(SkillErrorCode.PROVIDER_ERROR, "Draft organization unavailable")

        try:
            data = self._output_parser.parse(
                raw,
                memory_library=memory_library,
                current_memory_type=current_memory_type,
                fallback_content=content,
            )
            return skill_success(data)
        except SkillOutputParseError as error:
            logger.error("Organize-draft output invalid: errorType=%s", type(error).__name__)
            return skill_failure(SkillErrorCode.INVALID_RESPONSE, "Draft organization unavailable")
