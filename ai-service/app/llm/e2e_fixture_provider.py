"""Deterministic completion fixture for browser E2E tests only."""

import json
import re
from dataclasses import dataclass


E2E_FIXTURE_MODEL_PREFIX = "e2e-fixture/"
_SAVE_PLAN_SCHEMA_NAME = "agent_save_tool_plan"
_SELECTED_CONTENT_PATTERN = re.compile(
    r"<selected_content>\s*(.*?)\s*</selected_content>",
    re.DOTALL,
)


@dataclass(frozen=True)
class FixtureMessage:
    content: str


@dataclass(frozen=True)
class FixtureChoice:
    message: FixtureMessage


@dataclass(frozen=True)
class FixtureCompletionResponse:
    choices: list[FixtureChoice]


def is_e2e_fixture_model(model: str) -> bool:
    return model.strip().lower().startswith(E2E_FIXTURE_MODEL_PREFIX)


def build_e2e_fixture_completion(
    *,
    messages: object,
    response_format: object,
) -> FixtureCompletionResponse:
    """Return the one structured response required by the browser save flow."""
    schema_name = _response_schema_name(response_format)
    if schema_name != _SAVE_PLAN_SCHEMA_NAME:
        raise ValueError(f"Unsupported E2E fixture schema: {schema_name or 'none'}")

    content = _selected_content(messages) or "E2E editable memory draft"
    payload = {
        "should_save": True,
        "tool": "DIARY",
        "content": content[:1200],
        "title": "E2E editable draft",
        "summary": content[:160],
        "visibility": "PRIVATE",
        "entry_type": "DAILY",
        "memory_type": "NOTE",
        "personal_memory_type": "NOTE",
        "scope": "PRIVATE",
        "category": "OTHER",
        "severity": 1,
        "importance": 1,
        "tags": ["e2e"],
        "reason": "Deterministic browser test fixture.",
        "confirmation_message": "Draft ready for editing and confirmation.",
    }
    return FixtureCompletionResponse([
        FixtureChoice(FixtureMessage(json.dumps(payload, ensure_ascii=False)))
    ])


def _response_schema_name(response_format: object) -> str:
    if not isinstance(response_format, dict):
        return ""
    json_schema = response_format.get("json_schema")
    if not isinstance(json_schema, dict):
        return ""
    name = json_schema.get("name")
    return name.strip() if isinstance(name, str) else ""


def _selected_content(messages: object) -> str:
    if not isinstance(messages, list):
        return ""
    for message in reversed(messages):
        if not isinstance(message, dict):
            continue
        value = message.get("content")
        if not isinstance(value, str):
            continue
        match = _SELECTED_CONTENT_PATTERN.search(value)
        if match:
            return match.group(1).strip()
    return ""
