"""Contracts for privacy-safe evaluation report comparisons."""

from enum import StrEnum

from pydantic import BaseModel, ConfigDict

from .models import EvalArtifactKind, EvalGateName


class EvalComparisonConclusion(StrEnum):
    NO_CHANGE = "NO_CHANGE"
    IMPROVED = "IMPROVED"
    REGRESSION = "REGRESSION"
    INCOMPARABLE = "INCOMPARABLE"


class EvalCaseChangeKind(StrEnum):
    UNCHANGED = "UNCHANGED"
    IMPROVEMENT = "IMPROVEMENT"
    REGRESSION = "REGRESSION"
    ADDED = "ADDED"
    REMOVED = "REMOVED"


class EvalCaseChange(BaseModel):
    model_config = ConfigDict(frozen=True)

    case_id: str
    change: EvalCaseChangeKind
    baseline_passed: bool | None
    candidate_passed: bool | None


class EvalArtifactChange(BaseModel):
    model_config = ConfigDict(frozen=True)

    kind: EvalArtifactKind
    name: str
    baseline_version: str | None
    candidate_version: str | None


class EvalGateChange(BaseModel):
    model_config = ConfigDict(frozen=True)

    name: EvalGateName
    baseline_passed: bool | None
    candidate_passed: bool | None


class EvalComparisonMetrics(BaseModel):
    model_config = ConfigDict(frozen=True)

    unchanged_count: int
    improvement_count: int
    regression_count: int
    added_count: int
    removed_count: int
    gate_regression_count: int


class EvalComparisonReport(BaseModel):
    model_config = ConfigDict(frozen=True)

    schema_version: str
    baseline_suite_version: str
    candidate_suite_version: str
    conclusion: EvalComparisonConclusion
    metrics: EvalComparisonMetrics
    case_changes: tuple[EvalCaseChange, ...]
    gate_changes: tuple[EvalGateChange, ...]
    artifact_changes: tuple[EvalArtifactChange, ...]
