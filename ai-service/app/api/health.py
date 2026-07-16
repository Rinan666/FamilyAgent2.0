"""
Health check routes.
"""
import time

from fastapi import APIRouter
from fastapi.responses import JSONResponse

from app.api.health_models import ReadinessResponse, ReadinessStatus
from app.config import settings
from app.services.readiness import ReadinessService

router = APIRouter()
readiness_service = ReadinessService(settings)

_start_time = time.time()


@router.get("/health")
async def health_check():
    """Basic liveness check."""
    uptime = time.time() - _start_time
    return {
        "status": "healthy",
        "service": "familyagent-ai",
        "version": "0.1.0",
        "environment": settings.app_env,
        "uptime_seconds": round(uptime, 1),
        "default_model": settings.default_llm_model,
    }


@router.get(
    "/health/ready",
    response_model=ReadinessResponse,
    responses={503: {"model": ReadinessResponse}},
)
async def readiness_check() -> ReadinessResponse | JSONResponse:
    """Report whether required AI runtime configuration is available."""
    result = readiness_service.evaluate()
    if result.status == ReadinessStatus.NOT_READY:
        return JSONResponse(status_code=503, content=result.model_dump(mode="json"))
    return result
