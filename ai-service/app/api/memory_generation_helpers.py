"""Helpers for family memory drafting, compaction, and heritage sanitization."""

import re

from .memory_helpers import (
    _bounded_int,
    _choice,
    _compact_string_list,
    _looks_like_low_information_noise,
    _looks_like_prompt_injection,
    _looks_like_save_command_only,
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

def _sanitize_family_card(data: dict) -> dict:
    sensitivity = str(data.get("sensitivity", "LOW")).strip().upper()
    if sensitivity not in {"LOW", "MEDIUM", "HIGH"}:
        sensitivity = "LOW"
    return {
        "title": str(data.get("title", "经验沉淀")).strip()[:30] or "经验沉淀",
        "theme": str(data.get("theme", "长者建议")).strip()[:20] or "长者建议",
        "summary": str(data.get("summary", "")).strip()[:200],
        "motto": _sanitize_motto(data.get("motto"), data.get("summary")),
        "risk_points": _compact_string_list(data.get("risk_points"), 4, 80),
        "action_suggestions": _compact_string_list(data.get("action_suggestions"), 5, 100),
        "suitable_for": _compact_string_list(data.get("suitable_for"), 4, 20) or ["全家"],
        "sensitivity": sensitivity,
        "safety_note": str(
            data.get("safety_note", "这是一条家庭经验整理，不构成专业诊断。")
        ).strip()[:120],
    }

def _sanitize_motto(value: object, fallback_source: object = "") -> str:
    text = re.sub(r"\s+", "", str(value or "").strip())
    text = text.replace("。", "").replace("！", "").replace("？", "")
    if not text or _looks_garbled(text):
        source = str(fallback_source or "")
        if re.search(r"(牙|视力|体态|睡眠|运动|健康)", source):
            text = "小患早察，久安可期"
        elif re.search(r"(选择|决定|志愿|专业|工作|考研)", source):
            text = "大事慢决，远路慎行"
        elif re.search(r"(沟通|争吵|理解|亲子|家人)", source):
            text = "言有余地，心有回声"
        else:
            text = "事经一回，智留一寸"
    return text[:24]

def _heritage_missing_elements(content: str) -> list[str]:
    missing: list[str] = []
    if not re.search(r"(当时|那次|以前|小时候|年轻时|最近|今天|选择|决定|经历|发现|后悔|踩坑|观察|做法|家规|长辈|爷爷|奶奶|外公|外婆|爸爸|妈妈)", content):
        missing.append("具体经历")
    if not re.search(r"(教训|提醒|原则|方法|做法|如果|下次|以后|不要|应该|先|再|记住|值得|避坑|代价)", content):
        missing.append("可复用教训")
    if not re.search(r"(后辈|孩子|家人|年轻人|后来人|下一代|全家|家庭成员|遇到类似|类似情况)", content):
        missing.append("后辈可借鉴的做法")
    return missing or ["后辈学习价值"]

def _looks_like_low_value_heritage(content: str) -> bool:
    text = re.sub(r"\s+", "", str(content or ""))
    if len(text) < 12:
        return True
    if _looks_like_save_command_only(text):
        return True
    if _looks_like_low_information_noise(text):
        return True
    abstract_terms = re.findall(r"(成长|价值|未来|意义|家族|经验|传承|智慧|人生|积极|努力|温柔|深刻)", text)
    has_anchor = re.search(r"(当时|那次|以前|小时候|年轻时|最近|今天|爷爷|奶奶|外公|外婆|爸爸|妈妈|选择|决定|经历|发现|后悔|踩坑|观察|做法|家规)", text)
    if len(abstract_terms) >= 4 and not has_anchor:
        return True
    return len(_heritage_missing_elements(content)) >= 2

def _blocked_heritage_save_judge(reason: str, missing_elements: list[str]) -> dict:
    return {
        "should_save": False,
        "learning_value_score": 1,
        "descendant_value": "",
        "reason": reason,
        "suggested_revision": "请补充：这件事具体发生在什么场景、当时有什么判断或代价、后辈遇到类似情况可以怎么做。",
        "missing_elements": _compact_string_list(missing_elements, 5, 30),
        "sensitivity": "LOW",
    }

def _local_heritage_save_judge(content: str) -> dict:
    content = str(content or "").strip()
    missing = _heritage_missing_elements(content)
    if _looks_like_prompt_injection(content):
        return _blocked_heritage_save_judge("疑似提示词注入或越权指令，不适合保存为家族经验。", ["安全边界"])
    if _looks_like_low_value_heritage(content):
        return _blocked_heritage_save_judge("内容缺少具体经历、可复用教训或后辈可借鉴做法，暂不能保存为家族经验沉淀。", missing)
    score = 5 - min(2, len(missing))
    return {
        "should_save": score >= 3,
        "learning_value_score": score,
        "descendant_value": "后辈可以从这段经历中提炼可复用的提醒和行动方法。",
        "reason": "内容包含具体经历和可迁移提醒，具备保存为家族经验沉淀的基础。",
        "suggested_revision": "",
        "missing_elements": [],
        "sensitivity": "MEDIUM" if re.search(r"(健康|牙|视力|体态|睡眠|情绪|孩子)", content) else "LOW",
    }

def _sanitize_heritage_save_judge(data: dict, content: str) -> dict:
    local = _local_heritage_save_judge(content)
    missing = _compact_string_list(data.get("missing_elements"), 5, 30) or local["missing_elements"]
    score = _bounded_int(data.get("learning_value_score"), 1, 5, local["learning_value_score"])
    sensitivity = _choice(data.get("sensitivity"), {"LOW", "MEDIUM", "HIGH"}, local["sensitivity"])
    should_save = bool(data.get("should_save")) and score >= 3 and not _looks_like_low_value_heritage(content)
    reason = str(data.get("reason") or local["reason"]).strip()[:180]
    if not should_save and not missing:
        missing = _heritage_missing_elements(content)
    return {
        "should_save": should_save,
        "learning_value_score": score if should_save else min(score, 2),
        "descendant_value": str(data.get("descendant_value") or local["descendant_value"]).strip()[:160] if should_save else "",
        "reason": reason,
        "suggested_revision": str(data.get("suggested_revision") or local["suggested_revision"]).strip()[:500],
        "missing_elements": missing,
        "sensitivity": sensitivity,
    }

def _clean_heritage_form_traces(content: str) -> str:
    lines: list[str] = []
    for raw_line in str(content or "").splitlines():
        line = raw_line.strip()
        if not line:
            continue
        if re.search(r"请把以上|请整理|整理成|三句话经验原子", line):
            continue
        line = re.sub(r"^问题\d+[：:].*?[？?]\s*", "", line)
        line = re.sub(r"^回答[：:]\s*", "", line)
        line = re.sub(r"^(当时发生了什么|我当时怎么想|如果重来我会怎么做)[：:]\s*", "", line)
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

def _looks_garbled(value: str) -> bool:
    text = value.strip()
    if not text:
        return True
    question_count = text.count("?") + text.count("�")
    return question_count >= 3 or (question_count > 0 and question_count * 2 >= len(text))

