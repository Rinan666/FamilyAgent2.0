"""
LLM客户端 - 基于 LiteLLM 统一多模型调用
"""
import json
import logging
import re
from typing import AsyncIterator, Optional

import litellm
from tenacity import retry, stop_after_attempt, wait_exponential

from app.config import settings

logger = logging.getLogger("familyagent.ai.llm")


class LLMClient:
    """LLM调用客户端"""

    def __init__(self):
        self.default_model = settings.default_llm_model
        self.fallback_model = settings.fallback_llm_model

    def _is_deepseek(self, model: str) -> bool:
        return "deepseek" in model.lower()

    @retry(
        stop=stop_after_attempt(2),
        wait=wait_exponential(multiplier=1, min=2, max=15),
    )
    async def chat(
        self,
        messages: list[dict],
        model: Optional[str] = None,
        temperature: float = 0.7,
        max_tokens: int = 4096,
        response_format: Optional[dict] = None,
    ) -> str:
        """同步聊天调用"""
        model = model or self.default_model

        kwargs = dict(
            model=model,
            messages=messages,
            temperature=temperature,
            max_tokens=max_tokens,
        )

        # DeepSeek 不支持 response_format，改用 prompt 注入 JSON 指令
        if response_format and self._is_deepseek(model):
            json_schema = response_format.get("json_schema", {}).get("schema", {})
            # 简化为关键字段描述（避免DeepSeek回显完整schema）
            fields_desc = self._schema_to_fields(json_schema)
            json_instruction = f"\n\n请直接返回一个JSON对象，包含以下字段：{fields_desc}\n只输出JSON，不要加任何解释、markdown代码块标记或模板文字。"
            messages = [dict(m) for m in messages]
            messages[-1]["content"] += json_instruction
            kwargs["messages"] = messages
        elif response_format:
            kwargs["response_format"] = response_format

        try:
            response = await litellm.acompletion(**kwargs)
            content = response.choices[0].message.content

            # DeepSeek 结构化输出后提取 JSON
            if response_format:
                content = self._extract_json(content)

            return content
        except Exception as e:
            logger.warning(f"LLM调用失败 (model={model}): {e}, 尝试降级")
            if model != self.fallback_model and self.fallback_model != model:
                return await self.chat(
                    messages=messages, model=self.fallback_model,
                    temperature=temperature, max_tokens=max_tokens,
                    response_format=response_format if not self._is_deepseek(model) else None,
                )
            raise

    def _schema_to_fields(self, schema: dict, prefix: str = "") -> str:
        """递归提取JSON Schema的关键字段名称"""
        fields = []
        props = schema.get("properties", {})
        for name, prop in props.items():
            ptype = prop.get("type", "string")
            desc = prop.get("description", "")
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
        """从LLM输出中提取JSON"""
        # 尝试直接解析
        try:
            json.loads(text)
            return text
        except (json.JSONDecodeError, ValueError):
            pass

        # 尝试提取 ```json ... ``` 块
        json_match = re.search(r'```(?:json)?\s*([\s\S]*?)\s*```', text)
        if json_match:
            return json_match.group(1).strip()

        # 尝试提取 { ... } 块
        brace_match = re.search(r'\{[\s\S]*\}', text)
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
        """流式聊天调用"""
        model = model or self.default_model

        try:
            response = await litellm.acompletion(
                model=model,
                messages=messages,
                temperature=temperature,
                max_tokens=max_tokens,
                stream=True,
            )
            async for chunk in response:
                delta = chunk.choices[0].delta
                if delta.content:
                    yield delta.content
        except Exception as e:
            logger.error(f"LLM流式调用失败 (model={model}): {e}")
            if model != self.fallback_model and self.fallback_model != model:
                async for chunk in self.chat_stream(
                    messages, model=self.fallback_model,
                    temperature=temperature, max_tokens=max_tokens,
                ):
                    yield chunk
            else:
                yield "抱歉，AI服务暂时不可用，请稍后重试。"


# 全局单例
llm_client = LLMClient()
