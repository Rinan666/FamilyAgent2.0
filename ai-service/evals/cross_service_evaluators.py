"""Evaluators for cross-service privacy, stream, draft, and embedding cases."""

from __future__ import annotations

import json
import math
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from fastapi import HTTPException

from app.api import agent, embedding
from app.api.memory_contracts import ORGANIZED_DRAFT_SCHEMA, PERSONA_MATERIAL_DRAFT_SCHEMA
from app.api.memory_generation_helpers import (
    _sanitize_organized_draft,
    _sanitize_persona_material_draft,
)
from app.api.memory_models import OrganizeDraftRequest, PersonaMaterialDraftRequest
from app.runtime.draft_output_parser import OrganizeDraftOutputParser, PersonaMaterialOutputParser
from app.runtime.draft_prompt_renderer import OrganizeDraftPromptRenderer, PersonaMaterialPromptRenderer
from app.runtime.family_skill_runtime import (
    organize_draft_skill_runtime,
    persona_material_draft_skill_runtime,
)
from app.use_cases.organize_draft import OrganizeDraftUseCase
from app.use_cases.persona_material_draft import PersonaMaterialDraftUseCase

from .mock_llm import MockLLMClient
from .models import (
    DraftRuntimeEvalCase,
    DraftSkillName,
    EmbeddingEvalCase,
    EvalDecision,
    Evaluation,
    GoldenCase,
    MemoryContextEvalCase,
    OrganizeDraftEvalCase,
    OrganizeSchemaEvalCase,
    PersonaDraftEvalCase,
    PersonaSchemaEvalCase,
    StreamEvalCase,
)


async def evaluate_cross_service_case(case: GoldenCase) -> Evaluation:
    if isinstance(case, MemoryContextEvalCase):
        return _evaluate_memory_context(case)
    if isinstance(case, StreamEvalCase):
        return await _evaluate_stream(case)
    if isinstance(case, EmbeddingEvalCase):
        return await _evaluate_embedding(case)
    if isinstance(case, OrganizeSchemaEvalCase):
        return _evaluate_organize_schema(case)
    if isinstance(case, OrganizeDraftEvalCase):
        return _evaluate_organize_draft(case)
    if isinstance(case, PersonaSchemaEvalCase):
        return _evaluate_persona_schema(case)
    if isinstance(case, PersonaDraftEvalCase):
        return _evaluate_persona_draft(case)
    if isinstance(case, DraftRuntimeEvalCase):
        return await _evaluate_draft_runtime(case)
    raise TypeError(f"Unsupported eval case: {type(case).__name__}")


def _evaluate_memory_context(case: MemoryContextEvalCase) -> Evaluation:
    request = SimpleNamespace(
        state=SimpleNamespace(internal_service=case.internal_service),
    )
    result = agent._trusted_memory_context(case.value, request)
    fragments_match = all(item in result for item in case.required_fragments)
    fragments_safe = all(item not in result for item in case.forbidden_fragments)

    if not case.internal_service and not result:
        actual = EvalDecision.BLOCK
    elif "本轮忽略" in result:
        actual = EvalDecision.BLOCK
    elif case.required_fragments or case.forbidden_fragments:
        actual = EvalDecision.SANITIZED if fragments_match and fragments_safe else EvalDecision.ALLOW
    else:
        actual = EvalDecision.ALLOW
    return Evaluation(actual == case.expected_decision, actual)


async def _evaluate_stream(case: StreamEvalCase) -> Evaluation:
    async def fake_chat_stream(**_kwargs):
        if case.provider_failure:
            raise RuntimeError("mock private provider detail")
        yield {"type": "content", "content": "ok"}

    request = SimpleNamespace(
        headers={"x-request-id": "eval-stream", "x-agent-run-id": "1"},
        state=SimpleNamespace(internal_service=True),
    )
    with patch.object(agent.family_agent, "chat_stream", fake_chat_stream):
        response = await agent.stream_chat(
            agent.AgentChatRequest(member_message="请分析孩子最近做应用题时总是漏看条件的原因。"),
            request,
        )
        chunks = []
        async for chunk in response.body_iterator:
            chunks.append(chunk.decode("utf-8") if isinstance(chunk, bytes) else chunk)

    body = "".join(chunks)
    events = _parse_sse_events(body)
    terminal = events[-1] if events else {}
    if terminal.get("type") == "error" and "mock private provider detail" not in body:
        actual = EvalDecision.STRUCTURED_FAILURE
        error_code = terminal.get("code")
    elif terminal.get("type") == "done" and terminal.get("done") is True:
        actual = EvalDecision.ALLOW
        error_code = None
    else:
        actual = EvalDecision.EVALUATOR_ERROR
        error_code = "INVALID_STREAM_TERMINAL_EVENT"
    passed = actual == case.expected_decision and error_code == case.expected_error_code
    return Evaluation(passed, actual, error_code)


def _parse_sse_events(body: str) -> list[dict]:
    events = []
    for block in body.split("\n\n"):
        if block.startswith("data: "):
            events.append(json.loads(block.removeprefix("data: ")))
    return events


async def _evaluate_embedding(case: EmbeddingEvalCase) -> Evaluation:
    if case.provider_failure:
        with patch.object(
            embedding.litellm,
            "aembedding",
            new=AsyncMock(side_effect=RuntimeError("mock provider unavailable")),
        ):
            try:
                await embedding._embed("evaluation input", case.model, case.dimensions)
            except HTTPException as error:
                error_code = embedding._embedding_error_code(error)
                actual = EvalDecision.STRUCTURED_FAILURE
            else:  # pragma: no cover - assertion guard
                error_code = None
                actual = EvalDecision.ALLOW
    else:
        vector = await embedding._embed("evaluation input", case.model, case.dimensions)
        valid = len(vector) == case.dimensions and all(math.isfinite(item) for item in vector)
        actual = EvalDecision.ALLOW if valid else EvalDecision.EVALUATOR_ERROR
        error_code = None if valid else "INVALID_EMBEDDING"

    passed = actual == case.expected_decision and error_code == case.expected_error_code
    return Evaluation(passed, actual, error_code)


def _evaluate_organize_schema(case: OrganizeSchemaEvalCase) -> Evaluation:
    schema = ORGANIZED_DRAFT_SCHEMA["json_schema"]["schema"]
    actual = EvalDecision.ALLOW if _is_strict_schema(schema) else EvalDecision.EVALUATOR_ERROR
    return Evaluation(actual == case.expected_decision, actual)


def _is_strict_schema(value: object) -> bool:
    if isinstance(value, dict):
        if value.get("type") == "object":
            properties = value.get("properties")
            required = value.get("required")
            if not isinstance(properties, dict) or value.get("additionalProperties") is not False:
                return False
            if set(required or []) != set(properties):
                return False
        return all(_is_strict_schema(item) for item in value.values())
    if isinstance(value, list):
        return all(_is_strict_schema(item) for item in value)
    return True


def _evaluate_organize_draft(case: OrganizeDraftEvalCase) -> Evaluation:
    sanitized = _sanitize_organized_draft(
        json.loads(case.raw_output),
        case.scene,
        case.fallback_content,
    )
    expected_values = (
        sanitized["title"] == case.expected_title,
        sanitized["diary_entry_type"] == case.expected_entry_type,
        sanitized["diary_visibility"] == case.expected_visibility,
        all(item not in sanitized["content"] for item in case.forbidden_fragments),
    )
    actual = EvalDecision.SANITIZED if all(expected_values) else EvalDecision.EVALUATOR_ERROR
    return Evaluation(actual == case.expected_decision, actual)


def _evaluate_persona_schema(case: PersonaSchemaEvalCase) -> Evaluation:
    schema = PERSONA_MATERIAL_DRAFT_SCHEMA["json_schema"]["schema"]
    actual = EvalDecision.ALLOW if _is_strict_schema(schema) else EvalDecision.EVALUATOR_ERROR
    return Evaluation(actual == case.expected_decision, actual)


def _evaluate_persona_draft(case: PersonaDraftEvalCase) -> Evaluation:
    sanitized = _sanitize_persona_material_draft(
        json.loads(case.raw_output),
        case.fallback_profile.as_dict(),
        case.fallback_content,
    )
    materials = sanitized["materials"]
    valid = all(
        (
            sanitized["profile"]["name"] == case.expected_name,
            len(sanitized["profile"]["description"]) <= 500,
            len(materials) == case.expected_material_count,
            all(len(item["content"]) <= 600 for item in materials),
            all(len(item["tags"]) <= 6 for item in materials),
        )
    )
    actual = EvalDecision.SANITIZED if valid else EvalDecision.EVALUATOR_ERROR
    return Evaluation(actual == case.expected_decision, actual)


async def _evaluate_draft_runtime(case: DraftRuntimeEvalCase) -> Evaluation:
    llm = MockLLMClient(case.mock_response, case.mock_provider_failure)
    if case.skill == DraftSkillName.ORGANIZE_DRAFT:
        use_case = OrganizeDraftUseCase(
            organize_draft_skill_runtime,
            OrganizeDraftPromptRenderer(),
            OrganizeDraftOutputParser(),
        )
        response = await use_case.execute(
            OrganizeDraftRequest(content="今天孩子愿意先复述题意，再开始列式。"),
            llm,
        )
    else:
        use_case = PersonaMaterialDraftUseCase(
            persona_material_draft_skill_runtime,
            PersonaMaterialPromptRenderer(),
            PersonaMaterialOutputParser(),
        )
        response = await use_case.execute(
            PersonaMaterialDraftRequest(
                content="外公年轻时经营小店，常提醒家里人账目要写清楚。",
            ),
            llm,
        )

    actual = (
        EvalDecision.STRUCTURED_FAILURE
        if response.get("success") is False and response.get("data") is None
        else EvalDecision.ALLOW
    )
    error_code = response.get("errorCode")
    passed = actual == case.expected_decision and error_code == case.expected_error_code
    return Evaluation(passed, actual, error_code)
