"""Request models for the family memory API."""
from pydantic import BaseModel, Field


class ExtractMemoryRequest(BaseModel):
    session_id: int
    subject: str = ""
    messages: list[dict] = Field(default_factory=list)
    summary: str = ""


class FamilyMemoryCardRequest(BaseModel):
    content: str = Field(..., min_length=8)
    memory_type: str = "ELDER_ADVICE"
    family_context: str = ""
    # Keep the legacy field name for backward compatibility; the product UI still uses a Chinese scenario label.
    target: str = ""


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


class HeritageSaveJudgeRequest(BaseModel):
    content: str = Field(..., min_length=4)
    memory_type: str = "ELDER_ADVICE"
    scenario: str = ""
    family_context: str = ""
    source_mode: str = ""


