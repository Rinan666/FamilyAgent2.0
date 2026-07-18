"""Pure helpers for family memory save-plan routing and shared heuristics."""

import re

from .memory_save_signals import (
    _has_durable_save_value,
    _has_private_emotion_signal,
    _has_substantive_insight_signal,
    _infer_save_tool,
    _lacks_substantive_save_value,
    _looks_like_prompt_injection,
    _looks_like_save_command_only,
    _sentence_has_save_value,
)

def _sanitize_save_tool_plan(data: dict) -> dict:
    content = _normalize_save_content(data.get("content", ""))
    raw_tool = _choice(data.get("tool"), {"NONE", "DIARY", "FAMILY_MEMORY", "GROWTH_GUARD"}, "NONE")
    if _looks_like_save_command_only(content):
        return _blocked_save_tool_plan("只有保存指令，没有可沉淀的具体内容。")
    if _looks_like_prompt_injection(content):
        return _blocked_save_tool_plan("疑似提示词注入或越权指令，不适合保存为家族记忆。")
    if _lacks_substantive_save_value(content):
        return _blocked_save_tool_plan("内容缺乏具体人物、事件、观察、情绪强度或可跟进经验，不应沉淀为家族记忆。")
    tool = _infer_save_tool(content, raw_tool)
    should_save = (
        (bool(data.get("should_save")) or _has_durable_save_value(content))
        and tool != "NONE"
        and len(content) >= 4
    )
    if not should_save:
        tool = "NONE"

    visibility = _normalize_save_visibility(
        tool,
        _choice(
            data.get("visibility"),
            {"PRIVATE", "FAMILY_VISIBLE", "CARE_VISIBLE", "LEGACY_VISIBLE"},
            "PRIVATE",
        ),
        content,
    )
    scope = _normalize_save_scope(
        tool,
        _choice(
            data.get("scope"),
            {"PRIVATE", "CARE_VISIBLE", "FAMILY_VISIBLE", "PARENT_VISIBLE"},
            "PRIVATE" if visibility == "PRIVATE" else visibility,
        ),
        visibility,
    )
    category = _choice(
        data.get("category"),
        {
            "POSTURE",
            "DENTAL",
            "VISION",
            "SLEEP",
            "EXERCISE",
            "SCREEN_TIME",
            "EMOTION",
            "COMMUNICATION",
            "OTHER",
        },
        "OTHER",
    )
    if tool == "GROWTH_GUARD":
        category = _infer_growth_category(content, category)
    memory_type = _choice(
        data.get("memory_type"),
        {"FAMILY_STORY", "ELDER_ADVICE", "HEALTH_REMINDER", "GROWTH_RISK", "VALUE", "PLAN"},
        "ELDER_ADVICE",
    )
    if tool == "FAMILY_MEMORY":
        memory_type = _infer_family_memory_type(content, memory_type)
    entry_type = _choice(
        data.get("entry_type"),
        {"DAILY", "IMPORTANT_EVENT", "LESSON", "EMOTION", "MESSAGE_TO_FAMILY", "SELF_REFLECTION"},
        "DAILY",
    )
    if tool == "DIARY":
        entry_type = _infer_diary_entry_type(content, entry_type)

    return {
        "should_save": should_save,
        "tool": tool,
        "content": content,
        "title": str(data.get("title", "")).strip()[:24] or _default_save_title(tool),
        "summary": str(data.get("summary", content)).strip()[:80],
        "visibility": visibility,
        "entry_type": entry_type,
        "memory_type": memory_type,
        "scope": scope,
        "category": category,
        "severity": _bounded_int(data.get("severity"), 1, 5, _default_growth_severity(content)),
        "importance": _bounded_int(data.get("importance"), 1, 5, 3),
        "tags": _compact_string_list(data.get("tags"), 6, 18),
        "reason": _save_plan_reason(data.get("reason"), tool),
        "confirmation_message": _default_save_confirmation(tool),
    }

def _normalize_save_content(value: object, *, max_chars: int = 1200) -> str:
    content = re.sub(r"\s+", " ", str(value or "").strip())
    if len(content) <= 500:
        return content

    sentences = _split_sentences(content)
    if not sentences:
        return content[:max_chars].strip()

    selected: list[str] = []
    budget = max(240, min(max_chars, 420))
    for sentence in sentences:
        if not _sentence_has_save_value(sentence):
            continue
        if sum(len(item) for item in selected) + len(sentence) > budget:
            continue
        selected.append(sentence)
        if len(selected) >= 5:
            break

    if not selected:
        selected = sentences[:3]

    summary = "；".join(item.strip("，。；; ") for item in selected if item.strip())
    if len(summary) > max_chars:
        summary = summary[: max_chars - 1].rstrip("，。；; ") + "…"
    return summary.strip()

def _split_sentences(text: str) -> list[str]:
    parts = re.split(r"(?<=[。！？!?；;])|\n+", text)
    return [part.strip() for part in parts if part and part.strip()]

def _blocked_save_tool_plan(reason: str) -> dict:
    confirmation_message = (
        "这条内容缺少可保存的具体事实，我不会沉淀为家族记忆。"
        if "缺乏" in reason or "没有可沉淀" in reason
        else "这条内容像是在要求越权或泄露内部规则，我不会保存为家族记忆。"
    )
    return {
        "should_save": False,
        "tool": "NONE",
        "content": "",
        "title": "无需保存",
        "summary": "",
        "visibility": "PRIVATE",
        "entry_type": "DAILY",
        "memory_type": "ELDER_ADVICE",
        "scope": "PRIVATE",
        "category": "OTHER",
        "severity": 1,
        "importance": 1,
        "tags": [],
        "reason": reason,
        "confirmation_message": confirmation_message,
    }

def _unavailable_save_tool_plan() -> dict:
    return {
        "should_save": False,
        "tool": "NONE",
        "content": "",
        "title": "暂未保存",
        "summary": "",
        "visibility": "PRIVATE",
        "entry_type": "DAILY",
        "memory_type": "ELDER_ADVICE",
        "scope": "PRIVATE",
        "category": "OTHER",
        "severity": 1,
        "importance": 1,
        "tags": [],
        "reason": "保存规划暂时不可用，已跳过自动保存。",
        "confirmation_message": "这次没有自动保存，你可以稍后手动重试。",
    }

def _infer_diary_entry_type(content: str, fallback: str) -> str:
    if _has_private_emotion_signal(content):
        return "EMOTION"
    if _has_substantive_insight_signal(content):
        return "SELF_REFLECTION"
    return fallback

def _normalize_save_visibility(tool: str, visibility: str, content: str) -> str:
    if tool == "GROWTH_GUARD":
        return "CARE_VISIBLE"
    if tool == "FAMILY_MEMORY":
        if re.search(r"(冲突|吵架|失望|隐私|生病|诊断|学校|班级|孩子|儿子|女儿|未成年|情绪)", content):
            return "CARE_VISIBLE"
        return "FAMILY_VISIBLE"
    if tool == "DIARY" and (
        _has_private_emotion_signal(content)
        or re.search(r"(别让|不要让|不想让|只给我|私密|隐私|不能公开|别公开|别告诉)", content)
    ):
        return "PRIVATE"
    if tool == "DIARY" and re.search(r"(给家人|希望家人|全家|家里人都|大家)", content):
        return "FAMILY_VISIBLE"
    return visibility

def _normalize_save_scope(tool: str, scope: str, visibility: str) -> str:
    if tool == "GROWTH_GUARD":
        return "CARE_VISIBLE"
    if tool == "FAMILY_MEMORY":
        return "FAMILY_VISIBLE" if visibility == "FAMILY_VISIBLE" else "CARE_VISIBLE"
    if visibility == "PRIVATE":
        return "PRIVATE"
    if visibility in {"CARE_VISIBLE", "FAMILY_VISIBLE"}:
        return visibility
    return scope

def _infer_growth_category(content: str, fallback: str) -> str:
    category_patterns = [
        ("DENTAL", r"(牙|刷牙|龋齿|换牙|牙科|甜食|饮料)"),
        ("VISION", r"(视力|近视|眼睛|揉眼|看书|屏幕|用眼|户外)"),
        ("POSTURE", r"(体态|坐姿|驼背|含胸|耸肩|肩膀|前倾)"),
        ("SLEEP", r"(睡眠|入睡|熬夜|作息|早起|睡前)"),
        ("EXERCISE", r"(运动|户外|跑步|耐力|活动量)"),
        ("SCREEN_TIME", r"(屏幕|手机|平板|电子设备|游戏)"),
        ("EMOTION", r"(情绪|烦躁|压力|哭|沉默|表达意愿)"),
        ("COMMUNICATION", r"(沟通|反驳|争吵|说教|亲子|提醒时)"),
    ]
    for category, pattern in category_patterns:
        if re.search(pattern, content):
            return category
    return fallback

def _infer_family_memory_type(content: str, fallback: str) -> str:
    if re.search(r"(牙|视力|体态|睡眠|运动|健康|生病|就医)", content):
        return "HEALTH_REMINDER"
    if re.search(r"(踩坑|风险|不要|别只|跟风|不适合|教训|后悔|如果重来)", content):
        return "GROWTH_RISK"
    if re.search(r"(规矩|原则|价值观|家风)", content):
        return "VALUE"
    if re.search(r"(故事|以前|年轻时|当年)", content):
        return "FAMILY_STORY"
    return fallback

def _default_growth_severity(content: str) -> int:
    if re.search(r"(连续|明显|严重|尽快|疼|看不清|长期)", content):
        return 4
    if re.search(r"(担心|留意|观察|最近)", content):
        return 3
    return 2

def _save_plan_reason(value: object, tool: str) -> str:
    reason = str(value or "").strip()[:120]
    if reason:
        return reason
    return {
        "DIARY": "这段话包含具体经历或个人感受，适合作为每日记录保存。",
        "FAMILY_MEMORY": "这段话包含可复用的经验、家族故事或长辈提醒，适合沉淀为经验沉淀。",
        "GROWTH_GUARD": "这段话包含需要后续留意的成长观察信号，适合保存为成长观察。",
    }.get(tool, "内容不足或缺少长期保存价值。")

def _choice(value: object, allowed: set[str], fallback: str) -> str:
    text = str(value or "").strip().upper()
    return text if text in allowed else fallback

def _bounded_int(value: object, minimum: int, maximum: int, fallback: int) -> int:
    try:
        number = int(value)
    except (TypeError, ValueError):
        number = fallback
    return max(minimum, min(maximum, number))

def _default_save_title(tool: str) -> str:
    return {
        "DIARY": "对话保存的每日记录",
        "FAMILY_MEMORY": "对话沉淀的经验",
        "GROWTH_GUARD": "对话记录的成长观察",
    }.get(tool, "无需保存")

def _default_save_confirmation(tool: str) -> str:
    return {
        "DIARY": "建议保存为每日记录，等待后端执行结果。",
        "FAMILY_MEMORY": "建议保存为经验沉淀，等待后端执行结果。",
        "GROWTH_GUARD": "建议保存为成长观察，等待后端执行结果。",
    }.get(tool, "这条消息不需要保存。")

def _compact_string_list(value: object, limit: int, max_len: int) -> list[str]:
    if not isinstance(value, list):
        return []
    result: list[str] = []
    for item in value:
        text = str(item).strip()
        if text:
            result.append(text[:max_len])
        if len(result) >= limit:
            break
    return result
