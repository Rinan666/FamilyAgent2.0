"""
Primary FamilyAgent chat routes.
"""
import asyncio
import logging
import time
import uuid
from contextlib import suppress
from typing import Literal

from fastapi import APIRouter, Depends, Request
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field, field_validator

from app.agents.family_agent import family_agent
from app.api.stream_events import (
    StreamEvent,
    StreamTraceObservation,
    content_event,
    metadata_event,
    stream_done_event,
    stream_error_event,
    stream_sse_events,
    validate_trace_observation,
)
from app.middleware.auth import verify_token_or_internal_service
from app.utils.input_guard import enforce_input_guard
from app.utils.privacy_guard import redact_with_note
from app.utils.safety_limits import (
    PromptLeakAttemptError,
    RoleHijackAttemptError,
    enforce_ai_concurrency,
    enforce_ai_rate_limit,
    validate_no_prompt_leak_attempt,
    validate_no_role_hijack_attempt,
)
from app.utils.sanitizer import sanitize_text

logger = logging.getLogger("familyagent.ai.api.agent")
REQUEST_ID_HEADER = "x-request-id"
RUN_ID_HEADER = "x-agent-run-id"

router = APIRouter(dependencies=[
    Depends(verify_token_or_internal_service),
    Depends(enforce_ai_rate_limit),
    Depends(enforce_ai_concurrency),
])


class AgentHistoryMessage(BaseModel):
    role: Literal["user", "assistant"] = Field(default="user", description="Conversation role")
    content: str = Field(default="", max_length=4000, description="Conversation content")

    @field_validator("content", mode="before")
    @classmethod
    def normalize_content(cls, value):
        return "" if value is None else str(value)


class AgentChatRequest(BaseModel):
    member_message: str = Field(default="", description="User message")
    history: list[AgentHistoryMessage] | None = Field(default=None, max_length=20, description="Conversation history")
    subject: str = Field(default="FamilyAgent", description="Conversation subject")
    knowledge_point: str = Field(default="family_memory", description="Generic context label")
    memory_context: str = Field(default="", description="Authorized memory context")
    viewer_role: str = Field(default="MEMBER", description="Viewer role label")
    target_role: str = Field(default="MEMBER", description="Target role label")
    response_mode: str = Field(default="think", description="Agent response mode: quick or think")
    client_timestamp: str = Field(default="", description="Client timestamp in ISO format")
    client_timezone: str = Field(default="", description="Client timezone")


def _request_id_from_header(value: str | None) -> str:
    if not value or not value.strip():
        return f"ai-{uuid.uuid4()}"
    return value.strip()[:128]


def _run_id_from_header(value: str | None) -> int | None:
    if not value or not value.strip().isdigit():
        return None
    run_id = int(value.strip())
    return run_id if run_id > 0 else None


def _trusted_memory_context(value: str, http_request: Request) -> str:
    if not getattr(getattr(http_request, "state", None), "internal_service", False):
        if value.strip():
            logger.warning("Ignored untrusted client memory_context")
        return ""

    memory_context = redact_with_note(value).text
    try:
        validate_no_prompt_leak_attempt(memory_context)
        validate_no_role_hijack_attempt(memory_context)
    except (PromptLeakAttemptError, RoleHijackAttemptError):
        logger.warning("Rejected unsafe internal memory_context")
        return "已授权上下文因包含疑似指令注入内容被本轮忽略。"
    return memory_context


@router.post("/chat/stream")
async def stream_chat(request: AgentChatRequest, http_request: Request):
    """Primary SSE endpoint for FamilyAgent chat."""
    request_id = _request_id_from_header(http_request.headers.get(REQUEST_ID_HEADER))
    run_id = _run_id_from_header(http_request.headers.get(RUN_ID_HEADER))
    member_message = sanitize_text(request.member_message)
    enforce_input_guard(member_message)
    memory_context = _trusted_memory_context(request.memory_context, http_request)

    async def generate():
        queue: asyncio.Queue[StreamEvent] = asyncio.Queue()
        started_at = time.monotonic()
        trace_observations: list[StreamTraceObservation] = []

        def stream_latency_ms() -> int:
            return max(0, round((time.monotonic() - started_at) * 1000))

        async def produce():
            try:
                async for chunk in family_agent.chat_stream(
                    member_message=member_message,
                    history=[item.model_dump() for item in request.history] if request.history else [],
                    subject=request.subject,
                    context_label=request.knowledge_point,
                    memory_context=memory_context,
                    viewer_role=request.viewer_role,
                    target_role=request.target_role,
                    response_mode=request.response_mode,
                    client_timestamp=request.client_timestamp,
                    client_timezone=request.client_timezone,
                ):
                    if isinstance(chunk, dict) and chunk.get("type") == "metadata":
                        trace_observation = chunk.get("trace_observation")
                        if isinstance(trace_observation, dict):
                            validated_observation = validate_trace_observation(trace_observation)
                            if validated_observation is not None:
                                trace_observations.append(validated_observation)
                            continue
                        await queue.put(metadata_event(chunk, request_id, run_id))
                    elif isinstance(chunk, dict):
                        await queue.put(content_event(chunk.get("content", ""), request_id, run_id))
                    else:
                        await queue.put(content_event(chunk, request_id, run_id))

                await queue.put(stream_done_event(
                    request_id=request_id,
                    run_id=run_id,
                    latency_ms=stream_latency_ms(),
                    trace_observations=trace_observations or None,
                ))
            except Exception as error:
                latency_ms = stream_latency_ms()
                logger.error(
                    "FamilyAgent stream failed: requestId=%s runId=%s latencyMs=%s errorType=%s",
                    request_id,
                    run_id,
                    latency_ms,
                    type(error).__name__,
                )
                await queue.put(stream_error_event(
                    request_id=request_id,
                    run_id=run_id,
                    latency_ms=latency_ms,
                    trace_observations=trace_observations or None,
                ))

        task = asyncio.create_task(produce())
        try:
            async for event in stream_sse_events(queue):
                yield event
        finally:
            if not task.done():
                task.cancel()
                with suppress(asyncio.CancelledError):
                    await task

    return StreamingResponse(
        generate(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
            "X-Request-Id": request_id,
            **({"X-Agent-Run-Id": str(run_id)} if run_id is not None else {}),
        },
    )
