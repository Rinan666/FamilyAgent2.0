"""
Health check routes.
"""
import time

from fastapi import APIRouter

from app.config import settings

router = APIRouter()

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


@router.get("/health/ready")
async def readiness_check():
    """Readiness check placeholder."""
    return {
        "status": "ready",
        "checks": {
            "database": "not_checked",
            "llm": "not_checked",
        },
    }
