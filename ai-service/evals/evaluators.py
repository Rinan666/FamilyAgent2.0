"""Evaluators that bind golden cases to production safety boundaries."""

from __future__ import annotations

from app.api.memory_models import MemorySavePlanRequest
from app.runtime.family_skill_runtime import save_memory_skill_runtime
from app.runtime.output_parser import SaveMemoryOutputParser
from app.runtime.prompt_renderer import SaveMemoryPromptRenderer
from app.use_cases.save_memory_plan import SaveMemoryPlanUseCase
from app.utils.input_guard import inspect_input
from app.utils.safety_limits import (
    SafetyLimitError,
    looks_like_prompt_leak_attempt,
    looks_like_role_hijack_attempt,
    validate_messages,
)
from app.services.web_search import (
    is_private_web_search_query,
    needs_web_search,
    rewrite_public_search_query,
)

from .cross_service_evaluators import evaluate_cross_service_case
from .mock_llm import MockLLMClient
from .models import (
    EvalDecision,
    Evaluation,
    GoldenCase,
    MessageContractCase,
    RewriteEvalCase,
    SavePlanEvalCase,
    TextEvalCase,
    TextPolicy,
)


async def evaluate_case(case: GoldenCase) -> Evaluation:
    if isinstance(case, TextEvalCase):
        return _evaluate_text(case)
    if isinstance(case, MessageContractCase):
        return _evaluate_messages(case)
    if isinstance(case, RewriteEvalCase):
        return _evaluate_rewrite(case)
    if isinstance(case, SavePlanEvalCase):
        return await _evaluate_save_plan(case)
    return await evaluate_cross_service_case(case)


def _evaluate_text(case: TextEvalCase) -> Evaluation:
    if case.policy == TextPolicy.INPUT_GUARD:
        blocked = inspect_input(case.text).blocked
    elif case.policy == TextPolicy.PROMPT_LEAK:
        blocked = looks_like_prompt_leak_attempt(case.text)
    elif case.policy == TextPolicy.ROLE_HIJACK:
        blocked = looks_like_role_hijack_attempt(case.text)
    elif case.policy == TextPolicy.WEB_PRIVACY:
        blocked = is_private_web_search_query(case.text)
    elif case.policy == TextPolicy.WEB_TRIGGER:
        blocked = not needs_web_search(case.text) or is_private_web_search_query(case.text)
    else:  # pragma: no cover - enum exhaustiveness guard
        raise ValueError(f"Unsupported text policy: {case.policy}")

    actual = EvalDecision.BLOCK if blocked else EvalDecision.ALLOW
    return Evaluation(actual == case.expected_decision, actual)


def _evaluate_messages(case: MessageContractCase) -> Evaluation:
    try:
        validate_messages(list(case.messages))
        actual = EvalDecision.ALLOW
    except SafetyLimitError:
        actual = EvalDecision.BLOCK
    return Evaluation(actual == case.expected_decision, actual)


def _evaluate_rewrite(case: RewriteEvalCase) -> Evaluation:
    rewritten = rewrite_public_search_query(case.query)
    is_safe = all(fragment not in rewritten for fragment in case.forbidden_fragments)
    is_complete = all(fragment in rewritten for fragment in case.required_fragments)
    actual = EvalDecision.SANITIZED if is_safe and is_complete else EvalDecision.ALLOW
    return Evaluation(actual == case.expected_decision, actual)


async def _evaluate_save_plan(case: SavePlanEvalCase) -> Evaluation:
    llm = MockLLMClient(case.mock_response, case.mock_provider_failure)
    use_case = SaveMemoryPlanUseCase(
        save_memory_skill_runtime,
        SaveMemoryPromptRenderer(),
        SaveMemoryOutputParser(),
    )
    response = await use_case.execute(
        MemorySavePlanRequest(
            message=case.message,
            conversation_context=list(case.conversation_context),
        ),
        llm,
    )
    data = response.get("data") or {}
    success = response.get("success") is True
    should_save = data.get("should_save") is True
    actual = _save_decision(success, should_save)
    expected = case.expected
    passed = all(
        (
            actual == expected.decision,
            llm.call_count == expected.llm_calls,
            success == expected.success,
            should_save == expected.should_save,
            data.get("memory_library") == expected.memory_library,
            data.get("memory_type") == expected.memory_type,
            response.get("errorCode") == expected.error_code,
        )
    )
    return Evaluation(passed, actual, response.get("errorCode"))


def _save_decision(success: bool, should_save: bool) -> EvalDecision:
    if not success:
        return EvalDecision.STRUCTURED_FAILURE
    return EvalDecision.SAVE if should_save else EvalDecision.SKIP_SAVE
