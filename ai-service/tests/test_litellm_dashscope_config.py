import os

import pytest

from app.config import Settings
from app.llm.client import LLMClient
from app.llm.completion_config import completion_provider_kwargs, provider_completion


def test_settings_export_dashscope_env_for_litellm(monkeypatch):
    monkeypatch.delenv("DASHSCOPE_API_KEY", raising=False)
    monkeypatch.delenv("DASHSCOPE_API_BASE", raising=False)

    Settings(
        dashscope_api_key="secret-key",
        dashscope_base_url="https://dashscope.example/compatible-mode/v1",
    )

    assert os.environ["DASHSCOPE_API_KEY"] == "secret-key"
    assert os.environ["DASHSCOPE_API_BASE"] == "https://dashscope.example/compatible-mode/v1"


def test_dashscope_model_uses_openai_compatible_route(monkeypatch):
    monkeypatch.setattr(
        "app.llm.completion_config.settings.dashscope_api_key",
        "secret-key",
    )
    monkeypatch.setattr(
        "app.llm.completion_config.settings.dashscope_base_url",
        "https://dashscope.example/compatible-mode/v1",
    )

    kwargs = completion_provider_kwargs("dashscope/qwen-flash")

    assert kwargs == {
        "model": "openai/qwen-flash",
        "api_base": "https://dashscope.example/compatible-mode/v1",
        "api_key": "secret-key",
    }


@pytest.mark.asyncio
async def test_llm_client_reuses_dashscope_compatible_route(monkeypatch):
    captured = {}

    async def fake_completion(**kwargs):
        captured.update(kwargs)
        message = type("Message", (), {"content": "OK"})()
        choice = type("Choice", (), {"message": message})()
        return type("Response", (), {"choices": [choice]})()

    monkeypatch.setattr("app.llm.client.provider_completion", fake_completion)
    client = LLMClient()
    client.default_model = "dashscope/qwen-flash"

    result = await client.chat([{"role": "user", "content": "hello"}])

    assert result == "OK"
    assert captured["model"] == "dashscope/qwen-flash"


@pytest.mark.asyncio
async def test_dashscope_completion_uses_direct_compatible_client(monkeypatch):
    captured = {}

    class _Completions:
        async def create(self, **kwargs):
            captured.update(kwargs)
            return "response"

    class _Client:
        chat = type("Chat", (), {"completions": _Completions()})()

    monkeypatch.setattr(
        "app.llm.completion_config._openai_compatible_client",
        lambda api_key, api_base: _Client(),
    )
    monkeypatch.setattr(
        "app.llm.completion_config.settings.dashscope_api_key",
        "secret-key",
    )
    monkeypatch.setattr(
        "app.llm.completion_config.settings.dashscope_base_url",
        "https://dashscope.example/compatible-mode/v1",
    )

    response = await provider_completion(
        model="dashscope/qwen-flash",
        messages=[{"role": "user", "content": "hello"}],
        max_tokens=4,
    )

    assert response == "response"
    assert captured["model"] == "qwen-flash"
    assert captured["max_tokens"] == 4
