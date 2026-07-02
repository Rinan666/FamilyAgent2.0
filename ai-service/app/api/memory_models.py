"""Request models for the family memory API."""
from pydantic import BaseModel, Field


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
