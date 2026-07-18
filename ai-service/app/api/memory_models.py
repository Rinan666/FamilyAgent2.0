"""Typed contracts for the family memory API."""
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


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


class OrganizedDraftData(BaseModel):
    """Sanitized organize-draft payload returned to backend callers."""

    model_config = ConfigDict(frozen=True)

    title: str
    content: str
    tags: list[str]
    diary_entry_type: Literal[
        "DAILY",
        "IMPORTANT_EVENT",
        "LESSON",
        "EMOTION",
        "MESSAGE_TO_FAMILY",
        "SELF_REFLECTION",
    ]
    diary_visibility: Literal[
        "PRIVATE",
        "FAMILY_VISIBLE",
        "CARE_VISIBLE",
        "LEGACY_VISIBLE",
    ]
    memory_type: Literal[
        "FAMILY_STORY",
        "ELDER_ADVICE",
        "HEALTH_REMINDER",
        "GROWTH_RISK",
        "VALUE",
        "PLAN",
    ]
    memory_scope: Literal[
        "PRIVATE",
        "CARE_VISIBLE",
        "FAMILY_VISIBLE",
        "PARENT_VISIBLE",
    ]
    growth_category: Literal[
        "POSTURE",
        "DENTAL",
        "VISION",
        "SLEEP",
        "EXERCISE",
        "SCREEN_TIME",
        "EMOTION",
        "COMMUNICATION",
        "OTHER",
    ]
    growth_severity: int = Field(ge=1, le=5)
    scenario: str
    reason: str


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
