"""Low-cost provider monitor using fixed public input and privacy-safe output."""

from __future__ import annotations

import asyncio
import json
import time
import uuid
from dataclasses import asdict, dataclass
from enum import StrEnum
from typing import Protocol

from app.config import settings
from app.llm.completion_config import provider_completion

MONITOR_MESSAGE = "Reply with OK."
MAX_MONITOR_OUTPUT_TOKENS = 8
MAX_MONITOR_TIMEOUT_SECONDS = 30.0
CONFIG_INVALID_ERROR = "MONITOR_CONFIG_INVALID"
EMPTY_RESPONSE_ERROR = "AI_EMPTY_RESPONSE"


class ProviderMonitorStatus(StrEnum):
    DISABLED = "DISABLED"
    CONFIG_INVALID = "CONFIG_INVALID"
    PRIMARY_SUCCESS = "PRIMARY_SUCCESS"
    FALLBACK_SUCCESS = "FALLBACK_SUCCESS"
    FAILED = "FAILED"


@dataclass(frozen=True)
class ProviderMonitorAttempt:
    provider: str
    model: str
    latency_ms: int
    success: bool
    error_code: str | None
    degraded: bool


@dataclass(frozen=True)
class ProviderMonitorReport:
    monitor_run_id: str
    status: ProviderMonitorStatus
    success: bool
    degraded: bool
    error_code: str | None
    attempts: tuple[ProviderMonitorAttempt, ...]

    def as_dict(self) -> dict[str, object]:
        return asdict(self)


class ProviderMonitorClient(Protocol):
    async def complete(self, *, model: str, max_tokens: int) -> str: ...


class DefaultProviderMonitorClient:
    """Call each configured provider model once without retrying."""

    async def complete(self, *, model: str, max_tokens: int) -> str:
        response = await provider_completion(
            model=model,
            messages=[{"role": "user", "content": MONITOR_MESSAGE}],
            temperature=0,
            max_tokens=max_tokens,
        )
        return response.choices[0].message.content or ""


class SyntheticProviderMonitor:
    def __init__(self, client: ProviderMonitorClient | None = None):
        self._client = client or DefaultProviderMonitorClient()

    async def run(self) -> ProviderMonitorReport:
        monitor_run_id = str(uuid.uuid4())
        if not settings.provider_monitor_enabled:
            return ProviderMonitorReport(
                monitor_run_id=monitor_run_id,
                status=ProviderMonitorStatus.DISABLED,
                success=False,
                degraded=False,
                error_code=None,
                attempts=(),
            )

        models = _configured_models()
        if models is None or not _has_safe_budget():
            return ProviderMonitorReport(
                monitor_run_id=monitor_run_id,
                status=ProviderMonitorStatus.CONFIG_INVALID,
                success=False,
                degraded=False,
                error_code=CONFIG_INVALID_ERROR,
                attempts=(),
            )

        attempts: list[ProviderMonitorAttempt] = []
        for index, model in enumerate(models):
            attempt = await self._run_attempt(model, degraded=index > 0)
            attempts.append(attempt)
            if attempt.success:
                return ProviderMonitorReport(
                    monitor_run_id=monitor_run_id,
                    status=(
                        ProviderMonitorStatus.FALLBACK_SUCCESS
                        if attempt.degraded
                        else ProviderMonitorStatus.PRIMARY_SUCCESS
                    ),
                    success=True,
                    degraded=attempt.degraded,
                    error_code=None,
                    attempts=tuple(attempts),
                )

        completed_attempts = tuple(attempts)
        return ProviderMonitorReport(
            monitor_run_id=monitor_run_id,
            status=ProviderMonitorStatus.FAILED,
            success=False,
            degraded=False,
            error_code=_last_error(completed_attempts),
            attempts=completed_attempts,
        )

    async def _run_attempt(self, model: str, *, degraded: bool) -> ProviderMonitorAttempt:
        started_at = time.monotonic()
        error_code: str | None = None
        try:
            async with asyncio.timeout(settings.provider_monitor_timeout_seconds):
                content = await self._client.complete(
                    model=model,
                    max_tokens=settings.provider_monitor_max_tokens,
                )
            if not content.strip():
                error_code = EMPTY_RESPONSE_ERROR
        except TimeoutError:
            error_code = "AI_TIMEOUT"
        except Exception:
            error_code = "AI_PROVIDER_ERROR"
        return ProviderMonitorAttempt(
            provider=_provider_name(model),
            model=model,
            latency_ms=max(0, round((time.monotonic() - started_at) * 1000)),
            success=error_code is None,
            error_code=error_code,
            degraded=degraded,
        )


def _configured_models() -> tuple[str, ...] | None:
    primary = settings.default_llm_model.strip()
    fallback = settings.fallback_llm_model.strip()
    if not primary or not fallback:
        return None
    return (primary,) if primary == fallback else (primary, fallback)


def _has_safe_budget() -> bool:
    return (
        0 < settings.provider_monitor_timeout_seconds <= MAX_MONITOR_TIMEOUT_SECONDS
        and 0 < settings.provider_monitor_max_tokens <= MAX_MONITOR_OUTPUT_TOKENS
    )


def _provider_name(model: str) -> str:
    provider, separator, _ = model.partition("/")
    return provider.strip() if separator and provider.strip() else "unknown"


def _last_error(attempts: tuple[ProviderMonitorAttempt, ...]) -> str:
    return next(
        (item.error_code for item in reversed(attempts) if item.error_code),
        "AI_PROVIDER_ERROR",
    )


def report_exit_code(report: ProviderMonitorReport) -> int:
    if report.status == ProviderMonitorStatus.DISABLED or report.success:
        return 0
    return 1


async def _main() -> int:
    report = await SyntheticProviderMonitor().run()
    print(json.dumps(report.as_dict(), ensure_ascii=False))
    return report_exit_code(report)


if __name__ == "__main__":
    raise SystemExit(asyncio.run(_main()))
