"""Application use case for the save-memory planning skill."""

import json
import logging
from typing import Any

from app.api.memory_archive_helpers import _compact_transcript
from app.api.memory_contracts import SAVE_TOOL_PLAN_SCHEMA
from app.api.memory_helpers import (
    _blocked_save_tool_plan,
    _sanitize_save_tool_plan,
    _should_skip_save_planning,
    _unavailable_save_tool_plan,
)
from app.api.memory_models import SaveToolPlanRequest
from app.llm.client import LLMClient
from app.llm.prompts.memory import (
    SAVE_TOOL_PLAN_SYSTEM_PROMPT,
    build_save_tool_plan_user_prompt,
)
from app.runtime.skill_executor import SkillExecutor
from app.runtime.skill_manifest import SAVE_MEMORY_PLAN_MANIFEST
from app.utils.input_guard import InputGuardError, enforce_input_guard
from app.utils.privacy_guard import redact_with_note

logger = logging.getLogger("familyagent.ai.use_cases.save_memory_plan")


class SaveMemoryPlanUseCase:
    """Run the save-memory skill without changing its public response contract."""

    def __init__(self, skill_executor: SkillExecutor):
        self._skill_executor = skill_executor

    async def execute(self, request: SaveToolPlanRequest, llm_client: LLMClient) -> dict[str, Any]:
        return await self._skill_executor.execute(
            SAVE_MEMORY_PLAN_MANIFEST,
            lambda: self._plan(request, llm_client),
        )

    async def _plan(self, request: SaveToolPlanRequest, llm_client: LLMClient) -> dict[str, Any]:
        try:
            message = redact_with_note(request.message, max_length=3000).text
            enforce_input_guard(message)
            compact_context = _compact_transcript(request.conversation_context)
            if _should_skip_save_planning(message, compact_context):
                return self._success(_blocked_save_tool_plan(
                    "当前消息缺乏具体经历、对象、行为变化或可跟进信号，第一道意图审查已拦截。"
                ))

            family_context = redact_with_note(request.family_context, max_length=1200).text
            conversation_context = redact_with_note(compact_context, max_length=5000).text
            raw = await llm_client.chat(
                messages=[
                    {"role": "system", "content": SAVE_TOOL_PLAN_SYSTEM_PROMPT},
                    {
                        "role": "user",
                        "content": build_save_tool_plan_user_prompt(
                            family_context,
                            request.target_member_name,
                            request.viewer_role,
                            conversation_context,
                            message,
                        ),
                    },
                ],
                temperature=0.1,
                max_tokens=900,
                response_format=SAVE_TOOL_PLAN_SCHEMA,
            )
            return self._success(_sanitize_save_tool_plan(json.loads(raw)))
        except InputGuardError:
            raise
        except Exception:
            logger.error("Save memory skill failed", exc_info=True)
            return self._success(_unavailable_save_tool_plan())

    @staticmethod
    def _success(data: dict[str, Any]) -> dict[str, Any]:
        return {"success": True, "data": data}
