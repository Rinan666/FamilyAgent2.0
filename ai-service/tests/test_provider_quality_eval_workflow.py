from pathlib import Path

import yaml


def test_quality_eval_workflow_is_manual_cost_bounded_and_privacy_safe():
    workflow_path = (
        Path(__file__).resolve().parents[2]
        / ".github"
        / "workflows"
        / "provider-quality-eval.yml"
    )
    workflow = yaml.safe_load(workflow_path.read_text(encoding="utf-8"))
    job = workflow["jobs"]["quality-eval"]
    environment = job["env"]
    steps = job["steps"]

    assert workflow[True] == {"workflow_dispatch": None}
    assert environment["APP_ENV"] == "production"
    assert environment["PROVIDER_QUALITY_EVAL_CASE_LIMIT"] == "2"
    assert environment["PROVIDER_QUALITY_EVAL_TIMEOUT_SECONDS"] == "30"
    assert environment["PROVIDER_QUALITY_EVAL_CANDIDATE_MODEL"].endswith(
        "'dashscope/qwen-flash' }}"
    )
    assert "DEFAULT_LLM_MODEL" not in environment
    run_step = next(step for step in steps if step.get("name") == "Run privacy-safe provider quality evaluation")
    assert "PROVIDER_QUALITY_EVAL_ENABLED" in run_step["env"]
    assert "provider-quality-report.json" in run_step["run"]
    upload_step = next(step for step in steps if step.get("uses") == "actions/upload-artifact@v4")
    assert upload_step["with"]["retention-days"] == 14
