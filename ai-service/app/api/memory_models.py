"""Typed contracts for the family memory API."""
from typing import Literal, TypeAlias

from pydantic import BaseModel, ConfigDict, Field

SaveVisibility: TypeAlias = Literal[
    "PRIVATE",
    "FAMILY_VISIBLE",
    "CARE_VISIBLE",
    "ALL_FAMILIES_VISIBLE",
    "SELECTED_FAMILIES_VISIBLE",
]
MemoryType: TypeAlias = Literal[
    "NOTE",
    "KNOWLEDGE",
    "INSIGHT",
    "EXPERIENCE",
    "OBSERVATION",
    "PREFERENCE",
    "PLAN",
]
class MemorySavePlanRequest(BaseModel):
    message: str = Field(..., min_length=2)
    family_context: str = ""
    conversation_context: list[dict] = Field(default_factory=list)
    target_member_name: str = ""
    viewer_role: str = ""


class OrganizeDraftRequest(BaseModel):
    content: str = Field(..., min_length=4)
    memory_library: Literal["PERSONAL", "FAMILY"] = "FAMILY"
    family_context: str = ""
    current_memory_type: str = ""
    current_visibility: str = ""
    target: str = ""


class OrganizedDraftData(BaseModel):
    """Sanitized organize-draft payload returned to backend callers."""

    model_config = ConfigDict(frozen=True)

    title: str
    content: str
    tags: list[str]
    memory_type: MemoryType
    visibility: SaveVisibility
    reason: str


class PersonaProfileInput(BaseModel):
    name: str = ""
    description: str = ""
    era_identity: str = ""
    values: str = ""
    speaking_style: str = ""
    personality: str = ""


class PersonaProfileData(BaseModel):
    model_config = ConfigDict(frozen=True)

    name: str
    description: str
    era_identity: str
    values: str
    speaking_style: str
    personality: str


class PersonaMaterialData(BaseModel):
    model_config = ConfigDict(frozen=True)

    title: str
    content: str
    tags: list[str]


class PersonaMaterialDraftData(BaseModel):
    """Sanitized persona material payload returned to backend callers."""

    model_config = ConfigDict(frozen=True)

    profile: PersonaProfileData
    materials: list[PersonaMaterialData]
    reason: str


class PersonaMaterialDraftRequest(BaseModel):
    content: str = Field(..., min_length=8)
    profile: PersonaProfileInput = Field(default_factory=PersonaProfileInput)
    family_context: str = ""


class MemorySavePlanData(BaseModel):
    """Sanitized save-plan payload, including safe failure fallback data."""

    model_config = ConfigDict(frozen=True)

    should_save: bool
    memory_library: Literal["PERSONAL", "FAMILY"]
    content: str
    title: str
    summary: str
    visibility: SaveVisibility
    memory_type: MemoryType
    importance: int = Field(ge=1, le=5)
    tags: list[str]
    reason: str
    confirmation_message: str
