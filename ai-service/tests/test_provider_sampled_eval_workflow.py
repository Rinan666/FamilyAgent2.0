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
    assert environment["DEFAULT_LLM_MODEL"].endswith(
        "vars.PROVIDER_SAMPLED_EVAL_MODEL || 'dashscope/qwen-flash' }}"
    )
    assert environment["FALLBACK_LLM_MODEL"].endswith(
        "'dashscope/qwen-turbo' }}"
    )
    assert environment["PROVIDER_MONITOR_MAX_TOKENS"] == "8"
    assert environment["PROVIDER_MONITOR_TIMEOUT_SECONDS"] == "30"

    steps = {step["name"]: step for step in job["steps"] if "name" in step}
    monitor_step = steps["Run privacy-safe provider fallback monitor"]
    sampled_step = steps["Run cost-bounded provider evaluation"]
    upload_step = steps["Upload privacy-safe provider reports"]

    assert monitor_step["env"]["PROVIDER_MONITOR_ENABLED"] == "true"
    assert "provider_synthetic_monitor" in monitor_step["run"]
    assert sampled_step["if"].startswith("always()")
    assert "provider_sampled_eval" in sampled_step["run"]
    assert upload_step["with"]["retention-days"] == 14
    assert "health" not in monitor_step["run"]
