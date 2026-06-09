from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_removed_legacy_tutor_routes_return_404():
    legacy_paths = [
        "/ai/tutor/grade",
        "/ai/tutor/grade/quick",
        "/ai/tutor/generate",
        "/ai/tutor/math/verify?expected=4&member_answer=4",
    ]

    for path in legacy_paths:
        response = client.post(path, json={})
        assert response.status_code == 404, path
