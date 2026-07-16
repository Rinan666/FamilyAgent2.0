"""Stable error codes for executable AI skills."""

from enum import StrEnum


class SkillErrorCode(StrEnum):
    PROVIDER_ERROR = "AI_PROVIDER_ERROR"
    INVALID_RESPONSE = "AI_INVALID_RESPONSE"
    TIMEOUT = "AI_TIMEOUT"
