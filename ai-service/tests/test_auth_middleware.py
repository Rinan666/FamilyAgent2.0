from types import SimpleNamespace

import httpx
import pytest
from fastapi import HTTPException

from app.middleware import auth


class FakeResponse:
    def __init__(self, status_code: int, data: dict | None = None):
        self.status_code = status_code
        self._data = data or {}

    def json(self):
        return self._data


class FakeAsyncClient:
    response: FakeResponse | None = None
    error: Exception | None = None

    def __init__(self, *args, **kwargs):
        pass

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc, tb):
        return None

    async def get(self, *args, **kwargs):
        if self.error:
            raise self.error
        return self.response


@pytest.mark.asyncio
async def test_verify_token_rejects_missing_authorization():
    request = SimpleNamespace(state=SimpleNamespace())

    with pytest.raises(HTTPException) as exc:
        await auth.verify_token(request, authorization=None)

    assert exc.value.status_code == 401


@pytest.mark.asyncio
async def test_backend_401_is_always_rejected(monkeypatch):
    FakeAsyncClient.response = FakeResponse(401)
    FakeAsyncClient.error = None
    monkeypatch.setattr(auth.httpx, "AsyncClient", FakeAsyncClient)
    monkeypatch.setattr(auth.settings, "auth_fail_open", True)

    user = await auth._call_backend_verify("Bearer invalid")

    assert user is None


@pytest.mark.asyncio
async def test_backend_500_fail_open_returns_dev_user(monkeypatch):
    FakeAsyncClient.response = FakeResponse(500)
    FakeAsyncClient.error = None
    monkeypatch.setattr(auth.httpx, "AsyncClient", FakeAsyncClient)
    monkeypatch.setattr(auth.settings, "auth_fail_open", True)

    user = await auth._call_backend_verify("Bearer token")

    assert user["id"] == -1
    assert user["username"] == "backend_error"


@pytest.mark.asyncio
async def test_backend_500_fail_closed_returns_503(monkeypatch):
    FakeAsyncClient.response = FakeResponse(500)
    FakeAsyncClient.error = None
    monkeypatch.setattr(auth.httpx, "AsyncClient", FakeAsyncClient)
    monkeypatch.setattr(auth.settings, "auth_fail_open", False)

    with pytest.raises(HTTPException) as exc:
        await auth._call_backend_verify("Bearer token")

    assert exc.value.status_code == 503


@pytest.mark.asyncio
async def test_backend_timeout_respects_fail_closed(monkeypatch):
    FakeAsyncClient.response = None
    FakeAsyncClient.error = httpx.TimeoutException("timeout")
    monkeypatch.setattr(auth.httpx, "AsyncClient", FakeAsyncClient)
    monkeypatch.setattr(auth.settings, "auth_fail_open", False)

    with pytest.raises(HTTPException) as exc:
        await auth._call_backend_verify("Bearer token")

    assert exc.value.status_code == 503
