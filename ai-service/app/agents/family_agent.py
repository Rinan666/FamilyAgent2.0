"""
Generic FamilyAgent chat pipeline.
"""
import asyncio
from contextlib import suppress
from typing import AsyncIterator

from app.config import settings
from app.llm.client import llm_client
from app.llm.prompts.chat import build_family_agent_system_prompt
from app.services.web_search import build_web_search_context
from app.utils.safety_limits import (
    validate_no_prompt_leak_attempt,
    validate_no_role_hijack_attempt,
    validate_text_budget,
)


class FamilyAgent:
    async def chat_stream(
        self,
        *,
        member_message: str,
        history: list[dict] | None = None,
        subject: str = "FamilyAgent",
        context_label: str = "family_memory",
        memory_context: str = "",
        viewer_role: str = "MEMBER",
        target_role: str = "MEMBER",
        client_timestamp: str = "",
        client_timezone: str = "",
    ) -> AsyncIterator[dict]:
        validate_no_prompt_leak_attempt(member_message)
        validate_no_role_hijack_attempt(member_message)
        validate_text_budget(
            {
                "member_message": member_message,
                "history": history or [],
                "memory_context": memory_context,
            },
            label="family agent stream request",
        )

        web_search_task = asyncio.create_task(build_web_search_context(member_message))
        web_search_context = None

        try:
            try:
                web_search_context = await asyncio.wait_for(
                    asyncio.shield(web_search_task),
                    timeout=settings.web_search_stream_metadata_timeout_seconds,
                )
            except asyncio.TimeoutError:
                yield {
                    "type": "metadata",
                    "web_search": {
                        "needed": False,
                        "used": False,
                        "pending": True,
                        "result_count": 0,
                        "sources": [],
                    },
                }

            if web_search_context is None:
                web_search_context = await web_search_task

            yield {
                "type": "metadata",
                "web_search": {
                    "needed": web_search_context.needed,
                    "used": len(web_search_context.results) > 0,
                    "pending": False,
                    "result_count": len(web_search_context.results),
                    "sources": [
                        {
                            "title": item.title,
                            "url": item.url,
                            "snippet": item.snippet,
                        }
                        for item in web_search_context.results
                    ],
                },
            }

            messages = [
                {
                    "role": "system",
                    "content": build_family_agent_system_prompt(
                        subject=subject,
                        context_label=context_label,
                        memory_context=memory_context,
                        viewer_role=viewer_role,
                        target_role=target_role,
                        client_timestamp=client_timestamp,
                        client_timezone=client_timezone,
                        public_web_context=web_search_context.prompt_context,
                    ),
                },
                *(history or []),
                {"role": "user", "content": member_message},
            ]

            async for chunk in llm_client.chat_stream(messages, temperature=0.7):
                yield {"type": "content", "content": chunk}
        finally:
            if not web_search_task.done():
                web_search_task.cancel()
                with suppress(asyncio.CancelledError):
                    await web_search_task


family_agent = FamilyAgent()
