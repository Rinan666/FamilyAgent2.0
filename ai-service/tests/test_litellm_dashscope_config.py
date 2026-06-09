import os

from app.config import Settings


def test_settings_export_dashscope_env_for_litellm(monkeypatch):
    monkeypatch.delenv("DASHSCOPE_API_KEY", raising=False)
    monkeypatch.delenv("DASHSCOPE_API_BASE", raising=False)

    Settings(
        dashscope_api_key="secret-key",
        dashscope_base_url="https://dashscope.example/compatible-mode/v1",
    )

    assert os.environ["DASHSCOPE_API_KEY"] == "secret-key"
    assert os.environ["DASHSCOPE_API_BASE"] == "https://dashscope.example/compatible-mode/v1"
