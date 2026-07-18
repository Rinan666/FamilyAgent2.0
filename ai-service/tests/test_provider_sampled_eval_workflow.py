from pathlib import Path

import yaml


def test_scheduled_provider_eval_uses_isolated_production_configuration():
    workflow_path = (
        Path(__file__).resolve().parents[2]
        / ".github"
        / "workflows"
        / "provider-sampled-eval.yml"
    )
    workflow = yaml.safe_load(workflow_path.read_text(encoding="utf-8"))
    job = workflow["jobs"]["sampled-eval"]
    environment = job["env"]

    assert job["if"] == (
        "github.event_name == 'workflow_dispatch' || "
        "vars.PROVIDER_SAMPLED_EVAL_ENABLED == 'true'"
    )
    assert environment["APP_ENV"] == "production"
    assert environment["PROVIDER_SAMPLED_EVAL_MODEL"].endswith(
        "'dashscope/qwen-flash' }}"
    )
    assert "DEFAULT_LLM_MODEL" not in environment
