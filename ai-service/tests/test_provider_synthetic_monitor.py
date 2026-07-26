import pytest

from app.monitoring.provider_synthetic_monitor import (
    ProviderMonitorStatus,
    SyntheticProviderMonitor,
    report_exit_code,
)


class _ProbeClient:
    def __init__(self, outcomes):
        self.outcomes = list(outcomes)
        self.calls = []

    async def complete(self, *, model, max_tokens):
        self.calls.append((model, max_tokens))
        outcome = self.outcomes.pop(0)
        if isinstance(outcome, Exception):
            raise outcome
        return outcome


@pytest.mark.asyncio
async def test_monitor_calls_primary_and_fallback_once_without_reporting_output(monkeypatch):
    _enable_monitor(monkeypatch)
    client = _ProbeClient([
        RuntimeError("provider response body must not be reported"),
        "provider output must not be reported",
    ])

    report = await SyntheticProviderMonitor(client).run()

    assert report.status == ProviderMonitorStatus.FALLBACK_SUCCESS
    assert report.success is True
    assert report.degraded is True
    assert len(report.attempts) == 2
    assert len(client.calls) == 2
    assert all(max_tokens == 8 for _, max_tokens in client.calls)
    assert report_exit_code(report) == 0
    assert "provider output" not in str(report.as_dict())
    assert "response body" not in str(report.as_dict())


@pytest.mark.asyncio
async def test_monitor_reports_two_provider_failures_without_retrying(monkeypatch):
    _enable_monitor(monkeypatch)
    client = _ProbeClient([
        RuntimeError("primary response must not be reported"),
        RuntimeError("fallback response must not be reported"),
    ])

    report = await SyntheticProviderMonitor(client).run()

    assert report.status == ProviderMonitorStatus.FAILED
    assert report.success is False
    assert report.error_code == "AI_PROVIDER_ERROR"
    assert len(client.calls) == 2
    assert report_exit_code(report) == 1
    assert "response must not be reported" not in str(report.as_dict())


@pytest.mark.asyncio
async def test_monitor_uses_fallback_when_primary_response_is_empty(monkeypatch):
    _enable_monitor(monkeypatch)
    client = _ProbeClient(["", "OK"])

    report = await SyntheticProviderMonitor(client).run()

    assert report.status == ProviderMonitorStatus.FALLBACK_SUCCESS
    assert report.attempts[0].error_code == "AI_EMPTY_RESPONSE"
    assert report.attempts[1].success is True


@pytest.mark.asyncio
async def test_monitor_rejects_unsafe_budget_without_provider_calls(monkeypatch):
    _enable_monitor(monkeypatch)
    monkeypatch.setattr(
        "app.monitoring.provider_synthetic_monitor.settings.provider_monitor_max_tokens",
        9,
    )
    client = _ProbeClient(["must not be called"])

    report = await SyntheticProviderMonitor(client).run()

    assert report.status == ProviderMonitorStatus.CONFIG_INVALID
    assert report.error_code == "MONITOR_CONFIG_INVALID"
    assert client.calls == []
    assert report_exit_code(report) == 1


@pytest.mark.asyncio
async def test_monitor_disabled_is_a_clean_noop(monkeypatch):
    monkeypatch.setattr(
        "app.monitoring.provider_synthetic_monitor.settings.provider_monitor_enabled",
        False,
    )

    report = await SyntheticProviderMonitor(_ProbeClient([])).run()

    assert report.status == ProviderMonitorStatus.DISABLED
    assert report_exit_code(report) == 0


def _enable_monitor(monkeypatch):
    monkeypatch.setattr(
        "app.monitoring.provider_synthetic_monitor.settings.provider_monitor_enabled",
        True,
    )
    monkeypatch.setattr(
        "app.monitoring.provider_synthetic_monitor.settings.provider_monitor_max_tokens",
        8,
    )
    monkeypatch.setattr(
        "app.monitoring.provider_synthetic_monitor.settings.provider_monitor_timeout_seconds",
        30.0,
    )
