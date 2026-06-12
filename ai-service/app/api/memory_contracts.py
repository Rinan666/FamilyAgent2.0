"""LLM response schemas for the family memory API."""

FAMILY_CARD_SCHEMA = {
    "type": "json_schema",
    "json_schema": {
        "name": "family_memory_card",
        "schema": {
            "type": "object",
            "properties": {
                "title": {"type": "string"},
                "theme": {"type": "string"},
                "summary": {"type": "string"},
                "motto": {"type": "string"},
                "risk_points": {
                    "type": "array",
                    "items": {"type": "string"},
                },
                "action_suggestions": {
                    "type": "array",
                    "items": {"type": "string"},
                },
                "suitable_for": {
                    "type": "array",
                    "items": {"type": "string"},
                },
                "sensitivity": {"type": "string"},
                "safety_note": {"type": "string"},
            },
            "required": [
                "title",
                "theme",
                "summary",
                "motto",
                "risk_points",
                "action_suggestions",
                "suitable_for",
                "sensitivity",
                "safety_note",
            ],
        },
    },
}


HERITAGE_CLASSICAL_SCHEMA = {
    "type": "json_schema",
    "json_schema": {
        "name": "heritage_classical_draft",
        "schema": {
            "type": "object",
            "properties": {
                "title": {"type": "string"},
                "classical_text": {"type": "string"},
                "plain_summary": {"type": "string"},
                "style_note": {"type": "string"},
            },
            "required": ["title", "classical_text", "plain_summary", "style_note"],
        },
    },
}


SAVE_TOOL_PLAN_SCHEMA = {
    "type": "json_schema",
    "json_schema": {
        "name": "agent_save_tool_plan",
        "schema": {
            "type": "object",
            "properties": {
                "should_save": {"type": "boolean"},
                "tool": {"type": "string"},
                "content": {"type": "string"},
                "title": {"type": "string"},
                "summary": {"type": "string"},
                "visibility": {"type": "string"},
                "entry_type": {"type": "string"},
                "memory_type": {"type": "string"},
                "scope": {"type": "string"},
                "category": {"type": "string"},
                "severity": {"type": "integer"},
                "importance": {"type": "integer"},
                "tags": {"type": "array", "items": {"type": "string"}},
                "reason": {"type": "string"},
                "confirmation_message": {"type": "string"},
            },
            "required": [
                "should_save",
                "tool",
                "content",
                "title",
                "summary",
                "visibility",
                "entry_type",
                "memory_type",
                "scope",
                "category",
                "severity",
                "importance",
                "tags",
                "reason",
                "confirmation_message",
            ],
        },
    },
}


ORGANIZED_DRAFT_SCHEMA = {
    "type": "json_schema",
    "json_schema": {
        "name": "organized_family_draft",
        "schema": {
            "type": "object",
            "properties": {
                "title": {"type": "string"},
                "content": {"type": "string"},
                "tags": {"type": "array", "items": {"type": "string"}},
                "diary_entry_type": {"type": "string"},
                "diary_visibility": {"type": "string"},
                "memory_type": {"type": "string"},
                "memory_scope": {"type": "string"},
                "growth_category": {"type": "string"},
                "growth_severity": {"type": "integer"},
                "scenario": {"type": "string"},
                "reason": {"type": "string"},
            },
            "required": [
                "title",
                "content",
                "tags",
                "diary_entry_type",
                "diary_visibility",
                "memory_type",
                "memory_scope",
                "growth_category",
                "growth_severity",
                "scenario",
                "reason",
            ],
        },
    },
}


HERITAGE_SAVE_JUDGE_SCHEMA = {
    "type": "json_schema",
    "json_schema": {
        "name": "heritage_save_judge",
        "schema": {
            "type": "object",
            "properties": {
                "should_save": {"type": "boolean"},
                "learning_value_score": {"type": "integer"},
                "descendant_value": {"type": "string"},
                "reason": {"type": "string"},
                "suggested_revision": {"type": "string"},
                "missing_elements": {"type": "array", "items": {"type": "string"}},
                "sensitivity": {"type": "string"},
            },
            "required": [
                "should_save",
                "learning_value_score",
                "descendant_value",
                "reason",
                "suggested_revision",
                "missing_elements",
                "sensitivity",
            ],
        },
    },
}


COMPRESSED_DIARY_SCHEMA = {
    "type": "json_schema",
    "json_schema": {
        "name": "compressed_diary_entry",
        "schema": {
            "type": "object",
            "properties": {
                "content": {"type": "string"},
                "summary": {"type": "string"},
            },
            "required": ["content", "summary"],
        },
    },
}


FAMILY_WEEKLY_DIGEST_SCHEMA = {
    "type": "json_schema",
    "json_schema": {
        "name": "family_weekly_digest",
        "schema": {
            "type": "object",
            "properties": {
                "title": {"type": "string"},
                "summary": {"type": "string"},
                "memory_highlights": {
                    "type": "array",
                    "items": {"type": "string"},
                },
                "family_experience_refs": {
                    "type": "array",
                    "items": {"type": "string"},
                },
                "growth_signals": {
                    "type": "array",
                    "items": {"type": "string"},
                },
                "suggested_actions": {
                    "type": "array",
                    "items": {"type": "string"},
                },
                "questions_for_family": {
                    "type": "array",
                    "items": {"type": "string"},
                },
                "missing_records": {
                    "type": "array",
                    "items": {"type": "string"},
                },
                "safety_note": {"type": "string"},
            },
            "required": [
                "title",
                "summary",
                "memory_highlights",
                "family_experience_refs",
                "growth_signals",
                "suggested_actions",
                "questions_for_family",
                "missing_records",
                "safety_note",
            ],
        },
    },
}


HERITAGE_TASK_DRAFT_SCHEMA = {
    "type": "json_schema",
    "json_schema": {
        "name": "heritage_task_draft",
        "schema": {
            "type": "object",
            "properties": {
                "title": {"type": "string"},
                "action": {"type": "string"},
                "target_label": {"type": "string"},
                "due_days": {"type": "integer"},
                "completion_prompt": {"type": "string"},
                "reason": {"type": "string"},
            },
            "required": ["title", "action", "target_label", "due_days", "completion_prompt", "reason"],
        },
    },
}


SESSION_ARCHIVE_SUMMARY_SCHEMA = {
    "type": "json_schema",
    "json_schema": {
        "name": "session_archive_summary",
        "schema": {
            "type": "object",
            "properties": {
                "summary": {"type": "string"},
                "titleSuggestion": {"type": "string"},
                "focusTopics": {
                    "type": "array",
                    "items": {"type": "string"},
                },
                "confidence": {"type": "string"},
            },
            "required": ["summary", "titleSuggestion", "focusTopics", "confidence"],
        },
    },
}
