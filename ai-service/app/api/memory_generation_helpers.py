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

def _sanitize_heritage_classical_draft(data: dict, source_content: str) -> dict:
    title = str(data.get("title", "家训古文稿")).strip()[:18] or "家训古文稿"
    classical_text = _clean_heritage_form_traces(str(data.get("classical_text", "")).strip())
    plain_summary = str(data.get("plain_summary", "")).strip()[:120]
    style_note = str(data.get("style_note", "")).strip()[:80]

    if not classical_text or _looks_like_prompt_injection(classical_text) or _looks_garbled(classical_text):
        classical_text = _local_heritage_classical_draft(source_content)
    if not plain_summary or _looks_garbled(plain_summary):
        plain_summary = "这版古文稿保留了原经验中的提醒、分寸与做法，适合先在家族内部传看，再按需要继续润色。"
    if not style_note or _looks_garbled(style_note):
        style_note = "采用简洁家训体，适合放在家族经验沉淀或传承卡片中。"

    return {
        "title": title,
        "classical_text": classical_text[:260],
        "plain_summary": plain_summary,
        "style_note": style_note,
    }

def _local_heritage_classical_draft(source_content: str) -> str:
    cleaned = re.sub(r"\s+", " ", str(source_content or "").strip())
    cleaned = cleaned.replace("：", "，").replace(";", "，")
    if len(cleaned) > 120:
        cleaned = cleaned[:120].rstrip("，。；; ") + "。"
    if not cleaned.endswith(("。", "！", "？")):
        cleaned = cleaned + "。"
    return f"家人处世，当念前事之得失；{cleaned}知所守，亦知所戒，则后人有所取法。"

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

def _sanitize_compressed_diary(data: dict, max_chars: int, current_content: str, incoming_content: str) -> dict:
    fallback = _local_compress_diary(current_content, incoming_content, max_chars)
    content = str(data.get("content", "")).strip()
    if not content or _looks_like_prompt_injection(content):
        content = fallback
    content = content[:max_chars].strip() or fallback
    summary = str(data.get("summary", "")).strip()[:80] or content[:80]
    return {
        "content": content,
        "summary": summary,
    }

def _local_compress_diary(current_content: str, incoming_content: str, max_chars: int) -> str:
    parts = []
    for value in [current_content, incoming_content]:
        cleaned = re.sub(r"\s+", " ", str(value or "").strip())
        if cleaned:
            parts.append(cleaned)
    merged = "；".join(parts)
    if len(merged) <= max_chars:
        return merged
    if max_chars <= 1:
        return merged[:max_chars]
    return merged[: max_chars - 1].rstrip("，,；;。 ") + "…"

def _compact_diaries(diaries: list[dict]) -> list[dict]:
    result: list[dict] = []
    for item in diaries[-20:]:
        structured = item.get("structured") if isinstance(item.get("structured"), dict) else {}
        result.append({
            "title": str(structured.get("title") or structured.get("summary") or "")[:60],
            "entry_type": str(structured.get("entryType") or structured.get("entry_type") or "")[:30],
            "content": str(item.get("rawText") or item.get("content") or "")[:360],
            "tags": _compact_string_list(item.get("tags"), 6, 18),
            "visibility": str(item.get("visibility") or item.get("privacyLevel") or "")[:30],
            "created_at": str(item.get("createdAt") or item.get("created_at") or "")[:30],
        })
    return result

def _compact_family_memories(memories: list[dict]) -> list[dict]:
    result: list[dict] = []
    for item in memories[-16:]:
        metadata = item.get("metadata") if isinstance(item.get("metadata"), dict) else {}
        card = metadata.get("memoryCard") if isinstance(metadata.get("memoryCard"), dict) else {}
        result.append({
            "type": str(item.get("type", ""))[:30],
            "summary": str(card.get("summary") or item.get("summary") or item.get("content") or "")[:260],
            "actions": _compact_string_list(card.get("action_suggestions"), 4, 80),
            "suitable_for": _compact_string_list(card.get("suitable_for"), 4, 20),
            "scope": str(item.get("scope", ""))[:30],
            "created_at": str(item.get("createdAt") or item.get("created_at") or "")[:30],
        })
    return result

def _compact_growth_records(records: list[dict]) -> list[dict]:
    result: list[dict] = []
    for item in records[-20:]:
        metadata = item.get("metadata") if isinstance(item.get("metadata"), dict) else {}
        result.append({
            "category": str(item.get("category", ""))[:30],
            "content": str(item.get("content", ""))[:300],
            "severity": _bounded_int(item.get("severity"), 1, 5, 3),
            "follow_up_status": str(metadata.get("followUpStatus") or metadata.get("follow_up_status") or "")[:30],
            "observed_at": str(item.get("observedAt") or item.get("observed_at") or "")[:30],
        })
    return result

def _sanitize_family_weekly_digest(data: dict) -> dict:
    return {
        "title": str(data.get("title", "家族记忆摘要")).strip()[:30] or "家族记忆摘要",
        "summary": str(data.get("summary", "")).strip()[:180],
        "memory_highlights": _compact_string_list(data.get("memory_highlights"), 4, 120)
        or ["本周记录还不多，可以先补充一条重要经历。"],
        "family_experience_refs": _compact_string_list(data.get("family_experience_refs"), 3, 120),
        "growth_signals": _compact_string_list(data.get("growth_signals"), 4, 120),
        "suggested_actions": _compact_string_list(data.get("suggested_actions"), 4, 120)
        or ["本周邀请一位家人补充一个细节，让记录从一个人变成全家参与。"],
        "questions_for_family": _compact_string_list(data.get("questions_for_family"), 3, 120)
        or ["这周哪件小事最值得以后回头看？"],
        "missing_records": _compact_string_list(data.get("missing_records"), 3, 120),
        "safety_note": str(
            data.get("safety_note", "这份摘要只基于已授权可见记录生成，不构成医疗、心理或法律建议。")
        ).strip()[:140],
    }

def _sanitize_heritage_task_draft(data: dict, source_content: str, existing_actions: list[str]) -> dict:
    title = str(data.get("title", "家庭小实践")).strip()[:24] or "家庭小实践"
    action = str(data.get("action", "")).strip()[:120]
    completion_prompt = str(
        data.get("completion_prompt", "这次一起做了什么？谁有什么反应？你学到了什么？")
    ).strip()[:100]
    reason = str(data.get("reason", "把经验转成一次低压力的共同经历。")).strip()[:100]
    if (
        not action
        or _looks_garbled(title)
        or _looks_garbled(action)
        or not _has_source_overlap(f"{title} {action} {completion_prompt}", source_content)
    ):
        fallback_action = existing_actions[0] if existing_actions else _fallback_task_action(source_content)
        title = _fallback_task_title(fallback_action, source_content)
        action = fallback_action[:120]
        completion_prompt = _fallback_completion_prompt(source_content)
        reason = "模型草案与原经验不够贴合，已按原经验中的具体动作生成低压力家庭任务。"

    return {
        "title": title,
        "action": action,
        "target_label": str(data.get("target_label", "全家")).strip()[:40] or "全家",
        "due_days": _bounded_int(data.get("due_days"), 1, 14, 7),
        "completion_prompt": completion_prompt,
        "reason": reason,
    }

def _has_source_overlap(candidate: str, source_content: str) -> bool:
    source = source_content.strip()
    if not source:
        return False
    concrete_terms = [
        "草图", "材料", "步骤", "成品", "兴趣", "项目", "科技", "画",
        "牙", "视力", "体态", "睡眠", "运动", "拉伸", "检查",
        "志愿", "专业", "考研", "选择", "沟通", "误会", "复盘",
    ]
    source_terms = [term for term in concrete_terms if term in source]
    if source_terms:
        return any(term in candidate for term in source_terms)
    source_chars = {char for char in source if "\u4e00" <= char <= "\u9fff"}
    candidate_chars = {char for char in candidate if "\u4e00" <= char <= "\u9fff"}
    return len(source_chars & candidate_chars) >= 6

def _looks_garbled(value: str) -> bool:
    text = value.strip()
    if not text:
        return True
    question_count = text.count("?") + text.count("�")
    return question_count >= 3 or (question_count > 0 and question_count * 2 >= len(text))

def _fallback_task_action(source_content: str) -> str:
    if any(term in source_content for term in ["草图", "材料", "成品", "兴趣", "项目"]):
        return "本周请孩子先把一个兴趣想法画成草图并列出材料，家人只帮他拆成小步骤，不急着代做成品。"
    if any(term in source_content for term in ["牙", "换牙", "正畸"]):
        return "本周确认一次孩子最近的牙齿检查时间，记录一个需要继续观察的问题，必要时咨询专业医生。"
    if any(term in source_content for term in ["视力", "用眼", "屏幕"]):
        return "本周一起记录一天用眼和屏幕时间，找出一个可以微调的习惯。"
    if any(term in source_content for term in ["体态", "运动", "拉伸", "含胸"]):
        return "本周一起完成一次轻松运动，并在结束后做五分钟肩背放松或拉伸，记录感受。"
    return "和一位家人一起实践这条经验中的一个小动作，并记录一个新的发现。"

def _fallback_task_title(action: str, source_content: str) -> str:
    if any(term in source_content for term in ["草图", "材料", "成品", "兴趣", "项目"]):
        return "把兴趣画成草图"
    if "牙" in source_content:
        return "确认牙齿检查"
    if "视力" in source_content:
        return "记录一次用眼习惯"
    if any(term in source_content for term in ["体态", "运动"]):
        return "一起运动后拉伸"
    return action[:18] or "家庭小实践"

def _fallback_completion_prompt(source_content: str) -> str:
    if any(term in source_content for term in ["草图", "材料", "成品", "兴趣", "项目"]):
        return "这次画了什么草图？家人只帮了哪一步？孩子有什么反应？"
    return "这次一起做了什么？谁有什么反应？你学到了什么？"
