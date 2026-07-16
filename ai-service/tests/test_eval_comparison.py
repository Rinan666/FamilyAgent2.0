import pytest

from evals import EvalRunner, default_golden_cases
from evals.comparison import EvalReportComparator
from evals.comparison_models import EvalComparisonConclusion
from evals.models import EvalArtifactVersion


@pytest.mark.asyncio
async def test_comparison_reports_no_change_without_exposing_case_payload():
    report = await EvalRunner().run(default_golden_cases())

    comparison = EvalReportComparator().compare(report, report)

    assert comparison.schema_version == "eval.comparison.v1"
    assert comparison.conclusion == EvalComparisonConclusion.NO_CHANGE
    assert comparison.metrics.unchanged_count == report.metrics.case_count
    assert comparison.metrics.regression_count == 0
    assert comparison.artifact_changes == ()
    payload = comparison.model_dump_json()
    assert "13812345678" not in payload
    assert "model output" not in payload


@pytest.mark.asyncio
async def test_comparison_detects_case_gate_and_prompt_regression():
    report = await EvalRunner().run(default_golden_cases())
    first_result = report.results[0].model_copy(update={"passed": False})
    first_gate = report.gates[0].model_copy(update={"passed": False, "failed_count": 1})
    artifacts = tuple(
        EvalArtifactVersion(
            kind=item.kind,
            name=item.name,
            version="family_chat.system.v2" if item.name == "family_chat.system" else item.version,
        )
        for item in report.artifacts
    )
    candidate = report.model_copy(update={
        "results": (first_result, *report.results[1:]),
        "gates": (first_gate, *report.gates[1:]),
        "artifacts": artifacts,
    })

    comparison = EvalReportComparator().compare(report, candidate)

    assert comparison.conclusion == EvalComparisonConclusion.REGRESSION
    assert comparison.metrics.regression_count == 1
    assert comparison.metrics.gate_regression_count == 1
    assert len(comparison.artifact_changes) == 1
    assert comparison.artifact_changes[0].candidate_version == "family_chat.system.v2"
