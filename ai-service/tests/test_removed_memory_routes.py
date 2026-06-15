from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_removed_memory_routes_return_404():
    removed_paths = [
        "/ai/memory/family-card",
        "/ai/memory/heritage-save-judge",
    ]

    for path in removed_paths:
        response = client.post(path, json={})
        assert response.status_code == 404, path
