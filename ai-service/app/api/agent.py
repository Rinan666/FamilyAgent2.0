"""
Primary FamilyAgent chat routes.
"""
import asyncio
import json
import logging
from contextlib import suppress

from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from app.agents.family_agent import family_agent
from app.middleware.auth import verify_token
from app.utils.input_guard import enforce_input_guard
from app.utils.privacy_guard import redact_with_note
from app.utils.safety_limits import enforce_ai_concurrency, enforce_ai_rate_limit
from app.utils.sanitizer import sanitize_text

logger = logging.getLogger("familyagent.ai.api.agent")
SSE_KEEPALIVE_SECONDS = 10.0

router = APIRouter(dependencies=[
    Depends(verify_token),
    Depends(enforce_ai_rate_limit),
    Depends(enforce_ai_concurrency),
])


class AgentChatRequest(BaseModel):
    member_message: str = Field(default="", description="User message")
    history: list[dict] | None = Field(default=None, description="Conversation history")
    subject: str = Field(default="FamilyAgent", description="Conversation subject")
    knowledge_point: str = Field(default="family_memory", description="Generic context label")
    memory_context: str = Field(default="", description="Authorized memory context")
    viewer_role: str = Field(default="MEMBER", description="Viewer role label")
    target_role: str = Field(default="MEMBER", description="Target role label")
    response_mode: str = Field(default="think", description="Agent response mode: quick or think")
    client_timestamp: str = Field(default="", description="Client timestamp in ISO format")
    client_timezone: str = Field(default="", description="Client timezone")


def _sse_data(payload: dict) -> str:
    return f"data: {json.dumps(payload, ensure_ascii=False)}\n\n"


def _sse_comment(comment: str) -> str:
    return f": {comment}\n\n"


async def _stream_sse_events(queue: asyncio.Queue[dict]):
    yield _sse_comment("connected")
    while True:
        try:
            payload = await asyncio.wait_for(queue.get(), timeout=SSE_KEEPALIVE_SECONDS)
        except asyncio.TimeoutError:
            yield _sse_comment("keep-alive")
            continue

        yield _sse_data(payload)
        if payload.get("done") or payload.get("error"):
            break


@router.post("/chat/stream")
async def stream_chat(request: AgentChatRequest):
    """Primary SSE endpoint for FamilyAgent chat."""
    member_message = sanitize_text(request.member_message)
    enforce_input_guard(member_message)
    memory_context = redact_with_note(request.memory_context).text

    async def generate():
        queue: asyncio.Queue[dict] = asyncio.Queue()

        async def produce():
            try:
                async for chunk in family_agent.chat_stream(
                    member_message=member_message,
                    history=request.history,
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
                        await queue.put({"metadata": chunk})
                    elif isinstance(chunk, dict):
                        await queue.put({"content": chunk.get("content", "")})
                    else:
                        await queue.put({"content": chunk})

                await queue.put({"done": True})
            except Exception:
                logger.exception("FamilyAgent stream failed")
                await queue.put({"error": "AI service unavailable, please retry later."})

        task = asyncio.create_task(produce())
        try:
            async for event in _stream_sse_events(queue):
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
        },
    )
