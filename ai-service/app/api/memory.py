"""
Family memory organization API.
"""
import json
import logging

from fastapi import APIRouter, Depends, HTTPException

from app.agents.family_skill_registry import family_skill_registry, get_family_skill
from app.api.memory_contracts import (
    ORGANIZED_DRAFT_SCHEMA,
    PERSONA_MATERIAL_DRAFT_SCHEMA,
    SAVE_TOOL_PLAN_SCHEMA,
)
from app.api.memory_models import (
    ExtractMemoryRequest,
    ExtractMemoryResponse,
    OrganizeDraftRequest,
    PersonaMaterialDraftRequest,
    SaveToolPlanRequest,
)
from app.api.memory_archive_helpers import (
    _compact_transcript,
)
from app.api.memory_generation_helpers import (
    _sanitize_organized_draft,
    _sanitize_persona_material_draft,
)
from app.api.memory_helpers import (
    _blocked_save_tool_plan,
    _choice,
    _sanitize_save_tool_plan,
    _should_skip_save_planning,
    _unavailable_save_tool_plan,
)
from app.llm.client import llm_client
from app.llm.prompts.memory import (
    ORGANIZE_DRAFT_SYSTEM_PROMPT,
    PERSONA_MATERIAL_DRAFT_SYSTEM_PROMPT,
    SAVE_TOOL_PLAN_SYSTEM_PROMPT,
    build_organize_draft_user_prompt,
    build_persona_material_draft_user_prompt,
    build_save_tool_plan_user_prompt,
)
from app.middleware.auth import verify_token
from app.utils.input_guard import InputGuardError, enforce_input_guard
from app.utils.privacy_guard import redact_with_note
from app.utils.safety_limits import enforce_ai_concurrency, enforce_ai_rate_limit

logger = logging.getLogger("familyagent.ai.api.memory")

router = APIRouter(dependencies=[
    Depends(verify_token),
    Depends(enforce_ai_rate_limit),
    Depends(enforce_ai_concurrency),
])


@router.get("/skills")
def list_family_skill_registry(status: str = ""):
    return {
        "success": True,
        "data": family_skill_registry(status or None),
    }


@router.get("/skills/{name}")
def get_family_skill_registry_item(name: str):
    skill = get_family_skill(name)
    if not skill:
        raise HTTPException(status_code=404, detail="Skill not found")
    return {
        "success": True,
        "data": skill,
    }


@router.post("/extract", response_model=ExtractMemoryResponse)
async def extract_memories(request: ExtractMemoryRequest):
    return ExtractMemoryResponse(
        success=True,
        deprecated=True,
        degraded=False,
        memories=[],
        message="学习记忆功能已下线；请使用家族记忆、每日记录或成长观察。",
        errorCode=None,
    )


@router.post("/save-plan")
async def plan_agent_save_tool(request: SaveToolPlanRequest):
    try:
        message = redact_with_note(request.message, max_length=3000).text
        enforce_input_guard(message)
        compact_context = _compact_transcript(request.conversation_context)
        if _should_skip_save_planning(message, compact_context):
            return {
                "success": True,
                "data": _blocked_save_tool_plan("当前消息缺乏具体经历、对象、行为变化或可跟进信号，第一道意图审查已拦截。"),
            }
        family_context = redact_with_note(request.family_context, max_length=1200).text
        conversation_context = redact_with_note(
            compact_context,
            max_length=5000,
        ).text

        user_prompt = build_save_tool_plan_user_prompt(
            family_context,
            request.target_member_name,
            request.viewer_role,
            conversation_context,
            message,
        )
        raw = await llm_client.chat(
            messages=[
                {"role": "system", "content": SAVE_TOOL_PLAN_SYSTEM_PROMPT},
                {"role": "user", "content": user_prompt},
            ],
            temperature=0.1,
            max_tokens=900,
            response_format=SAVE_TOOL_PLAN_SCHEMA,
        )
        data = json.loads(raw)
        return {"success": True, "data": _sanitize_save_tool_plan(data)}
    except InputGuardError:
        raise
    except Exception:
        logger.error("Save tool planning failed", exc_info=True)
        return {"success": True, "data": _unavailable_save_tool_plan()}


@router.post("/organize-draft")
async def organize_family_draft(request: OrganizeDraftRequest):
    try:
        content = request.content.strip()
        if len(content) < 4:
            raise HTTPException(status_code=400, detail="内容太短，无法整理")

        guarded_content = redact_with_note(content, max_length=4000).text
        guarded_family_context = redact_with_note(request.family_context, max_length=1200).text
        scene = _choice(request.scene, {"DIARY", "HERITAGE", "GROWTH_GUARD"}, "DIARY")

        user_prompt = build_organize_draft_user_prompt(
            scene,
            request.current_type,
            request.current_visibility,
            request.target,
            guarded_family_context,
            guarded_content,
        )
        raw = await llm_client.chat(
            messages=[
                {"role": "system", "content": ORGANIZE_DRAFT_SYSTEM_PROMPT},
                {"role": "user", "content": user_prompt},
            ],
            temperature=0.15,
            max_tokens=1200,
            response_format=ORGANIZED_DRAFT_SCHEMA,
        )
        data = json.loads(raw)
        return {"success": True, "data": _sanitize_organized_draft(data, scene, content)}
    except HTTPException:
        raise
    except Exception as e:
        logger.error("Draft organization failed: %s", e)
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/persona-material-draft")
async def organize_persona_material_draft(request: PersonaMaterialDraftRequest):
    try:
        content = request.content.strip()
        if len(content) < 8:
            raise HTTPException(status_code=400, detail="材料太短，无法整理")

        guarded_content = redact_with_note(content, max_length=6000).text
        guarded_family_context = redact_with_note(request.family_context, max_length=1200).text
        profile = request.profile.model_dump()
        user_prompt = build_persona_material_draft_user_prompt(
            profile,
            guarded_family_context,
            guarded_content,
        )
        raw = await llm_client.chat(
            messages=[
                {"role": "system", "content": PERSONA_MATERIAL_DRAFT_SYSTEM_PROMPT},
                {"role": "user", "content": user_prompt},
            ],
            temperature=0.12,
            max_tokens=1600,
            response_format=PERSONA_MATERIAL_DRAFT_SCHEMA,
        )
        data = json.loads(raw)
        return {"success": True, "data": _sanitize_persona_material_draft(data, profile, content)}
    except HTTPException:
        raise
    except Exception as e:
        logger.error("Persona material draft organization failed: %s", e)
        raise HTTPException(status_code=500, detail=str(e))
