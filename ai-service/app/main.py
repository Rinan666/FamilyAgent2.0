"""
FamilyAgent AI service entrypoint.
"""
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api import assessment, embedding, growth, health, memory, tutor
from app.config import settings
from app.utils.logger import setup_logging


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifecycle."""
    logger = setup_logging(settings.log_level)
    app.state.logger = logger
    logger.info(f"AI Service starting (env={settings.app_env})")
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
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(tutor.router, prefix="/ai/tutor", tags=["Tutor"])
app.include_router(assessment.router, prefix="/ai/assessment", tags=["Assessment"])
app.include_router(memory.router, prefix="/ai/memory", tags=["Memory"])
app.include_router(growth.router, prefix="/ai/growth", tags=["Growth"])
app.include_router(embedding.router, prefix="/ai/embedding", tags=["Embedding"])
app.include_router(health.router, prefix="/ai", tags=["Health"])


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=settings.ai_service_port,
        reload=settings.app_debug,
    )
