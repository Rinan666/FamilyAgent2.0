"""Application use case for the save-memory planning skill."""

import logging

from app.api.memory_archive_helpers import _compact_transcript
from app.api.memory_contracts import SAVE_TOOL_PLAN_SCHEMA
from app.api.memory_helpers import (
    _blocked_save_tool_plan,
    _should_skip_save_planning,
    _unavailable_save_tool_plan,
)
from app.api.memory_models import SaveToolPlanData, SaveToolPlanRequest
from app.llm.client import LLMClient
from app.runtime.output_parser import SaveMemoryOutputParser, SkillOutputParseError
from app.runtime.prompt_renderer import SaveMemoryPromptRenderer
from app.runtime.skill_error import SkillErrorCode
from app.runtime.skill_registry import SkillRuntime
from app.runtime.skill_response import skill_data_failure, skill_success
from app.utils.input_guard import InputGuardError, enforce_input_guard
from app.utils.privacy_guard import redact_with_note

logger = logging.getLogger("familyagent.ai.use_cases.save_memory_plan")


class SaveMemoryPlanUseCase:
    """Run the save-memory skill without changing its public response contract."""

    def __init__(
        self,
        skill_runtime: SkillRuntime,
        prompt_renderer: SaveMemoryPromptRenderer,
        output_parser: SaveMemoryOutputParser,
    ):
        self._skill_runtime = skill_runtime
        self._prompt_renderer = prompt_renderer
        self._output_parser = output_parser

    async def execute(self, request: SaveToolPlanRequest, llm_client: LLMClient) -> dict[str, object]:
        try:
            return await self._skill_runtime.execute(lambda: self._plan(request, llm_client))
        except TimeoutError:
            logger.warning("Save memory skill timed out")
            return self._failure(SkillErrorCode.TIMEOUT)

    async def _plan(self, request: SaveToolPlanRequest, llm_client: LLMClient) -> dict[str, object]:
        try:
            message = redact_with_note(request.message, max_length=3000).text
            enforce_input_guard(message)
            compact_context = _compact_transcript(request.conversation_context)
            if _should_skip_save_planning(message, compact_context):
                return self._success(
                    SaveToolPlanData.model_validate(
                        _blocked_save_tool_plan(
                            "当前消息缺乏具体经历、对象、行为变化或可跟进信号，第一道意图审查已拦截。"
                        )
                    )
                )

            try:
                raw = await llm_client.chat(
                    messages=self._prompt_renderer.render(
                        family_context=request.family_context,
                        target_member_name=request.target_member_name,
                        viewer_role=request.viewer_role,
                        conversation_context=compact_context,
                        message=message,
                    ),
                    temperature=0.1,
                    max_tokens=900,
                    response_format=SAVE_TOOL_PLAN_SCHEMA,
                )
            except Exception as error:
                logger.error(
                    "Save memory provider call failed: errorType=%s",
                    type(error).__name__,
                )
                return self._failure(SkillErrorCode.PROVIDER_ERROR)

            try:
                return self._success(self._output_parser.parse(raw))
            except SkillOutputParseError as error:
                logger.error(
                    "Save memory provider returned invalid output: errorType=%s",
                    type(error).__name__,
                )
                return self._failure(SkillErrorCode.INVALID_RESPONSE)
        except InputGuardError:
            raise

    @staticmethod
    def _success(data: SaveToolPlanData) -> dict[str, object]:
        return skill_success(data)

    @staticmethod
    def _failure(error_code: SkillErrorCode) -> dict[str, object]:
        return skill_data_failure(
            SaveToolPlanData.model_validate(_unavailable_save_tool_plan()),
            error_code,
            "Save-memory planning unavailable",
        )
