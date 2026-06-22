from fastapi import FastAPI
from fastapi.testclient import TestClient

from app import main


def test_load_dip_router_falls_back_when_cv2_is_missing(monkeypatch):
    def fake_import_module(name: str):
        exc = ModuleNotFoundError("No module named 'cv2'")
        exc.name = "cv2"
        raise exc

    monkeypatch.setattr(main, "import_module", fake_import_module)

    router, reason = main._load_dip_router()

    assert reason is not None
    assert "cv2" in reason

    app = FastAPI()
    app.include_router(router, prefix="/ai/dip")
    client = TestClient(app)

    response = client.post("/ai/dip/faces/cluster")

    assert response.status_code == 503
    assert response.json()["success"] is False


def test_load_dip_router_falls_back_when_cv2_runtime_library_is_missing(monkeypatch):
    def fake_import_module(name: str):
        raise ImportError("libxcb.so.1: cannot open shared object file")

    monkeypatch.setattr(main, "import_module", fake_import_module)

    router, reason = main._load_dip_router()

    assert reason is not None
    assert "libxcb.so.1" in reason

    app = FastAPI()
    app.include_router(router, prefix="/ai/dip")
    client = TestClient(app)

    response = client.post("/ai/dip/faces/cluster")

    assert response.status_code == 503
    assert response.json()["success"] is False
