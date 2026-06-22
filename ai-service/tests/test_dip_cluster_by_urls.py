import importlib
import sys
from types import ModuleType

import httpx
import numpy as np
from fastapi import FastAPI
from fastapi.testclient import TestClient


def _load_router_with_stubbed_dip(monkeypatch):
    class FakeFaceAnalysis:
        def __init__(self, *args, **kwargs):
            pass

        def prepare(self, *args, **kwargs):
            return None

        def get(self, img):
            return []

    fake_cv2 = ModuleType("cv2")
    fake_cv2.IMREAD_COLOR = 1
    fake_cv2.INTER_CUBIC = 2
    fake_cv2.imdecode = lambda arr, flags: np.ones((160, 160, 3), dtype=np.uint8)
    fake_cv2.resize = lambda img, size, interpolation=None: img

    fake_insightface = ModuleType("insightface")
    fake_insightface_app = ModuleType("insightface.app")
    fake_insightface_app.FaceAnalysis = FakeFaceAnalysis

    monkeypatch.setitem(sys.modules, "cv2", fake_cv2)
    monkeypatch.setitem(sys.modules, "insightface", fake_insightface)
    monkeypatch.setitem(sys.modules, "insightface.app", fake_insightface_app)
    sys.modules.pop("dip.router", None)

    return importlib.import_module("dip.router")


def test_cluster_by_urls_skips_http_status_failures(monkeypatch):
    router_module = _load_router_with_stubbed_dip(monkeypatch)

    class FakeAsyncClient:
        def __init__(self, *args, **kwargs):
            assert kwargs["trust_env"] is False
            pass

        async def __aenter__(self):
            return self

        async def __aexit__(self, exc_type, exc, tb):
            return None

        async def get(self, url, headers=None):
            if url.endswith("/missing"):
                return httpx.Response(404, request=httpx.Request("GET", url))
            return httpx.Response(200, content=b"image", request=httpx.Request("GET", url))

    def fake_cluster(embeddings, eps):
        return np.array([0, 0]), 0.75

    monkeypatch.setattr(router_module.httpx, "AsyncClient", FakeAsyncClient)
    monkeypatch.setattr(router_module, "cluster", fake_cluster)

    app = FastAPI()
    app.dependency_overrides[router_module.verify_token] = lambda: None
    app.dependency_overrides[router_module.enforce_ai_rate_limit] = lambda: None
    app.dependency_overrides[router_module.enforce_ai_concurrency] = lambda: None
    app.include_router(router_module.router)
    client = TestClient(app)

    response = client.post(
        "/faces/cluster-by-urls",
        json={
            "urls": ["http://photos/ok-1", "http://photos/missing", "http://photos/ok-2"],
            "photo_ids": [170, 171, 172],
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["success"] is True
    assert body["total_faces"] == 2
    assert body["failed_photos"] == [{
        "photo_id": 171,
        "file_index": 1,
        "reason": "HTTP_STATUS",
        "status_code": 404,
    }]
    assert body["groups"][0]["faces"][0]["photo_id"] == 170
    assert body["groups"][0]["faces"][1]["photo_id"] == 172


def test_cluster_by_urls_fails_when_no_photos_can_be_read(monkeypatch):
    router_module = _load_router_with_stubbed_dip(monkeypatch)

    class FakeAsyncClient:
        def __init__(self, *args, **kwargs):
            assert kwargs["trust_env"] is False
            pass

        async def __aenter__(self):
            return self

        async def __aexit__(self, exc_type, exc, tb):
            return None

        async def get(self, url, headers=None):
            return httpx.Response(502, request=httpx.Request("GET", url))

    monkeypatch.setattr(router_module.httpx, "AsyncClient", FakeAsyncClient)

    app = FastAPI()
    app.dependency_overrides[router_module.verify_token] = lambda: None
    app.dependency_overrides[router_module.enforce_ai_rate_limit] = lambda: None
    app.dependency_overrides[router_module.enforce_ai_concurrency] = lambda: None
    app.include_router(router_module.router)
    client = TestClient(app)

    response = client.post(
        "/faces/cluster-by-urls",
        json={
            "urls": ["http://photos/one", "http://photos/two"],
            "photo_ids": [170, 171],
        },
    )

    assert response.status_code == 502
    assert response.json()["detail"] == "All photos failed to fetch for clustering."
