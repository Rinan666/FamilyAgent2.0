"""Score provider quality samples and build privacy-safe aggregate reports."""

from __future__ import annotations

from app.monitoring.provider_eval_support import (
    provider_name,
    sum_optional_cost,
    sum_optional_int,
)
from app.monitoring.provider_quality_eval_models import (
    MIN_QUALITY_SCORE,
    ProviderQualityCase,
    ProviderQualityCaseResult,
    ProviderQualityComparison,
    ProviderQualityErrorCode,
    ProviderQualityModelReport,
    ProviderQualityProbeResponse,
)


def evaluate_response(
    case: ProviderQualityCase,
    response: ProviderQualityProbeResponse,
) -> ProviderQualityCaseResult:
    plan = response.plan
    expected_should_save = case.expected_tool != "NONE"
    tool_match = plan.tool == case.expected_tool
    should_save_match = plan.should_save == expected_should_save
    scope_match = plan.scope == case.expected_scope if case.expected_scope else None
    content = plan.content.strip()
    matched_anchors = sum(
        any(anchor in content for anchor in group)
        for group in case.anchor_groups
    )
    anchor_coverage = (
        round(matched_anchors / len(case.anchor_groups), 4)
        if case.anchor_groups
        else 1.0
    )
    forbidden_term_count = sum(term in content for term in case.forbidden_terms)
    criteria = [tool_match, should_save_match, forbidden_term_count == 0]
    if scope_match is not None:
        criteria.append(scope_match)
    if case.expected_tool == "NONE":
        criteria.append(not content)
    criteria.extend(
        any(anchor in content for anchor in group)
        for group in case.anchor_groups
    )
    quality_score = round(sum(criteria) / len(criteria), 4)
    token_budget_exceeded = (
        response.output_tokens is not None
        and response.output_tokens > case.max_tokens
    )
    required_match = (
        tool_match
        and should_save_match
        and scope_match is not False
        and forbidden_term_count == 0
    )
    passed = required_match and quality_score >= MIN_QUALITY_SCORE and not token_budget_exceeded
    error_code = None
    if token_budget_exceeded:
        error_code = ProviderQualityErrorCode.TOKEN_BUDGET_EXCEEDED
    elif not passed:
        error_code = ProviderQualityErrorCode.RESPONSE_MISMATCH
    return ProviderQualityCaseResult(
        case_id=case.case_id,
        passed=passed,
        quality_score=quality_score,
        structured=True,
        actual_tool=plan.tool,
        actual_scope=plan.scope,
        tool_match=tool_match,
        should_save_match=should_save_match,
        scope_match=scope_match,
        anchor_coverage=anchor_coverage,
        forbidden_term_count=forbidden_term_count,
        latency_ms=response.latency_ms,
        max_tokens=case.max_tokens,
        input_tokens=response.input_tokens,
        output_tokens=response.output_tokens,
        cost_usd=response.cost_usd,
        error_code=error_code,
    )


def failed_result(
    case: ProviderQualityCase,
    error_code: ProviderQualityErrorCode,
    latency_ms: int,
) -> ProviderQualityCaseResult:
    return ProviderQualityCaseResult(
        case_id=case.case_id,
        passed=False,
        quality_score=0.0,
        structured=False,
        actual_tool=None,
        actual_scope=None,
        tool_match=False,
        should_save_match=False,
        scope_match=None,
        anchor_coverage=0.0,
        forbidden_term_count=0,
        latency_ms=latency_ms,
        max_tokens=case.max_tokens,
        input_tokens=None,
        output_tokens=None,
        cost_usd=None,
        error_code=error_code,
    )


def build_model_report(
    model: str,
    cases: tuple[ProviderQualityCase, ...],
    results: list[ProviderQualityCaseResult],
) -> ProviderQualityModelReport:
    completed = len(results) == len(cases)
    pass_rate = round(sum(item.passed for item in results) / len(cases), 4)
    average_score = round(
        sum(item.quality_score for item in results) / len(cases),
        4,
    )
    structured_failures = sum(not item.structured for item in results)
    return ProviderQualityModelReport(
        model=model,
        provider=provider_name(model),
        completed=completed,
        success=completed and all(item.passed for item in results),
        configured_case_count=len(cases),
        executed_case_count=len(results),
        pass_rate=pass_rate,
        average_quality_score=average_score,
        structured_failure_rate=round(structured_failures / len(cases), 4),
        total_latency_ms=sum(item.latency_ms for item in results),
        total_input_tokens=sum_optional_int([item.input_tokens for item in results]),
        total_output_tokens=sum_optional_int([item.output_tokens for item in results]),
        estimated_cost_usd=sum_optional_cost([item.cost_usd for item in results]),
        error_code=next((item.error_code for item in results if item.error_code), None),
        results=tuple(results),
    )


def compare_reports(
    baseline: ProviderQualityModelReport,
    candidate: ProviderQualityModelReport,
) -> ProviderQualityComparison:
    return ProviderQualityComparison(
        baseline_model=baseline.model,
        candidate_model=candidate.model,
        pass_rate_delta=round(candidate.pass_rate - baseline.pass_rate, 4),
        quality_score_delta=round(
            candidate.average_quality_score - baseline.average_quality_score,
            4,
        ),
        structured_failure_rate_delta=round(
            candidate.structured_failure_rate - baseline.structured_failure_rate,
            4,
        ),
        latency_ms_delta=candidate.total_latency_ms - baseline.total_latency_ms,
        estimated_cost_usd_delta=_optional_cost_delta(
            baseline.estimated_cost_usd,
            candidate.estimated_cost_usd,
        ),
    )


def report_error_code(
    baseline: ProviderQualityModelReport | None,
    candidate: ProviderQualityModelReport,
    success: bool,
) -> str | None:
    if success:
        return None
    if candidate.error_code:
        return candidate.error_code
    if baseline and not baseline.completed:
        return baseline.error_code
    return ProviderQualityErrorCode.RESPONSE_MISMATCH


def _optional_cost_delta(baseline: float | None, candidate: float | None) -> float | None:
    if baseline is None or candidate is None:
        return None
    return round(candidate - baseline, 8)
