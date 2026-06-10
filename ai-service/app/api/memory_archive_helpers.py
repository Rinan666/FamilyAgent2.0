"""Helpers for session transcript compaction and archive summaries."""

from app.utils.privacy_guard import redact_with_note

from .memory_helpers import _choice, _compact_string_list

def _compact_transcript(messages: list[dict]) -> str:
    lines: list[str] = []
    for message in messages[-20:]:
        role = str(message.get("role", ""))
        if role not in {"user", "assistant"}:
            continue
        content = str(message.get("content", "")).strip()
        if not content:
            continue
        lines.append(f"{role}: {content[:600]}")
    return "\n".join(lines)[-6000:]

def _compact_session_archive_messages(messages: list[dict]) -> list[dict]:
    result: list[dict] = []
    for item in messages[:60]:
        role = str(item.get("role") or "user").strip().lower()
        if role not in {"user", "assistant", "system"}:
            role = "user"
        content = redact_with_note(str(item.get("content") or ""), max_length=600).text.strip()
        if not content:
            continue
        result.append({
            "seq": item.get("seq"),
            "role": role,
            "content": content[:600],
            "created_at": str(item.get("created_at") or "")[:40],
        })
    return result

def _local_session_archive_summary(session_title: str, messages: list[dict]) -> dict:
    summary = ""
    for item in reversed(messages):
        content = str(item.get("content") or "").strip()
        if content:
            summary = content[:120]
            break
    if not summary:
        summary = "This archive chunk records a FamilyAgent conversation."

    title = str(session_title or "").strip()[:24]
    if not title:
        for item in messages:
            if str(item.get("role")) == "user":
                title = str(item.get("content") or "").strip()[:24]
                if title:
                    break
    if not title:
        title = "Family chat archive"

    focus_topics: list[str] = []
    for item in messages:
        content = str(item.get("content") or "")
        for keyword in ["family", "growth", "memory", "health", "communication", "choice", "陪伴", "家族"]:
            if keyword in content and keyword not in focus_topics:
                focus_topics.append(keyword)
            if len(focus_topics) >= 4:
                break
        if len(focus_topics) >= 4:
            break
    if not focus_topics:
        focus_topics = ["family_chat"]

    return {
        "summary": summary,
        "titleSuggestion": title,
        "focusTopics": focus_topics,
        "confidence": "MEDIUM",
    }

def _sanitize_session_archive_summary(data: dict, fallback: dict) -> dict:
    summary = str(data.get("summary") or fallback["summary"]).strip()[:120] or fallback["summary"]
    title = str(data.get("titleSuggestion") or data.get("title_suggestion") or fallback["titleSuggestion"]).strip()[:24]
    topics = _compact_string_list(data.get("focusTopics") or data.get("focus_topics"), 4, 16) or fallback["focusTopics"]
    confidence = _choice(data.get("confidence"), {"LOW", "MEDIUM", "HIGH"}, fallback["confidence"])
    return {
        "summary": summary,
        "titleSuggestion": title or fallback["titleSuggestion"],
        "focusTopics": topics,
        "confidence": confidence,
    }
