"""
Lightweight AI security event logging.

Events intentionally avoid raw prompts or family memory content. They record
only operational metadata useful for tuning safety rules during testing.
"""
from __future__ import annotations

import logging
from collections import Counter, deque
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from typing import Any, Deque

from fastapi import Request

logger = logging.getLogger("familyagent.ai.security")

_MAX_EVENTS = 200
_events: Deque["AISecurityEvent"] = deque(maxlen=_MAX_EVENTS)


@dataclass(frozen=True)
class AISecurityEvent:
    timestamp: str
    event_type: str
    status_code: int
    path: str
    method: str
    user_id: str
    ip: str
    request_chars: int
    reason: str


def total_payload_chars(value: Any) -> int:
    if value is None:
        return 0
    if isinstance(value, str):
        return len(value)
    if isinstance(value, bytes):
        return len(value)
    if isinstance(value, dict):
        return sum(total_payload_chars(item) for item in value.values())
    if isinstance(value, (list, tuple, set)):
        return sum(total_payload_chars(item) for item in value)
    return len(str(value))


def client_ip(request: Request) -> str:
    forwarded = request.headers.get("x-forwarded-for")
    if forwarded:
        return forwarded.split(",")[0].strip() or "unknown"
    real_ip = request.headers.get("x-real-ip")
    if real_ip:
        return real_ip.strip()
    return request.client.host if request.client else "unknown"


async def request_payload_chars(request: Request) -> int:
    cached_size = getattr(request.state, "request_chars", None)
    if isinstance(cached_size, int):
        return cached_size
    try:
        body = await request.body()
    except Exception:
        body = b""
    size = len(body)
    request.state.request_chars = size
    return size


async def record_security_event(
    request: Request,
    *,
    event_type: str,
    status_code: int,
    reason: str,
) -> AISecurityEvent:
    event = AISecurityEvent(
        timestamp=datetime.now(timezone.utc).isoformat(),
        event_type=event_type,
        status_code=status_code,
        path=request.url.path,
        method=request.method,
        user_id=str(getattr(request.state, "user_id", "") or "anonymous"),
        ip=client_ip(request),
        request_chars=await request_payload_chars(request),
        reason=str(reason).strip()[:180],
    )
    _events.appendleft(event)
    logger.warning(
        "AI security event type=%s status=%s user=%s ip=%s path=%s chars=%s reason=%s",
        event.event_type,
        event.status_code,
        event.user_id,
        event.ip,
        event.path,
        event.request_chars,
        event.reason,
    )
    return event


def recent_security_events(limit: int = 50) -> list[dict]:
    return [asdict(event) for event in list(_events)[: max(0, min(limit, _MAX_EVENTS))]]


def security_event_summary() -> dict:
    counts = Counter(event.event_type for event in _events)
    return {
        "total": len(_events),
        "by_type": dict(counts),
        "max_events": _MAX_EVENTS,
    }
