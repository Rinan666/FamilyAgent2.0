"""Prompt rendering for executable skills."""

from typing import TypedDict

from app.llm.prompts.memory import (
    MEMORY_SAVE_PLAN_SYSTEM_PROMPT,
    build_memory_save_plan_user_prompt,
)
from app.utils.privacy_guard import redact_with_note


class ChatPromptMessage(TypedDict):
    role: str
    content: str


class SaveMemoryPromptRenderer:
    """Build the trusted prompt envelope for save-memory planning."""

    def render(
        self,
        *,
        family_context: str,
        target_member_name: str,
        viewer_role: str,
        conversation_context: str,
        message: str,
    ) -> list[ChatPromptMessage]:
        guarded_family_context = redact_with_note(family_context, max_length=1200).text
        guarded_conversation_context = redact_with_note(
            conversation_context,
            max_length=5000,
        ).text
        return [
            {"role": "system", "content": MEMORY_SAVE_PLAN_SYSTEM_PROMPT},
            {
                "role": "user",
                "content": build_memory_save_plan_user_prompt(
                    guarded_family_context,
                    target_member_name,
                    viewer_role,
                    guarded_conversation_context,
                    message,
                ),
            },
        ]
