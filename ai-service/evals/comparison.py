"""Compare two eval reports without reading prompts or model output."""

from .comparison_models import (
    EvalArtifactChange,
    EvalCaseChange,
    EvalCaseChangeKind,
    EvalComparisonConclusion,
    EvalComparisonMetrics,
    EvalComparisonReport,
    EvalGateChange,
)
from .models import EvalReport

COMPARISON_SCHEMA_VERSION = "eval.comparison.v1"


class EvalReportComparator:
    def compare(self, baseline: EvalReport, candidate: EvalReport) -> EvalComparisonReport:
        case_changes = _case_changes(baseline, candidate)
        gate_changes = _gate_changes(baseline, candidate)
        artifact_changes = _artifact_changes(baseline, candidate)
        metrics = EvalComparisonMetrics(
            unchanged_count=_count(case_changes, EvalCaseChangeKind.UNCHANGED),
            improvement_count=_count(case_changes, EvalCaseChangeKind.IMPROVEMENT),
            regression_count=_count(case_changes, EvalCaseChangeKind.REGRESSION),
            added_count=_count(case_changes, EvalCaseChangeKind.ADDED),
            removed_count=_count(case_changes, EvalCaseChangeKind.REMOVED),
            gate_regression_count=sum(
                change.baseline_passed is True and change.candidate_passed is not True
                for change in gate_changes
            ),
        )
        return EvalComparisonReport(
            schema_version=COMPARISON_SCHEMA_VERSION,
            baseline_suite_version=baseline.suite_version,
            candidate_suite_version=candidate.suite_version,
            conclusion=_conclusion(baseline, candidate, metrics),
            metrics=metrics,
            case_changes=case_changes,
            gate_changes=gate_changes,
            artifact_changes=artifact_changes,
        )


def _case_changes(baseline: EvalReport, candidate: EvalReport) -> tuple[EvalCaseChange, ...]:
    baseline_cases = {item.case_id: item for item in baseline.results}
    candidate_cases = {item.case_id: item for item in candidate.results}
    changes = []
    for case_id in sorted(set(baseline_cases) | set(candidate_cases)):
        before = baseline_cases.get(case_id)
        after = candidate_cases.get(case_id)
        changes.append(EvalCaseChange(
            case_id=case_id,
            change=_case_change_kind(
                before.passed if before else None,
                after.passed if after else None,
            ),
            baseline_passed=before.passed if before else None,
            candidate_passed=after.passed if after else None,
        ))
    return tuple(changes)


def _case_change_kind(before: bool | None, after: bool | None) -> EvalCaseChangeKind:
    if before is None:
        return EvalCaseChangeKind.ADDED
    if after is None:
        return EvalCaseChangeKind.REMOVED
    if before == after:
        return EvalCaseChangeKind.UNCHANGED
    return EvalCaseChangeKind.IMPROVEMENT if after else EvalCaseChangeKind.REGRESSION


def _gate_changes(baseline: EvalReport, candidate: EvalReport) -> tuple[EvalGateChange, ...]:
    before = {item.name: item for item in baseline.gates}
    after = {item.name: item for item in candidate.gates}
    return tuple(
        EvalGateChange(
            name=name,
            baseline_passed=before[name].passed if name in before else None,
            candidate_passed=after[name].passed if name in after else None,
        )
        for name in sorted(set(before) | set(after), key=str)
    )


def _artifact_changes(baseline: EvalReport, candidate: EvalReport) -> tuple[EvalArtifactChange, ...]:
    before = {(item.kind, item.name): item.version for item in baseline.artifacts}
    after = {(item.kind, item.name): item.version for item in candidate.artifacts}
    changes = []
    for kind, name in sorted(set(before) | set(after), key=lambda item: (item[0].value, item[1])):
        baseline_version = before.get((kind, name))
        candidate_version = after.get((kind, name))
        if baseline_version != candidate_version:
            changes.append(EvalArtifactChange(
                kind=kind,
                name=name,
                baseline_version=baseline_version,
                candidate_version=candidate_version,
            ))
    return tuple(changes)


def _count(changes: tuple[EvalCaseChange, ...], kind: EvalCaseChangeKind) -> int:
    return sum(change.change == kind for change in changes)


def _conclusion(
    baseline: EvalReport,
    candidate: EvalReport,
    metrics: EvalComparisonMetrics,
) -> EvalComparisonConclusion:
    if (
        baseline.suite_version != candidate.suite_version
        or metrics.added_count > 0
        or metrics.removed_count > 0
    ):
        return EvalComparisonConclusion.INCOMPARABLE
    if metrics.regression_count > 0 or metrics.gate_regression_count > 0:
        return EvalComparisonConclusion.REGRESSION
    if metrics.improvement_count > 0:
        return EvalComparisonConclusion.IMPROVED
    return EvalComparisonConclusion.NO_CHANGE
