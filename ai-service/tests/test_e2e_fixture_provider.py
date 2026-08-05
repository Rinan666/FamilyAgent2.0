import json

import pytest

from app.llm.completion_config import provider_completion


@pytest.mark.asyncio
async def test_e2e_fixture_provider_returns_save_plan_in_e2e(monkeypatch):
    monkeypatch.setattr("app.llm.completion_config.settings.app_env", "e2e")
    monkeypatch.setattr(
        "app.llm.completion_config.settings.e2e_fixture_provider_enabled",
        True,
    )

    response = await provider_completion(
        model="e2e-fixture/save-plan",
        messages=[{
            "role": "user",
            "content": "<selected_content>保存这段 E2E 内容</selected_content>",
        }],
        response_format={"json_schema": {"name": "agent_memory_save_plan"}},
    )

    payload = json.loads(response.choices[0].message.content)
    assert payload["should_save"] is True
    assert payload["content"] == "保存这段 E2E 内容"
    assert payload["confirmation_message"]


@pytest.mark.asyncio
async def test_e2e_fixture_provider_rejects_other_schemas(monkeypatch):
    monkeypatch.setattr("app.llm.completion_config.settings.app_env", "e2e")
    monkeypatch.setattr(
        "app.llm.completion_config.settings.e2e_fixture_provider_enabled",
        True,
    )

    with pytest.raises(ValueError, match="Unsupported E2E fixture schema"):
        await provider_completion(
            model="e2e-fixture/save-plan",
            messages=[],
            response_format={"json_schema": {"name": "unrelated_contract"}},
        )


@pytest.mark.asyncio
async def test_e2e_fixture_provider_is_rejected_outside_e2e(monkeypatch):
    monkeypatch.setattr("app.llm.completion_config.settings.app_env", "production")
    monkeypatch.setattr(
        "app.llm.completion_config.settings.e2e_fixture_provider_enabled",
        True,
    )

    with pytest.raises(RuntimeError, match="disabled"):
        await provider_completion(
            model="e2e-fixture/save-plan",
            messages=[],
            response_format={"json_schema": {"name": "agent_memory_save_plan"}},
        )
