"""Helpers for family memory drafting and draft sanitation."""

import re

from .memory_helpers import (
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


def _sanitize_organized_draft(
    data: dict,
    memory_library: str,
    current_memory_type: str,
    fallback_content: str,
) -> dict:
    content = _clean_draft_content(data.get("content"))[:3000] or fallback_content[:3000]
    memory_type = _normalize_memory_type(data.get("memory_type"), current_memory_type or "NOTE")
    visibility = _choice(
        data.get("visibility"),
        {
            "PRIVATE",
            "FAMILY_VISIBLE",
            "CARE_VISIBLE",
            "ALL_FAMILIES_VISIBLE",
            "SELECTED_FAMILIES_VISIBLE",
        },
        "PRIVATE" if memory_library == "PERSONAL" else "FAMILY_VISIBLE",
    )
    if memory_library == "PERSONAL" and visibility == "FAMILY_VISIBLE":
        visibility = "PRIVATE"
    if memory_library == "FAMILY" and visibility in {"ALL_FAMILIES_VISIBLE", "SELECTED_FAMILIES_VISIBLE"}:
        visibility = "FAMILY_VISIBLE"
    return {
        "title": str(data.get("title", "未命名记录")).strip()[:30] or "未命名记录",
        "content": content,
        "tags": _compact_string_list(data.get("tags"), 8, 18),
        "memory_type": memory_type,
        "visibility": visibility,
        "reason": str(data.get("reason", "")).strip()[:120],
    }


def _clean_draft_content(value: object) -> str:
    lines: list[str] = []
    for raw_line in str(value or "").splitlines():
        line = raw_line.strip()
        if not line or re.match(r"^请(?:整理|改写|总结|提炼)为", line):
            continue
        line = re.sub(r"^问题\d*\s*[：:]\s*", "", line)
        line = re.sub(r"^回答\s*[：:]\s*", "", line)
        line = re.sub(r"^(?:当时发生了什么|如果重来我会怎么做)\s*[：:]\s*", "", line)
        if line:
            lines.append(line)
    return "\n".join(lines)


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
