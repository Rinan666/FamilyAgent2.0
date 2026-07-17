"""Low-cost provider monitor using fixed public input and privacy-safe output."""

from __future__ import annotations

import asyncio
import json
import uuid
from dataclasses import asdict, dataclass
from enum import StrEnum

from app.config import settings
from app.llm.client import LLMClient, llm_client
from app.llm.observation import LLMCallObservation

MONITOR_MESSAGE = "Reply with OK."


class ProviderMonitorStatus(StrEnum):
    DISABLED = "DISABLED"
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


class SyntheticProviderMonitor:
    def __init__(self, client: LLMClient = llm_client):
        self._client = client

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

        observations: list[LLMCallObservation] = []
        error_code: str | None = None
        try:
            async with asyncio.timeout(settings.provider_monitor_timeout_seconds):
                await self._client.chat(
                    messages=[{"role": "user", "content": MONITOR_MESSAGE}],
                    temperature=0,
                    max_tokens=settings.provider_monitor_max_tokens,
                    observation_sink=observations.append,
                )
        except TimeoutError:
            error_code = "AI_TIMEOUT"
        except Exception:
            error_code = "AI_PROVIDER_ERROR"

        attempts = tuple(_attempt(item) for item in observations)
        successful = next((item for item in reversed(attempts) if item.success), None)
        if successful is not None:
            return ProviderMonitorReport(
                monitor_run_id=monitor_run_id,
                status=(
                    ProviderMonitorStatus.FALLBACK_SUCCESS
                    if successful.degraded
                    else ProviderMonitorStatus.PRIMARY_SUCCESS
                ),
                success=True,
                degraded=successful.degraded,
                error_code=None,
                attempts=attempts,
            )
        return ProviderMonitorReport(
            monitor_run_id=monitor_run_id,
            status=ProviderMonitorStatus.FAILED,
            success=False,
            degraded=False,
            error_code=error_code or _last_error(attempts),
            attempts=attempts,
        )


def _attempt(observation: LLMCallObservation) -> ProviderMonitorAttempt:
    return ProviderMonitorAttempt(
        provider=observation.provider,
        model=observation.model,
        latency_ms=observation.latency_ms,
        success=observation.success,
        error_code=observation.error_code,
        degraded=observation.degraded,
    )


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
