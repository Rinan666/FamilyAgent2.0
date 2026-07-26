import asyncio

import pytest

from app.api.memory_models import SaveToolPlanData
from app.monitoring.provider_quality_eval import (
    DefaultProviderQualityProbeClient,
    ProviderQualityEval,
    report_exit_code,
)
from app.monitoring.provider_quality_eval_models import (
    MAX_QUALITY_CASES,
    MAX_QUALITY_CASE_TOKENS,
    SYNTHETIC_QUALITY_CASES,
    ProviderQualityProbeResponse,
    ProviderQualityStatus,
    ProviderQualityStructuredOutputError,
)


class _QualityClient:
    def __init__(self, plans=None, error=None, output_tokens=120):
        self.plans = plans or {}
        self.error = error
        self.output_tokens = output_tokens
        self.calls = []

    async def complete(self, model, case):
        self.calls.append((model, case.case_id))
        if self.error:
            raise self.error
        plan = self.plans[(model, case.case_id)]
        return ProviderQualityProbeResponse(
            plan=plan,
            provider=model.split("/", 1)[0],
            model=model,
            latency_ms=10,
            input_tokens=80,
            output_tokens=self.output_tokens,
            cost_usd=0.00001,
        )


class _SlowQualityClient:
    async def complete(self, model, case):
        await asyncio.sleep(1)


@pytest.mark.asyncio
async def test_quality_eval_is_disabled_without_provider_calls(monkeypatch):
    monkeypatch.setattr(
        "app.monitoring.provider_quality_eval.settings.provider_quality_eval_enabled",
        False,
    )
    client = _QualityClient()

    report = await ProviderQualityEval(client).run()

    assert report.status == ProviderQualityStatus.DISABLED
    assert report.model_run_count == 0
    assert report_exit_code(report) == 0
    assert client.calls == []


@pytest.mark.asyncio
async def test_quality_eval_scores_all_synthetic_memory_value_cases(monkeypatch):
    _enable(monkeypatch, case_limit=MAX_QUALITY_CASES)
    model = "dashscope/qwen-flash"
    client = _QualityClient(plans={
        (model, "learning-strategy"): _plan(
            "FAMILY_MEMORY",
            "孩子做应用题先复述题意，再画线段图后能更稳定地说出等量关系。",
            "CARE_VISIBLE",
        ),
        (model, "low-value-insight"): _plan("NONE", "", "PRIVATE"),
        (model, "sensitive-vision-follow-up"): _plan(
            "GROWTH_GUARD",
            "孩子看黑板时会眯眼并说后排字看不清，周末安排视力检查并继续记录。",
            "CARE_VISIBLE",
        ),
    })

    report = await ProviderQualityEval(client).run()

    assert report.status == ProviderQualityStatus.PASSED
    assert report.success is True
    assert report.schema_version == "provider.quality-eval.v1"
    assert report.fixture_version == "provider.quality-fixtures.v1"
    assert report.configured_case_count == MAX_QUALITY_CASES
    assert report.max_output_token_budget <= MAX_QUALITY_CASES * MAX_QUALITY_CASE_TOKENS
    run = report.runs[0]
    assert run.pass_rate == 1.0
    assert run.average_quality_score == 1.0
    assert run.structured_failure_rate == 0.0
    assert run.total_input_tokens == 240
    assert run.total_output_tokens == 360
    assert run.estimated_cost_usd == 0.00003
    payload = str(report.as_dict())
    assert "孩子最近" not in payload
    assert "积极向前看" not in payload
    assert "视力检查并继续记录" not in payload


@pytest.mark.asyncio
async def test_quality_eval_compares_baseline_and_candidate(monkeypatch):
    baseline = "dashscope/qwen-lite"
    candidate = "dashscope/qwen-flash"
    _enable(monkeypatch, baseline=baseline, candidate=candidate)
    client = _QualityClient(plans={
        (baseline, "learning-strategy"): _plan(
            "FAMILY_MEMORY",
            "孩子做应用题时需要调整方法。",
            "CARE_VISIBLE",
        ),
        (baseline, "low-value-insight"): _plan("NONE", "", "PRIVATE"),
        (candidate, "learning-strategy"): _plan(
            "FAMILY_MEMORY",
            "孩子做应用题先复述题意，再画线段图后能更稳定地说出等量关系。",
            "CARE_VISIBLE",
        ),
        (candidate, "low-value-insight"): _plan("NONE", "", "PRIVATE"),
    })

    report = await ProviderQualityEval(client).run()

    assert report.success is True
    assert report.runs[0].completed is True
    assert report.runs[0].success is False
    assert report.runs[1].success is True
    assert report.comparison is not None
    assert report.comparison.quality_score_delta > 0
    assert report.comparison.pass_rate_delta > 0
    assert report.comparison.estimated_cost_usd_delta == 0.0


@pytest.mark.asyncio
async def test_quality_eval_reports_structured_failure_without_output(monkeypatch):
    _enable(monkeypatch)
    client = _QualityClient(
        error=ProviderQualityStructuredOutputError("private malformed output"),
    )

    report = await ProviderQualityEval(client).run()

    assert report.status == ProviderQualityStatus.FAILED
    assert report.runs[0].structured_failure_rate == 1.0
    assert report.error_code == "AI_QUALITY_EVAL_STRUCTURED_OUTPUT_INVALID"
    assert "private malformed output" not in str(report.as_dict())


@pytest.mark.asyncio
async def test_quality_eval_stops_after_transport_failure(monkeypatch):
    _enable(monkeypatch, case_limit=MAX_QUALITY_CASES)
    client = _QualityClient(error=RuntimeError("private provider response"))

    report = await ProviderQualityEval(client).run()

    assert report.status == ProviderQualityStatus.FAILED
    assert report.error_code == "AI_PROVIDER_ERROR"
    assert report.runs[0].executed_case_count == 1
    assert "private provider response" not in str(report.as_dict())


@pytest.mark.asyncio
async def test_quality_eval_rejects_unsafe_or_ambiguous_configuration(monkeypatch):
    _enable(monkeypatch, baseline="dashscope/qwen-flash")
    client = _QualityClient()

    report = await ProviderQualityEval(client).run()

    assert report.status == ProviderQualityStatus.CONFIG_INVALID
    assert report.error_code == "AI_QUALITY_EVAL_CONFIG_INVALID"
    assert report_exit_code(report) == 1
    assert client.calls == []


@pytest.mark.asyncio
async def test_quality_eval_enforces_output_token_budget(monkeypatch):
    _enable(monkeypatch, case_limit=1)
    model = "dashscope/qwen-flash"
    client = _QualityClient(
        plans={(model, "learning-strategy"): _plan(
            "FAMILY_MEMORY",
            "应用题先复述题意再画线段图。",
            "CARE_VISIBLE",
        )},
        output_tokens=MAX_QUALITY_CASE_TOKENS + 1,
    )

    report = await ProviderQualityEval(client).run()

    assert report.status == ProviderQualityStatus.FAILED
    assert report.error_code == "AI_QUALITY_EVAL_TOKEN_BUDGET_EXCEEDED"


@pytest.mark.asyncio
async def test_quality_eval_exposes_timeout_without_retry(monkeypatch):
    _enable(monkeypatch, case_limit=1)
    monkeypatch.setattr(
        "app.monitoring.provider_quality_eval.settings.provider_quality_eval_timeout_seconds",
        0.001,
    )

    report = await ProviderQualityEval(_SlowQualityClient()).run()

    assert report.error_code == "AI_TIMEOUT"
    assert report.runs[0].executed_case_count == 1


@pytest.mark.asyncio
async def test_default_quality_client_uses_production_schema_and_hides_raw_output(monkeypatch):
    plan = _plan(
        "FAMILY_MEMORY",
        "孩子做应用题先复述题意，再画线段图。",
        "CARE_VISIBLE",
    )

    class _Usage:
        prompt_tokens = 90
        completion_tokens = 100

    class _Message:
        content = plan.model_dump_json()

    class _Choice:
        message = _Message()

    class _Response:
        choices = [_Choice()]
        usage = _Usage()

    async def fake_completion(**kwargs):
        assert kwargs["response_format"]["json_schema"]["name"] == "agent_save_tool_plan"
        assert kwargs["extra_body"] == {"enable_thinking": False}
        assert "Synthetic evaluation family" in kwargs["messages"][1]["content"]
        return _Response()

    monkeypatch.setattr(
        "app.monitoring.provider_quality_eval.provider_completion",
        fake_completion,
    )

    response = await DefaultProviderQualityProbeClient().complete(
        "dashscope/qwen-flash",
        SYNTHETIC_QUALITY_CASES[0],
    )

    assert response.plan.tool == "FAMILY_MEMORY"
    assert response.input_tokens == 90
    assert response.output_tokens == 100
    assert response.cost_usd is None


def _enable(
    monkeypatch,
    *,
    case_limit=2,
    baseline="",
    candidate="dashscope/qwen-flash",
):
    monkeypatch.setattr(
        "app.monitoring.provider_quality_eval.settings.provider_quality_eval_enabled",
        True,
    )
    monkeypatch.setattr(
        "app.monitoring.provider_quality_eval.settings.provider_quality_eval_case_limit",
        case_limit,
    )
    monkeypatch.setattr(
        "app.monitoring.provider_quality_eval.settings.provider_quality_eval_baseline_model",
        baseline,
    )
    monkeypatch.setattr(
        "app.monitoring.provider_quality_eval.settings.provider_quality_eval_candidate_model",
        candidate,
    )


def _plan(tool: str, content: str, scope: str) -> SaveToolPlanData:
    should_save = tool != "NONE"
    return SaveToolPlanData.model_validate({
        "should_save": should_save,
        "tool": tool,
        "content": content,
        "title": "Synthetic result" if should_save else "无需保存",
        "summary": content,
        "visibility": scope if scope != "PARENT_VISIBLE" else "CARE_VISIBLE",
        "entry_type": "DAILY",
        "memory_type": "ELDER_ADVICE",
        "scope": scope,
        "category": "VISION" if tool == "GROWTH_GUARD" else "OTHER",
        "severity": 2,
        "importance": 3,
        "tags": [],
        "reason": "Synthetic evaluation",
        "confirmation_message": "Synthetic evaluation",
    })
