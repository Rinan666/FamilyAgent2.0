"""Stable structured failure responses for executable AI skills."""

from typing import Literal

from pydantic import BaseModel, ConfigDict

from .skill_error import SkillErrorCode


class SkillFailureResponse(BaseModel):
    model_config = ConfigDict(frozen=True)

    success: Literal[False] = False
    data: None = None
    errorCode: SkillErrorCode
    error: str


def skill_failure(error_code: SkillErrorCode, message: str) -> dict:
    return SkillFailureResponse(errorCode=error_code, error=message).model_dump(mode="json")
