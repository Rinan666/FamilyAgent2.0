"""Pure helpers for family memory save-plan routing and shared heuristics."""

import re

from .memory_contracts import MEMORY_TYPES, PERSONAL_MEMORY_TYPES
from .memory_save_signals import (
    _has_private_emotion_signal,
    _has_substantive_insight_signal,
    _looks_like_save_command_only,
)

LEGACY_MEMORY_TYPE_MAP = {
    "FAMILY_STORY": "EXPERIENCE",
    "ELDER_ADVICE": "KNOWLEDGE",
    "HEALTH_REMINDER": "PLAN",
    "GROWTH_RISK": "OBSERVATION",
    "VALUE": "INSIGHT",
    "LEARNING": "KNOWLEDGE",
    "MISTAKE": "INSIGHT",
}

def _sanitize_save_tool_plan(data: dict) -> dict:
    content = _normalize_save_content(data.get("content", ""))
    raw_tool = _choice(
        data.get("tool"),
        {"NONE", "DIARY", "PERSONAL_MEMORY", "FAMILY_MEMORY", "GROWTH_GUARD"},
        "NONE",
    )
    if _looks_like_save_command_only(content):
        return _blocked_save_tool_plan("只有保存指令，没有找到可保存的原始内容。")
    tool = raw_tool if raw_tool != "NONE" else "DIARY"
    should_save = bool(content)

    visibility = _normalize_save_visibility(
        tool,
        _choice(
            data.get("visibility"),
            {
                "PRIVATE",
                "FAMILY_VISIBLE",
                "CARE_VISIBLE",
                "LEGACY_VISIBLE",
                "ALL_FAMILIES_VISIBLE",
                "SELECTED_FAMILIES_VISIBLE",
            },
            "PRIVATE",
        ),
        content,
    )
    scope = _normalize_save_scope(
        tool,
        _choice(
            data.get("scope"),
            {
                "PRIVATE",
                "CARE_VISIBLE",
                "FAMILY_VISIBLE",
                "PARENT_VISIBLE",
                "ALL_FAMILIES_VISIBLE",
                "SELECTED_FAMILIES_VISIBLE",
            },
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
    memory_type = _normalize_memory_type(data.get("memory_type"), "NOTE")
    personal_memory_type = _normalize_memory_type(
        data.get("personal_memory_type"),
        "NOTE",
        allowed=set(PERSONAL_MEMORY_TYPES),
    )
    entry_type = _choice(
        data.get("entry_type"),
        {"DAILY", "IMPORTANT_EVENT", "LESSON", "EMOTION", "MESSAGE_TO_FAMILY", "SELF_REFLECTION"},
        "DAILY",
    )
    if tool == "DIARY":
        entry_type = _infer_diary_entry_type(content, entry_type)
        memory_type = _memory_type_for_diary_entry(entry_type)
    elif tool == "PERSONAL_MEMORY":
        memory_type = personal_memory_type
    elif tool == "FAMILY_MEMORY":
        memory_type = _infer_family_memory_type(content, memory_type)
    elif tool == "GROWTH_GUARD":
        memory_type = "OBSERVATION"

    return {
        "should_save": should_save,
        "tool": tool,
        "content": content,
        "title": str(data.get("title", "")).strip()[:24] or _default_save_title(tool),
        "summary": str(data.get("summary", content)).strip()[:80],
        "visibility": visibility,
        "entry_type": entry_type,
        "memory_type": memory_type,
        "personal_memory_type": personal_memory_type,
        "scope": scope,
        "category": category,
        "severity": _bounded_int(data.get("severity"), 1, 5, 1),
        "importance": _bounded_int(data.get("importance"), 1, 5, 3),
        "tags": _compact_string_list(data.get("tags"), 6, 18),
        "reason": _save_plan_reason(data.get("reason"), tool),
        "confirmation_message": _default_save_confirmation(tool),
    }

def _normalize_save_content(value: object, *, max_chars: int = 1200) -> str:
    content = re.sub(r"\s+", " ", str(value or "").strip())
    if len(content) <= max_chars:
        return content
    return content[: max_chars - 1].rstrip("，。；; ") + "…"

def _blocked_save_tool_plan(reason: str) -> dict:
    return {
        "should_save": False,
        "tool": "NONE",
        "content": "",
        "title": "无需保存",
        "summary": "",
        "visibility": "PRIVATE",
        "entry_type": "DAILY",
        "memory_type": "NOTE",
        "personal_memory_type": "NOTE",
        "scope": "PRIVATE",
        "category": "OTHER",
        "severity": 1,
        "importance": 1,
        "tags": [],
        "reason": reason,
        "confirmation_message": "没有找到可保存的内容，请补充内容后重试。",
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
        "memory_type": "NOTE",
        "personal_memory_type": "NOTE",
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
    if tool == "DIARY" and re.search(r"(给家人|希望家人|全家|家里人都|大家)", content):
        return "FAMILY_VISIBLE"
    if tool == "PERSONAL_MEMORY":
        if visibility in {
            "ALL_FAMILIES_VISIBLE",
            "SELECTED_FAMILIES_VISIBLE",
            "CARE_VISIBLE",
        }:
            return visibility
        return "PRIVATE"
    if tool == "DIARY":
        return "PRIVATE"
    return visibility

def _normalize_save_scope(tool: str, scope: str, visibility: str) -> str:
    if tool == "GROWTH_GUARD":
        return "CARE_VISIBLE"
    if tool == "FAMILY_MEMORY":
        return "FAMILY_VISIBLE" if visibility == "FAMILY_VISIBLE" else "CARE_VISIBLE"
    if tool == "PERSONAL_MEMORY":
        return visibility if visibility in {
            "PRIVATE",
            "ALL_FAMILIES_VISIBLE",
            "SELECTED_FAMILIES_VISIBLE",
            "CARE_VISIBLE",
        } else "PRIVATE"
    if visibility == "PRIVATE":
        return "PRIVATE"
    if visibility in {"CARE_VISIBLE", "FAMILY_VISIBLE"}:
        return visibility
    return scope

def _infer_family_memory_type(content: str, fallback: str) -> str:
    if re.search(r"(牙|视力|体态|睡眠|运动|健康|生病|就医)", content):
        return "PLAN"
    if re.search(r"(踩坑|风险|不要|别只|跟风|不适合|教训|后悔|如果重来)", content):
        return "INSIGHT"
    if re.search(r"(规矩|原则|价值观|家风)", content):
        return "INSIGHT"
    if re.search(r"(故事|以前|年轻时|当年)", content):
        return "EXPERIENCE"
    return fallback

def _normalize_memory_type(
    value: object,
    fallback: str,
    *,
    allowed: set[str] | None = None,
) -> str:
    normalized = str(value or "").strip().upper()
    normalized = LEGACY_MEMORY_TYPE_MAP.get(normalized, normalized)
    valid_types = allowed or set(MEMORY_TYPES)
    return normalized if normalized in valid_types else fallback

def _memory_type_for_diary_entry(entry_type: str) -> str:
    if entry_type == "LESSON":
        return "KNOWLEDGE"
    if entry_type in {"EMOTION", "SELF_REFLECTION"}:
        return "INSIGHT"
    return "NOTE"

def _save_plan_reason(value: object, tool: str) -> str:
    reason = str(value or "").strip()[:120]
    if reason:
        return reason
    return {
        "DIARY": "用户明确要求保存，已整理为可编辑的家庭记录草稿。",
        "PERSONAL_MEMORY": "用户明确要求保存，已整理为可编辑的个人记忆草稿。",
        "FAMILY_MEMORY": "用户明确要求保存，已整理为可编辑的家庭记忆草稿。",
        "GROWTH_GUARD": "用户明确要求保存，已整理为可编辑的家庭观察草稿。",
    }.get(tool, "没有找到可保存的内容。")

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
        "DIARY": "对话保存的家庭记录",
        "PERSONAL_MEMORY": "对话保存的个人记忆",
        "FAMILY_MEMORY": "对话沉淀的经验",
        "GROWTH_GUARD": "对话记录的家庭观察",
    }.get(tool, "无需保存")

def _default_save_confirmation(tool: str) -> str:
    return {
        "DIARY": "家庭记录草稿已准备，请修改或确认后保存。",
        "PERSONAL_MEMORY": "个人记忆草稿已准备，请修改或确认后保存。",
        "FAMILY_MEMORY": "家庭记忆草稿已准备，请修改或确认后保存。",
        "GROWTH_GUARD": "家庭观察草稿已准备，请修改或确认后保存。",
    }.get(tool, "没有找到可保存的内容。")

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
