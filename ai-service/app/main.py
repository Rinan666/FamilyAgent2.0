"""
家族教育Agent - AI服务入口

FastAPI 应用，提供：
- 讲题Agent (/ai/tutor/explain)
- 批改Agent (/ai/tutor/grade)
- 出题Agent (/ai/tutor/generate)
- 学力评估 (/ai/assessment/profile)
- 健康检查 (/ai/health)
"""
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api import tutor, assessment, health
from app.config import settings
from app.utils.logger import setup_logging


@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期"""
    logger = setup_logging(settings.log_level)
    app.state.logger = logger
    logger.info(f"AI Service starting (env={settings.app_env})")
    yield
    logger.info("AI Service shutting down")


app = FastAPI(
    title="FamilyAgent AI Service",
    version="0.1.0",
    description="家族教育Agent - AI服务层",
    lifespan=lifespan,
)

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 注册路由（tutor/assessment 路由内通过 Depends(verify_token) 鉴权）
app.include_router(tutor.router, prefix="/ai/tutor", tags=["家教"])
app.include_router(assessment.router, prefix="/ai/assessment", tags=["评估"])
app.include_router(health.router, prefix="/ai", tags=["健康"])


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=settings.ai_service_port,
        reload=settings.app_debug,
    )
