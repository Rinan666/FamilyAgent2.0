"""Pure helpers for family memory save-plan routing and shared heuristics."""

import re

from .memory_contracts import MEMORY_TYPES
from .memory_save_signals import (
    _looks_like_save_command_only,
)

def _sanitize_memory_save_plan(data: dict) -> dict:
    content = _normalize_save_content(data.get("content", ""))
    if _looks_like_save_command_only(content):
        return _blocked_memory_save_plan("只有保存指令，没有找到可保存的原始内容。")
    should_save = bool(content)
    memory_library = _required_choice(data.get("memory_library"), {"PERSONAL", "FAMILY"})
    memory_type = _required_choice(data.get("memory_type"), set(MEMORY_TYPES))
    visibility = _normalize_save_visibility(
        memory_library,
        _choice(
            data.get("visibility"),
            {
                "PRIVATE",
                "FAMILY_VISIBLE",
                "CARE_VISIBLE",
                "ALL_FAMILIES_VISIBLE",
                "SELECTED_FAMILIES_VISIBLE",
            },
            "PRIVATE",
        ),
        content,
    )

    return {
        "should_save": should_save,
        "memory_library": memory_library,
        "memory_type": memory_type,
        "content": content,
        "title": str(data.get("title", "")).strip()[:24] or _default_save_title(memory_library),
        "summary": str(data.get("summary", content)).strip()[:80],
        "visibility": visibility,
        "importance": _bounded_int(data.get("importance"), 1, 5, 3),
        "tags": _compact_string_list(data.get("tags"), 6, 18),
        "reason": _save_plan_reason(data.get("reason"), memory_library),
        "confirmation_message": _default_save_confirmation(memory_library),
    }

def _normalize_save_content(value: object, *, max_chars: int = 1200) -> str:
    content = re.sub(r"\s+", " ", str(value or "").strip())
    if len(content) <= max_chars:
        return content
    return content[: max_chars - 1].rstrip("，。；; ") + "…"

def _blocked_memory_save_plan(reason: str) -> dict:
    return {
        "should_save": False,
        "memory_library": "PERSONAL",
        "memory_type": "NOTE",
        "content": "",
        "title": "无需保存",
        "summary": "",
        "visibility": "PRIVATE",
        "importance": 1,
        "tags": [],
        "reason": reason,
        "confirmation_message": "没有找到可保存的内容，请补充内容后重试。",
    }

def _unavailable_memory_save_plan() -> dict:
    return {
        "should_save": False,
        "memory_library": "PERSONAL",
        "memory_type": "NOTE",
        "content": "",
        "title": "暂未保存",
        "summary": "",
        "visibility": "PRIVATE",
        "importance": 1,
        "tags": [],
        "reason": "保存规划暂时不可用，已跳过自动保存。",
        "confirmation_message": "这次没有自动保存，你可以稍后手动重试。",
    }

def _normalize_save_visibility(memory_library: str, visibility: str, content: str) -> str:
    if memory_library == "FAMILY":
        if re.search(r"(冲突|吵架|失望|隐私|生病|诊断|学校|班级|孩子|儿子|女儿|未成年|情绪)", content):
            return "CARE_VISIBLE"
        return visibility if visibility in {"PRIVATE", "CARE_VISIBLE", "FAMILY_VISIBLE"} else "FAMILY_VISIBLE"
    if visibility in {"ALL_FAMILIES_VISIBLE", "SELECTED_FAMILIES_VISIBLE", "CARE_VISIBLE"}:
        return visibility
    return "PRIVATE"

def _normalize_memory_type(
    value: object,
    fallback: str,
    *,
    allowed: set[str] | None = None,
) -> str:
    normalized = str(value or "").strip().upper()
    valid_types = allowed or set(MEMORY_TYPES)
    return normalized if normalized in valid_types else fallback

def _save_plan_reason(value: object, memory_library: str) -> str:
    reason = str(value or "").strip()[:120]
    if reason:
        return reason
    library_label = "个人记忆库" if memory_library == "PERSONAL" else "家庭记忆库"
    return f"用户明确要求保存，已整理为可编辑的{library_label}草稿。"

def _choice(value: object, allowed: set[str], fallback: str) -> str:
    text = str(value or "").strip().upper()
    return text if text in allowed else fallback


def _required_choice(value: object, allowed: set[str]) -> str:
    text = str(value or "").strip().upper()
    if text not in allowed:
        raise ValueError("Unsupported memory save contract value")
    return text

def _bounded_int(value: object, minimum: int, maximum: int, fallback: int) -> int:
    try:
        number = int(value)
    except (TypeError, ValueError):
        number = fallback
    return max(minimum, min(maximum, number))

def _default_save_title(memory_library: str) -> str:
    return "对话保存的个人记忆" if memory_library == "PERSONAL" else "对话保存的家庭记忆"

def _default_save_confirmation(memory_library: str) -> str:
    library_label = "个人记忆库" if memory_library == "PERSONAL" else "家庭记忆库"
    return f"{library_label}草稿已准备，请修改或确认后保存。"

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
