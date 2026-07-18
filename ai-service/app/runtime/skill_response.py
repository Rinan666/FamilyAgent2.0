"""Stable structured responses for executable AI skills."""

from typing import Generic, Literal, TypeVar

from pydantic import BaseModel, ConfigDict

from .skill_error import SkillErrorCode

DataT = TypeVar("DataT", bound=BaseModel)


class SkillSuccessResponse(BaseModel, Generic[DataT]):
    model_config = ConfigDict(frozen=True)

    success: Literal[True] = True
    data: DataT


class SkillFailureResponse(BaseModel):
    model_config = ConfigDict(frozen=True)

    success: Literal[False] = False
    data: None = None
    errorCode: SkillErrorCode
    error: str


def skill_success(data: DataT) -> dict[str, object]:
    return SkillSuccessResponse(data=data).model_dump(mode="json")


def skill_failure(error_code: SkillErrorCode, message: str) -> dict[str, object]:
    return SkillFailureResponse(errorCode=error_code, error=message).model_dump(mode="json")
