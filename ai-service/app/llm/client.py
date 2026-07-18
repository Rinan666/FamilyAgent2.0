"""Unified LiteLLM client with cost and safety limits."""

import json
import logging
import re
import time
from collections.abc import Callable
from typing import AsyncIterator, Optional

from tenacity import retry, retry_if_not_exception_type, stop_after_attempt, wait_exponential

from app.config import settings
from app.llm.completion_config import provider_completion
from app.llm.observation import LLMCallObservation
from app.utils.safety_limits import (
    PromptLeakAttemptError,
    RoleHijackAttemptError,
    SafetyLimitError,
    bounded_output_tokens,
    stream_with_timeouts,
    validate_messages,
    with_hard_timeout,
)

logger = logging.getLogger("familyagent.ai.llm")


class LLMClient:
    """LLM client wrapper."""

    def __init__(self):
        self.default_model = settings.default_llm_model
        self.fallback_model = settings.fallback_llm_model

    @retry(
        stop=stop_after_attempt(2),
        wait=wait_exponential(multiplier=1, min=2, max=15),
        retry=retry_if_not_exception_type((
            SafetyLimitError,
            PromptLeakAttemptError,
            RoleHijackAttemptError,
            TimeoutError,
        )),
    )
    async def chat(
        self,
        messages: list[dict],
        model: Optional[str] = None,
        temperature: float = 0.7,
        max_tokens: int = 4096,
        response_format: Optional[dict] = None,
        observation_sink: Callable[[LLMCallObservation], None] | None = None,
        _degraded: bool = False,
    ) -> str:
        """Run a non-streaming chat completion."""
        model = model or self.default_model
        started_at = time.monotonic()
        validate_messages(messages)
        max_tokens = bounded_output_tokens(max_tokens)

        kwargs = {
            "model": model,
            "messages": messages,
            "temperature": temperature,
            "max_tokens": max_tokens,
        }
        if response_format:
            kwargs["response_format"] = response_format

        try:
            response = await with_hard_timeout(
                provider_completion(**kwargs),
                label="LLM chat",
            )
            content = response.choices[0].message.content

            if response_format:
                content = self._extract_json(content)

            self._observe_call(
                observation_sink,
                model=model,
                started_at=started_at,
                success=True,
                error_code=None,
                degraded=_degraded,
            )
            return content
        except (SafetyLimitError, PromptLeakAttemptError, RoleHijackAttemptError, TimeoutError) as exc:
            self._observe_call(
                observation_sink,
                model=model,
                started_at=started_at,
                success=False,
                error_code="AI_TIMEOUT" if isinstance(exc, TimeoutError) else "AI_INPUT_REJECTED",
                degraded=_degraded,
            )
            raise
        except Exception as exc:
            self._observe_call(
                observation_sink,
                model=model,
                started_at=started_at,
                success=False,
                error_code="AI_PROVIDER_ERROR",
                degraded=_degraded,
            )
            logger.warning(
                "LLM call failed: model=%s errorType=%s; trying fallback",
                model,
                type(exc).__name__,
            )
            if model != self.fallback_model and self.fallback_model != model:
                return await self.chat(
                    messages=messages,
                    model=self.fallback_model,
                    temperature=temperature,
                    max_tokens=max_tokens,
                    response_format=response_format,
                    observation_sink=observation_sink,
                    _degraded=True,
                )
            raise

    def _extract_json(self, text: str) -> str:
        """Extract JSON from LLM output."""
        try:
            json.loads(text)
            return text
        except (json.JSONDecodeError, ValueError):
            pass

        json_match = re.search(r"```(?:json)?\s*([\s\S]*?)\s*```", text)
        if json_match:
            return json_match.group(1).strip()

        brace_match = re.search(r"\{[\s\S]*\}", text)
        if brace_match:
            return brace_match.group(0).strip()

        return text

    async def chat_stream(
        self,
        messages: list[dict],
        model: Optional[str] = None,
        temperature: float = 0.7,
        max_tokens: int = 4096,
        observation_sink: Callable[[LLMCallObservation], None] | None = None,
        _degraded: bool = False,
    ) -> AsyncIterator[str]:
        """Run a streaming chat completion."""
        model = model or self.default_model
        started_at = time.monotonic()
        validate_messages(messages)
        max_tokens = bounded_output_tokens(max_tokens)

        try:
            response = await with_hard_timeout(
                provider_completion(
                    model=model,
                    messages=messages,
                    temperature=temperature,
                    max_tokens=max_tokens,
                    stream=True,
                ),
                label="LLM stream startup",
            )
            async for chunk in stream_with_timeouts(response, label="LLM stream"):
                delta = chunk.choices[0].delta
                if delta.content:
                    yield delta.content
            self._observe_call(
                observation_sink,
                model=model,
                started_at=started_at,
                success=True,
                error_code=None,
                degraded=_degraded,
            )
        except (SafetyLimitError, PromptLeakAttemptError, RoleHijackAttemptError, TimeoutError) as exc:
            self._observe_call(
                observation_sink,
                model=model,
                started_at=started_at,
                success=False,
                error_code="AI_TIMEOUT" if isinstance(exc, TimeoutError) else "AI_INPUT_REJECTED",
                degraded=_degraded,
            )
            raise
        except Exception as exc:
            self._observe_call(
                observation_sink,
                model=model,
                started_at=started_at,
                success=False,
                error_code="AI_PROVIDER_ERROR",
                degraded=_degraded,
            )
            logger.error(
                "LLM stream call failed: model=%s errorType=%s",
                model,
                type(exc).__name__,
            )
            if model != self.fallback_model and self.fallback_model != model:
                async for chunk in self.chat_stream(
                    messages,
                    model=self.fallback_model,
                    temperature=temperature,
                    max_tokens=max_tokens,
                    observation_sink=observation_sink,
                    _degraded=True,
                ):
                    yield chunk
            else:
                raise RuntimeError("LLM stream provider unavailable") from exc

    def _observe_call(
        self,
        observation_sink: Callable[[LLMCallObservation], None] | None,
        *,
        model: str,
        started_at: float,
        success: bool,
        error_code: str | None,
        degraded: bool,
    ) -> None:
        if observation_sink is None:
            return
        provider = model.split("/", 1)[0].strip() if "/" in model else "unknown"
        observation_sink(LLMCallObservation(
            provider=provider or "unknown",
            model=model,
            latency_ms=max(0, round((time.monotonic() - started_at) * 1000)),
            success=success,
            error_code=error_code,
            degraded=degraded,
        ))


llm_client = LLMClient()
