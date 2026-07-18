"""Typed contracts and public fixtures for sampled provider evaluation."""

from __future__ import annotations

from dataclasses import asdict, dataclass
from enum import StrEnum
from typing import Protocol

MAX_CASES = 2
MAX_CASE_TOKENS = 8
MAX_TIMEOUT_SECONDS = 60.0


class ProviderSampleStatus(StrEnum):
    DISABLED = "DISABLED"
    CONFIG_INVALID = "CONFIG_INVALID"
    PASSED = "PASSED"
    FAILED = "FAILED"


class ProviderSampleErrorCode(StrEnum):
    CONFIG_INVALID = "AI_EVAL_CONFIG_INVALID"
    RESPONSE_MISMATCH = "AI_EVAL_RESPONSE_MISMATCH"
    TOKEN_BUDGET_EXCEEDED = "AI_EVAL_TOKEN_BUDGET_EXCEEDED"
    TIMEOUT = "AI_TIMEOUT"
    PROVIDER_ERROR = "AI_PROVIDER_ERROR"


@dataclass(frozen=True)
class ProviderSampleCase:
    case_id: str
    prompt: str
    expected_output: str
    max_tokens: int


PUBLIC_SAMPLE_CASES = (
    ProviderSampleCase(
        case_id="exact-ok",
        prompt="Return exactly the uppercase token OK and nothing else.",
        expected_output="OK",
        max_tokens=4,
    ),
    ProviderSampleCase(
        case_id="public-arithmetic",
        prompt="Return only the integer result of 19 + 23.",
        expected_output="42",
        max_tokens=8,
    ),
)


@dataclass(frozen=True)
class ProviderProbeResponse:
    content: str
    provider: str
    model: str
    latency_ms: int
    input_tokens: int | None
    output_tokens: int | None
    cost_usd: float | None


class ProviderProbeClient(Protocol):
    async def complete(self, case: ProviderSampleCase) -> ProviderProbeResponse: ...


@dataclass(frozen=True)
class ProviderSampleResult:
    case_id: str
    passed: bool
    provider: str
    model: str
    latency_ms: int
    max_tokens: int
    attempt_count: int
    degraded: bool
    error_code: str | None
    input_tokens: int | None
    output_tokens: int | None
    cost_usd: float | None


@dataclass(frozen=True)
class ProviderSampledEvalReport:
    eval_run_id: str
    status: ProviderSampleStatus
    success: bool
    configured_case_count: int
    executed_case_count: int
    max_output_token_budget: int
    total_input_tokens: int | None
    total_output_tokens: int | None
    estimated_cost_usd: float | None
    error_code: str | None
    results: tuple[ProviderSampleResult, ...]

    def as_dict(self) -> dict[str, object]:
        return asdict(self)
