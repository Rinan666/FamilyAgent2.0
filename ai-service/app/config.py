"""Application configuration loaded from environment variables."""

import os
from pathlib import Path
from typing import Optional

from pydantic_settings import BaseSettings, PydanticBaseSettingsSource

ROOT_ENV_FILE = Path(__file__).resolve().parents[2] / ".env"


class Settings(BaseSettings):
    """Application settings."""

    # App
    app_env: str = "development"
    app_debug: bool = True

    # AI service
    ai_service_port: int = 8090

    # Backend
    backend_url: str = "http://localhost:8080"
    auth_fail_open: Optional[bool] = None
    cors_allow_origins: Optional[str] = None

    # LLM
    claude_api_key: Optional[str] = None
    openai_api_key: Optional[str] = None
    dashscope_api_key: Optional[str] = None
    dashscope_base_url: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    dashscope_multimodal_url: str = (
        "https://dashscope.aliyuncs.com/api/v1/services/embeddings/"
        "multimodal-embedding/multimodal-embedding"
    )
    default_llm_model: str = "dashscope/qwen-flash"
    fallback_llm_model: str = "dashscope/qwen-turbo"
    ai_internal_service_token: Optional[str] = None
    embedding_model: str = "dashscope-multimodal/qwen3-vl-embedding"
    embedding_dimension: int = 1536

    # Web search for time-sensitive public facts
    web_search_enabled: bool = True
    web_search_provider: str = "duckduckgo"
    web_search_timeout_seconds: float = 8.0
    web_search_max_results: int = 4
    web_search_stream_metadata_timeout_seconds: float = 0.25
    tavily_api_key: Optional[str] = None

    # AI cost and DoS protection
    ai_max_message_chars: int = 8000
    ai_max_total_input_chars: int = 20000
    ai_max_output_tokens: int = 1800
    ai_hard_timeout_seconds: float = 100.0
    ai_stream_idle_timeout_seconds: float = 20.0
    ai_global_concurrency: int = 8
    ai_user_concurrency: int = 2
    ai_user_rate_limit_per_minute: int = 20
    ai_ip_rate_limit_per_minute: int = 60
    ai_embedding_user_rate_limit_per_minute: int = 120
    ai_embedding_ip_rate_limit_per_minute: int = 240
    ai_embedding_timeout_seconds: float = 30.0

    # Synthetic provider monitoring (never used by readiness)
    provider_monitor_enabled: bool = False
    provider_monitor_timeout_seconds: float = 30.0
    provider_monitor_max_tokens: int = 8

    # Sampled provider evaluation (fixed public fixtures, never used by readiness)
    provider_sampled_eval_enabled: bool = False
    provider_sampled_eval_model: str = "dashscope/qwen-flash"
    provider_sampled_eval_timeout_seconds: float = 20.0
    provider_sampled_eval_case_limit: int = 2

    # Math sandbox
    math_sandbox_timeout: int = 5
    math_sandbox_max_memory_mb: int = 256

    # Logging
    log_level: str = "DEBUG"

    model_config = {
        "env_file": str(ROOT_ENV_FILE),
        "env_file_encoding": "utf-8",
        "extra": "ignore",
    }

    @classmethod
    def settings_customise_sources(
        cls,
        settings_cls,
        init_settings: PydanticBaseSettingsSource,
        env_settings: PydanticBaseSettingsSource,
        dotenv_settings: PydanticBaseSettingsSource,
        file_secret_settings: PydanticBaseSettingsSource,
    ):
        # Local development prefers the repository-root .env, while
        # production-like environments should honor process-level env vars.
        current_env = os.environ.get("APP_ENV", "").strip().lower()
        if current_env and current_env != "development":
            return init_settings, env_settings, dotenv_settings, file_secret_settings
        return init_settings, dotenv_settings, env_settings, file_secret_settings

    def __init__(self, **kwargs):
        super().__init__(**kwargs)

        # LiteLLM expects provider keys in OS environment variables.
        if self.claude_api_key:
            os.environ["ANTHROPIC_API_KEY"] = self.claude_api_key
        else:
            os.environ.pop("ANTHROPIC_API_KEY", None)

        if self.openai_api_key:
            os.environ["OPENAI_API_KEY"] = self.openai_api_key
        else:
            os.environ.pop("OPENAI_API_KEY", None)

        if self.dashscope_api_key:
            os.environ["DASHSCOPE_API_KEY"] = self.dashscope_api_key
        else:
            os.environ.pop("DASHSCOPE_API_KEY", None)

        # LiteLLM's DashScope provider reads the compatible endpoint from env.
        if self.dashscope_base_url:
            os.environ["DASHSCOPE_API_BASE"] = self.dashscope_base_url
        else:
            os.environ.pop("DASHSCOPE_API_BASE", None)

    @property
    def auth_fail_open_enabled(self) -> bool:
        """Fail-open is only permitted in local development."""
        if not self.is_development_env:
            return False
        if self.auth_fail_open is not None:
            return self.auth_fail_open
        return False

    @property
    def is_development_env(self) -> bool:
        """Treat common local aliases as development to avoid accidental fail-closed mode."""
        return self.app_env.strip().lower() in {"dev", "development", "local"}

    @property
    def internal_service_token(self) -> Optional[str]:
        """Token used by the Java backend for service-to-service AI calls."""
        if self.ai_internal_service_token:
            return self.ai_internal_service_token
        if self.is_development_env:
            return "familyagent-dev-internal-token"
        return None

    @property
    def cors_origins(self) -> list[str]:
        """Allowed browser origins for the AI service."""
        raw = self.cors_allow_origins
        if not raw:
            if self.is_development_env:
                return ["http://localhost:3000", "http://127.0.0.1:3000"]
            return []
        return [origin.strip() for origin in raw.split(",") if origin.strip()]


settings = Settings()
