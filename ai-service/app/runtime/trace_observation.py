"""Typed, privacy-safe observations shared by AI runtime producers."""

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class TraceObservation(BaseModel):
    model_config = ConfigDict(frozen=True, extra="ignore")

    stepType: Literal["LLM", "WEB_SEARCH"]
    operation: str = Field(min_length=1, max_length=120)
    provider: str | None = Field(default=None, max_length=80)
    model: str | None = Field(default=None, max_length=160)
    promptVersion: str | None = Field(default=None, max_length=80)
    skillVersion: str | None = Field(default=None, max_length=40)
    latencyMs: int | None = Field(default=None, ge=0)
    success: bool
    errorCode: str | None = Field(default=None, max_length=80)
    degraded: bool = False
    privacyCategories: list[Literal["FAMILY_DATA", "PUBLIC_DATA"]] = Field(
        default_factory=list,
    )
