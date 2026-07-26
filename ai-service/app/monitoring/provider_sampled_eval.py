"""Cost-bounded real-provider evaluation with fixed public fixtures."""

from __future__ import annotations

import asyncio
import json
import time
import uuid

from openai import AuthenticationError, PermissionDeniedError, RateLimitError

from app.config import settings
from app.llm.completion_config import provider_completion
from app.monitoring.provider_eval_support import (
    completion_cost,
    provider_name,
    sum_optional_cost,
    sum_optional_int,
    token_usage,
)
from app.monitoring.provider_sampled_eval_models import (
    MAX_CASES,
    MAX_CASE_TOKENS,
    MAX_TIMEOUT_SECONDS,
    PROVIDER_SAMPLED_EVAL_SCHEMA_VERSION,
    PUBLIC_SAMPLE_FIXTURE_VERSION,
    PUBLIC_SAMPLE_CASES,
    ProviderProbeAuthenticationError,
    ProviderProbeClient,
    ProviderProbeRateLimitError,
    ProviderProbeResponse,
    ProviderSampleCase,
    ProviderSampleErrorCode,
    ProviderSampleResult,
    ProviderSampleStatus,
    ProviderSampledEvalReport,
)


class DefaultProviderProbeClient:
    """Call the primary model once without retry or fallback."""

    async def complete(self, case: ProviderSampleCase) -> ProviderProbeResponse:
        model = settings.provider_sampled_eval_model
        started_at = time.monotonic()
        try:
            response = await provider_completion(
                model=model,
                messages=[{"role": "user", "content": case.prompt}],
                temperature=0,
                max_tokens=case.max_tokens,
                extra_body={"enable_thinking": False},
            )
        except (AuthenticationError, PermissionDeniedError) as error:
            raise ProviderProbeAuthenticationError from error
        except RateLimitError as error:
            raise ProviderProbeRateLimitError from error
        input_tokens, output_tokens = token_usage(response)
        return ProviderProbeResponse(
            content=response.choices[0].message.content or "",
            provider=provider_name(model),
            model=model,
            latency_ms=max(0, round((time.monotonic() - started_at) * 1000)),
            input_tokens=input_tokens,
            output_tokens=output_tokens,
            cost_usd=completion_cost(response, model),
        )


class ProviderSampledEval:
    def __init__(self, client: ProviderProbeClient | None = None):
        self._client = client or DefaultProviderProbeClient()

    async def run(self) -> ProviderSampledEvalReport:
        eval_run_id = str(uuid.uuid4())
        if not settings.provider_sampled_eval_enabled:
            return _empty_report(eval_run_id, ProviderSampleStatus.DISABLED)

        cases = _configured_cases()
        if cases is None:
            return _empty_report(
                eval_run_id,
                ProviderSampleStatus.CONFIG_INVALID,
                ProviderSampleErrorCode.CONFIG_INVALID,
            )

        results: list[ProviderSampleResult] = []
        for case in cases:
            result = await self._run_case(case)
            results.append(result)
            if not result.passed:
                break

        passed = len(results) == len(cases) and all(item.passed for item in results)
        return ProviderSampledEvalReport(
            schema_version=PROVIDER_SAMPLED_EVAL_SCHEMA_VERSION,
            fixture_version=PUBLIC_SAMPLE_FIXTURE_VERSION,
            eval_run_id=eval_run_id,
            model=settings.provider_sampled_eval_model,
            status=ProviderSampleStatus.PASSED if passed else ProviderSampleStatus.FAILED,
            success=passed,
            configured_case_count=len(cases),
            executed_case_count=len(results),
            max_output_token_budget=sum(case.max_tokens for case in cases),
            total_input_tokens=sum_optional_int([item.input_tokens for item in results]),
            total_output_tokens=sum_optional_int([item.output_tokens for item in results]),
            estimated_cost_usd=sum_optional_cost([item.cost_usd for item in results]),
            error_code=None if passed else results[-1].error_code,
            results=tuple(results),
        )

    async def _run_case(self, case: ProviderSampleCase) -> ProviderSampleResult:
        started_at = time.monotonic()
        try:
            async with asyncio.timeout(settings.provider_sampled_eval_timeout_seconds):
                response = await self._client.complete(case)
        except TimeoutError:
            return _failed_result(
                case,
                ProviderSampleErrorCode.TIMEOUT,
                _elapsed_ms(started_at),
            )
        except ProviderProbeAuthenticationError:
            return _failed_result(
                case,
                ProviderSampleErrorCode.AUTHENTICATION_ERROR,
                _elapsed_ms(started_at),
            )
        except ProviderProbeRateLimitError:
            return _failed_result(
                case,
                ProviderSampleErrorCode.RATE_LIMITED,
                _elapsed_ms(started_at),
            )
        except Exception:
            return _failed_result(
                case,
                ProviderSampleErrorCode.PROVIDER_ERROR,
                _elapsed_ms(started_at),
            )

        token_budget_exceeded = (
            response.output_tokens is not None
            and response.output_tokens > case.max_tokens
        )
        passed = (
            not token_budget_exceeded
            and response.content.strip() == case.expected_output
        )
        error_code = None
        if token_budget_exceeded:
            error_code = ProviderSampleErrorCode.TOKEN_BUDGET_EXCEEDED
        elif not passed:
            error_code = ProviderSampleErrorCode.RESPONSE_MISMATCH
        return ProviderSampleResult(
            case_id=case.case_id,
            passed=passed,
            provider=response.provider,
            model=response.model,
            latency_ms=response.latency_ms,
            max_tokens=case.max_tokens,
            attempt_count=1,
            degraded=False,
            error_code=error_code,
            input_tokens=response.input_tokens,
            output_tokens=response.output_tokens,
            cost_usd=response.cost_usd,
        )


def _configured_cases() -> tuple[ProviderSampleCase, ...] | None:
    case_limit = settings.provider_sampled_eval_case_limit
    timeout = settings.provider_sampled_eval_timeout_seconds
    model = settings.provider_sampled_eval_model.strip()
    if (
        not 1 <= case_limit <= MAX_CASES
        or not 0 < timeout <= MAX_TIMEOUT_SECONDS
        or "/" not in model
    ):
        return None
    cases = PUBLIC_SAMPLE_CASES[:case_limit]
    if any(case.max_tokens > MAX_CASE_TOKENS for case in cases):
        return None
    return cases


def _empty_report(
    eval_run_id: str,
    status: ProviderSampleStatus,
    error_code: ProviderSampleErrorCode | None = None,
) -> ProviderSampledEvalReport:
    return ProviderSampledEvalReport(
        schema_version=PROVIDER_SAMPLED_EVAL_SCHEMA_VERSION,
        fixture_version=PUBLIC_SAMPLE_FIXTURE_VERSION,
        eval_run_id=eval_run_id,
        model=settings.provider_sampled_eval_model,
        status=status,
        success=False,
        configured_case_count=0,
        executed_case_count=0,
        max_output_token_budget=0,
        total_input_tokens=0,
        total_output_tokens=0,
        estimated_cost_usd=0.0,
        error_code=error_code,
        results=(),
    )


def _failed_result(
    case: ProviderSampleCase,
    error_code: ProviderSampleErrorCode,
    latency_ms: int,
) -> ProviderSampleResult:
    model = settings.provider_sampled_eval_model
    return ProviderSampleResult(
        case_id=case.case_id,
        passed=False,
        provider=provider_name(model),
        model=model,
        latency_ms=latency_ms,
        max_tokens=case.max_tokens,
        attempt_count=1,
        degraded=False,
        error_code=error_code,
        input_tokens=None,
        output_tokens=None,
        cost_usd=None,
    )


def _elapsed_ms(started_at: float) -> int:
    return max(0, round((time.monotonic() - started_at) * 1000))


def report_exit_code(report: ProviderSampledEvalReport) -> int:
    if report.status in {ProviderSampleStatus.DISABLED, ProviderSampleStatus.PASSED}:
        return 0
    return 1


async def _main() -> int:
    report = await ProviderSampledEval().run()
    print(json.dumps(report.as_dict(), ensure_ascii=False))
    return report_exit_code(report)


if __name__ == "__main__":
    raise SystemExit(asyncio.run(_main()))
