"""Shared usage accounting for privacy-safe provider evaluations."""

from __future__ import annotations

import math

import litellm


def provider_name(model: str) -> str:
    return model.split("/", 1)[0].strip() if "/" in model else "unknown"


def token_usage(response: object) -> tuple[int | None, int | None]:
    usage = getattr(response, "usage", None)
    return usage_value(usage, "prompt_tokens"), usage_value(usage, "completion_tokens")


def usage_value(usage: object, field: str) -> int | None:
    value = getattr(usage, field, None)
    if value is None and isinstance(usage, dict):
        value = usage.get(field)
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        return None
    return parsed if parsed >= 0 else None


def completion_cost(response: object, model: str) -> float | None:
    if model.lower().startswith("dashscope/"):
        return None
    try:
        cost = float(litellm.completion_cost(completion_response=response))
    except Exception:
        return None
    return round(cost, 8) if math.isfinite(cost) and cost >= 0 else None


def sum_optional_int(values: list[int | None]) -> int | None:
    if any(value is None for value in values):
        return None
    return sum(value for value in values if value is not None)


def sum_optional_cost(values: list[float | None]) -> float | None:
    if any(value is None for value in values):
        return None
    return round(sum(value for value in values if value is not None), 8)
