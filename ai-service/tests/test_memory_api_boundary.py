from fastapi.routing import APIRoute

from app.api import memory


def _route_dependencies(path: str) -> list[object]:
    route = next(
        item
        for item in memory.router.routes
        if isinstance(item, APIRoute) and item.path == path
    )
    return [dependency.dependency for dependency in route.dependencies]


def test_memory_router_accepts_backend_service_identity():
    dependencies = [item.dependency for item in memory.router.dependencies]

    assert memory.verify_token_or_internal_service in dependencies


def test_backend_owned_memory_skills_require_internal_identity():
    for path in ("/save-plan", "/organize-draft", "/persona-material-draft"):
        assert memory.require_internal_service in _route_dependencies(path)


def test_skill_manifest_reads_remain_available_without_internal_only_gate():
    assert memory.require_internal_service not in _route_dependencies("/skills")
    assert memory.require_internal_service not in _route_dependencies("/skills/{name}")
