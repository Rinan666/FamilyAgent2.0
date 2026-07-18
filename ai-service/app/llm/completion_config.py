"""Resolve configured models into privacy-safe completion transports."""

from functools import lru_cache
from typing import Any

import litellm
from openai import AsyncOpenAI

from app.config import settings


def completion_provider_kwargs(model: str) -> dict[str, str]:
    """Route DashScope models through its OpenAI-compatible endpoint."""
    provider, separator, provider_model = model.partition("/")
    if separator and provider.strip().lower() == "dashscope":
        kwargs = {
            "model": f"openai/{provider_model.strip()}",
            "api_base": settings.dashscope_base_url,
        }
        if settings.dashscope_api_key:
            kwargs["api_key"] = settings.dashscope_api_key
        return kwargs
    return {"model": model}


async def provider_completion(*, model: str, **kwargs: Any) -> Any:
    """Use the direct OpenAI-compatible client for DashScope models."""
    route = completion_provider_kwargs(model)
    routed_model = route["model"]
    if routed_model.startswith("openai/") and model.lower().startswith("dashscope/"):
        client = _openai_compatible_client(
            route.get("api_key", ""),
            route["api_base"],
        )
        return await client.chat.completions.create(
            model=routed_model.removeprefix("openai/"),
            **kwargs,
        )
    return await litellm.acompletion(model=routed_model, **kwargs)


@lru_cache(maxsize=4)
def _openai_compatible_client(api_key: str, api_base: str) -> AsyncOpenAI:
    return AsyncOpenAI(api_key=api_key, base_url=api_base)
