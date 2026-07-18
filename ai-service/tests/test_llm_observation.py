import pytest
from tenacity import RetryError

from app.llm.client import LLMClient


class _Chunk:
    def __init__(self, content: str):
        self.choices = [type("Choice", (), {
            "delta": type("Delta", (), {"content": content})(),
        })()]


class _EmptyChoicesChunk:
    choices = []


async def _stream(*chunks):
    for chunk in chunks:
        yield chunk if hasattr(chunk, "choices") else _Chunk(chunk)


@pytest.mark.asyncio
async def test_chat_stream_ignores_openai_usage_chunks_without_choices(monkeypatch):
    client = LLMClient()
    client.default_model = "dashscope/model"
    client.fallback_model = "dashscope/model"

    async def fake_completion(**kwargs):
        return _stream("first", _EmptyChoicesChunk(), "second")

    monkeypatch.setattr("app.llm.client.provider_completion", fake_completion)
    observations = []

    chunks = [chunk async for chunk in client.chat_stream(
        [{"role": "user", "content": "hello"}],
        observation_sink=observations.append,
    )]

    assert chunks == ["first", "second"]
    assert [(item.success, item.error_code, item.degraded) for item in observations] == [
        (True, None, False),
    ]


@pytest.mark.asyncio
async def test_chat_stream_observes_primary_failure_and_fallback_success(monkeypatch, caplog):
    client = LLMClient()
    client.default_model = "primary/model"
    client.fallback_model = "fallback/model"

    async def fake_completion(**kwargs):
        if kwargs["model"] == "primary/model":
            raise RuntimeError("primary unavailable")
        return _stream("fallback response")

    monkeypatch.setattr("app.llm.client.provider_completion", fake_completion)
    observations = []

    chunks = [chunk async for chunk in client.chat_stream(
        [{"role": "user", "content": "hello"}],
        observation_sink=observations.append,
    )]

    assert chunks == ["fallback response"]
    assert [(item.model, item.success, item.degraded) for item in observations] == [
        ("primary/model", False, False),
        ("fallback/model", True, True),
    ]
    assert observations[0].error_code == "AI_PROVIDER_ERROR"
    assert observations[1].error_code is None
    assert "primary unavailable" not in caplog.text


@pytest.mark.asyncio
async def test_chat_stream_observes_all_provider_failures(monkeypatch, caplog):
    client = LLMClient()
    client.default_model = "primary/model"
    client.fallback_model = "fallback/model"

    async def fake_completion(**kwargs):
        raise RuntimeError(f"{kwargs['model']} unavailable")

    monkeypatch.setattr("app.llm.client.provider_completion", fake_completion)
    observations = []

    with pytest.raises(RuntimeError, match="LLM stream provider unavailable"):
        async for _ in client.chat_stream(
            [{"role": "user", "content": "hello"}],
            observation_sink=observations.append,
        ):
            pass

    assert [(item.model, item.success, item.degraded) for item in observations] == [
        ("primary/model", False, False),
        ("fallback/model", False, True),
    ]
    assert all(item.error_code == "AI_PROVIDER_ERROR" for item in observations)
    assert "primary/model unavailable" not in caplog.text
    assert "fallback/model unavailable" not in caplog.text


@pytest.mark.asyncio
async def test_chat_does_not_log_provider_exception_details(monkeypatch, caplog):
    client = LLMClient()
    client.default_model = "primary/model"
    client.fallback_model = "primary/model"

    async def fake_completion(**kwargs):
        raise RuntimeError("private non-stream provider detail")

    monkeypatch.setattr("app.llm.client.provider_completion", fake_completion)

    with pytest.raises(RetryError):
        await client.chat([{"role": "user", "content": "hello"}])

    assert "private non-stream provider detail" not in caplog.text


@pytest.mark.asyncio
async def test_chat_observes_primary_failure_and_fallback_success(monkeypatch):
    client = LLMClient()
    client.default_model = "primary/model"
    client.fallback_model = "fallback/model"

    async def fake_completion(**kwargs):
        if kwargs["model"] == "primary/model":
            raise RuntimeError("primary unavailable")
        message = type("Message", (), {"content": "OK"})()
        choice = type("Choice", (), {"message": message})()
        return type("Response", (), {"choices": [choice]})()

    monkeypatch.setattr("app.llm.client.provider_completion", fake_completion)
    observations = []

    result = await client.chat(
        [{"role": "user", "content": "hello"}],
        observation_sink=observations.append,
    )

    assert result == "OK"
    assert [(item.model, item.success, item.degraded) for item in observations] == [
        ("primary/model", False, False),
        ("fallback/model", True, True),
    ]
