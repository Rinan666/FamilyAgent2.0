"""Typed contracts and synthetic fixtures for real-provider quality sampling."""

from __future__ import annotations

from dataclasses import asdict, dataclass
from enum import StrEnum
from typing import Protocol

from app.api.memory_models import SaveToolPlanData

MAX_QUALITY_CASES = 3
MAX_QUALITY_CASE_TOKENS = 360
MAX_QUALITY_TIMEOUT_SECONDS = 60.0
MIN_QUALITY_SCORE = 0.8
PROVIDER_QUALITY_EVAL_SCHEMA_VERSION = "provider.quality-eval.v1"
PROVIDER_QUALITY_FIXTURE_VERSION = "provider.quality-fixtures.v1"


class ProviderQualityStatus(StrEnum):
    DISABLED = "DISABLED"
    CONFIG_INVALID = "CONFIG_INVALID"
    PASSED = "PASSED"
    FAILED = "FAILED"


class ProviderQualityErrorCode(StrEnum):
    CONFIG_INVALID = "AI_QUALITY_EVAL_CONFIG_INVALID"
    RESPONSE_MISMATCH = "AI_QUALITY_EVAL_RESPONSE_MISMATCH"
    STRUCTURED_OUTPUT_INVALID = "AI_QUALITY_EVAL_STRUCTURED_OUTPUT_INVALID"
    TOKEN_BUDGET_EXCEEDED = "AI_QUALITY_EVAL_TOKEN_BUDGET_EXCEEDED"
    TIMEOUT = "AI_TIMEOUT"
    AUTHENTICATION_ERROR = "AI_PROVIDER_AUTHENTICATION_ERROR"
    RATE_LIMITED = "AI_PROVIDER_RATE_LIMITED"
    PROVIDER_ERROR = "AI_PROVIDER_ERROR"


class ProviderQualityAuthenticationError(RuntimeError):
    """Provider rejected the configured credential or permission."""


class ProviderQualityRateLimitError(RuntimeError):
    """Provider rejected the sample because of rate limiting."""


class ProviderQualityStructuredOutputError(RuntimeError):
    """Provider output could not be parsed as a safe save plan."""


@dataclass(frozen=True)
class ProviderQualityCase:
    case_id: str
    message: str
    expected_tool: str
    expected_scope: str | None
    anchor_groups: tuple[tuple[str, ...], ...]
    forbidden_terms: tuple[str, ...]
    max_tokens: int = MAX_QUALITY_CASE_TOKENS


SYNTHETIC_QUALITY_CASES = (
    ProviderQualityCase(
        case_id="learning-strategy",
        message=(
            "孩子最近做应用题总是先抓数字，不读完整题意，所以容易列错式。"
            "我让他先复述题意，再画一张简单线段图；今天他能更稳定地说出等量关系，"
            "后面遇到应用题准备继续先拆题意再计算。"
        ),
        expected_tool="FAMILY_MEMORY",
        expected_scope="CARE_VISIBLE",
        anchor_groups=(("应用题",), ("复述题意", "复述"), ("线段图", "画图")),
        forbidden_terms=("注意力缺陷", "智力问题", "确诊"),
    ),
    ProviderQualityCase(
        case_id="low-value-insight",
        message="我突然明白了，人要积极向前看，保持乐观，未来一定会越来越好。",
        expected_tool="NONE",
        expected_scope=None,
        anchor_groups=(),
        forbidden_terms=("家族智慧", "核心记忆"),
    ),
    ProviderQualityCase(
        case_id="sensitive-vision-follow-up",
        message=(
            "孩子最近上课看黑板时会眯眼，有两次说后排字有点看不清。"
            "我准备周末带他做一次视力检查，之后继续记录变化。"
        ),
        expected_tool="GROWTH_GUARD",
        expected_scope="CARE_VISIBLE",
        anchor_groups=(("眯眼",), ("看不清", "黑板"), ("视力检查", "检查")),
        forbidden_terms=("已经近视", "确诊近视", "医学诊断"),
    ),
)


@dataclass(frozen=True)
class ProviderQualityProbeResponse:
    plan: SaveToolPlanData
    provider: str
    model: str
    latency_ms: int
    input_tokens: int | None
    output_tokens: int | None
    cost_usd: float | None


class ProviderQualityProbeClient(Protocol):
    async def complete(
        self,
        model: str,
        case: ProviderQualityCase,
    ) -> ProviderQualityProbeResponse: ...


@dataclass(frozen=True)
class ProviderQualityCaseResult:
    case_id: str
    passed: bool
    quality_score: float
    structured: bool
    actual_tool: str | None
    actual_scope: str | None
    tool_match: bool
    should_save_match: bool
    scope_match: bool | None
    anchor_coverage: float
    forbidden_term_count: int
    latency_ms: int
    max_tokens: int
    input_tokens: int | None
    output_tokens: int | None
    cost_usd: float | None
    error_code: str | None


@dataclass(frozen=True)
class ProviderQualityModelReport:
    model: str
    provider: str
    completed: bool
    success: bool
    configured_case_count: int
    executed_case_count: int
    pass_rate: float
    average_quality_score: float
    structured_failure_rate: float
    total_latency_ms: int
    total_input_tokens: int | None
    total_output_tokens: int | None
    estimated_cost_usd: float | None
    error_code: str | None
    results: tuple[ProviderQualityCaseResult, ...]


@dataclass(frozen=True)
class ProviderQualityComparison:
    baseline_model: str
    candidate_model: str
    pass_rate_delta: float
    quality_score_delta: float
    structured_failure_rate_delta: float
    latency_ms_delta: int
    estimated_cost_usd_delta: float | None


@dataclass(frozen=True)
class ProviderQualityEvalReport:
    schema_version: str
    fixture_version: str
    eval_run_id: str
    status: ProviderQualityStatus
    success: bool
    candidate_model: str
    baseline_model: str | None
    configured_case_count: int
    model_run_count: int
    max_output_token_budget: int
    error_code: str | None
    runs: tuple[ProviderQualityModelReport, ...]
    comparison: ProviderQualityComparison | None

    def as_dict(self) -> dict[str, object]:
        return asdict(self)
