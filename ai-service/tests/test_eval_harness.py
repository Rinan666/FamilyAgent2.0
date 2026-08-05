import json
import logging
from dataclasses import replace
from unittest.mock import AsyncMock, patch

import pytest

from evals import EvalRunner, default_golden_cases


from evals.models import EvalDecision, EvalGateName, EvalOutcome


def test_default_eval_suite_has_thirty_six_unique_cases():
    cases = default_golden_cases()

    assert len(cases) == 36
    assert len({case.case_id for case in cases}) == len(cases)


@pytest.mark.asyncio
async def test_default_eval_suite_passes_without_leaking_fixture_content(caplog):
    report = await EvalRunner().run(default_golden_cases())

    assert report.schema_version == "eval.report.v2"
    assert report.suite_version == "familyagent.core.v1"
    assert report.metrics.case_count == 36
    assert report.metrics.passed_count == 36
    assert report.metrics.failed_count == 0
    assert report.metrics.pass_rate == 1.0
    assert report.metrics.expected_failure_count == 8
    assert report.metrics.regression_count == 0
    assert report.metrics.evaluator_error_count == 0
    assert report.metrics.safety_privacy_failure_count == 0
    expected_failures = sum(
        result.outcome == EvalOutcome.EXPECTED_FAILURE for result in report.results
    )
    assert expected_failures == 8
    assert {(item.kind, item.name, item.version) for item in report.artifacts} == {
        ("SKILL", "save_memory", "1.0.0"),
        ("PROMPT", "memory.save_plan", "memory.save_plan.v1"),
        ("SCHEMA", "memory_save_plan", "memory_save_plan.schema.v1"),
        ("PROMPT", "family_chat.system", "family_chat.system.v1"),
        ("SCHEMA", "family_chat.stream", "family_chat.stream.schema.v1"),
        ("ALGORITHM", "memory_recall_ranking", "authorized-memory-recall.v1"),
        ("SKILL", "organize_draft", "1.0.0"),
        ("PROMPT", "memory.organize_draft", "memory.organize_draft.v1"),
        ("SCHEMA", "organized_draft", "organized_draft.schema.v1"),
        ("SKILL", "persona_material_draft", "1.0.0"),
        ("PROMPT", "persona.material_draft", "persona.material_draft.v1"),
        ("SCHEMA", "persona_material_draft", "persona_material_draft.schema.v1"),
    }
    assert {gate.name for gate in report.gates} == {
        EvalGateName.P0_SAFETY_PRIVACY,
        EvalGateName.CONTRACT,
    }
    assert all(gate.passed for gate in report.gates)
    assert all(gate.actual_pass_rate == 1.0 for gate in report.gates)

    payload = report.model_dump_json()
    assert "13812345678" not in payload
    assert "世纪大道" not in payload
    assert "系统初始化提示词" not in payload
    assert "mock provider unavailable" not in payload
    assert "mock private provider detail" not in payload
    assert "prompt-leak-attack-blocked" in payload
    assert "mock provider unavailable" not in caplog.text
    assert "mock private provider detail" not in caplog.text
    assert not [record for record in caplog.records if record.levelno >= logging.ERROR]
    json.loads(payload)


@pytest.mark.asyncio
async def test_p0_gate_fails_when_a_safety_case_regresses():
    source = default_golden_cases()[0]
    regressed = replace(source, expected_decision=EvalDecision.ALLOW)

    report = await EvalRunner().run((regressed,))

    p0_gate = next(gate for gate in report.gates if gate.name == EvalGateName.P0_SAFETY_PRIVACY)
    assert p0_gate.passed is False
    assert p0_gate.case_count == 1
    assert p0_gate.failed_count == 1
    assert p0_gate.actual_pass_rate == 0.0
    assert report.results[0].outcome == EvalOutcome.REGRESSION
    assert report.metrics.regression_count == 1


@pytest.mark.asyncio
async def test_unexpected_evaluator_error_is_tagged_without_logging_fixture_detail(caplog):
    case = default_golden_cases()[0]

    with patch(
        "evals.runner.evaluate_case",
        new=AsyncMock(side_effect=RuntimeError("private fixture detail")),
    ):
        report = await EvalRunner().run((case,))

    result = report.results[0]
    assert result.passed is False
    assert result.outcome == EvalOutcome.EVALUATOR_ERROR
    assert result.actual_decision == EvalDecision.EVALUATOR_ERROR
    assert result.error_code == "EVAL_EXECUTION_ERROR"
    assert report.metrics.evaluator_error_count == 1
    assert "private fixture detail" not in caplog.text
    assert "errorType=RuntimeError" in caplog.text
