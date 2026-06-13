"""
FamilyAgent AI service entrypoint.
"""
from contextlib import asynccontextmanager
from importlib import import_module
from typing import Optional

from fastapi import APIRouter, FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.api import agent, embedding, growth, health, memory
from app.config import settings
from app.utils.logger import setup_logging
from app.utils.safety_limits import (
    PromptLeakAttemptError,
    RateLimitExceededError,
    RoleHijackAttemptError,
    SafetyLimitError,
)
from app.utils.input_guard import InputGuardError
from app.utils.security_events import record_security_event


def _build_unavailable_dip_router(detail: str) -> APIRouter:
    router = APIRouter()

    @router.post("/faces/cluster")
    async def cluster_faces_unavailable() -> JSONResponse:
        return JSONResponse(
            status_code=503,
            content={"success": False, "detail": detail},
        )

    @router.post("/faces/cluster-by-urls")
    async def cluster_faces_by_urls_unavailable() -> JSONResponse:
        return JSONResponse(
            status_code=503,
            content={"success": False, "detail": detail},
        )

    return router


def _load_dip_router() -> tuple[APIRouter, Optional[str]]:
    try:
        return import_module("dip.router").router, None
    except ModuleNotFoundError as exc:
        missing_module = exc.name or "unknown"
        if missing_module.startswith("dip"):
            raise

        detail = (
            "DIP image processing is unavailable because optional dependency "
            f"'{missing_module}' is not installed. Install ai-service "
            "requirements to enable these endpoints."
        )
        return _build_unavailable_dip_router(detail), detail


dip_router, dip_unavailable_reason = _load_dip_router()


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifecycle."""
    logger = setup_logging(settings.log_level)
    app.state.logger = logger
    logger.info(f"AI Service starting (env={settings.app_env})")
    if dip_unavailable_reason:
        logger.warning(dip_unavailable_reason)
    yield
    logger.info("AI Service shutting down")


app = FastAPI(
    title="FamilyAgent AI Service",
    version="0.1.0",
    description="FamilyAgent AI service layer",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.middleware("http")
async def response_header_middleware(request: Request, call_next):
    response = await call_next(request)
    response.headers["X-Content-Type-Options"] = "nosniff"

    content_type = response.headers.get("content-type", "")
    if content_type.startswith("application/json") and "charset=" not in content_type.lower():
        response.headers["Content-Type"] = "application/json; charset=utf-8"

    if response.headers.get("cache-control") is None:
        response.headers["Cache-Control"] = "no-store, max-age=0, must-revalidate"

    return response


@app.exception_handler(PromptLeakAttemptError)
async def prompt_leak_handler(request: Request, exc: PromptLeakAttemptError):
    await record_security_event(
        request,
        event_type="PROMPT_LEAK",
        status_code=400,
        reason=str(exc),
    )
    return JSONResponse(status_code=400, content={"success": False, "detail": str(exc)})


@app.exception_handler(RoleHijackAttemptError)
async def role_hijack_handler(request: Request, exc: RoleHijackAttemptError):
    await record_security_event(
        request,
        event_type="ROLE_HIJACK",
        status_code=400,
        reason=str(exc),
    )
    return JSONResponse(status_code=400, content={"success": False, "detail": str(exc)})


@app.exception_handler(SafetyLimitError)
async def safety_limit_handler(request: Request, exc: SafetyLimitError):
    await record_security_event(
        request,
        event_type="INPUT_TOO_LARGE",
        status_code=413,
        reason=str(exc),
    )
    return JSONResponse(status_code=413, content={"success": False, "detail": str(exc)})


@app.exception_handler(InputGuardError)
async def input_guard_handler(request: Request, exc: InputGuardError):
    await record_security_event(
        request,
        event_type=f"INPUT_GUARD_{exc.reason.value}",
        status_code=400,
        reason=str(exc),
    )
    return JSONResponse(status_code=400, content={"success": False, "detail": str(exc)})


@app.exception_handler(RateLimitExceededError)
async def rate_limit_handler(request: Request, exc: RateLimitExceededError):
    await record_security_event(
        request,
        event_type="RATE_LIMIT",
        status_code=429,
        reason=str(exc),
    )
    return JSONResponse(
        status_code=429,
        content={"success": False, "detail": str(exc)},
        headers={"Retry-After": str(exc.retry_after_seconds)},
    )


@app.exception_handler(TimeoutError)
async def timeout_handler(request: Request, exc: TimeoutError):
    await record_security_event(
        request,
        event_type="TIMEOUT",
        status_code=504,
        reason=str(exc),
    )
    return JSONResponse(status_code=504, content={"success": False, "detail": str(exc)})


app.include_router(agent.router, prefix="/ai/agent", tags=["FamilyAgent"])
app.include_router(memory.router, prefix="/ai/memory", tags=["Memory"])
app.include_router(growth.router, prefix="/ai/growth", tags=["Growth"])
app.include_router(embedding.router, prefix="/ai/embedding", tags=["Embedding"])
app.include_router(health.router, prefix="/ai", tags=["Health"])
app.include_router(dip_router, prefix="/ai/dip", tags=["DIP"])


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=settings.ai_service_port,
        reload=settings.app_debug,
    )
