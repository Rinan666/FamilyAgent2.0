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


WEEKLY_REPORT_SCHEMA = {
    "type": "json_schema",
    "json_schema": {
        "name": "growth_guard_weekly_report",
        "schema": {
            "type": "object",
            "properties": {
                "title": {"type": "string"},
                "summary": {"type": "string"},
                "affirmations": {"type": "array", "items": {"type": "string"}},
                "concerns": {"type": "array", "items": {"type": "string"}},
                "signals": {"type": "array", "items": {"type": "string"}},
                "uncertainty_notes": {"type": "array", "items": {"type": "string"}},
                "family_experience_refs": {"type": "array", "items": {"type": "string"}},
                "suggested_actions": {"type": "array", "items": {"type": "string"}},
                "follow_up_questions": {"type": "array", "items": {"type": "string"}},
                "safety_note": {"type": "string"},
            },
            "required": [
                "title", "summary", "affirmations", "concerns", "signals",
                "uncertainty_notes", "family_experience_refs", "suggested_actions",
                "follow_up_questions", "safety_note",
            ],
        },
    },
}
