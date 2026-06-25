from fastapi import HTTPException
import pytest

from app.api import embedding
from app.api.embedding import _dashscope_multimodal_dimension, _embed, _embedding_provider, _hash_embedding


def test_hash_embedding_is_stable_and_normalized():
    first = _hash_embedding("爷爷提醒换牙期要认真刷牙", 128)
    second = _hash_embedding("爷爷提醒换牙期要认真刷牙", 128)

    assert first == second
    assert len(first) == 128
    assert abs(sum(value * value for value in first) - 1.0) < 0.001


def test_embedding_response_exposes_provider_metadata():
    assert _embedding_provider("local/hash-embedding") == "local"
    assert _embedding_provider("dashscope/text-embedding-v4") == "dashscope"
    assert _embedding_provider("dashscope-multimodal/qwen3-vl-embedding") == "dashscope"
    assert _embedding_provider("openai/text-embedding-3-small") == "openai"


@pytest.mark.asyncio
async def test_local_embedding_uses_requested_dimensions():
    vector = await _embed("妈妈手机号是13812345678，注意隐私。", "local/hash-embedding", 256)

    assert len(vector) == 256
    assert any(value != 0 for value in vector)


@pytest.mark.asyncio
async def test_external_embedding_provider_failure_is_not_hash_fallback(monkeypatch):
    async def fail_embedding(*args, **kwargs):
        raise RuntimeError("provider unavailable")

    monkeypatch.setattr(embedding.litellm, "aembedding", fail_embedding)

    with pytest.raises(HTTPException) as exc:
        await _embed("家庭记忆", "openai/text-embedding-3-small", 128)

    assert exc.value.status_code == 503


@pytest.mark.asyncio
async def test_dashscope_missing_key_is_not_hash_fallback(monkeypatch):
    monkeypatch.setattr(embedding.settings, "dashscope_api_key", None)

    with pytest.raises(HTTPException) as exc:
        await _embed("家庭记忆", "dashscope/text-embedding-v4", 128)

    assert exc.value.status_code == 503


def test_embedding_router_requires_backend_token_verification():
    dependencies = [item.dependency for item in embedding.router.dependencies]

    assert embedding.verify_token_or_internal_service in dependencies


def test_qwen3_vl_embedding_allows_1536_dimensions():
    assert _dashscope_multimodal_dimension("qwen3-vl-embedding", 1536) == 1536
