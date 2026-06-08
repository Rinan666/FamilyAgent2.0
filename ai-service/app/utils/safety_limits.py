"""
Lightweight AI cost and DoS protection.

The limits here guard model-bound calls instead of only UI entry points, so
new endpoints inherit a baseline safety boundary when they use llm_client.
"""
from __future__ import annotations

import asyncio
import re
import time
from collections import defaultdict, deque
from contextlib import asynccontextmanager
from typing import Any, AsyncIterator, Awaitable, Deque

from fastapi import HTTPException, Request

from app.config import settings
from app.utils.security_events import record_security_event


class SafetyLimitError(ValueError):
    """Raised when model-bound input exceeds the configured safety envelope."""


class PromptLeakAttemptError(PermissionError):
    """Raised when user-controlled input asks for hidden prompts or policies."""


class RoleHijackAttemptError(PermissionError):
    """Raised when user-controlled input tries to replace the product persona."""


_global_semaphore = asyncio.Semaphore(settings.ai_global_concurrency)
_user_semaphores: dict[str, asyncio.Semaphore] = defaultdict(
    lambda: asyncio.Semaphore(settings.ai_user_concurrency)
)
_rate_windows: dict[str, Deque[float]] = defaultdict(deque)
_rate_lock = asyncio.Lock()


class RateLimitExceededError(ValueError):
    """Raised when a caller exceeds the configured AI request rate."""

    def __init__(self, message: str, retry_after_seconds: int = 1):
        super().__init__(message)
        self.retry_after_seconds = retry_after_seconds


def total_text_chars(value: Any) -> int:
    """Count text-like payload size recursively."""
    if value is None:
        return 0
    if isinstance(value, str):
        return len(value)
    if isinstance(value, bytes):
        return len(value)
    if isinstance(value, dict):
        return sum(total_text_chars(item) for item in value.values())
    if isinstance(value, (list, tuple, set)):
        return sum(total_text_chars(item) for item in value)
    return len(str(value))


def validate_text_budget(
    payload: Any,
    *,
    max_chars: int | None = None,
    label: str = "input",
) -> None:
    """Reject oversized model-bound payloads before spending tokens."""
    limit = max_chars or settings.ai_max_total_input_chars
    size = total_text_chars(payload)
    if size > limit:
        raise SafetyLimitError(f"{label} is too large: {size} chars, limit {limit}")


def validate_messages(messages: list[dict]) -> None:
    """Validate per-message and total prompt size for LLM chat calls."""
    total = 0
    for message in messages:
        content_size = total_text_chars(message.get("content", ""))
        total += content_size
        if message.get("role") != "system":
            validate_no_prompt_leak_attempt(str(message.get("content", "")))
            validate_no_role_hijack_attempt(str(message.get("content", "")))
        if content_size > settings.ai_max_message_chars:
            role = message.get("role", "message")
            raise SafetyLimitError(
                f"{role} content is too large: {content_size} chars, "
                f"limit {settings.ai_max_message_chars}"
            )
    if total > settings.ai_max_total_input_chars:
        raise SafetyLimitError(
            f"LLM prompt is too large: {total} chars, "
            f"limit {settings.ai_max_total_input_chars}"
        )


def validate_no_prompt_leak_attempt(text: str) -> None:
    """Fast-reject prompt extraction attempts before any model call."""
    if looks_like_prompt_leak_attempt(text):
        raise PromptLeakAttemptError("拒绝输出或回溯系统提示词、开发者指令或隐藏策略。")


def validate_no_role_hijack_attempt(text: str) -> None:
    """Fast-reject attempts to replace FamilyAgent's role boundary."""
    if looks_like_role_hijack_attempt(text):
        raise RoleHijackAttemptError("拒绝覆盖 FamilyAgent 的身份设定或安全边界。")


def looks_like_prompt_leak_attempt(text: str) -> bool:
    normalized = " ".join(str(text or "").lower().split())
    if not normalized:
        return False

    benign_security_context = any(
        marker in normalized
        for marker in [
            "复盘提示词注入",
            "讨论提示词注入",
            "记录提示词注入",
            "总结安全事件",
            "学习 prompt injection",
            "prompt injection 防御",
        ]
    )
    if benign_security_context and not any(
        marker in normalized
        for marker in ["完整输出", "逐字输出", "回溯并", "打印", "泄露", "show me", "reveal"]
    ):
        return False

    prompt_patterns = [
        r"系统.{0,8}(初始化|隐藏|完整)?.{0,8}(prompt|提示词|指令|规则)",
        r"(developer|system).{0,8}(message|prompt|instruction)",
        r"隐藏.{0,8}(prompt|提示词|指令|规则)",
        r"开发者.{0,8}(指令|消息|规则)",
        r"内部.{0,8}(规则|策略|指令|提示词)",
    ]
    extraction_patterns = [
        r"(完整|逐字|原样).{0,8}(输出|打印|复述|展示)",
        r"(回溯|核对|审计|合规|泄露|透露|导出).{0,12}(prompt|提示词|指令|规则|策略)",
        r"(show me|reveal|print|verbatim|dump).{0,16}(system|developer|prompt|instruction|message)",
    ]
    role_pressure_patterns = [
        r"(安全|合规).{0,8}审计",
        r"内存溢出风险",
        r"恶意注入代码",
        r"(立刻|必须|最高优先级).{0,16}(输出|回溯|打印|展示|核对)",
    ]

    has_prompt = any(re.search(pattern, normalized, re.IGNORECASE) for pattern in prompt_patterns)
    has_extract = any(re.search(pattern, normalized, re.IGNORECASE) for pattern in extraction_patterns)
    has_role_pressure = any(re.search(pattern, normalized, re.IGNORECASE) for pattern in role_pressure_patterns)
    return has_prompt and (has_extract or has_role_pressure)


def looks_like_role_hijack_attempt(text: str) -> bool:
    normalized = " ".join(str(text or "").lower().split())
    if not normalized:
        return False

    persona_patterns = [
        r"(喵娘|猫娘|兽娘|狐娘|魅魔|女仆|恋人|老婆|男友|女友|主人|奴隶|宠物)",
        r"(neko|catgirl|maid|girlfriend|boyfriend|lover|master)",
        r"(dan|jailbreak|越狱|无约束|无限制|不受限制)",
    ]
    override_patterns = [
        r"(从现在起|以后|接下来|现在开始|立刻).{0,18}(你是|扮演|变成|化身|改成|切换成)",
        r"(你必须|必须|无条件|只能|永远).{0,18}(服从|听我的|按我说的|扮演|保持)",
        r"(忽略|无视|覆盖|删除|忘记|绕过|停止遵守).{0,18}(设定|身份|规则|边界|系统|开发者|限制|安全)",
        r"(不要|不准).{0,12}(提到|说明|声明|提醒).{0,12}(边界|限制|规则|设定|模拟)",
        r"(roleplay|pretend|act as|you are now|from now on).{0,24}(catgirl|maid|girlfriend|boyfriend|dan|unrestricted)",
    ]
    allowed_style_patterns = [
        r"(语气|风格|表达).{0,10}(温和|简洁|活泼|轻松|正式|像长者|自然|亲切)",
        r"(说得|回答得).{0,10}(温和|简洁|轻松|自然|亲切)",
    ]

    has_persona = any(re.search(pattern, normalized, re.IGNORECASE) for pattern in persona_patterns)
    has_override = any(re.search(pattern, normalized, re.IGNORECASE) for pattern in override_patterns)
    allowed_style_only = any(re.search(pattern, normalized, re.IGNORECASE) for pattern in allowed_style_patterns)
    if allowed_style_only and not has_persona and not has_override:
        return False
    return has_persona and has_override


def bounded_output_tokens(max_tokens: int) -> int:
    return min(max_tokens, settings.ai_max_output_tokens)


async def with_hard_timeout(coro: Awaitable[Any], *, label: str = "AI request") -> Any:
    try:
        return await asyncio.wait_for(coro, timeout=settings.ai_hard_timeout_seconds)
    except asyncio.TimeoutError as exc:
        raise TimeoutError(f"{label} exceeded {settings.ai_hard_timeout_seconds:.0f}s safety timeout") from exc


async def stream_with_timeouts(source: AsyncIterator[str], *, label: str = "AI stream") -> AsyncIterator[str]:
    """Apply idle and hard timeouts to a streaming iterator."""
    start = time.monotonic()
    iterator = source.__aiter__()
    while True:
        remaining = settings.ai_hard_timeout_seconds - (time.monotonic() - start)
        if remaining <= 0:
            raise TimeoutError(f"{label} exceeded {settings.ai_hard_timeout_seconds:.0f}s safety timeout")
        try:
            chunk = await asyncio.wait_for(
                iterator.__anext__(),
                timeout=min(settings.ai_stream_idle_timeout_seconds, remaining),
            )
        except StopAsyncIteration:
            break
        except asyncio.TimeoutError as exc:
            raise TimeoutError(
                f"{label} was idle for {settings.ai_stream_idle_timeout_seconds:.0f}s"
            ) from exc
        yield chunk


def _user_key(request: Request) -> str:
    user_id = getattr(request.state, "user_id", None)
    if user_id is not None:
        return str(user_id)
    client = request.client.host if request.client else "unknown"
    return f"ip:{client}"


def _client_ip(request: Request) -> str:
    forwarded = request.headers.get("x-forwarded-for")
    if forwarded:
        return forwarded.split(",")[0].strip() or "unknown"
    real_ip = request.headers.get("x-real-ip")
    if real_ip:
        return real_ip.strip()
    return request.client.host if request.client else "unknown"


def _rate_retry_after_seconds(window: Deque[float], now: float, period_seconds: float) -> int:
    if not window:
        return 1
    retry_after = period_seconds - (now - window[0])
    return max(1, int(retry_after) + 1)


async def check_rate_limit(
    key: str,
    *,
    limit: int,
    period_seconds: float = 60.0,
    label: str = "AI 请求",
) -> None:
    """Sliding-window in-process rate limiter."""
    if limit <= 0:
        return

    now = time.monotonic()
    async with _rate_lock:
        window = _rate_windows[key]
        while window and now - window[0] >= period_seconds:
            window.popleft()
        if len(window) >= limit:
            retry_after = _rate_retry_after_seconds(window, now, period_seconds)
            raise RateLimitExceededError(
                f"{label}过于频繁，请约 {retry_after} 秒后再试。",
                retry_after_seconds=retry_after,
            )
        window.append(now)

        if len(_rate_windows) > 2000:
            stale_keys = [
                item_key
                for item_key, item_window in _rate_windows.items()
                if not item_window or now - item_window[-1] >= period_seconds * 2
            ]
            for item_key in stale_keys[:500]:
                _rate_windows.pop(item_key, None)


async def enforce_ai_rate_limit(request: Request):
    """Limit model-bound request frequency per user and per IP."""
    user_key = _user_key(request)
    ip = _client_ip(request)
    await check_rate_limit(
        f"ai:user:{user_key}",
        limit=settings.ai_user_rate_limit_per_minute,
        label="同一用户的 AI 请求",
    )
    await check_rate_limit(
        f"ai:ip:{ip}",
        limit=settings.ai_ip_rate_limit_per_minute,
        label="同一来源的 AI 请求",
    )


async def enforce_embedding_rate_limit(request: Request):
    """Separate, looser limiter for backend-triggered embedding rebuild work."""
    user_key = _user_key(request)
    ip = _client_ip(request)
    await check_rate_limit(
        f"embedding:user:{user_key}",
        limit=settings.ai_embedding_user_rate_limit_per_minute,
        label="同一用户的向量化请求",
    )
    await check_rate_limit(
        f"embedding:ip:{ip}",
        limit=settings.ai_embedding_ip_rate_limit_per_minute,
        label="同一来源的向量化请求",
    )


@asynccontextmanager
async def ai_concurrency_guard(request: Request):
    """Limit concurrent model-bound work globally and per user."""
    user_key = _user_key(request)
    user_semaphore = _user_semaphores[user_key]
    try:
        await asyncio.wait_for(_global_semaphore.acquire(), timeout=0.1)
    except asyncio.TimeoutError as exc:
        await record_security_event(
            request,
            event_type="CONCURRENCY_LIMIT",
            status_code=429,
            reason="AI global concurrency limit exceeded",
        )
        raise HTTPException(status_code=429, detail="AI 服务繁忙，请稍后再试") from exc
    try:
        try:
            await asyncio.wait_for(user_semaphore.acquire(), timeout=0.1)
        except asyncio.TimeoutError as exc:
            await record_security_event(
                request,
                event_type="CONCURRENCY_LIMIT",
                status_code=429,
                reason="AI per-user concurrency limit exceeded",
            )
            raise HTTPException(status_code=429, detail="同一用户的 AI 请求过多，请等上一条完成") from exc
        try:
            yield
        finally:
            user_semaphore.release()
    finally:
        _global_semaphore.release()


async def enforce_ai_concurrency(request: Request):
    """FastAPI dependency for endpoints that call LLMs."""
    async with ai_concurrency_guard(request):
        yield
