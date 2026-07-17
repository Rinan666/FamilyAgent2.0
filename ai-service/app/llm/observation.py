"""Privacy-safe observations for LLM provider attempts."""

from dataclasses import dataclass

from app.runtime.trace_observation import TraceObservation


@dataclass(frozen=True)
class LLMCallObservation:
    provider: str
    model: str
    latency_ms: int
    success: bool
    error_code: str | None
    degraded: bool

    def as_trace_observation(self, prompt_version: str | None = None) -> TraceObservation:
        return TraceObservation(
            stepType="LLM",
            operation="llm.chat_stream",
            provider=self.provider,
            model=self.model,
            promptVersion=prompt_version,
            latencyMs=self.latency_ms,
            success=self.success,
            errorCode=self.error_code,
            degraded=self.degraded,
            privacyCategories=["FAMILY_DATA"],
        )
