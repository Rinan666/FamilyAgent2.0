"""Configuration-only readiness checks without external provider calls."""

from typing import Protocol

from app.api.health_models import (
    ReadinessChecks,
    ReadinessCheckState,
    ReadinessResponse,
    ReadinessStatus,
)


class ReadinessSettings(Protocol):
    default_llm_model: str
    fallback_llm_model: str
    claude_api_key: str | None
    openai_api_key: str | None
    dashscope_api_key: str | None

    @property
    def internal_service_token(self) -> str | None: ...


class ReadinessService:
    """Evaluate whether required AI runtime configuration is available."""

    _PROVIDER_KEY_FIELDS = {
        "anthropic": "claude_api_key",
        "dashscope": "dashscope_api_key",
        "openai": "openai_api_key",
    }

    def __init__(self, app_settings: ReadinessSettings):
        self._settings = app_settings

    def evaluate(self) -> ReadinessResponse:
        primary_state = self._model_state(self._settings.default_llm_model)
        internal_auth_state = self._internal_auth_state()
        required_ready = (
            primary_state == ReadinessCheckState.CONFIGURED
            and internal_auth_state == ReadinessCheckState.CONFIGURED
        )
        return ReadinessResponse(
            status=(
                ReadinessStatus.READY
                if required_ready
                else ReadinessStatus.NOT_READY
            ),
            checks=ReadinessChecks(
                database=ReadinessCheckState.NOT_REQUIRED,
                llm=primary_state,
                fallback_llm=self._fallback_state(),
                internal_service_auth=internal_auth_state,
            ),
        )

    def _fallback_state(self) -> ReadinessCheckState:
        fallback_model = self._settings.fallback_llm_model.strip()
        if not fallback_model:
            return ReadinessCheckState.MISSING_CONFIGURATION
        if fallback_model == self._settings.default_llm_model.strip():
            return ReadinessCheckState.SAME_AS_PRIMARY
        return self._model_state(fallback_model)

    def _internal_auth_state(self) -> ReadinessCheckState:
        if self._settings.internal_service_token:
            return ReadinessCheckState.CONFIGURED
        return ReadinessCheckState.MISSING_CONFIGURATION

    def _model_state(self, model: str) -> ReadinessCheckState:
        provider = self._provider_name(model)
        if not provider:
            return ReadinessCheckState.MISSING_CONFIGURATION
        key_field = self._PROVIDER_KEY_FIELDS.get(provider)
        if key_field is None:
            return ReadinessCheckState.UNSUPPORTED_PROVIDER
        if getattr(self._settings, key_field):
            return ReadinessCheckState.CONFIGURED
        return ReadinessCheckState.MISSING_CONFIGURATION

    @staticmethod
    def _provider_name(model: str) -> str:
        normalized = model.strip().lower()
        if not normalized:
            return ""
        if "/" in normalized:
            return normalized.split("/", 1)[0]
        if normalized.startswith("claude"):
            return "anthropic"
        if normalized.startswith(("gpt-", "o1", "o3", "o4")):
            return "openai"
        if normalized.startswith("qwen"):
            return "dashscope"
        return normalized
