"""Typed contracts for the family memory API."""
from typing import Literal, TypeAlias

from pydantic import BaseModel, ConfigDict, Field

DiaryEntryType: TypeAlias = Literal[
    "DAILY",
    "IMPORTANT_EVENT",
    "LESSON",
    "EMOTION",
    "MESSAGE_TO_FAMILY",
    "SELF_REFLECTION",
]
Visibility: TypeAlias = Literal[
    "PRIVATE",
    "FAMILY_VISIBLE",
    "CARE_VISIBLE",
    "LEGACY_VISIBLE",
]
SaveVisibility: TypeAlias = Literal[
    "PRIVATE",
    "FAMILY_VISIBLE",
    "CARE_VISIBLE",
    "LEGACY_VISIBLE",
    "ALL_FAMILIES_VISIBLE",
    "SELECTED_FAMILIES_VISIBLE",
]
MemoryType: TypeAlias = Literal[
    "FAMILY_STORY",
    "ELDER_ADVICE",
    "HEALTH_REMINDER",
    "GROWTH_RISK",
    "VALUE",
    "PLAN",
]
MemoryScope: TypeAlias = Literal[
    "PRIVATE",
    "CARE_VISIBLE",
    "FAMILY_VISIBLE",
    "PARENT_VISIBLE",
]
SaveMemoryScope: TypeAlias = Literal[
    "PRIVATE",
    "CARE_VISIBLE",
    "FAMILY_VISIBLE",
    "PARENT_VISIBLE",
    "ALL_FAMILIES_VISIBLE",
    "SELECTED_FAMILIES_VISIBLE",
]
PersonalMemoryType: TypeAlias = Literal[
    "NOTE",
    "KNOWLEDGE",
    "INSIGHT",
    "EXPERIENCE",
    "PREFERENCE",
    "PLAN",
]
GrowthCategory: TypeAlias = Literal[
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
    diary_entry_type: DiaryEntryType
    diary_visibility: Visibility
    memory_type: MemoryType
    memory_scope: MemoryScope
    growth_category: GrowthCategory
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


class SaveToolPlanData(BaseModel):
    """Sanitized save-plan payload, including safe failure fallback data."""

    model_config = ConfigDict(frozen=True)

    should_save: bool
    tool: Literal["NONE", "DIARY", "PERSONAL_MEMORY", "FAMILY_MEMORY", "GROWTH_GUARD"]
    content: str
    title: str
    summary: str
    visibility: SaveVisibility
    entry_type: DiaryEntryType
    memory_type: MemoryType
    personal_memory_type: PersonalMemoryType = "NOTE"
    scope: SaveMemoryScope
    category: GrowthCategory
    severity: int = Field(ge=1, le=5)
    importance: int = Field(ge=1, le=5)
    tags: list[str]
    reason: str
    confirmation_message: str
