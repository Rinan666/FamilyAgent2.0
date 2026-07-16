"""Deterministic LLM double used by low-cost evaluation cases."""


class MockLLMClient:
    def __init__(self, response: str | None, provider_failure: bool = False):
        self._response = response
        self._provider_failure = provider_failure
        self.call_count = 0

    async def chat(self, **_kwargs) -> str:
        self.call_count += 1
        if self._provider_failure:
            raise RuntimeError("mock provider unavailable")
        return self._response or ""
