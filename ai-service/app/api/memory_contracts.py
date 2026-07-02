"""LLM response schemas for the family memory API."""

VISIBILITY_VALUES = ["PRIVATE", "FAMILY_VISIBLE", "CARE_VISIBLE", "LEGACY_VISIBLE"]
DIARY_ENTRY_TYPES = ["DAILY", "IMPORTANT_EVENT", "LESSON", "EMOTION", "MESSAGE_TO_FAMILY", "SELF_REFLECTION"]
MEMORY_TYPES = ["FAMILY_STORY", "ELDER_ADVICE", "HEALTH_REMINDER", "GROWTH_RISK", "VALUE", "PLAN"]
MEMORY_SCOPES = ["PRIVATE", "CARE_VISIBLE", "FAMILY_VISIBLE", "PARENT_VISIBLE"]
GROWTH_CATEGORIES = [
    "POSTURE",
    "DENTAL",
    "VISION",
    "SLEEP",
    "EXERCISE",
    "SCREEN_TIME",
    "EMOTION",
    "COMMUNICATION",
    "OTHER",
]


def _string_schema(max_length: int, min_length: int = 0) -> dict:
    schema = {"type": "string", "maxLength": max_length}
    if min_length > 0:
        schema["minLength"] = min_length
    return schema


def _string_array_schema(max_items: int, item_max_length: int) -> dict:
    return {
        "type": "array",
        "maxItems": max_items,
        "items": _string_schema(item_max_length),
    }


SAVE_TOOL_PLAN_SCHEMA = {
    "type": "json_schema",
    "json_schema": {
        "name": "agent_save_tool_plan",
        "strict": True,
        "schema": {
            "type": "object",
            "additionalProperties": False,
            "properties": {
                "should_save": {"type": "boolean"},
                "tool": {"type": "string", "enum": ["NONE", "DIARY", "FAMILY_MEMORY", "GROWTH_GUARD"]},
                "content": _string_schema(1200),
                "title": _string_schema(48),
                "summary": _string_schema(160),
                "visibility": {"type": "string", "enum": VISIBILITY_VALUES},
                "entry_type": {"type": "string", "enum": DIARY_ENTRY_TYPES},
                "memory_type": {"type": "string", "enum": MEMORY_TYPES},
                "scope": {"type": "string", "enum": MEMORY_SCOPES},
                "category": {"type": "string", "enum": GROWTH_CATEGORIES},
                "severity": {"type": "integer", "minimum": 1, "maximum": 5},
                "importance": {"type": "integer", "minimum": 1, "maximum": 5},
                "tags": _string_array_schema(6, 18),
                "reason": _string_schema(160),
                "confirmation_message": _string_schema(160),
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
        "strict": True,
        "schema": {
            "type": "object",
            "additionalProperties": False,
            "properties": {
                "title": _string_schema(48, 1),
                "content": _string_schema(3000, 1),
                "tags": _string_array_schema(8, 18),
                "diary_entry_type": {"type": "string", "enum": DIARY_ENTRY_TYPES},
                "diary_visibility": {"type": "string", "enum": VISIBILITY_VALUES},
                "memory_type": {"type": "string", "enum": MEMORY_TYPES},
                "memory_scope": {"type": "string", "enum": MEMORY_SCOPES},
                "growth_category": {"type": "string", "enum": GROWTH_CATEGORIES},
                "growth_severity": {"type": "integer", "minimum": 1, "maximum": 5},
                "scenario": _string_schema(48),
                "reason": _string_schema(160),
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


PERSONA_MATERIAL_DRAFT_SCHEMA = {
    "type": "json_schema",
    "json_schema": {
        "name": "persona_material_draft",
        "strict": True,
        "schema": {
            "type": "object",
            "additionalProperties": False,
            "properties": {
                "profile": {
                    "type": "object",
                    "additionalProperties": False,
                    "properties": {
                        "name": _string_schema(100),
                        "description": _string_schema(500),
                        "era_identity": _string_schema(200),
                        "values": _string_schema(1000),
                        "speaking_style": _string_schema(1000),
                        "personality": _string_schema(1000),
                    },
                    "required": [
                        "name",
                        "description",
                        "era_identity",
                        "values",
                        "speaking_style",
                        "personality",
                    ],
                },
                "materials": {
                    "type": "array",
                    "maxItems": 5,
                    "items": {
                        "type": "object",
                        "additionalProperties": False,
                        "properties": {
                            "title": _string_schema(48),
                            "content": _string_schema(600, 1),
                            "tags": _string_array_schema(6, 24),
                        },
                        "required": ["title", "content", "tags"],
                    },
                },
                "reason": _string_schema(160),
            },
            "required": ["profile", "materials", "reason"],
        },
    },
}


WEEKLY_REPORT_SCHEMA = {
    "type": "json_schema",
    "json_schema": {
        "name": "growth_guard_weekly_report",
        "strict": True,
        "schema": {
            "type": "object",
            "additionalProperties": False,
            "properties": {
                "title": _string_schema(60, 1),
                "summary": _string_schema(800, 1),
                "affirmations": _string_array_schema(8, 240),
                "concerns": _string_array_schema(8, 240),
                "signals": _string_array_schema(12, 240),
                "uncertainty_notes": _string_array_schema(8, 240),
                "family_experience_refs": _string_array_schema(8, 240),
                "suggested_actions": _string_array_schema(8, 240),
                "follow_up_questions": _string_array_schema(8, 240),
                "safety_note": _string_schema(500),
            },
            "required": [
                "title", "summary", "affirmations", "concerns", "signals",
                "uncertainty_notes", "family_experience_refs", "suggested_actions",
                "follow_up_questions", "safety_note",
            ],
        },
    },
}
