"""Typed health endpoint contracts."""

from enum import StrEnum

from pydantic import BaseModel


class ReadinessStatus(StrEnum):
    READY = "ready"
    NOT_READY = "not_ready"


class ReadinessCheckState(StrEnum):
    CONFIGURED = "configured"
    MISSING_CONFIGURATION = "missing_configuration"
    NOT_REQUIRED = "not_required"
    SAME_AS_PRIMARY = "same_as_primary"
    UNSUPPORTED_PROVIDER = "unsupported_provider"


class ReadinessChecks(BaseModel):
    database: ReadinessCheckState
    llm: ReadinessCheckState
    fallback_llm: ReadinessCheckState
    internal_service_auth: ReadinessCheckState


class ReadinessResponse(BaseModel):
    status: ReadinessStatus
    checks: ReadinessChecks


class LivenessResponse(BaseModel):
    status: str
    service: str
    version: str
    environment: str
    uptime_seconds: float
    default_model: str
