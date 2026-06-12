"""
Family memory organization API.
"""
import json
import logging

from fastapi import APIRouter, Depends, HTTPException

from app.agents.family_skill_registry import family_skill_registry, get_family_skill
from app.api.memory_contracts import (
    FAMILY_CARD_SCHEMA,
    HERITAGE_CLASSICAL_SCHEMA,
    SAVE_TOOL_PLAN_SCHEMA,
    ORGANIZED_DRAFT_SCHEMA,
    HERITAGE_SAVE_JUDGE_SCHEMA,
    COMPRESSED_DIARY_SCHEMA,
    FAMILY_WEEKLY_DIGEST_SCHEMA,
    HERITAGE_TASK_DRAFT_SCHEMA,
    SESSION_ARCHIVE_SUMMARY_SCHEMA,
)
from app.api.memory_models import (
    ExtractMemoryRequest,
    FamilyMemoryCardRequest,
    SaveToolPlanRequest,
    OrganizeDraftRequest,
    CompressDiaryRequest,
    FamilyWeeklyDigestRequest,
    HeritageTaskDraftRequest,
    HeritageSaveJudgeRequest,
    HeritageClassicalRequest,
    SessionArchiveSummaryRequest,
)
from app.api.memory_archive_helpers import (
    _compact_session_archive_messages,
    _compact_transcript,
    _local_session_archive_summary,
    _sanitize_session_archive_summary,
)
from app.api.memory_generation_helpers import (
    _blocked_heritage_save_judge,
    _compact_diaries,
    _compact_family_memories,
    _compact_growth_records,
    _heritage_missing_elements,
    _local_heritage_save_judge,
    _looks_like_low_value_heritage,
    _sanitize_compressed_diary,
    _sanitize_family_card,
    _sanitize_family_weekly_digest,
    _sanitize_heritage_classical_draft,
    _sanitize_heritage_save_judge,
    _sanitize_heritage_task_draft,
    _sanitize_organized_draft,
)
from app.api.memory_helpers import (
    _blocked_save_tool_plan,
    _choice,
    _compact_string_list,
    _looks_like_prompt_injection,
    _sanitize_save_tool_plan,
    _should_skip_save_planning,
    _unavailable_save_tool_plan,
)
from app.llm.client import llm_client
from app.llm.prompts.memory import (
    COMPRESS_DIARY_SYSTEM_PROMPT,
    FAMILY_CARD_SYSTEM_PROMPT,
    FAMILY_WEEKLY_DIGEST_SYSTEM_PROMPT,
    HERITAGE_CLASSICAL_SYSTEM_PROMPT,
    HERITAGE_SAVE_JUDGE_SYSTEM_PROMPT,
    HERITAGE_TASK_DRAFT_SYSTEM_PROMPT,
    ORGANIZE_DRAFT_SYSTEM_PROMPT,
    SAVE_TOOL_PLAN_SYSTEM_PROMPT,
    SESSION_ARCHIVE_SUMMARY_SYSTEM_PROMPT,
)
from app.middleware.auth import verify_token, verify_token_or_internal_service
from app.utils.input_guard import InputGuardError, enforce_input_guard
from app.utils.privacy_guard import redact_with_note
from app.utils.safety_limits import enforce_ai_concurrency, enforce_ai_rate_limit

logger = logging.getLogger("familyagent.ai.api.memory")

router = APIRouter(dependencies=[
    Depends(verify_token),
    Depends(enforce_ai_rate_limit),
    Depends(enforce_ai_concurrency),
])

internal_router = APIRouter(dependencies=[
    Depends(verify_token_or_internal_service),
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


@router.post("/extract")
async def extract_memories(request: ExtractMemoryRequest):
    return {
        "success": True,
        "deprecated": True,
        "memories": [],
        "message": "学习记忆功能已下线；请使用家族记忆、每日记录或成长观察。",
    }


@router.post("/family-card")
async def create_family_memory_card(request: FamilyMemoryCardRequest):
    try:
        content = request.content.strip()
        if len(content) < 8:
            raise HTTPException(status_code=400, detail="内容太短，无法整理为经验卡")
        guarded_content = redact_with_note(content, max_length=5000).text
        guarded_family_context = redact_with_note(request.family_context, max_length=2000).text

        user_prompt = f"""经验类型：{request.memory_type or "未知"}
适用场景：{request.target or "未指定"}
家庭背景：{guarded_family_context or "无"}

原始内容：
{guarded_content}

请整理为经验沉淀卡。"""
        raw = await llm_client.chat(
            messages=[
                {"role": "system", "content": FAMILY_CARD_SYSTEM_PROMPT},
                {"role": "user", "content": user_prompt},
            ],
            temperature=0.2,
            max_tokens=1000,
            response_format=FAMILY_CARD_SCHEMA,
        )
        data = json.loads(raw)
        return {"success": True, "data": _sanitize_family_card(data)}
    except HTTPException:
        raise
    except Exception as e:
        logger.error("Family memory card generation failed: %s", e)
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/heritage-classical")
async def create_heritage_classical_draft(request: HeritageClassicalRequest):
    try:
        content = request.content.strip()
        if len(content) < 8:
            raise HTTPException(status_code=400, detail="内容太短，无法提炼为古文稿")

        guarded_content = redact_with_note(content, max_length=4000).text
        guarded_family_context = redact_with_note(request.family_context, max_length=1200).text

        user_prompt = f"""经验类型：{request.memory_type or "未指定"}
适用场景：{request.scenario or "未指定"}
家庭背景：{guarded_family_context or "无"}

原始内容：
{guarded_content}

请提炼为一版古文稿。"""
        raw = await llm_client.chat(
            messages=[
                {"role": "system", "content": HERITAGE_CLASSICAL_SYSTEM_PROMPT},
                {"role": "user", "content": user_prompt},
            ],
            temperature=0.25,
            max_tokens=900,
            response_format=HERITAGE_CLASSICAL_SCHEMA,
        )
        data = json.loads(raw)
        return {"success": True, "data": _sanitize_heritage_classical_draft(data, content)}
    except HTTPException:
        raise
    except Exception as e:
        logger.error("Heritage classical draft generation failed: %s", e)
        raise HTTPException(status_code=500, detail=str(e))


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

        user_prompt = f"""当前家族背景：{family_context or "无"}
当前镜像/关联成员：{request.target_member_name or "未指定"}
当前用户角色：{request.viewer_role or "未知"}

最近对话上下文：
{conversation_context or "无"}

用户消息：
{message}

请从“用户消息”和“最近对话上下文”中判断是否需要调用保存工具，并把真正要保存的事实整理成 content。"""
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


@router.post("/heritage-save-judge")
async def judge_heritage_save(request: HeritageSaveJudgeRequest):
    try:
        content = redact_with_note(request.content, max_length=3000).text.strip()
        enforce_input_guard(content)
        if _looks_like_prompt_injection(content):
            return {"success": True, "data": _blocked_heritage_save_judge("疑似提示词注入或越权指令，不适合保存为家族经验。", ["安全边界"])}
        if _looks_like_low_value_heritage(content):
            return {"success": True, "data": _blocked_heritage_save_judge("内容缺少具体经历、可复用教训或后辈可借鉴做法，暂不能保存为家族经验沉淀。", _heritage_missing_elements(content))}

        family_context = redact_with_note(request.family_context, max_length=1200).text
        user_prompt = f"""经验类型：{request.memory_type or "未知"}
适用场景：{request.scenario or "未指定"}
来源方式：{request.source_mode or "未指定"}
家庭背景：{family_context or "无"}

待审查内容：
{content}

请判断这段内容是否具有后辈学习价值，能否保存为家族经验沉淀。"""
        raw = await llm_client.chat(
            messages=[
                {"role": "system", "content": HERITAGE_SAVE_JUDGE_SYSTEM_PROMPT},
                {"role": "user", "content": user_prompt},
            ],
            temperature=0.05,
            max_tokens=700,
            response_format=HERITAGE_SAVE_JUDGE_SCHEMA,
        )
        data = json.loads(raw)
        return {"success": True, "data": _sanitize_heritage_save_judge(data, content)}
    except InputGuardError:
        raise
    except Exception:
        logger.error("Heritage save judge failed", exc_info=True)
        fallback = _local_heritage_save_judge(request.content)
        return {"success": True, "data": fallback}


@router.post("/organize-draft")
async def organize_family_draft(request: OrganizeDraftRequest):
    try:
        content = request.content.strip()
        if len(content) < 4:
            raise HTTPException(status_code=400, detail="内容太短，无法整理")

        guarded_content = redact_with_note(content, max_length=4000).text
        guarded_family_context = redact_with_note(request.family_context, max_length=1200).text
        scene = _choice(request.scene, {"DIARY", "HERITAGE", "GROWTH_GUARD"}, "DIARY")

        user_prompt = f"""整理场景：{scene}
当前类型：{request.current_type or "未指定"}
当前可见范围：{request.current_visibility or "未指定"}
适用对象/场景：{request.target or "未指定"}
家庭背景：{guarded_family_context or "无"}

原始草稿：
{guarded_content}

请整理为表单草稿。"""
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


@router.post("/compress-diary")
async def compress_diary_entry(request: CompressDiaryRequest):
    try:
        max_chars = max(80, min(1000, int(request.max_chars or 600)))
        current_content = redact_with_note(request.current_content, max_length=3000).text
        incoming_content = redact_with_note(request.incoming_content, max_length=3000).text

        user_prompt = f"""日记日期：{request.diary_date or "未指定"}
字数上限：{max_chars}

已有同日日记：
{current_content or "无"}

新增片段：
{incoming_content}

请合并并压缩为一段不超过 {max_chars} 字的日记。"""
        raw = await llm_client.chat(
            messages=[
                {"role": "system", "content": COMPRESS_DIARY_SYSTEM_PROMPT},
                {"role": "user", "content": user_prompt},
            ],
            temperature=0.15,
            max_tokens=1000,
            response_format=COMPRESSED_DIARY_SCHEMA,
        )
        data = json.loads(raw)
        return {"success": True, "data": _sanitize_compressed_diary(data, max_chars, current_content, incoming_content)}
    except Exception as e:
        logger.error("Diary compression failed: %s", e)
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/family-weekly-digest")
async def create_family_weekly_digest(request: FamilyWeeklyDigestRequest):
    try:
        prompt = f"""家庭：{request.family_name or "未命名家庭"}
对象：{request.target or "全家"}

每日记录：
{json.dumps(_compact_diaries(request.diaries), ensure_ascii=False)}

经验沉淀：
{json.dumps(_compact_family_memories(request.memories), ensure_ascii=False)}

成长观察：
{json.dumps(_compact_growth_records(request.growth_records), ensure_ascii=False)}

请生成一份家族记忆摘要。"""
        raw = await llm_client.chat(
            messages=[
                {"role": "system", "content": FAMILY_WEEKLY_DIGEST_SYSTEM_PROMPT},
                {"role": "user", "content": prompt},
            ],
            temperature=0.2,
            max_tokens=1200,
            response_format=FAMILY_WEEKLY_DIGEST_SCHEMA,
        )
        data = json.loads(raw)
        return {"success": True, "data": _sanitize_family_weekly_digest(data)}
    except Exception as e:
        logger.error("Family weekly digest failed: %s", e)
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/heritage-task-draft")
async def create_heritage_task_draft(request: HeritageTaskDraftRequest):
    try:
        content = request.content.strip()
        if len(content) < 8:
            raise HTTPException(status_code=400, detail="内容太短，无法生成家庭任务")

        guarded_content = redact_with_note(content, max_length=4000).text
        guarded_summary = redact_with_note(request.summary, max_length=800).text
        guarded_family_context = redact_with_note(request.family_context, max_length=1200).text
        actions = _compact_string_list(request.existing_actions, 5, 100)

        user_prompt = f"""经验类型：{request.memory_type or "未知"}
适用场景：{request.scenario or "未指定"}
家庭背景：{guarded_family_context or "无"}
经验摘要：{guarded_summary or "无"}
已有建议行动：{json.dumps(actions, ensure_ascii=False)}

经验原文：
{guarded_content}

请生成一次家庭小实践任务。"""
        raw = await llm_client.chat(
            messages=[
                {"role": "system", "content": HERITAGE_TASK_DRAFT_SYSTEM_PROMPT},
                {"role": "user", "content": user_prompt},
            ],
            temperature=0.2,
            max_tokens=800,
            response_format=HERITAGE_TASK_DRAFT_SCHEMA,
        )
        data = json.loads(raw)
        return {"success": True, "data": _sanitize_heritage_task_draft(data, content, actions)}
    except HTTPException:
        raise
    except Exception as e:
        logger.error("Heritage task draft generation failed: %s", e)
        raise HTTPException(status_code=500, detail=str(e))


@internal_router.post("/session-archive-summary")
async def summarize_session_archive(request: SessionArchiveSummaryRequest):
    messages = _compact_session_archive_messages(request.messages)
    fallback = _local_session_archive_summary(request.session_title, messages)
    if not messages:
        return {"success": True, "data": fallback}

    prompt = f"""session_id: {request.session_id}
session_title: {request.session_title or "untitled"}
family_id: {request.family_id if request.family_id is not None else "unknown"}
subject: {request.subject or "FamilyAgent"}
messages:
{json.dumps(messages, ensure_ascii=False)}
"""

    try:
        raw = await llm_client.chat(
            messages=[
                {"role": "system", "content": SESSION_ARCHIVE_SUMMARY_SYSTEM_PROMPT},
                {"role": "user", "content": prompt},
            ],
            temperature=0.2,
            max_tokens=500,
            response_format=SESSION_ARCHIVE_SUMMARY_SCHEMA,
        )
        data = json.loads(raw)
        return {"success": True, "data": _sanitize_session_archive_summary(data, fallback)}
    except Exception as e:
        logger.warning("Session archive summary failed: %s", e)
        return {"success": True, "data": fallback}
