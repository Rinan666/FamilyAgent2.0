"""Helpers for family memory drafting and draft sanitation."""

import re

from .memory_helpers import (
    _bounded_int,
    _choice,
    _compact_string_list,
)


def _sanitize_memory(item: dict) -> dict | None:
    content = str(item.get("content", "")).strip()
    if len(content) < 8:
        return None
    memory_type = str(item.get("type", "LEARNING")).strip().upper()
    if memory_type not in {"LEARNING", "MISTAKE", "PREFERENCE", "PLAN"}:
        memory_type = "LEARNING"
    try:
        importance = int(item.get("importance", 3))
    except (TypeError, ValueError):
        importance = 3
    try:
        confidence = float(item.get("confidence", 0.7))
    except (TypeError, ValueError):
        confidence = 0.7
    return {
        "type": memory_type,
        "content": content[:500],
        "summary": str(item.get("summary", content)).strip()[:200],
        "importance": max(1, min(5, importance)),
        "confidence": max(0.0, min(1.0, confidence)),
    }


def _clean_heritage_form_traces(content: str) -> str:
    lines: list[str] = []
    for raw_line in str(content or "").splitlines():
        line = raw_line.strip()
        if not line:
            continue
        if re.search(r"请把以上|请整理为|整理成|三句话经验原子", line):
            continue
        line = re.sub(r"^问题\d+[：:]\s*", "", line)
        line = re.sub(r"^回答[：:]\s*", "", line)
        line = re.sub(r"^(当时发生了什么|我当时怎么想的|如果重来我会怎么做)[：:]\s*", "", line)
        if line and line != "未填写":
            lines.append(line)
    return " ".join(lines).strip()


def _sanitize_organized_draft(data: dict, scene: str, fallback_content: str) -> dict:
    content = str(data.get("content", "")).strip()[:3000] or fallback_content[:3000]
    if scene == "HERITAGE":
        content = _clean_heritage_form_traces(content) or _clean_heritage_form_traces(fallback_content) or fallback_content[:3000]
    return {
        "title": str(data.get("title", "未命名记录")).strip()[:30] or "未命名记录",
        "content": content,
        "tags": _compact_string_list(data.get("tags"), 8, 18),
        "diary_entry_type": _choice(
            data.get("diary_entry_type"),
            {"DAILY", "IMPORTANT_EVENT", "LESSON", "EMOTION", "MESSAGE_TO_FAMILY", "SELF_REFLECTION"},
            "LESSON" if scene == "HERITAGE" else "DAILY",
        ),
        "diary_visibility": _choice(
            data.get("diary_visibility"),
            {"PRIVATE", "FAMILY_VISIBLE", "CARE_VISIBLE", "LEGACY_VISIBLE"},
            "CARE_VISIBLE" if scene == "GROWTH_GUARD" else "PRIVATE",
        ),
        "memory_type": _choice(
            data.get("memory_type"),
            {"FAMILY_STORY", "ELDER_ADVICE", "HEALTH_REMINDER", "GROWTH_RISK", "VALUE", "PLAN"},
            "ELDER_ADVICE" if scene == "HERITAGE" else "FAMILY_STORY",
        ),
        "memory_scope": _choice(
            data.get("memory_scope"),
            {"PRIVATE", "CARE_VISIBLE", "FAMILY_VISIBLE", "PARENT_VISIBLE"},
            "FAMILY_VISIBLE" if scene == "HERITAGE" else "CARE_VISIBLE",
        ),
        "growth_category": _choice(
            data.get("growth_category"),
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
        ),
        "growth_severity": _bounded_int(data.get("growth_severity"), 1, 5, 3),
        "scenario": str(data.get("scenario", "")).strip()[:30],
        "reason": str(data.get("reason", "")).strip()[:120],
    }
