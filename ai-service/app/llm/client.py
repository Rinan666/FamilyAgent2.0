"""
Unified LiteLLM client with cost and safety limits.
"""
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

    def _is_deepseek(self, model: str) -> bool:
        return "deepseek" in model.lower()

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

        kwargs = dict(
            model=model,
            messages=messages,
            temperature=temperature,
            max_tokens=max_tokens,
        )

        if response_format and self._is_deepseek(model):
            json_schema = response_format.get("json_schema", {}).get("schema", {})
            fields_desc = self._schema_to_fields(json_schema)
            json_instruction = (
                "\n\n请直接返回一个 JSON 对象，包含以下字段："
                f"{fields_desc}\n只输出 JSON，不要加解释、markdown 代码块标记或模板文字。"
            )
            messages = [dict(m) for m in messages]
            messages[-1]["content"] += json_instruction
            validate_messages(messages)
            kwargs["messages"] = messages
        elif response_format:
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
        except Exception as e:
            logger.warning("LLM call failed (model=%s): %s, trying fallback", model, e)
            if model != self.fallback_model and self.fallback_model != model:
                return await self.chat(
                    messages=messages,
                    model=self.fallback_model,
                    temperature=temperature,
                    max_tokens=max_tokens,
                    response_format=response_format if not self._is_deepseek(model) else None,
                )
            raise

    def _schema_to_fields(self, schema: dict, prefix: str = "") -> str:
        """Extract a compact field description from JSON schema."""
        fields = []
        props = schema.get("properties", {})
        for name, prop in props.items():
            ptype = prop.get("type", "string")
            if ptype == "array":
                items = prop.get("items", {}).get("properties", {})
                sub_fields = ", ".join(items.keys()) if items else "object"
                fields.append(f"{name}(数组,每项包含:{sub_fields})")
            elif ptype == "object":
                sub = self._schema_to_fields(prop, name)
                fields.append(f"{name}(对象:{sub})")
            else:
                fields.append(f"{name}({ptype})")
        return "{ " + ", ".join(fields) + " }"

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
        except Exception as e:
            logger.error("LLM stream call failed (model=%s): %s", model, e)
            if model != self.fallback_model and self.fallback_model != model:
                async for chunk in self.chat_stream(
                    messages,
                    model=self.fallback_model,
                    temperature=temperature,
                    max_tokens=max_tokens,
                ):
                    yield chunk
            else:
                yield "抱歉，AI 服务暂时不可用，请稍后重试。"


llm_client = LLMClient()
