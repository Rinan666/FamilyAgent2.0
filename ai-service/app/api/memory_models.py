"""Request models for the family memory API."""
from typing import Optional

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


class CompressDiaryRequest(BaseModel):
    current_content: str = ""
    incoming_content: str = Field(..., min_length=1)
    max_chars: int = 600
    diary_date: str = ""


class FamilyWeeklyDigestRequest(BaseModel):
    family_name: str = ""
    diaries: list[dict] = Field(default_factory=list)
    memories: list[dict] = Field(default_factory=list)
    growth_records: list[dict] = Field(default_factory=list)
    target: str = ""


class HeritageTaskDraftRequest(BaseModel):
    content: str = Field(..., min_length=8)
    summary: str = ""
    memory_type: str = "ELDER_ADVICE"
    scenario: str = ""
    family_context: str = ""
    existing_actions: list[str] = Field(default_factory=list)


class HeritageSaveJudgeRequest(BaseModel):
    content: str = Field(..., min_length=4)
    memory_type: str = "ELDER_ADVICE"
    scenario: str = ""
    family_context: str = ""
    source_mode: str = ""


class HeritageClassicalRequest(BaseModel):
    content: str = Field(..., min_length=8)
    memory_type: str = "ELDER_ADVICE"
    scenario: str = ""
    family_context: str = ""


class SessionArchiveSummaryRequest(BaseModel):
    session_id: int
    session_title: str = ""
    family_id: Optional[int] = None
    subject: str = ""
    messages: list[dict] = Field(default_factory=list)
