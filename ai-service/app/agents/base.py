"""
Base agent helpers.
"""
import logging
from typing import AsyncIterator, Optional

from app.llm.client import llm_client

logger = logging.getLogger("familyagent.ai.agent")


class BaseAgent:
    """Base class for AI agents."""

    def __init__(self, name: str, system_prompt: str):
        self.name = name
        self.system_prompt = system_prompt

    def _build_messages(
        self,
        user_message: str,
        history: Optional[list[dict]] = None,
    ) -> list[dict]:
        """Build the outbound message list."""
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
        """Run the agent and return a single response."""
        messages = self._build_messages(user_message, history)
        logger.info(f"[{self.name}] Request started (history_len={len(history or [])})")
        result = await llm_client.chat(
            messages, temperature=temperature, max_tokens=max_tokens
        )
        logger.info(f"[{self.name}] Request finished (output_len={len(result)})")
        return result

    async def run_stream(
        self,
        user_message: str,
        history: Optional[list[dict]] = None,
        temperature: float = 0.7,
        max_tokens: int = 4096,
    ) -> AsyncIterator[str]:
        """Run the agent as a token stream."""
        messages = self._build_messages(user_message, history)
        logger.info(f"[{self.name}] Streaming request started (history_len={len(history or [])})")
        async for chunk in llm_client.chat_stream(
            messages, temperature=temperature, max_tokens=max_tokens
        ):
            yield chunk
        logger.info(f"[{self.name}] Streaming request finished")
