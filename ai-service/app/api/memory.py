"""
Family memory organization API.
"""
from fastapi import APIRouter, Depends, HTTPException

from app.agents.family_skill_registry import family_skill_registry, get_family_skill
from app.api.memory_models import (
    OrganizeDraftRequest,
    PersonaMaterialDraftRequest,
    SaveToolPlanRequest,
)
from app.api.memory_generation_helpers import (  # noqa: F401
    _sanitize_organized_draft,
    _sanitize_persona_material_draft,
)
from app.api.memory_helpers import _choice, _sanitize_save_tool_plan  # noqa: F401
from app.llm.client import llm_client
from app.middleware.auth import require_internal_service, verify_token_or_internal_service
from app.runtime.draft_output_parser import OrganizeDraftOutputParser, PersonaMaterialOutputParser
from app.runtime.draft_prompt_renderer import OrganizeDraftPromptRenderer, PersonaMaterialPromptRenderer
from app.runtime.family_skill_runtime import (
    organize_draft_skill_runtime,
    persona_material_draft_skill_runtime,
    save_memory_skill_runtime,
)
from app.runtime.output_parser import SaveMemoryOutputParser
from app.runtime.prompt_renderer import SaveMemoryPromptRenderer
from app.use_cases.organize_draft import OrganizeDraftUseCase
from app.use_cases.persona_material_draft import PersonaMaterialDraftUseCase
from app.use_cases.save_memory_plan import SaveMemoryPlanUseCase
from app.utils.input_guard import InputGuardError  # noqa: F401
from app.utils.safety_limits import enforce_ai_concurrency, enforce_ai_rate_limit

router = APIRouter(dependencies=[
    Depends(verify_token_or_internal_service),
    Depends(enforce_ai_rate_limit),
    Depends(enforce_ai_concurrency),
])
save_memory_plan_use_case = SaveMemoryPlanUseCase(
    save_memory_skill_runtime,
    SaveMemoryPromptRenderer(),
    SaveMemoryOutputParser(),
)
organize_draft_use_case = OrganizeDraftUseCase(
    organize_draft_skill_runtime,
    OrganizeDraftPromptRenderer(),
    OrganizeDraftOutputParser(),
)
persona_material_draft_use_case = PersonaMaterialDraftUseCase(
    persona_material_draft_skill_runtime,
    PersonaMaterialPromptRenderer(),
    PersonaMaterialOutputParser(),
)


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


@router.post("/save-plan", dependencies=[Depends(require_internal_service)])
async def plan_agent_save_tool(request: SaveToolPlanRequest):
    return await save_memory_plan_use_case.execute(request, llm_client)


@router.post("/organize-draft", dependencies=[Depends(require_internal_service)])
async def organize_family_draft(request: OrganizeDraftRequest):
    if len(request.content.strip()) < 4:
        raise HTTPException(status_code=400, detail="内容太短，无法整理")
    return await organize_draft_use_case.execute(request, llm_client)


@router.post("/persona-material-draft", dependencies=[Depends(require_internal_service)])
async def organize_persona_material_draft(request: PersonaMaterialDraftRequest):
    if len(request.content.strip()) < 8:
        raise HTTPException(status_code=400, detail="材料太短，无法整理")
    return await persona_material_draft_use_case.execute(request, llm_client)
