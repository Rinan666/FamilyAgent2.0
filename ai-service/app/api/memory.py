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
)
from app.api.memory_models import (
    OrganizeDraftRequest,
    PersonaMaterialDraftRequest,
    SaveToolPlanRequest,
)
from app.api.memory_generation_helpers import (
    _sanitize_organized_draft,
    _sanitize_persona_material_draft,
)
from app.api.memory_helpers import _choice
from app.llm.client import llm_client
from app.llm.prompts.memory import (
    ORGANIZE_DRAFT_SYSTEM_PROMPT,
    PERSONA_MATERIAL_DRAFT_SYSTEM_PROMPT,
    build_organize_draft_user_prompt,
    build_persona_material_draft_user_prompt,
)
from app.middleware.auth import verify_token
from app.runtime.skill_executor import SkillExecutor
from app.use_cases.save_memory_plan import SaveMemoryPlanUseCase
from app.utils.privacy_guard import redact_with_note
from app.utils.safety_limits import enforce_ai_concurrency, enforce_ai_rate_limit

logger = logging.getLogger("familyagent.ai.api.memory")

router = APIRouter(dependencies=[
    Depends(verify_token),
    Depends(enforce_ai_rate_limit),
    Depends(enforce_ai_concurrency),
])
save_memory_plan_use_case = SaveMemoryPlanUseCase(SkillExecutor())


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


@router.post("/save-plan")
async def plan_agent_save_tool(request: SaveToolPlanRequest):
    return await save_memory_plan_use_case.execute(request, llm_client)


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
