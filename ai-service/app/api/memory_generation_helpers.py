"""Helpers for family memory drafting and draft sanitation."""

import re

from .memory_helpers import (
    _bounded_int,
    _choice,
    _compact_string_list,
    _normalize_memory_type,
)


def _sanitize_memory(item: dict) -> dict | None:
    content = str(item.get("content", "")).strip()
    if len(content) < 8:
        return None
    memory_type = _normalize_memory_type(item.get("type"), "NOTE")
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
        "memory_type": _normalize_memory_type(
            data.get("memory_type"),
            "EXPERIENCE" if scene == "HERITAGE" else "OBSERVATION" if scene == "GROWTH_GUARD" else "NOTE",
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
        "growth_severity": _bounded_int(data.get("growth_severity"), 1, 5, 1),
        "scenario": str(data.get("scenario", "")).strip()[:30],
        "reason": str(data.get("reason", "")).strip()[:120],
    }


def _sanitize_persona_material_draft(data: dict, fallback_profile: dict, fallback_content: str) -> dict:
    profile_data = data.get("profile") if isinstance(data.get("profile"), dict) else {}
    profile = {
        "name": str(profile_data.get("name") or fallback_profile.get("name") or "").strip()[:100],
        "description": str(profile_data.get("description") or fallback_profile.get("description") or "").strip()[:500],
        "era_identity": str(profile_data.get("era_identity") or fallback_profile.get("era_identity") or "").strip()[:200],
        "values": str(profile_data.get("values") or fallback_profile.get("values") or "").strip()[:1000],
        "speaking_style": str(profile_data.get("speaking_style") or fallback_profile.get("speaking_style") or "").strip()[:1000],
        "personality": str(profile_data.get("personality") or fallback_profile.get("personality") or "").strip()[:1000],
    }

    materials: list[dict] = []
    raw_materials = data.get("materials") if isinstance(data.get("materials"), list) else []
    for index, item in enumerate(raw_materials[:5], start=1):
        if not isinstance(item, dict):
            continue
        content = str(item.get("content", "")).strip()[:600]
        if len(content) < 8:
            continue
        title = str(item.get("title", "")).strip()[:40] or f"材料卡 {index}"
        materials.append({
            "title": title,
            "content": content,
            "tags": _compact_string_list(item.get("tags"), 6, 24),
        })

    return {
        "profile": profile,
        "materials": materials,
        "reason": str(data.get("reason", "")).strip()[:120]
        or ("未生成可保存材料卡，请补充材料后重新整理。" if not materials else ""),
    }
