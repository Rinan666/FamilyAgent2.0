import asyncio

import pytest

from app.monitoring.provider_sampled_eval import (
    DefaultProviderProbeClient,
    MAX_CASES,
    MAX_CASE_TOKENS,
    ProviderProbeResponse,
    ProviderSampleStatus,
    ProviderSampledEval,
    report_exit_code,
)
from app.monitoring.provider_sampled_eval_models import PUBLIC_SAMPLE_CASES


class _ProbeClient:
    def __init__(self, outputs=(), error=None):
        self.outputs = iter(outputs)
        self.error = error
        self.cases = []

    async def complete(self, case):
        self.cases.append(case)
        if self.error:
            raise self.error
        return ProviderProbeResponse(
            content=next(self.outputs),
            provider="public-provider",
            model="public-provider/low-cost-model",
            latency_ms=7,
            input_tokens=6,
            output_tokens=2,
            cost_usd=0.000001,
        )


class _SlowProbeClient:
    async def complete(self, case):
        await asyncio.sleep(1)


@pytest.mark.asyncio
async def test_sampled_eval_is_disabled_without_provider_calls(monkeypatch):
    monkeypatch.setattr(
        "app.monitoring.provider_sampled_eval.settings.provider_sampled_eval_enabled",
        False,
    )
    client = _ProbeClient()

    report = await ProviderSampledEval(client).run()

    assert report.status == ProviderSampleStatus.DISABLED
    assert report.executed_case_count == 0
    assert report_exit_code(report) == 0
    assert client.cases == []


@pytest.mark.asyncio
async def test_sampled_eval_passes_fixed_public_cases_with_bounded_cost(monkeypatch):
    _enable(monkeypatch)
    client = _ProbeClient(outputs=("OK", "42"))

    report = await ProviderSampledEval(client).run()

    assert report.status == ProviderSampleStatus.PASSED
    assert report.success is True
    assert report.configured_case_count == MAX_CASES
    assert report.executed_case_count == MAX_CASES
    assert report_exit_code(report) == 0
    assert report.max_output_token_budget <= MAX_CASES * MAX_CASE_TOKENS
    assert report.total_input_tokens == 12
    assert report.total_output_tokens == 4
    assert report.estimated_cost_usd == 0.000002
    assert all(case.max_tokens <= MAX_CASE_TOKENS for case in client.cases)
    assert all(result.attempt_count == 1 for result in report.results)
    assert all(result.degraded is False for result in report.results)
    payload = str(report.as_dict())
    assert "Return exactly" not in payload
    assert "19 + 23" not in payload


@pytest.mark.asyncio
async def test_sampled_eval_stops_on_mismatch_without_reporting_output(monkeypatch):
    _enable(monkeypatch)
    client = _ProbeClient(outputs=("private unexpected output", "42"))

    report = await ProviderSampledEval(client).run()

    assert report.status == ProviderSampleStatus.FAILED
    assert report.executed_case_count == 1
    assert report.error_code == "AI_EVAL_RESPONSE_MISMATCH"
    assert report_exit_code(report) == 1
    assert "private unexpected output" not in str(report.as_dict())


@pytest.mark.asyncio
async def test_sampled_eval_rejects_unsafe_budget_configuration(monkeypatch):
    _enable(monkeypatch)
    monkeypatch.setattr(
        "app.monitoring.provider_sampled_eval.settings.provider_sampled_eval_case_limit",
        MAX_CASES + 1,
    )
    client = _ProbeClient(outputs=("OK", "42"))

    report = await ProviderSampledEval(client).run()

    assert report.status == ProviderSampleStatus.CONFIG_INVALID
    assert report.error_code == "AI_EVAL_CONFIG_INVALID"
    assert report_exit_code(report) == 1
    assert client.cases == []


@pytest.mark.asyncio
async def test_sampled_eval_hides_provider_exception_detail(monkeypatch):
    _enable(monkeypatch)
    client = _ProbeClient(error=RuntimeError("private provider response"))

    report = await ProviderSampledEval(client).run()

    assert report.status == ProviderSampleStatus.FAILED
    assert report.error_code == "AI_PROVIDER_ERROR"
    assert report_exit_code(report) == 1
    assert "private provider response" not in str(report.as_dict())


@pytest.mark.asyncio
async def test_sampled_eval_transport_failure_does_not_write_exception_detail(
    monkeypatch,
    capsys,
):
    _enable(monkeypatch)

    async def fail_completion(**_kwargs):
        raise RuntimeError("private transport response")

    monkeypatch.setattr(
        "app.monitoring.provider_sampled_eval.provider_completion",
        fail_completion,
    )

    report = await ProviderSampledEval().run()
    captured = capsys.readouterr()

    assert report.error_code == "AI_PROVIDER_ERROR"
    assert "private transport response" not in captured.out
    assert "private transport response" not in captured.err


@pytest.mark.asyncio
async def test_sampled_eval_exposes_timeout_without_retry(monkeypatch):
    _enable(monkeypatch)
    monkeypatch.setattr(
        "app.monitoring.provider_sampled_eval.settings.provider_sampled_eval_timeout_seconds",
        0.001,
    )

    report = await ProviderSampledEval(_SlowProbeClient()).run()

    assert report.status == ProviderSampleStatus.FAILED
    assert report.error_code == "AI_TIMEOUT"
    assert report_exit_code(report) == 1
    assert report.executed_case_count == 1
    assert report.results[0].attempt_count == 1


@pytest.mark.asyncio
async def test_litellm_probe_collects_usage_without_reporting_content(monkeypatch):
    class _Usage:
        prompt_tokens = 9
        completion_tokens = 1

    class _Message:
        content = "OK"

    class _Choice:
        message = _Message()

    class _Response:
        choices = [_Choice()]
        usage = _Usage()

    async def fake_completion(**_kwargs):
        return _Response()

    monkeypatch.setattr(
        "app.monitoring.provider_sampled_eval.provider_completion",
        fake_completion,
    )
    monkeypatch.setattr(
        "app.monitoring.provider_sampled_eval.litellm.completion_cost",
        lambda **_kwargs: 0.00000321,
    )
    monkeypatch.setattr(
        "app.monitoring.provider_sampled_eval.settings.default_llm_model",
        "openai/public-low-cost-model",
    )

    response = await DefaultProviderProbeClient().complete(PUBLIC_SAMPLE_CASES[0])

    assert response.content == "OK"
    assert response.input_tokens == 9
    assert response.output_tokens == 1
    assert response.cost_usd == 0.00000321


def _enable(monkeypatch):
    monkeypatch.setattr(
        "app.monitoring.provider_sampled_eval.settings.provider_sampled_eval_enabled",
        True,
    )
    monkeypatch.setattr(
        "app.monitoring.provider_sampled_eval.settings.provider_sampled_eval_case_limit",
        MAX_CASES,
    )
