import pytest

from app.api import agent


@pytest.mark.asyncio
async def test_stream_chat_redacts_internal_errors(monkeypatch):
    async def broken_chat_stream(**kwargs):
        raise RuntimeError("secret upstream stack detail")
        yield "unreachable"

    monkeypatch.setattr(agent.family_agent, "chat_stream", broken_chat_stream)

    response = await agent.stream_chat(agent.AgentChatRequest(member_message="hello"))
    chunks = []
    async for chunk in response.body_iterator:
        chunks.append(chunk.decode("utf-8") if isinstance(chunk, bytes) else chunk)

    body = "".join(chunks)
    assert "AI service unavailable" in body
    assert "secret upstream stack detail" not in body
