"""
Generic FamilyAgent chat pipeline.
"""
import asyncio
from contextlib import suppress
from typing import AsyncIterator

from app.config import settings
from app.llm.client import llm_client
from app.llm.observation import LLMCallObservation
from app.llm.prompts.chat import build_family_agent_system_prompt
from app.runtime.artifact_versions import FAMILY_CHAT_PROMPT_VERSION
from app.runtime.trace_observation import TraceObservation
from app.services.web_search import build_web_search_context
from app.utils.safety_limits import (
    validate_no_prompt_leak_attempt,
    validate_no_role_hijack_attempt,
    validate_text_budget,
)

_WEB_SEARCH_TIMEOUT_FALLBACK = "联网搜索未在时限内完成，本轮不使用搜索结果。"
_NO_WEB_CONTEXT = "- 本轮不需要联网搜索。"


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
        response_plan: dict | None = None,
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

        plan = _normalize_response_plan(response_plan, response_mode)

        yield {
            "type": "metadata",
            "answer_depth": plan["answer_depth"],
            "recall_depth": plan["recall_depth"],
            "web_search_policy": plan["web_search_policy"],
            "decision_support": plan["decision_support"],
            "intent_degraded": plan["degraded"],
        }

        web_search_task = None if plan["web_search_policy"] == "NONE" else asyncio.create_task(
            build_web_search_context(
                member_message,
                response_mode="auto",
                web_search_policy=plan["web_search_policy"],
            )
        )
        web_search_context = None

        try:
            if web_search_task is None:
                yield {
                    "type": "metadata",
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
                        "trace_observation": TraceObservation(
                            stepType="WEB_SEARCH",
                            operation="web_search.public",
                            latencyMs=max(
                                0,
                                round(settings.web_search_stream_metadata_timeout_seconds * 1000),
                            ),
                            success=False,
                            errorCode="WEB_SEARCH_METADATA_TIMEOUT",
                            degraded=True,
                            privacyCategories=["PUBLIC_DATA"],
                        ),
                    }
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

            if web_search_context is not None:
                trace_factory = getattr(web_search_context, "as_trace_observation", None)
                if callable(trace_factory) and (
                    web_search_context.needed or not getattr(web_search_context, "success", True)
                ):
                    yield {
                        "type": "metadata",
                        "trace_observation": trace_factory(),
                    }
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

            web_prompt = (
                web_search_context.prompt_context
                if web_search_context is not None
                else (_NO_WEB_CONTEXT if web_search_task is None else _WEB_SEARCH_TIMEOUT_FALLBACK)
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
                        response_mode="auto",
                        answer_depth=plan["answer_depth"],
                        recall_depth=plan["recall_depth"],
                        web_search_policy=plan["web_search_policy"],
                        decision_support=plan["decision_support"],
                        client_timestamp=client_timestamp,
                        client_timezone=client_timezone,
                        public_web_context=web_prompt,
                        member_message=member_message,
                    ),
                },
                *(history or []),
                {"role": "user", "content": member_message},
            ]

            llm_observations: list[LLMCallObservation] = []
            llm_error: Exception | None = None
            try:
                async for chunk in llm_client.chat_stream(
                    messages,
                    temperature=0.7,
                    observation_sink=llm_observations.append,
                ):
                    yield {"type": "content", "content": chunk}
            except Exception as exc:
                llm_error = exc
            finally:
                for observation in llm_observations:
                    yield {
                        "type": "metadata",
                        "trace_observation": observation.as_trace_observation(
                            prompt_version=FAMILY_CHAT_PROMPT_VERSION,
                        ),
                    }
            if llm_error is not None:
                raise llm_error
        finally:
            if web_search_task is not None and not web_search_task.done():
                web_search_task.cancel()
                with suppress(asyncio.CancelledError):
                    await web_search_task


family_agent = FamilyAgent()


def _normalize_response_plan(response_plan: dict | None, response_mode: str | None) -> dict:
    raw = response_plan or {}
    legacy_mode = (response_mode or "").strip().lower()
    legacy_quick = legacy_mode == "quick" and not response_plan
    return {
        "answer_depth": str(raw.get("answer_depth") or ("BRIEF" if legacy_quick else "STANDARD")).upper(),
        "recall_depth": str(raw.get("recall_depth") or ("NONE" if legacy_quick else "STANDARD")).upper(),
        "web_search_policy": str(raw.get("web_search_policy") or ("NONE" if legacy_quick else "AUTO")).upper(),
        "decision_support": bool(raw.get("decision_support", False)),
        "degraded": bool(raw.get("degraded", False)),
    }
