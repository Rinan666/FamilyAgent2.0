"""Privacy-safe real-provider quality sampling for save-plan behavior."""

from __future__ import annotations

import asyncio
import json
import time
import uuid

from openai import AuthenticationError, PermissionDeniedError, RateLimitError

from app.api.memory_contracts import MEMORY_SAVE_PLAN_SCHEMA
from app.config import settings
from app.llm.completion_config import provider_completion
from app.monitoring.provider_eval_support import (
    completion_cost,
    provider_name,
    token_usage,
)
from app.monitoring.provider_quality_eval_models import (
    MAX_QUALITY_CASES,
    MAX_QUALITY_CASE_TOKENS,
    MAX_QUALITY_TIMEOUT_SECONDS,
    PROVIDER_QUALITY_EVAL_SCHEMA_VERSION,
    PROVIDER_QUALITY_FIXTURE_VERSION,
    SYNTHETIC_QUALITY_CASES,
    ProviderQualityAuthenticationError,
    ProviderQualityCase,
    ProviderQualityCaseResult,
    ProviderQualityErrorCode,
    ProviderQualityEvalReport,
    ProviderQualityModelReport,
    ProviderQualityProbeClient,
    ProviderQualityProbeResponse,
    ProviderQualityRateLimitError,
    ProviderQualityStatus,
    ProviderQualityStructuredOutputError,
)
from app.monitoring.provider_quality_scoring import (
    build_model_report,
    compare_reports,
    evaluate_response,
    failed_result,
    report_error_code,
)
from app.runtime.output_parser import SaveMemoryOutputParser, SkillOutputParseError
from app.runtime.prompt_renderer import SaveMemoryPromptRenderer

_FATAL_PROVIDER_ERRORS = {
    ProviderQualityErrorCode.TIMEOUT,
    ProviderQualityErrorCode.AUTHENTICATION_ERROR,
    ProviderQualityErrorCode.RATE_LIMITED,
    ProviderQualityErrorCode.PROVIDER_ERROR,
}


class DefaultProviderQualityProbeClient:
    """Call one configured model without retry or fallback and parse a safe plan."""

    def __init__(self) -> None:
        self._renderer = SaveMemoryPromptRenderer()
        self._parser = SaveMemoryOutputParser()

    async def complete(
        self,
        model: str,
        case: ProviderQualityCase,
    ) -> ProviderQualityProbeResponse:
        started_at = time.monotonic()
        try:
            response = await provider_completion(
                model=model,
                messages=self._renderer.render(
                    family_context="Synthetic evaluation family",
                    target_member_name="Synthetic child",
                    viewer_role="PARENT",
                    conversation_context="",
                    message=case.message,
                ),
                temperature=0.1,
                max_tokens=case.max_tokens,
                response_format=MEMORY_SAVE_PLAN_SCHEMA,
                extra_body={"enable_thinking": False},
            )
        except (AuthenticationError, PermissionDeniedError) as error:
            raise ProviderQualityAuthenticationError from error
        except RateLimitError as error:
            raise ProviderQualityRateLimitError from error

        try:
            raw_content = response.choices[0].message.content or ""
            plan = self._parser.parse(raw_content)
        except (IndexError, AttributeError, SkillOutputParseError) as error:
            raise ProviderQualityStructuredOutputError from error

        input_tokens, output_tokens = token_usage(response)
        return ProviderQualityProbeResponse(
            plan=plan,
            provider=provider_name(model),
            model=model,
            latency_ms=_elapsed_ms(started_at),
            input_tokens=input_tokens,
            output_tokens=output_tokens,
            cost_usd=completion_cost(response, model),
        )


class ProviderQualityEval:
    def __init__(self, client: ProviderQualityProbeClient | None = None) -> None:
        self._client = client or DefaultProviderQualityProbeClient()

    async def run(self) -> ProviderQualityEvalReport:
        eval_run_id = str(uuid.uuid4())
        if not settings.provider_quality_eval_enabled:
            return _empty_report(eval_run_id, ProviderQualityStatus.DISABLED)

        configuration = _configured_evaluation()
        if configuration is None:
            return _empty_report(
                eval_run_id,
                ProviderQualityStatus.CONFIG_INVALID,
                ProviderQualityErrorCode.CONFIG_INVALID,
            )

        models, cases = configuration
        runs = tuple([await self._run_model(model, cases) for model in models])
        baseline = runs[0] if len(runs) == 2 else None
        candidate = runs[-1]
        comparison = (
            compare_reports(baseline, candidate)
            if baseline and baseline.completed
            else None
        )
        success = candidate.success and (baseline is None or baseline.completed)
        error_code = report_error_code(baseline, candidate, success)
        return ProviderQualityEvalReport(
            schema_version=PROVIDER_QUALITY_EVAL_SCHEMA_VERSION,
            fixture_version=PROVIDER_QUALITY_FIXTURE_VERSION,
            eval_run_id=eval_run_id,
            status=ProviderQualityStatus.PASSED if success else ProviderQualityStatus.FAILED,
            success=success,
            candidate_model=candidate.model,
            baseline_model=baseline.model if baseline else None,
            configured_case_count=len(cases),
            model_run_count=len(runs),
            max_output_token_budget=sum(case.max_tokens for case in cases) * len(models),
            error_code=error_code,
            runs=runs,
            comparison=comparison,
        )

    async def _run_model(
        self,
        model: str,
        cases: tuple[ProviderQualityCase, ...],
    ) -> ProviderQualityModelReport:
        results: list[ProviderQualityCaseResult] = []
        for case in cases:
            result = await self._run_case(model, case)
            results.append(result)
            if result.error_code in _FATAL_PROVIDER_ERRORS:
                break
        return build_model_report(model, cases, results)

    async def _run_case(
        self,
        model: str,
        case: ProviderQualityCase,
    ) -> ProviderQualityCaseResult:
        started_at = time.monotonic()
        try:
            async with asyncio.timeout(settings.provider_quality_eval_timeout_seconds):
                response = await self._client.complete(model, case)
        except TimeoutError:
            return failed_result(case, ProviderQualityErrorCode.TIMEOUT, _elapsed_ms(started_at))
        except ProviderQualityAuthenticationError:
            return failed_result(
                case,
                ProviderQualityErrorCode.AUTHENTICATION_ERROR,
                _elapsed_ms(started_at),
            )
        except ProviderQualityRateLimitError:
            return failed_result(
                case,
                ProviderQualityErrorCode.RATE_LIMITED,
                _elapsed_ms(started_at),
            )
        except ProviderQualityStructuredOutputError:
            return failed_result(
                case,
                ProviderQualityErrorCode.STRUCTURED_OUTPUT_INVALID,
                _elapsed_ms(started_at),
            )
        except Exception:
            return failed_result(
                case,
                ProviderQualityErrorCode.PROVIDER_ERROR,
                _elapsed_ms(started_at),
            )
        return evaluate_response(case, response)


def _configured_evaluation(
) -> tuple[tuple[str, ...], tuple[ProviderQualityCase, ...]] | None:
    case_limit = settings.provider_quality_eval_case_limit
    timeout = settings.provider_quality_eval_timeout_seconds
    candidate = settings.provider_quality_eval_candidate_model.strip()
    baseline = settings.provider_quality_eval_baseline_model.strip()
    if (
        not 1 <= case_limit <= MAX_QUALITY_CASES
        or not 0 < timeout <= MAX_QUALITY_TIMEOUT_SECONDS
        or "/" not in candidate
        or (baseline and ("/" not in baseline or baseline == candidate))
    ):
        return None
    cases = SYNTHETIC_QUALITY_CASES[:case_limit]
    if any(case.max_tokens > MAX_QUALITY_CASE_TOKENS for case in cases):
        return None
    models = (baseline, candidate) if baseline else (candidate,)
    return models, cases


def _empty_report(
    eval_run_id: str,
    status: ProviderQualityStatus,
    error_code: ProviderQualityErrorCode | None = None,
) -> ProviderQualityEvalReport:
    return ProviderQualityEvalReport(
        schema_version=PROVIDER_QUALITY_EVAL_SCHEMA_VERSION,
        fixture_version=PROVIDER_QUALITY_FIXTURE_VERSION,
        eval_run_id=eval_run_id,
        status=status,
        success=False,
        candidate_model=settings.provider_quality_eval_candidate_model,
        baseline_model=settings.provider_quality_eval_baseline_model or None,
        configured_case_count=0,
        model_run_count=0,
        max_output_token_budget=0,
        error_code=error_code,
        runs=(),
        comparison=None,
    )


def _elapsed_ms(started_at: float) -> int:
    return max(0, round((time.monotonic() - started_at) * 1000))


def report_exit_code(report: ProviderQualityEvalReport) -> int:
    if report.status in {ProviderQualityStatus.DISABLED, ProviderQualityStatus.PASSED}:
        return 0
    return 1


async def _main() -> int:
    report = await ProviderQualityEval().run()
    print(json.dumps(report.as_dict(), ensure_ascii=False))
    return report_exit_code(report)


if __name__ == "__main__":
    raise SystemExit(asyncio.run(_main()))
