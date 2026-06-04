"""
健康检查路由
"""
import time
from fastapi import APIRouter

from app.config import settings

router = APIRouter()

_start_time = time.time()


@router.get("/health")
async def health_check():
    """健康检查"""
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
    """就绪检查（含数据库连接检查）"""
    # TODO: 添加数据库连接检查
    return {
        "status": "ready",
        "checks": {
            "database": "not_checked",  # Phase 2: 添加实际检查
            "llm": "not_checked",
        },
    }
