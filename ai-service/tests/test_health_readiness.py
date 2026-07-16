from types import SimpleNamespace

from fastapi.testclient import TestClient

from app.api.health_models import ReadinessCheckState, ReadinessStatus
from app.main import app
from app.services.readiness import ReadinessService


def _settings(**overrides) -> SimpleNamespace:
    values = {
        "app_env": "prod",
        "internal_service_token": "internal-token",
        "dashscope_api_key": "dashscope-key",
        "claude_api_key": None,
        "openai_api_key": None,
        "default_llm_model": "dashscope/qwen-flash",
        "fallback_llm_model": "dashscope/qwen-turbo",
    }
    values.update(overrides)
    return SimpleNamespace(**values)


def test_readiness_reports_required_configuration():
    result = ReadinessService(_settings()).evaluate()

    assert result.status == ReadinessStatus.READY
    assert result.checks.database == ReadinessCheckState.NOT_REQUIRED
    assert result.checks.llm == ReadinessCheckState.CONFIGURED
    assert result.checks.fallback_llm == ReadinessCheckState.CONFIGURED
    assert result.checks.internal_service_auth == ReadinessCheckState.CONFIGURED


def test_readiness_fails_when_primary_provider_key_is_missing():
    result = ReadinessService(_settings(dashscope_api_key=None)).evaluate()

    assert result.status == ReadinessStatus.NOT_READY
    assert result.checks.llm == ReadinessCheckState.MISSING_CONFIGURATION


def test_readiness_fails_when_internal_service_auth_is_missing():
    result = ReadinessService(
        _settings(internal_service_token=None),
    ).evaluate()

    assert result.status == ReadinessStatus.NOT_READY
    assert result.checks.internal_service_auth == ReadinessCheckState.MISSING_CONFIGURATION


def test_readiness_rejects_unknown_primary_provider():
    result = ReadinessService(
        _settings(default_llm_model="unknown/model"),
    ).evaluate()

    assert result.status == ReadinessStatus.NOT_READY
    assert result.checks.llm == ReadinessCheckState.UNSUPPORTED_PROVIDER


def test_readiness_endpoint_returns_typed_response(monkeypatch):
    from app.api import health

    monkeypatch.setattr(
        health.readiness_service,
        "_settings",
        _settings(),
    )

    response = TestClient(app).get("/ai/health/ready")

    assert response.status_code == 200
    assert response.json() == {
        "status": "ready",
        "checks": {
            "database": "not_required",
            "llm": "configured",
            "fallback_llm": "configured",
            "internal_service_auth": "configured",
        },
    }


def test_readiness_endpoint_returns_503_when_not_ready(monkeypatch):
    from app.api import health

    monkeypatch.setattr(
        health.readiness_service,
        "_settings",
        _settings(dashscope_api_key=None),
    )

    response = TestClient(app).get("/ai/health/ready")

    assert response.status_code == 503
    assert response.json()["status"] == "not_ready"
    assert response.json()["checks"]["llm"] == "missing_configuration"
