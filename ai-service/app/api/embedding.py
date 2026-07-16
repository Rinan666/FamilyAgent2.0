"""
Embedding API for backend-owned family memory indexing.
"""
import asyncio
import hashlib
import logging
import math
import time
import uuid
from typing import Optional

import httpx
import litellm
from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel, Field

from app.config import settings
from app.middleware.auth import verify_token_or_internal_service
from app.utils.privacy_guard import redact_ai_bound_text
from app.utils.sanitizer import sanitize_text
from app.utils.safety_limits import enforce_ai_concurrency, enforce_embedding_rate_limit

logger = logging.getLogger("familyagent.ai.api.embedding")

router = APIRouter(dependencies=[
    Depends(verify_token_or_internal_service),
    Depends(enforce_embedding_rate_limit),
    Depends(enforce_ai_concurrency),
])


class EmbedRequest(BaseModel):
    text: str = Field(..., min_length=1)
    model: Optional[str] = None
    dimensions: Optional[int] = Field(default=None, ge=128, le=4096)
    source_type: Optional[str] = Field(default=None, max_length=64)
    family_id: Optional[int] = Field(default=None, ge=1)
    user_id: Optional[int] = Field(default=None, ge=1)


class EmbedResponse(BaseModel):
    success: bool
    degraded: bool = False
    provider: str
    model: str
    dimensions: int
    embedding: list[float]
    privacy_categories: list[str] = Field(default_factory=list)
    latency_ms: int
    request_id: str
    errorCode: Optional[str] = None


@router.post("/embed", response_model=EmbedResponse)
async def embed_text(request: EmbedRequest, http_request: Request):
    started_at = time.monotonic()
    request_id = http_request.headers.get("X-Request-Id") or str(uuid.uuid4())
    text = sanitize_text(request.text, max_length=6000)
    guarded = redact_ai_bound_text(text, max_length=6000)
    model = request.model or settings.embedding_model
    dimensions = request.dimensions or settings.embedding_dimension
    provider = _embedding_provider(model)

    try:
        vector = await _embed(text=guarded.text, model=model, dimensions=dimensions)
        latency_ms = _elapsed_ms(started_at)
        logger.info(
            "Embedding generated: request_id=%s provider=%s model=%s dimensions=%s latency_ms=%s degraded=%s privacy_categories=%s",
            request_id,
            provider,
            model,
            len(vector),
            latency_ms,
            False,
            guarded.categories,
        )
        return {
            "success": True,
            "degraded": False,
            "provider": provider,
            "model": model,
            "dimensions": len(vector),
            "embedding": vector,
            "privacy_categories": guarded.categories,
            "latency_ms": latency_ms,
            "request_id": request_id,
        }
    except HTTPException as e:
        logger.warning(
            "Embedding generation failed: request_id=%s provider=%s model=%s dimensions=%s latency_ms=%s errorCode=%s status_code=%s",
            request_id,
            provider,
            model,
            dimensions,
            _elapsed_ms(started_at),
            _embedding_error_code(e),
            e.status_code,
        )
        raise
    except Exception as error:
        logger.warning(
            "Embedding generation failed: request_id=%s provider=%s model=%s dimensions=%s latency_ms=%s errorCode=EMBEDDING_GENERATION_FAILED errorType=%s",
            request_id,
            provider,
            model,
            dimensions,
            _elapsed_ms(started_at),
            type(error).__name__,
        )
        raise HTTPException(status_code=500, detail="Embedding generation failed") from error


def _embedding_provider(model: str) -> str:
    if model.startswith("local/"):
        return "local"
    if model.startswith("dashscope-multimodal/") or model.startswith("dashscope/"):
        return "dashscope"
    if "/" in model:
        return model.split("/", 1)[0]
    return "litellm"


def _embedding_error_code(exc: HTTPException) -> str:
    if exc.status_code == 503:
        return "EMBEDDING_PROVIDER_UNAVAILABLE"
    return "EMBEDDING_GENERATION_FAILED"


def _elapsed_ms(started_at: float) -> int:
    return max(0, round((time.monotonic() - started_at) * 1000))


async def _embed(text: str, model: str, dimensions: int) -> list[float]:
    if model.startswith("local/"):
        return _hash_embedding(text, dimensions)
    if model.startswith("dashscope-multimodal/"):
        return await _dashscope_multimodal_embedding(
            text,
            model.removeprefix("dashscope-multimodal/"),
            dimensions,
        )
    if model.startswith("dashscope/"):
        return await _dashscope_embedding(text, model.removeprefix("dashscope/"), dimensions)

    try:
        response = await asyncio.wait_for(
            litellm.aembedding(model=model, input=[text]),
            timeout=settings.ai_embedding_timeout_seconds,
        )
        values = response["data"][0]["embedding"]
        return _fit_dimensions([float(item) for item in values], dimensions)
    except Exception as error:
        logger.warning("LiteLLM embedding failed: errorType=%s", type(error).__name__)
        raise HTTPException(status_code=503, detail="Embedding provider unavailable")


async def _dashscope_embedding(text: str, model: str, dimensions: int) -> list[float]:
    if not settings.dashscope_api_key:
        logger.warning("DASHSCOPE_API_KEY is missing")
        raise HTTPException(status_code=503, detail="Embedding provider is not configured")

    try:
        async with httpx.AsyncClient(timeout=settings.ai_embedding_timeout_seconds) as client:
            response = await client.post(
                f"{settings.dashscope_base_url.rstrip('/')}/embeddings",
                headers={
                    "Authorization": f"Bearer {settings.dashscope_api_key}",
                    "Content-Type": "application/json",
                },
                json={
                    "model": model,
                    "input": text,
                    "dimensions": dimensions,
                    "encoding_format": "float",
                },
            )
            response.raise_for_status()
            data = response.json()
            values = data["data"][0]["embedding"]
            return _fit_dimensions([float(item) for item in values], dimensions)
    except Exception as error:
        logger.warning("DashScope embedding failed: errorType=%s", type(error).__name__)
        raise HTTPException(status_code=503, detail="Embedding provider unavailable")


async def _dashscope_multimodal_embedding(text: str, model: str, dimensions: int) -> list[float]:
    if not settings.dashscope_api_key:
        logger.warning("DASHSCOPE_API_KEY is missing")
        raise HTTPException(status_code=503, detail="Embedding provider is not configured")

    request_dimension = _dashscope_multimodal_dimension(model, dimensions)
    try:
        async with httpx.AsyncClient(timeout=settings.ai_embedding_timeout_seconds) as client:
            response = await client.post(
                settings.dashscope_multimodal_url,
                headers={
                    "Authorization": f"Bearer {settings.dashscope_api_key}",
                    "Content-Type": "application/json",
                },
                json={
                    "model": model,
                    "input": {
                        "contents": [
                            {"text": text},
                        ],
                    },
                    "parameters": {
                        "dimension": request_dimension,
                    },
                },
            )
            response.raise_for_status()
            data = response.json()
            values = data["output"]["embeddings"][0]["embedding"]
            return _fit_dimensions([float(item) for item in values], dimensions)
    except Exception as error:
        logger.warning(
            "DashScope multimodal embedding failed: errorType=%s",
            type(error).__name__,
        )
        raise HTTPException(status_code=503, detail="Embedding provider unavailable")


def _dashscope_multimodal_dimension(model: str, dimensions: int) -> int:
    if model == "qwen3-vl-embedding":
        supported = [2560, 2048, 1536, 1024, 768, 512, 256]
        return min(supported, key=lambda item: (abs(item - dimensions), -item))
    return min(dimensions, 768)


def _hash_embedding(text: str, dimensions: int) -> list[float]:
    values = [0.0] * dimensions
    tokens = _tokens(text)
    if not tokens:
        tokens = [text or "empty"]

    for token in tokens:
        digest = hashlib.sha256(token.encode("utf-8")).digest()
        for i in range(0, len(digest), 4):
            bucket = int.from_bytes(digest[i:i + 2], "big") % dimensions
            sign = 1.0 if digest[i + 2] % 2 == 0 else -1.0
            weight = 1.0 + (digest[i + 3] / 255.0)
            values[bucket] += sign * weight

    norm = math.sqrt(sum(value * value for value in values)) or 1.0
    return [round(value / norm, 8) for value in values]


def _tokens(text: str) -> list[str]:
    compact = "".join(ch.lower() if ch.isalnum() else " " for ch in text)
    words = [word for word in compact.split() if word]
    char_chunks = [
        compact[i:i + 2]
        for i in range(0, len(compact) - 1)
        if not compact[i].isspace() and not compact[i + 1].isspace()
    ]
    return words + char_chunks


def _fit_dimensions(values: list[float], dimensions: int) -> list[float]:
    if len(values) == dimensions:
        return values
    if len(values) > dimensions:
        fitted = values[:dimensions]
    else:
        fitted = values + [0.0] * (dimensions - len(values))
    norm = math.sqrt(sum(value * value for value in fitted)) or 1.0
    return [round(value / norm, 8) for value in fitted]
