"""Unified LiteLLM client with cost and safety limits."""

import json
import logging
import re
from typing import AsyncIterator, Optional

import litellm
from tenacity import retry, retry_if_not_exception_type, stop_after_attempt, wait_exponential

from app.config import settings
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
    ) -> str:
        """Run a non-streaming chat completion."""
        model = model or self.default_model
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
                litellm.acompletion(**kwargs),
                label="LLM chat",
            )
            content = response.choices[0].message.content

            if response_format:
                content = self._extract_json(content)

            return content
        except (SafetyLimitError, PromptLeakAttemptError, RoleHijackAttemptError, TimeoutError):
            raise
        except Exception as exc:
            logger.warning("LLM call failed (model=%s): %s, trying fallback", model, exc)
            if model != self.fallback_model and self.fallback_model != model:
                return await self.chat(
                    messages=messages,
                    model=self.fallback_model,
                    temperature=temperature,
                    max_tokens=max_tokens,
                    response_format=response_format,
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
    ) -> AsyncIterator[str]:
        """Run a streaming chat completion."""
        model = model or self.default_model
        validate_messages(messages)
        max_tokens = bounded_output_tokens(max_tokens)

        try:
            response = await with_hard_timeout(
                litellm.acompletion(
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
        except (SafetyLimitError, PromptLeakAttemptError, RoleHijackAttemptError, TimeoutError):
            raise
        except Exception as exc:
            logger.error("LLM stream call failed (model=%s): %s", model, exc)
            if model != self.fallback_model and self.fallback_model != model:
                async for chunk in self.chat_stream(
                    messages,
                    model=self.fallback_model,
                    temperature=temperature,
                    max_tokens=max_tokens,
                ):
                    yield chunk
            else:
                raise RuntimeError("LLM stream provider unavailable") from exc


llm_client = LLMClient()
