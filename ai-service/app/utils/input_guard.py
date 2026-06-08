"""Input guard for low-entropy spam and coded vulgar slang.

This is a deterministic first pass before model calls. It is intentionally
conservative: normal family/life contexts should pass, while short repeated
slang loops and very low-information text are rejected.
"""

from __future__ import annotations

import math
import re
from collections import Counter
from dataclasses import dataclass
from enum import StrEnum


class InputGuardReason(StrEnum):
    LOW_ENTROPY = "LOW_ENTROPY"
    SENSITIVE_SLANG = "SENSITIVE_SLANG"


class InputGuardError(ValueError):
    """Raised when user input is likely spam, token abuse, or coded vulgar slang."""

    def __init__(self, reason: InputGuardReason, message: str):
        super().__init__(message)
        self.reason = reason


@dataclass(frozen=True)
class InputGuardResult:
    blocked: bool
    reason: InputGuardReason | None = None
    message: str = ""


SENSITIVE_SLANG_TERMS = ("导管子", "睡觉", "面条", "下雨")
NORMAL_FAMILY_CONTEXT_PATTERNS = [
    r"(睡眠|入睡|作息|熬夜|午睡|早睡|睡前|睡醒|失眠|睡不着|休息)",
    r"(天气|下雨天|雨伞|带伞|淋雨|雨衣|路滑|降雨|雨季)",
    r"(吃饭|早餐|午餐|晚餐|做饭|煮面|面食|饭菜|营养|食谱)",
    r"(孩子|家人|妈妈|爸爸|爷爷|奶奶|外公|外婆|学校|学习|健康|观察|提醒)",
]
LOW_INFORMATION_FILLERS = [
    r"^(哈哈|嘿嘿|嗯嗯|啊啊|好好|行行|知道了|谢谢|可以|还行)+$",
    r"^(成长|价值|意义|未来|深刻|温柔|积极|家族|经验|沉淀|哲理|人生)+$",
]


def inspect_input(text: str) -> InputGuardResult:
    normalized = str(text or "").strip()
    compact = re.sub(r"\s+", "", normalized)
    if not compact:
        return InputGuardResult(blocked=False)

    if _looks_like_sensitive_slang_loop(compact):
        return InputGuardResult(
            blocked=True,
            reason=InputGuardReason.SENSITIVE_SLANG,
            message="输入疑似高频低俗暗语或恶意复读，请换成正常家庭/学习语境表达。",
        )

    if _looks_like_low_entropy_input(compact):
        return InputGuardResult(
            blocked=True,
            reason=InputGuardReason.LOW_ENTROPY,
            message="输入信息熵过低，疑似无意义复读或刷 Token，请补充具体问题、事件或观察。",
        )

    return InputGuardResult(blocked=False)


def enforce_input_guard(text: str) -> None:
    result = inspect_input(text)
    if result.blocked:
        raise InputGuardError(result.reason or InputGuardReason.LOW_ENTROPY, result.message)


def _looks_like_sensitive_slang_loop(text: str) -> bool:
    if len(text) >= 300:
        return False
    match_count = sum(text.count(term) for term in SENSITIVE_SLANG_TERMS)
    if match_count < 4:
        return False
    normal_context = any(re.search(pattern, text) for pattern in NORMAL_FAMILY_CONTEXT_PATTERNS)
    if normal_context and match_count < 6:
        return False
    return True


def _looks_like_low_entropy_input(text: str) -> bool:
    if len(text) < 6:
        return False
    if any(re.fullmatch(pattern, text) for pattern in LOW_INFORMATION_FILLERS):
        return True
    if _has_repeated_chunk(text):
        return True
    if len(text) < 300 and _dominant_token_ratio(text) >= 0.55:
        return True
    if len(text) < 300 and _char_entropy(text) < 2.2 and _unique_cjk_ratio(text) < 0.38:
        return True
    return False


def _has_repeated_chunk(text: str) -> bool:
    if re.fullmatch(r"(.{1,6})\1{2,}", text):
        return True
    for size in range(2, 7):
        chunks = [text[index : index + size] for index in range(0, len(text), size)]
        if len(chunks) >= 4:
            counts = Counter(chunks)
            chunk, count = counts.most_common(1)[0]
            if chunk and count >= 4 and count / len(chunks) >= 0.6:
                return True
    return False


def _dominant_token_ratio(text: str) -> float:
    tokens = re.findall(r"[\u4e00-\u9fff]{1,4}|[a-zA-Z0-9_]+", text)
    if not tokens:
        return 0.0
    normalized = [token.lower() for token in tokens]
    _, count = Counter(normalized).most_common(1)[0]
    return count / len(normalized)


def _char_entropy(text: str) -> float:
    chars = [char for char in text if not char.isspace()]
    if not chars:
        return 0.0
    counts = Counter(chars)
    total = len(chars)
    return -sum((count / total) * math.log2(count / total) for count in counts.values())


def _unique_cjk_ratio(text: str) -> float:
    cjk = [char for char in text if "\u4e00" <= char <= "\u9fff"]
    if not cjk:
        return 1.0
    return len(set(cjk)) / len(cjk)

