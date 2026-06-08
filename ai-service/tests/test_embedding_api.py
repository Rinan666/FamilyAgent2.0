import pytest

from app.api import embedding
from app.api.embedding import _dashscope_multimodal_dimension, _embed, _hash_embedding


def test_hash_embedding_is_stable_and_normalized():
    first = _hash_embedding("爷爷提醒换牙期要认真刷牙", 128)
    second = _hash_embedding("爷爷提醒换牙期要认真刷牙", 128)

    assert first == second
    assert len(first) == 128
    assert abs(sum(value * value for value in first) - 1.0) < 0.001


@pytest.mark.asyncio
async def test_local_embedding_uses_requested_dimensions():
    vector = await _embed("妈妈手机号是13812345678，注意隐私。", "local/hash-embedding", 256)

    assert len(vector) == 256
    assert any(value != 0 for value in vector)


def test_embedding_router_requires_backend_token_verification():
    dependencies = [item.dependency for item in embedding.router.dependencies]

    assert embedding.verify_token in dependencies


def test_qwen3_vl_embedding_allows_1536_dimensions():
    assert _dashscope_multimodal_dimension("qwen3-vl-embedding", 1536) == 1536
