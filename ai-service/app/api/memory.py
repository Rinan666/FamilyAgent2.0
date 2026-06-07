"""
Learning memory extraction API.
"""
import json
import logging
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field

from app.llm.client import llm_client
from app.middleware.auth import verify_token
from app.utils.privacy_guard import redact_with_note

logger = logging.getLogger("familyagent.ai.api.memory")

router = APIRouter(dependencies=[Depends(verify_token)])


class ExtractMemoryRequest(BaseModel):
    session_id: int
    subject: str = ""
    knowledge_point_id: Optional[int | str] = None
    messages: list[dict] = Field(default_factory=list)
    summary: str = ""


class FamilyMemoryCardRequest(BaseModel):
    content: str = Field(..., min_length=8)
    memory_type: str = "ELDER_ADVICE"
    family_context: str = ""
    # Backward compatible field name; product wording is "适用场景".
    target: str = ""


class SaveToolPlanRequest(BaseModel):
    message: str = Field(..., min_length=2)
    family_context: str = ""
    target_member_name: str = ""
    viewer_role: str = ""


class OrganizeDraftRequest(BaseModel):
    content: str = Field(..., min_length=4)
    scene: str = "DIARY"
    family_context: str = ""
    current_type: str = ""
    current_visibility: str = ""
    target: str = ""


MEMORY_SCHEMA = {
    "type": "json_schema",
    "json_schema": {
        "name": "learning_memory_extraction",
        "schema": {
            "type": "object",
            "properties": {
                "memories": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "type": {"type": "string"},
                            "content": {"type": "string"},
                            "summary": {"type": "string"},
                            "importance": {"type": "integer"},
                            "confidence": {"type": "number"},
                        },
                        "required": ["type", "content", "summary", "importance", "confidence"],
                    },
                }
            },
            "required": ["memories"],
        },
    },
}


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


SYSTEM_PROMPT = """你是 FamilyAgent 的学习记忆提取器。
只提取对后续学习陪伴 AI 有帮助、可验证、低敏感度的学习记忆。

允许保存：
- 学生在某个知识点上的稳定薄弱点或常见错因
- 学生偏好的讲解方式、节奏、类比方式
- 学生明确提出的近期学习目标
- 与数学学习直接相关的复习建议

禁止保存：
- 家庭矛盾、健康、身份隐私、联系方式、学校班级等敏感信息
- 一次性闲聊或情绪宣泄
- 未经证据支持的人格标签
- 大段原始对话、题目全文、答案全文

输出 JSON。最多 3 条 memories。没有值得长期保存的内容时返回空数组。
type 只能是 LEARNING、MISTAKE、PREFERENCE、PLAN。
importance 为 1-5，confidence 为 0-1。
content 要短、具体、可用于下次教学。"""


FAMILY_CARD_SYSTEM_PROMPT = """你是 FamilyAgent 的家族经验整理助手。
你的任务是把家族成员输入的经验、故事、提醒或建议，整理成温和、清晰、可保存的经验卡。

原则：
- 尊重原意，不编造事实，不夸大风险。
- 涉及体态、牙齿、视力、睡眠、运动、屏幕时间、情绪等内容时，只做提醒、记录、科普和就医建议，不做医疗诊断。
- 输出要适合家庭内部阅读，语气克制，不制造焦虑。
- 风险点和行动建议必须具体、可执行。
- 如果内容涉及隐私或未成年人敏感信息，sensitivity 标为 MEDIUM 或 HIGH，并在 safety_note 中提示需要限制可见范围。

字段要求：
- title：不超过 20 字。
- theme：例如 家族故事、长者建议、健康提醒、成长风险、价值观。
- summary：80 字以内。
- risk_points：0-4 条。
- action_suggestions：1-5 条。
- suitable_for：可包含 家长、学习者、长者、全家。
- sensitivity：LOW、MEDIUM 或 HIGH。
- safety_note：一句权限或专业边界提示。"""


SAVE_TOOL_PLAN_SYSTEM_PROMPT = """你是 FamilyAgent 的对话工具规划器。
用户可能会在对话中说“帮我记下来 / 保存起来 / 沉淀为经验 / 记录一下”。你的任务是判断是否需要调用保存工具，并选择最合适的数据形态。

可用工具：
1. DIARY：人生记录。适合个人经历、当天发生的事、情绪、选择、给家人的话、自我反思。
2. FAMILY_MEMORY：家族经验。适合长辈经验、家族故事、价值观、可复用建议、踩坑提醒。
3. GROWTH_GUARD：成长守护记录。适合体态、牙齿、视力、睡眠、运动、屏幕时间、情绪沟通等需要跟进的观察提醒。
4. NONE：没有明确保存意图，或内容过短/不适合保存。

可见范围原则：
- 涉及个人隐私、情绪、未成年人敏感信息：优先 PRIVATE 或 CARE_VISIBLE。
- 普通人生记录：PRIVATE。
- 明确希望家人看到的记录：FAMILY_VISIBLE。
- 家族经验、长辈建议、价值观：FAMILY_VISIBLE；若含敏感健康/冲突细节，用 CARE_VISIBLE。
- 成长守护：默认 CARE_VISIBLE，低敏提醒可 FAMILY_VISIBLE。

字段要求：
- tool 只能是 NONE、DIARY、FAMILY_MEMORY、GROWTH_GUARD。
- DIARY 的 entry_type 只能是 DAILY、IMPORTANT_EVENT、LESSON、EMOTION、MESSAGE_TO_FAMILY、SELF_REFLECTION。
- FAMILY_MEMORY 的 memory_type 只能是 FAMILY_STORY、ELDER_ADVICE、HEALTH_REMINDER、GROWTH_RISK、VALUE、PLAN。
- GROWTH_GUARD 的 category 只能是 POSTURE、DENTAL、VISION、SLEEP、EXERCISE、SCREEN_TIME、EMOTION、COMMUNICATION、OTHER。
- visibility 只能是 PRIVATE、FAMILY_VISIBLE、CARE_VISIBLE、LEGACY_VISIBLE。
- scope 只能是 PRIVATE、CARE_VISIBLE、FAMILY_VISIBLE、PARENT_VISIBLE。
- content 保留用户原意，去掉“帮我保存”等指令性外壳，不编造事实。
- title 不超过 24 字，summary 不超过 80 字。
- severity 和 importance 为 1-5。
- confirmation_message 用一句话告诉用户保存到了哪里。

只输出 JSON。"""


ORGANIZE_DRAFT_SYSTEM_PROMPT = """你是 FamilyAgent 的口述草稿整理助手。
你的任务是把家庭成员口述或随手写下的草稿整理成更适合保存的表单草稿。

重要原则：
- 只整理表达，不扩写事实，不编造人物、时间、医学判断。
- 保留第一人称和原始情绪，不把个人记录改成说教。
- 涉及未成年人、健康、家庭冲突、强烈情绪时，可见范围要保守。
- 输出内容应自然、清晰、可回看；不要写成商业文案。
- 这只是草稿，不直接保存。

场景：
- DIARY：人生记录，适合整理标题、正文、标签、日记类型、可见范围。
- HERITAGE：家族经验，适合整理为长者建议、家族故事、价值观、健康提醒等。
- GROWTH_GUARD：成长守护，适合整理观察内容、类别、留意程度。

枚举：
- diary_entry_type：DAILY、IMPORTANT_EVENT、LESSON、EMOTION、MESSAGE_TO_FAMILY、SELF_REFLECTION。
- diary_visibility：PRIVATE、FAMILY_VISIBLE、CARE_VISIBLE、LEGACY_VISIBLE。
- memory_type：FAMILY_STORY、ELDER_ADVICE、HEALTH_REMINDER、GROWTH_RISK、VALUE、PLAN。
- memory_scope：PRIVATE、CARE_VISIBLE、FAMILY_VISIBLE、PARENT_VISIBLE。
- growth_category：POSTURE、DENTAL、VISION、SLEEP、EXERCISE、SCREEN_TIME、EMOTION、COMMUNICATION、OTHER。
- growth_severity：1-5。

只输出 JSON。"""


@router.post("/extract")
async def extract_memories(request: ExtractMemoryRequest):
    try:
        transcript = _compact_transcript(request.messages)
        if not transcript:
            return {"success": True, "memories": []}

        user_prompt = f"""学科：{request.subject or "未知"}
知识点ID：{request.knowledge_point_id or "未知"}
会话摘要：{request.summary or "无"}

对话：
{transcript}

请提取学习记忆。"""
        raw = await llm_client.chat(
            messages=[
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": user_prompt},
            ],
            temperature=0.1,
            max_tokens=900,
            response_format=MEMORY_SCHEMA,
        )
        data = json.loads(raw)
        memories = [_sanitize_memory(item) for item in data.get("memories", [])]
        memories = [item for item in memories if item is not None][:3]
        return {"success": True, "memories": memories}
    except Exception as e:
        logger.error("Memory extraction failed: %s", e)
        raise HTTPException(status_code=500, detail=str(e))


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

请整理为家族经验卡。"""
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


@router.post("/save-plan")
async def plan_agent_save_tool(request: SaveToolPlanRequest):
    try:
        message = redact_with_note(request.message, max_length=3000).text
        family_context = redact_with_note(request.family_context, max_length=1200).text

        user_prompt = f"""当前家族背景：{family_context or "无"}
当前镜像/关联成员：{request.target_member_name or "未指定"}
当前用户角色：{request.viewer_role or "未知"}

用户消息：
{message}

请判断是否需要调用保存工具。"""
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
    except Exception as e:
        logger.error("Save tool planning failed: %s", e)
        raise HTTPException(status_code=500, detail=str(e))


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


def _compact_transcript(messages: list[dict]) -> str:
    lines: list[str] = []
    for message in messages[-20:]:
        role = str(message.get("role", ""))
        if role not in {"user", "assistant"}:
            continue
        content = str(message.get("content", "")).strip()
        if not content:
            continue
        lines.append(f"{role}: {content[:600]}")
    return "\n".join(lines)[-6000:]


def _sanitize_memory(item: dict) -> dict | None:
    content = str(item.get("content", "")).strip()
    if len(content) < 8:
        return None
    memory_type = str(item.get("type", "LEARNING")).strip().upper()
    if memory_type not in {"LEARNING", "MISTAKE", "PREFERENCE", "PLAN"}:
        memory_type = "LEARNING"
    try:
        importance = int(item.get("importance", 3))
    except (TypeError, ValueError):
        importance = 3
    try:
        confidence = float(item.get("confidence", 0.7))
    except (TypeError, ValueError):
        confidence = 0.7
    return {
        "type": memory_type,
        "content": content[:500],
        "summary": str(item.get("summary", content)).strip()[:200],
        "importance": max(1, min(5, importance)),
        "confidence": max(0.0, min(1.0, confidence)),
    }


def _sanitize_family_card(data: dict) -> dict:
    sensitivity = str(data.get("sensitivity", "LOW")).strip().upper()
    if sensitivity not in {"LOW", "MEDIUM", "HIGH"}:
        sensitivity = "LOW"
    return {
        "title": str(data.get("title", "家族经验")).strip()[:30] or "家族经验",
        "theme": str(data.get("theme", "长者建议")).strip()[:20] or "长者建议",
        "summary": str(data.get("summary", "")).strip()[:200],
        "risk_points": _compact_string_list(data.get("risk_points"), 4, 80),
        "action_suggestions": _compact_string_list(data.get("action_suggestions"), 5, 100),
        "suitable_for": _compact_string_list(data.get("suitable_for"), 4, 20) or ["全家"],
        "sensitivity": sensitivity,
        "safety_note": str(
            data.get("safety_note", "这是一条家庭经验整理，不构成专业诊断。")
        ).strip()[:120],
    }


def _sanitize_save_tool_plan(data: dict) -> dict:
    tool = _choice(data.get("tool"), {"NONE", "DIARY", "FAMILY_MEMORY", "GROWTH_GUARD"}, "NONE")
    content = str(data.get("content", "")).strip()[:1200]
    should_save = bool(data.get("should_save")) and tool != "NONE" and len(content) >= 4
    if not should_save:
        tool = "NONE"

    visibility = _choice(
        data.get("visibility"),
        {"PRIVATE", "FAMILY_VISIBLE", "CARE_VISIBLE", "LEGACY_VISIBLE"},
        "PRIVATE",
    )
    scope = _choice(
        data.get("scope"),
        {"PRIVATE", "CARE_VISIBLE", "FAMILY_VISIBLE", "PARENT_VISIBLE"},
        "PRIVATE" if visibility == "PRIVATE" else visibility,
    )

    return {
        "should_save": should_save,
        "tool": tool,
        "content": content,
        "title": str(data.get("title", "")).strip()[:24] or _default_save_title(tool),
        "summary": str(data.get("summary", content)).strip()[:80],
        "visibility": visibility,
        "entry_type": _choice(
            data.get("entry_type"),
            {"DAILY", "IMPORTANT_EVENT", "LESSON", "EMOTION", "MESSAGE_TO_FAMILY", "SELF_REFLECTION"},
            "DAILY",
        ),
        "memory_type": _choice(
            data.get("memory_type"),
            {"FAMILY_STORY", "ELDER_ADVICE", "HEALTH_REMINDER", "GROWTH_RISK", "VALUE", "PLAN"},
            "ELDER_ADVICE",
        ),
        "scope": scope,
        "category": _choice(
            data.get("category"),
            {
                "POSTURE",
                "DENTAL",
                "VISION",
                "SLEEP",
                "EXERCISE",
                "SCREEN_TIME",
                "EMOTION",
                "COMMUNICATION",
                "OTHER",
            },
            "OTHER",
        ),
        "severity": _bounded_int(data.get("severity"), 1, 5, 2),
        "importance": _bounded_int(data.get("importance"), 1, 5, 3),
        "tags": _compact_string_list(data.get("tags"), 6, 18),
        "reason": str(data.get("reason", "")).strip()[:120],
        "confirmation_message": str(
            data.get("confirmation_message", _default_save_confirmation(tool))
        ).strip()[:120] or _default_save_confirmation(tool),
    }


def _sanitize_organized_draft(data: dict, scene: str, fallback_content: str) -> dict:
    content = str(data.get("content", "")).strip()[:3000] or fallback_content[:3000]
    return {
        "title": str(data.get("title", "未命名记录")).strip()[:30] or "未命名记录",
        "content": content,
        "tags": _compact_string_list(data.get("tags"), 8, 18),
        "diary_entry_type": _choice(
            data.get("diary_entry_type"),
            {"DAILY", "IMPORTANT_EVENT", "LESSON", "EMOTION", "MESSAGE_TO_FAMILY", "SELF_REFLECTION"},
            "LESSON" if scene == "HERITAGE" else "DAILY",
        ),
        "diary_visibility": _choice(
            data.get("diary_visibility"),
            {"PRIVATE", "FAMILY_VISIBLE", "CARE_VISIBLE", "LEGACY_VISIBLE"},
            "CARE_VISIBLE" if scene == "GROWTH_GUARD" else "PRIVATE",
        ),
        "memory_type": _choice(
            data.get("memory_type"),
            {"FAMILY_STORY", "ELDER_ADVICE", "HEALTH_REMINDER", "GROWTH_RISK", "VALUE", "PLAN"},
            "ELDER_ADVICE" if scene == "HERITAGE" else "FAMILY_STORY",
        ),
        "memory_scope": _choice(
            data.get("memory_scope"),
            {"PRIVATE", "CARE_VISIBLE", "FAMILY_VISIBLE", "PARENT_VISIBLE"},
            "FAMILY_VISIBLE" if scene == "HERITAGE" else "CARE_VISIBLE",
        ),
        "growth_category": _choice(
            data.get("growth_category"),
            {
                "POSTURE",
                "DENTAL",
                "VISION",
                "SLEEP",
                "EXERCISE",
                "SCREEN_TIME",
                "EMOTION",
                "COMMUNICATION",
                "OTHER",
            },
            "OTHER",
        ),
        "growth_severity": _bounded_int(data.get("growth_severity"), 1, 5, 3),
        "scenario": str(data.get("scenario", "")).strip()[:30],
        "reason": str(data.get("reason", "")).strip()[:120],
    }


def _choice(value: object, allowed: set[str], fallback: str) -> str:
    text = str(value or "").strip().upper()
    return text if text in allowed else fallback


def _bounded_int(value: object, minimum: int, maximum: int, fallback: int) -> int:
    try:
        number = int(value)
    except (TypeError, ValueError):
        number = fallback
    return max(minimum, min(maximum, number))


def _default_save_title(tool: str) -> str:
    return {
        "DIARY": "对话保存的人生记录",
        "FAMILY_MEMORY": "对话沉淀的家族经验",
        "GROWTH_GUARD": "对话记录的成长观察",
    }.get(tool, "无需保存")


def _default_save_confirmation(tool: str) -> str:
    return {
        "DIARY": "已保存为人生记录。",
        "FAMILY_MEMORY": "已保存为家族经验。",
        "GROWTH_GUARD": "已保存为成长守护记录。",
    }.get(tool, "这条消息不需要保存。")


def _compact_string_list(value: object, limit: int, max_len: int) -> list[str]:
    if not isinstance(value, list):
        return []
    result: list[str] = []
    for item in value:
        text = str(item).strip()
        if text:
            result.append(text[:max_len])
        if len(result) >= limit:
            break
    return result
