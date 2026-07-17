import pytest

from app.llm.observation import LLMCallObservation
from app.monitoring.provider_synthetic_monitor import SyntheticProviderMonitor


class _ObservedClient:
    def __init__(self, observations, should_fail=False):
        self.observations = observations
        self.should_fail = should_fail

    async def chat(self, *, observation_sink, **kwargs):
        for observation in self.observations:
            observation_sink(observation)
        if self.should_fail:
            raise RuntimeError("provider response body must not be reported")
        return "provider output must not be reported"


@pytest.mark.asyncio
async def test_monitor_distinguishes_primary_failure_and_fallback_success(monkeypatch):
    monkeypatch.setattr("app.monitoring.provider_synthetic_monitor.settings.provider_monitor_enabled", True)
    client = _ObservedClient([
        observation("primary/model", False, False, "AI_PROVIDER_ERROR"),
        observation("fallback/model", True, True, None),
    ])

    report = await SyntheticProviderMonitor(client).run()

    assert report.status == "FALLBACK_SUCCESS"
    assert report.success is True
    assert report.degraded is True
    assert len(report.attempts) == 2
    assert "provider output" not in str(report.as_dict())


@pytest.mark.asyncio
async def test_monitor_reports_all_provider_failure_without_output(monkeypatch):
    monkeypatch.setattr("app.monitoring.provider_synthetic_monitor.settings.provider_monitor_enabled", True)
    client = _ObservedClient([
        observation("primary/model", False, False, "AI_PROVIDER_ERROR"),
        observation("fallback/model", False, True, "AI_PROVIDER_ERROR"),
    ], should_fail=True)

    report = await SyntheticProviderMonitor(client).run()

    assert report.status == "FAILED"
    assert report.success is False
    assert report.error_code == "AI_PROVIDER_ERROR"
    assert "response body" not in str(report.as_dict())


def observation(model, success, degraded, error_code):
    return LLMCallObservation(
        provider=model.split("/", 1)[0],
        model=model,
        latency_ms=12,
        success=success,
        error_code=error_code,
        degraded=degraded,
    )
