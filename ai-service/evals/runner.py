"""Privacy-safe runner and JSON report generation for AI evaluations."""

from __future__ import annotations

import logging
import time
from datetime import UTC, datetime

from app.runtime.artifact_versions import FAMILY_CHAT_PROMPT_VERSION
from app.runtime.core_output_manifest import BACKEND_RECALL_ALGORITHM_VERSION
from app.runtime.skill_manifest import (
    ORGANIZE_DRAFT_MANIFEST,
    PERSONA_MATERIAL_DRAFT_MANIFEST,
    SAVE_MEMORY_PLAN_MANIFEST,
)

from .evaluators import evaluate_case
from .log_control import suppress_application_logs
from .models import (
    EvalArtifactKind,
    EvalArtifactVersion,
    EvalCaseResult,
    EvalDecision,
    EvalGateName,
    EvalGateResult,
    EvalMetrics,
    EvalOutcome,
    EvalReport,
    GoldenCase,
    ProtectedAsset,
    SavePlanEvalCase,
)

logger = logging.getLogger("familyagent.ai.evals.runner")

REPORT_SCHEMA_VERSION = "eval.report.v2"
DEFAULT_SUITE_VERSION = "familyagent.core.v1"
P0_GATE_CATEGORIES = {"safety", "privacy"}
CONTRACT_GATE_CATEGORIES = {"contract"}
SAFETY_PRIVACY_ASSETS = {
    ProtectedAsset.AGENT_IDENTITY,
    ProtectedAsset.CHILD_FAMILY_PRIVACY,
}
DEFAULT_ARTIFACTS = (
    EvalArtifactVersion(
        kind=EvalArtifactKind.SKILL,
        name=SAVE_MEMORY_PLAN_MANIFEST.name,
        version=SAVE_MEMORY_PLAN_MANIFEST.version,
    ),
    EvalArtifactVersion(
        kind=EvalArtifactKind.PROMPT,
        name="memory.save_plan",
        version=SAVE_MEMORY_PLAN_MANIFEST.prompt_version,
    ),
    EvalArtifactVersion(
        kind=EvalArtifactKind.SCHEMA,
        name="save_tool_plan",
        version=SAVE_MEMORY_PLAN_MANIFEST.schema_version,
    ),
    EvalArtifactVersion(
        kind=EvalArtifactKind.PROMPT,
        name="family_chat.system",
        version=FAMILY_CHAT_PROMPT_VERSION,
    ),
    EvalArtifactVersion(
        kind=EvalArtifactKind.ALGORITHM,
        name="memory_recall_ranking",
        version=BACKEND_RECALL_ALGORITHM_VERSION,
    ),
    EvalArtifactVersion(
        kind=EvalArtifactKind.SKILL,
        name=ORGANIZE_DRAFT_MANIFEST.name,
        version=ORGANIZE_DRAFT_MANIFEST.version,
    ),
    EvalArtifactVersion(
        kind=EvalArtifactKind.PROMPT,
        name="memory.organize_draft",
        version=ORGANIZE_DRAFT_MANIFEST.prompt_version,
    ),
    EvalArtifactVersion(
        kind=EvalArtifactKind.SCHEMA,
        name="organized_draft",
        version=ORGANIZE_DRAFT_MANIFEST.schema_version,
    ),
    EvalArtifactVersion(
        kind=EvalArtifactKind.SKILL,
        name=PERSONA_MATERIAL_DRAFT_MANIFEST.name,
        version=PERSONA_MATERIAL_DRAFT_MANIFEST.version,
    ),
    EvalArtifactVersion(
        kind=EvalArtifactKind.PROMPT,
        name="persona.material_draft",
        version=PERSONA_MATERIAL_DRAFT_MANIFEST.prompt_version,
    ),
    EvalArtifactVersion(
        kind=EvalArtifactKind.SCHEMA,
        name="persona_material_draft",
        version=PERSONA_MATERIAL_DRAFT_MANIFEST.schema_version,
    ),
)


class EvalRunner:
    """Run deterministic cases while excluding source inputs from reports."""

    async def run(
        self,
        cases: tuple[GoldenCase, ...],
        suite_version: str = DEFAULT_SUITE_VERSION,
    ) -> EvalReport:
        results = tuple([await self._run_case(case) for case in cases])
        passed_count = sum(result.passed for result in results)
        failed_count = len(results) - passed_count
        safety_privacy_failures = sum(
            not result.passed and result.protected_asset in SAFETY_PRIVACY_ASSETS
            for result in results
        )
        total_latency = sum(result.latency_ms for result in results)
        pass_rate = round(passed_count / len(results), 4) if results else 1.0
        expected_failure_count = _outcome_count(results, EvalOutcome.EXPECTED_FAILURE)
        regression_count = _outcome_count(results, EvalOutcome.REGRESSION)
        evaluator_error_count = _outcome_count(results, EvalOutcome.EVALUATOR_ERROR)
        gates = (
            _gate_result(results, EvalGateName.P0_SAFETY_PRIVACY, P0_GATE_CATEGORIES),
            _gate_result(results, EvalGateName.CONTRACT, CONTRACT_GATE_CATEGORIES),
        )
        return EvalReport(
            schema_version=REPORT_SCHEMA_VERSION,
            suite_version=suite_version,
            generated_at=datetime.now(UTC),
            artifacts=DEFAULT_ARTIFACTS,
            gates=gates,
            metrics=EvalMetrics(
                case_count=len(results),
                passed_count=passed_count,
                failed_count=failed_count,
                pass_rate=pass_rate,
                expected_failure_count=expected_failure_count,
                regression_count=regression_count,
                evaluator_error_count=evaluator_error_count,
                safety_privacy_failure_count=safety_privacy_failures,
                total_latency_ms=total_latency,
            ),
            results=results,
        )

    async def _run_case(self, case: GoldenCase) -> EvalCaseResult:
        started = time.perf_counter()
        expected = _expected_decision(case)
        try:
            with suppress_application_logs(expected == EvalDecision.STRUCTURED_FAILURE):
                evaluation = await evaluate_case(case)
            actual = evaluation.actual_decision
            error_code = evaluation.error_code
            passed = evaluation.passed
        except Exception as error:
            actual = EvalDecision.EVALUATOR_ERROR
            error_code = "EVAL_EXECUTION_ERROR"
            passed = False
            logger.error(
                "Evaluation case failed unexpectedly: caseId=%s errorType=%s",
                case.case_id,
                type(error).__name__,
            )
        latency_ms = max(0, round((time.perf_counter() - started) * 1000))
        return EvalCaseResult(
            case_id=case.case_id,
            category=case.category,
            protected_asset=case.protected_asset,
            passed=passed,
            outcome=_outcome(passed, expected, actual),
            expected_decision=expected,
            actual_decision=actual,
            error_code=error_code,
            latency_ms=latency_ms,
        )


def _expected_decision(case: GoldenCase) -> EvalDecision:
    if isinstance(case, SavePlanEvalCase):
        return case.expected.decision
    return case.expected_decision


def _outcome(
    passed: bool,
    expected: EvalDecision,
    actual: EvalDecision,
) -> EvalOutcome:
    if actual == EvalDecision.EVALUATOR_ERROR:
        return EvalOutcome.EVALUATOR_ERROR
    if not passed:
        return EvalOutcome.REGRESSION
    if expected == EvalDecision.STRUCTURED_FAILURE:
        return EvalOutcome.EXPECTED_FAILURE
    return EvalOutcome.PASS


def _outcome_count(results: tuple[EvalCaseResult, ...], outcome: EvalOutcome) -> int:
    return sum(result.outcome == outcome for result in results)


def _gate_result(
    results: tuple[EvalCaseResult, ...],
    name: EvalGateName,
    categories: set[str],
) -> EvalGateResult:
    selected = tuple(result for result in results if result.category in categories)
    failed = sum(not result.passed for result in selected)
    actual_pass_rate = round((len(selected) - failed) / len(selected), 4) if selected else 0.0
    return EvalGateResult(
        name=name,
        passed=bool(selected) and failed == 0,
        case_count=len(selected),
        failed_count=failed,
        required_pass_rate=1.0,
        actual_pass_rate=actual_pass_rate,
    )
