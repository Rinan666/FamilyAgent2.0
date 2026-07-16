"""Prompt renderers for draft-only family skills."""

from app.llm.prompts.memory import (
    ORGANIZE_DRAFT_SYSTEM_PROMPT,
    PERSONA_MATERIAL_DRAFT_SYSTEM_PROMPT,
    build_organize_draft_user_prompt,
    build_persona_material_draft_user_prompt,
)
from app.utils.privacy_guard import redact_with_note

from .prompt_renderer import ChatPromptMessage


class OrganizeDraftPromptRenderer:
    def render(
        self,
        *,
        scene: str,
        current_type: str,
        current_visibility: str,
        target: str,
        family_context: str,
        content: str,
    ) -> list[ChatPromptMessage]:
        guarded_content = redact_with_note(content, max_length=4000).text
        guarded_context = redact_with_note(family_context, max_length=1200).text
        return [
            {"role": "system", "content": ORGANIZE_DRAFT_SYSTEM_PROMPT},
            {
                "role": "user",
                "content": build_organize_draft_user_prompt(
                    scene,
                    current_type,
                    current_visibility,
                    target,
                    guarded_context,
                    guarded_content,
                ),
            },
        ]


class PersonaMaterialPromptRenderer:
    def render(
        self,
        *,
        profile: dict[str, str],
        family_context: str,
        content: str,
    ) -> list[ChatPromptMessage]:
        guarded_content = redact_with_note(content, max_length=6000).text
        guarded_context = redact_with_note(family_context, max_length=1200).text
        guarded_profile = {
            key: redact_with_note(value, max_length=1000).text
            for key, value in profile.items()
        }
        return [
            {"role": "system", "content": PERSONA_MATERIAL_DRAFT_SYSTEM_PROMPT},
            {
                "role": "user",
                "content": build_persona_material_draft_user_prompt(
                    guarded_profile,
                    guarded_context,
                    guarded_content,
                ),
            },
        ]
