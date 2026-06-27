"""Request models for the family memory API."""
from typing import Literal, Optional

from pydantic import BaseModel, Field


class ExtractMemoryMessage(BaseModel):
    role: Literal["user", "assistant"] = "user"
    content: str = Field(default="", max_length=4000)


class ExtractMemoryRequest(BaseModel):
    session_id: int
    subject: str = ""
    messages: list[ExtractMemoryMessage] = Field(default_factory=list, max_length=100)
    summary: str = ""


class ExtractedMemory(BaseModel):
    type: str = ""
    content: str = ""
    summary: str = ""
    importance: int = Field(default=1, ge=1, le=5)
    confidence: float = Field(default=0.0, ge=0.0, le=1.0)


class ExtractMemoryResponse(BaseModel):
    success: bool
    deprecated: bool = True
    degraded: bool = False
    memories: list[ExtractedMemory] = Field(default_factory=list)
    message: str = ""
    errorCode: Optional[str] = None


class SaveToolPlanRequest(BaseModel):
    message: str = Field(..., min_length=2)
    family_context: str = ""
    conversation_context: list[dict] = Field(default_factory=list)
    target_member_name: str = ""
    viewer_role: str = ""


class OrganizeDraftRequest(BaseModel):
    content: str = Field(..., min_length=4)
    scene: str = "DIARY"
    family_context: str = ""
    current_type: str = ""
    current_visibility: str = ""
    target: str = ""


class PersonaProfileInput(BaseModel):
    name: str = ""
    description: str = ""
    era_identity: str = ""
    values: str = ""
    speaking_style: str = ""
    personality: str = ""


class PersonaMaterialDraftRequest(BaseModel):
    content: str = Field(..., min_length=8)
    profile: PersonaProfileInput = Field(default_factory=PersonaProfileInput)
    family_context: str = ""
