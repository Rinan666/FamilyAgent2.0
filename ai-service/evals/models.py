"""Strongly typed contracts for deterministic AI evaluation runs."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from enum import StrEnum

from pydantic import BaseModel, ConfigDict


class EvalDecision(StrEnum):
    ALLOW = "ALLOW"
    BLOCK = "BLOCK"
    SANITIZED = "SANITIZED"
    SKIP_SAVE = "SKIP_SAVE"
    SAVE = "SAVE"
    STRUCTURED_FAILURE = "STRUCTURED_FAILURE"
    EVALUATOR_ERROR = "EVALUATOR_ERROR"


class ProtectedAsset(StrEnum):
    AGENT_IDENTITY = "AGENT_IDENTITY"
    CHILD_FAMILY_PRIVACY = "CHILD_FAMILY_PRIVACY"
    COST_DOS = "COST_DOS"
    MEMORY_QUALITY = "MEMORY_QUALITY"
    PROVIDER_FAILURE = "PROVIDER_FAILURE"


class EvalArtifactKind(StrEnum):
    PROMPT = "PROMPT"
    SCHEMA = "SCHEMA"
    SKILL = "SKILL"
    ALGORITHM = "ALGORITHM"


class EvalGateName(StrEnum):
    P0_SAFETY_PRIVACY = "P0_SAFETY_PRIVACY"
    CONTRACT = "CONTRACT"


class TextPolicy(StrEnum):
    INPUT_GUARD = "INPUT_GUARD"
    PROMPT_LEAK = "PROMPT_LEAK"
    ROLE_HIJACK = "ROLE_HIJACK"
    WEB_PRIVACY = "WEB_PRIVACY"
    WEB_TRIGGER = "WEB_TRIGGER"


class DraftSkillName(StrEnum):
    ORGANIZE_DRAFT = "ORGANIZE_DRAFT"
    PERSONA_MATERIAL_DRAFT = "PERSONA_MATERIAL_DRAFT"


@dataclass(frozen=True)
class TextEvalCase:
    case_id: str
    category: str
    protected_asset: ProtectedAsset
    policy: TextPolicy
    text: str
    expected_decision: EvalDecision


@dataclass(frozen=True)
class MessageContractCase:
    case_id: str
    category: str
    protected_asset: ProtectedAsset
    messages: tuple[dict[str, str], ...]
    expected_decision: EvalDecision


@dataclass(frozen=True)
class RewriteEvalCase:
    case_id: str
    category: str
    protected_asset: ProtectedAsset
    query: str
    forbidden_fragments: tuple[str, ...]
    required_fragments: tuple[str, ...]
    expected_decision: EvalDecision = EvalDecision.SANITIZED


@dataclass(frozen=True)
class SavePlanExpectation:
    decision: EvalDecision
    llm_calls: int
    success: bool
    should_save: bool
    tool: str
    error_code: str | None = None


@dataclass(frozen=True)
class SavePlanEvalCase:
    case_id: str
    category: str
    protected_asset: ProtectedAsset
    message: str
    conversation_context: tuple[dict[str, str], ...]
    mock_response: str | None
    mock_provider_failure: bool
    expected: SavePlanExpectation


@dataclass(frozen=True)
class MemoryContextEvalCase:
    case_id: str
    category: str
    protected_asset: ProtectedAsset
    value: str
    internal_service: bool
    expected_decision: EvalDecision
    required_fragments: tuple[str, ...] = ()
    forbidden_fragments: tuple[str, ...] = ()


@dataclass(frozen=True)
class StreamEvalCase:
    case_id: str
    category: str
    protected_asset: ProtectedAsset
    provider_failure: bool
    expected_decision: EvalDecision
    expected_error_code: str | None = None


@dataclass(frozen=True)
class EmbeddingEvalCase:
    case_id: str
    category: str
    protected_asset: ProtectedAsset
    model: str
    dimensions: int
    provider_failure: bool
    expected_decision: EvalDecision
    expected_error_code: str | None = None


@dataclass(frozen=True)
class OrganizeSchemaEvalCase:
    case_id: str
    category: str
    protected_asset: ProtectedAsset
    expected_decision: EvalDecision


@dataclass(frozen=True)
class OrganizeDraftEvalCase:
    case_id: str
    category: str
    protected_asset: ProtectedAsset
    scene: str
    raw_output: str
    fallback_content: str
    expected_decision: EvalDecision
    expected_title: str
    expected_entry_type: str
    expected_visibility: str
    forbidden_fragments: tuple[str, ...] = ()


@dataclass(frozen=True)
class PersonaProfileFixture:
    name: str = ""
    description: str = ""
    era_identity: str = ""
    values: str = ""
    speaking_style: str = ""
    personality: str = ""

    def as_dict(self) -> dict[str, str]:
        return {
            "name": self.name,
            "description": self.description,
            "era_identity": self.era_identity,
            "values": self.values,
            "speaking_style": self.speaking_style,
            "personality": self.personality,
        }


@dataclass(frozen=True)
class PersonaSchemaEvalCase:
    case_id: str
    category: str
    protected_asset: ProtectedAsset
    expected_decision: EvalDecision


@dataclass(frozen=True)
class PersonaDraftEvalCase:
    case_id: str
    category: str
    protected_asset: ProtectedAsset
    raw_output: str
    fallback_profile: PersonaProfileFixture
    fallback_content: str
    expected_decision: EvalDecision
    expected_name: str
    expected_material_count: int


@dataclass(frozen=True)
class DraftRuntimeEvalCase:
    case_id: str
    category: str
    protected_asset: ProtectedAsset
    skill: DraftSkillName
    mock_response: str | None
    mock_provider_failure: bool
    expected_decision: EvalDecision
    expected_error_code: str


GoldenCase = (
    TextEvalCase
    | MessageContractCase
    | RewriteEvalCase
    | SavePlanEvalCase
    | MemoryContextEvalCase
    | StreamEvalCase
    | EmbeddingEvalCase
    | OrganizeSchemaEvalCase
    | OrganizeDraftEvalCase
    | PersonaSchemaEvalCase
    | PersonaDraftEvalCase
    | DraftRuntimeEvalCase
)


@dataclass(frozen=True)
class Evaluation:
    passed: bool
    actual_decision: EvalDecision
    error_code: str | None = None


class EvalCaseResult(BaseModel):
    model_config = ConfigDict(frozen=True)

    case_id: str
    category: str
    protected_asset: ProtectedAsset
    passed: bool
    expected_decision: EvalDecision
    actual_decision: EvalDecision
    error_code: str | None = None
    latency_ms: int


class EvalMetrics(BaseModel):
    model_config = ConfigDict(frozen=True)

    case_count: int
    passed_count: int
    failed_count: int
    pass_rate: float
    safety_privacy_failure_count: int
    total_latency_ms: int


class EvalArtifactVersion(BaseModel):
    model_config = ConfigDict(frozen=True)

    kind: EvalArtifactKind
    name: str
    version: str


class EvalGateResult(BaseModel):
    model_config = ConfigDict(frozen=True)

    name: EvalGateName
    passed: bool
    case_count: int
    failed_count: int
    required_pass_rate: float
    actual_pass_rate: float


class EvalReport(BaseModel):
    model_config = ConfigDict(frozen=True)

    schema_version: str
    suite_version: str
    generated_at: datetime
    artifacts: tuple[EvalArtifactVersion, ...]
    gates: tuple[EvalGateResult, ...]
    metrics: EvalMetrics
    results: tuple[EvalCaseResult, ...]
