import pytest

from app.utils.input_guard import (
    InputGuardError,
    InputGuardReason,
    enforce_input_guard,
    inspect_input,
)


def test_blocks_coded_slang_loop():
    result = inspect_input("导管子睡觉面条下雨导管子睡觉")

    assert result.blocked
    assert result.reason == InputGuardReason.SENSITIVE_SLANG


def test_allows_normal_family_context_with_shared_words():
    result = inspect_input("孩子最近睡觉不太规律，下雨天放学也容易淋湿，晚上想煮面条简单吃一点。")

    assert not result.blocked


def test_blocks_low_entropy_repetition():
    result = inspect_input("成长成长成长成长成长价值价值价值价值")

    assert result.blocked
    assert result.reason == InputGuardReason.LOW_ENTROPY


def test_allows_specific_family_observation():
    result = inspect_input("孩子最近写作业时会反复擦掉答案，我想先观察是不是怕出错。")

    assert not result.blocked


def test_enforce_input_guard_raises_with_reason():
    with pytest.raises(InputGuardError) as exc_info:
        enforce_input_guard("哈哈哈哈哈哈哈哈哈哈哈哈")

    assert exc_info.value.reason == InputGuardReason.LOW_ENTROPY

