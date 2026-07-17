"""Typed SSE event contracts and serialization for FamilyAgent chat."""

from __future__ import annotations

import asyncio
import json
import logging
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, ValidationError

logger = logging.getLogger("familyagent.ai.api.stream_events")
SSE_KEEPALIVE_SECONDS = 10.0


class StreamContentEvent(BaseModel):
    type: Literal["content"] = "content"
    content: str
    requestId: str
    runId: int | None = None


class StreamTraceObservation(BaseModel):
    stepType: Literal["LLM", "WEB_SEARCH"]
    operation: str = Field(min_length=1, max_length=120)
    provider: str | None = Field(default=None, max_length=80)
    model: str | None = Field(default=None, max_length=160)
    promptVersion: str | None = Field(default=None, max_length=80)
    skillVersion: str | None = Field(default=None, max_length=40)
    latencyMs: int | None = Field(default=None, ge=0)
    success: bool
    errorCode: str | None = Field(default=None, max_length=80)
    degraded: bool = False
    privacyCategories: list[Literal["FAMILY_DATA", "PUBLIC_DATA"]] = Field(
        default_factory=list,
    )


class StreamDoneEvent(BaseModel):
    type: Literal["done"] = "done"
    done: bool = True
    degraded: bool = False
    requestId: str
    runId: int | None = None
    latencyMs: int | None = None
    traceObservations: list[StreamTraceObservation] | None = None


class StreamErrorEvent(BaseModel):
    type: Literal["error"] = "error"
    error: bool = True
    code: str
    message: str
    retryable: bool
    degraded: bool = False
    requestId: str
    runId: int | None = None
    latencyMs: int | None = None
    traceObservations: list[StreamTraceObservation] | None = None


class StreamMetadataEvent(BaseModel):
    model_config = ConfigDict(extra="allow")

    type: Literal["metadata"] = "metadata"
    requestId: str
    runId: int | None = None


StreamEvent = StreamContentEvent | StreamDoneEvent | StreamErrorEvent | StreamMetadataEvent


def validate_trace_observation(payload: object) -> StreamTraceObservation | None:
    try:
        return StreamTraceObservation.model_validate(payload)
    except ValidationError:
        logger.warning("Dropped invalid internal trace observation")
        return None


def metadata_event(
    payload: dict,
    request_id: str,
    run_id: int | None = None,
) -> StreamMetadataEvent:
    metadata = dict(payload)
    metadata["type"] = "metadata"
    metadata["requestId"] = request_id
    metadata["runId"] = run_id
    return StreamMetadataEvent(**metadata)


def content_event(
    content: object,
    request_id: str,
    run_id: int | None = None,
) -> StreamContentEvent:
    return StreamContentEvent(
        content=content if isinstance(content, str) else "",
        requestId=request_id,
        runId=run_id,
    )


def stream_error_event(
    *,
    code: str = "AI_STREAM_UNAVAILABLE",
    message: str = "AI service unavailable, please retry later.",
    retryable: bool = True,
    request_id: str = "",
    run_id: int | None = None,
    latency_ms: int | None = None,
    trace_observations: list[StreamTraceObservation] | None = None,
) -> StreamErrorEvent:
    return StreamErrorEvent(
        code=code,
        message=message,
        retryable=retryable,
        requestId=request_id,
        runId=run_id,
        latencyMs=latency_ms,
        traceObservations=trace_observations,
    )


def stream_done_event(
    *,
    degraded: bool = False,
    request_id: str = "",
    run_id: int | None = None,
    latency_ms: int | None = None,
    trace_observations: list[StreamTraceObservation] | None = None,
) -> StreamDoneEvent:
    return StreamDoneEvent(
        degraded=degraded,
        requestId=request_id,
        runId=run_id,
        latencyMs=latency_ms,
        traceObservations=trace_observations,
    )


async def stream_sse_events(queue: asyncio.Queue[StreamEvent]):
    yield _sse_comment("connected")
    while True:
        try:
            event = await asyncio.wait_for(queue.get(), timeout=SSE_KEEPALIVE_SECONDS)
        except asyncio.TimeoutError:
            yield _sse_comment("keep-alive")
            continue

        yield _sse_data(event)
        if isinstance(event, (StreamDoneEvent, StreamErrorEvent)):
            break


def _sse_data(event: StreamEvent) -> str:
    payload = event.model_dump(exclude_none=True)
    return f"data: {json.dumps(payload, ensure_ascii=False)}\n\n"


def _sse_comment(comment: str) -> str:
    return f": {comment}\n\n"
