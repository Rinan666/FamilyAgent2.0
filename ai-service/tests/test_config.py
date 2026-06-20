from app.config import Settings


def test_development_cors_defaults_do_not_allow_wildcard():
    settings = Settings(app_env="development", cors_allow_origins=None)

    assert "*" not in settings.cors_origins
    assert settings.cors_origins == ["http://localhost:3000", "http://127.0.0.1:3000"]


def test_dev_alias_uses_development_defaults():
    settings = Settings(app_env="dev", auth_fail_open=None, cors_allow_origins=None)

    assert settings.is_development_env is True
    assert settings.auth_fail_open_enabled is True
    assert settings.internal_service_token == "familyagent-dev-internal-token"
    assert settings.cors_origins == ["http://localhost:3000", "http://127.0.0.1:3000"]


def test_cors_allow_origins_parses_comma_separated_values():
    settings = Settings(
        app_env="production",
        cors_allow_origins="https://app.familyagent.cn, https://www.familyagent.cn",
    )

    assert settings.cors_origins == ["https://app.familyagent.cn", "https://www.familyagent.cn"]


def test_auth_fail_open_defaults_to_development_only():
    assert Settings(app_env="development", auth_fail_open=None).auth_fail_open_enabled is True
    assert Settings(app_env="production", auth_fail_open=None).auth_fail_open_enabled is False


def test_auth_fail_open_cannot_be_enabled_outside_development():
    assert Settings(app_env="production", auth_fail_open=True).auth_fail_open_enabled is False
    assert Settings(app_env="prod", auth_fail_open=True).auth_fail_open_enabled is False


def test_production_env_vars_override_local_dotenv(monkeypatch):
    monkeypatch.setenv("APP_ENV", "production")
    monkeypatch.setenv("APP_DEBUG", "false")

    settings = Settings()

    assert settings.app_env == "production"
    assert settings.app_debug is False
