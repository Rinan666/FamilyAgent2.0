"""
Agent 基类
"""
import logging
from typing import AsyncIterator, Optional

from app.llm.client import llm_client

logger = logging.getLogger("familyagent.ai.agent")


class BaseAgent:
    """AI Agent 基类"""

    def __init__(self, name: str, system_prompt: str):
        self.name = name
        self.system_prompt = system_prompt

    def _build_messages(
        self,
        user_message: str,
        history: Optional[list[dict]] = None,
    ) -> list[dict]:
        """构建消息列表"""
        messages = [{"role": "system", "content": self.system_prompt}]
        if history:
            messages.extend(history)
        messages.append({"role": "user", "content": user_message})
        return messages

    async def run(
        self,
        user_message: str,
        history: Optional[list[dict]] = None,
        temperature: float = 0.7,
        max_tokens: int = 4096,
    ) -> str:
        """同步运行Agent"""
        messages = self._build_messages(user_message, history)
        logger.info(f"[{self.name}] 开始处理请求 (history_len={len(history or [])})")
        result = await llm_client.chat(
            messages, temperature=temperature, max_tokens=max_tokens
        )
        logger.info(f"[{self.name}] 处理完成 (output_len={len(result)})")
        return result

    async def run_stream(
        self,
        user_message: str,
        history: Optional[list[dict]] = None,
        temperature: float = 0.7,
        max_tokens: int = 4096,
    ) -> AsyncIterator[str]:
        """流式运行Agent"""
        messages = self._build_messages(user_message, history)
        logger.info(f"[{self.name}] 开始流式处理 (history_len={len(history or [])})")
        async for chunk in llm_client.chat_stream(
            messages, temperature=temperature, max_tokens=max_tokens
        ):
            yield chunk
        logger.info(f"[{self.name}] 流式处理完成")
