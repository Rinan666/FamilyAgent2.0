"""Deterministic signals that protect save-draft intent without judging value."""

import re


def _looks_like_save_command_only(text: str) -> bool:
    normalized = re.sub(r"\s+", "", text.strip())
    if not normalized:
        return True
    return bool(re.fullmatch(
        r"(请|麻烦)?(帮我)?(把)?(上面|刚才|前面|上一段|前一段|之前|刚刚|这件事|这段话|这些内容)?"
        r"(提到的|说的|讲的)?(事情|内容|记录)?"
        r"(保存|存起来|记下来|记录一下|记录下来|沉淀下来|帮我记|帮我存|帮我保存|帮我记录|"
        r"保存到记忆库|加入记忆库|放到记忆库|收进记忆库)"
        r"(一下|起来)?(吧)?[。.!！?？]*",
        normalized,
    ))


def _should_skip_save_planning(message: str, conversation_context: str = "") -> bool:
    content = str(message or "").strip()
    if not content:
        return True
    return _looks_like_save_command_only(content) and not str(conversation_context or "").strip()


def _has_private_emotion_signal(text: str) -> bool:
    return bool(re.search(
        r"(难过|开心|焦虑|压力|委屈|生气|害怕|失落|感动|担心|烦躁|崩溃|释然|后悔|"
        r"遗憾|不安|孤独|撑不住|很累|很痛苦)",
        text,
    ))


def _has_substantive_insight_signal(text: str) -> bool:
    return bool(
        re.search(r"(意识到|明白了|学到了|教训|反思|如果重来|以后要|下次会|提醒自己)", text)
        and re.search(r"(因为|所以|不能|不要|需要|应该|选择|决定|代价|适合|不适合|坚持)", text)
    )
