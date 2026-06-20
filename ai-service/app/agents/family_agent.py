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

_WEB_SEARCH_TIMEOUT_FALLBACK = "联网搜索未在时限内完成，本轮不使用搜索结果。"
_QUICK_MODE_WEB_CONTEXT = "- 本轮为快速模式，禁止联网搜索。"
_THINKING_SUMMARY = "我会先梳理问题、判断是否需要外部信息，再结合已授权上下文给出结论。"


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
        response_mode: str = "think",
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

        normalized_mode = (response_mode or "").strip().lower()
        is_quick_mode = normalized_mode == "quick"

        yield {
            "type": "metadata",
            "response_mode": "quick" if is_quick_mode else "think",
            **({"thinking_summary": _THINKING_SUMMARY} if not is_quick_mode else {}),
        }

        web_search_task = None if is_quick_mode else asyncio.create_task(
            build_web_search_context(member_message, normalized_mode)
        )
        web_search_context = None

        try:
            if is_quick_mode:
                yield {
                    "type": "metadata",
                    "response_mode": "quick",
                    "web_search": {
                        "needed": False,
                        "used": False,
                        "pending": False,
                        "result_count": 0,
                        "sources": [],
                    },
                }
            else:
                try:
                    web_search_context = await asyncio.wait_for(
                        asyncio.shield(web_search_task),
                        timeout=settings.web_search_stream_metadata_timeout_seconds,
                    )
                except asyncio.TimeoutError:
                    yield {
                        "type": "metadata",
                        "response_mode": "think",
                        "web_search": {
                            "needed": False,
                            "used": False,
                            "pending": True,
                            "result_count": 0,
                            "sources": [],
                        },
                    }

            if web_search_context is not None:
                yield {
                    "type": "metadata",
                    "response_mode": "think",
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

            web_prompt = (
                web_search_context.prompt_context
                if web_search_context is not None
                else (_QUICK_MODE_WEB_CONTEXT if is_quick_mode else _WEB_SEARCH_TIMEOUT_FALLBACK)
            )

            messages = [
                {
                    "role": "system",
                    "content": build_family_agent_system_prompt(
                        subject=subject,
                        context_label=context_label,
                        memory_context=memory_context,
                        viewer_role=viewer_role,
                        target_role=target_role,
                        response_mode=normalized_mode,
                        client_timestamp=client_timestamp,
                        client_timezone=client_timezone,
                        public_web_context=web_prompt,
                        member_message=member_message,
                    ),
                },
                *(history or []),
                {"role": "user", "content": member_message},
            ]

            async for chunk in llm_client.chat_stream(messages, temperature=0.7):
                yield {"type": "content", "content": chunk}
        finally:
            if web_search_task is not None and not web_search_task.done():
                web_search_task.cancel()
                with suppress(asyncio.CancelledError):
                    await web_search_task


family_agent = FamilyAgent()
