"""
Growth guard AI APIs.
"""
import json
import logging

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field

from app.llm.client import llm_client
from app.middleware.auth import verify_token
from app.utils.safety_limits import enforce_ai_concurrency, enforce_ai_rate_limit

logger = logging.getLogger("familyagent.ai.api.growth")

router = APIRouter(dependencies=[
    Depends(verify_token),
    Depends(enforce_ai_rate_limit),
    Depends(enforce_ai_concurrency),
])


class WeeklyReportRequest(BaseModel):
    family_name: str = ""
    records: list[dict] = Field(default_factory=list)
    memories: list[dict] = Field(default_factory=list)
    target: str = ""


WEEKLY_REPORT_SCHEMA = {
    "type": "json_schema",
    "json_schema": {
        "name": "growth_guard_weekly_report",
        "schema": {
            "type": "object",
            "properties": {
                "title": {"type": "string"},
                "summary": {"type": "string"},
                "affirmations": {
                    "type": "array",
                    "items": {"type": "string"},
                },
                "concerns": {
                    "type": "array",
                    "items": {"type": "string"},
                },
                "signals": {
                    "type": "array",
                    "items": {"type": "string"},
                },
                "uncertainty_notes": {
                    "type": "array",
                    "items": {"type": "string"},
                },
                "family_experience_refs": {
                    "type": "array",
                    "items": {"type": "string"},
                },
                "suggested_actions": {
                    "type": "array",
                    "items": {"type": "string"},
                },
                "follow_up_questions": {
                    "type": "array",
                    "items": {"type": "string"},
                },
                "safety_note": {"type": "string"},
            },
            "required": [
                "title",
                "summary",
                "affirmations",
                "concerns",
                "signals",
                "uncertainty_notes",
                "family_experience_refs",
                "suggested_actions",
                "follow_up_questions",
                "safety_note",
            ],
        },
    },
}


SYSTEM_PROMPT = """你是 FamilyAgent 的成长观察摘要助手。
你要根据家庭成员记录的成长观察、来源视角、证据类型、置信度和家族经验卡，生成一份温和、简短、可执行的照护摘要。

严格原则：
- 不做医疗诊断，不判断孩子有病或有心理问题。
- 不把观察者的主观判断当作事实；要区分“可观察事实”“观察者感受/担心”“推测”。
- 人可能会隐藏真实状态，因此不能声称看穿对方，只能说明现有记录覆盖了哪些视角、缺少哪些视角。
- 如果只有单一照护者视角，必须降低置信度，并建议补充本人记录或其他场景观察。
- 如果记录中标明本人未确认，不能推断本人真实想法。
- 只输出照护提醒、观察线索、不确定性说明、轻量行动建议和必要时的专业咨询建议。
- 默认面向照护者或被授权成员，不是全家公开的家族记忆摘要。
- 不制造焦虑，不用恐吓语气。
- 如果证据不足，要明确说“记录还少，建议继续观察”。
- 建议行动必须轻量，适合家庭下周执行。
- 不暴露未成年人或被观察成员的敏感隐私，只做授权范围内摘要。

输出要求：
- title 不超过 18 字。
- summary 不超过 100 字。
- affirmations 1-3 条，先肯定家庭或孩子已有的积极变化。
- concerns 0-3 条，只写需要温和留意的风险，不制造焦虑。
- signals 0-5 条，只描述观察到的信号，不写诊断结论。
- uncertainty_notes 1-3 条，说明来源偏差、缺失视角、本人是否确认、记录是否单一等不确定性。
- family_experience_refs 0-3 条，引用家族经验中的可用提醒。
- suggested_actions 1-3 条，每条必须可执行。
- follow_up_questions 1-3 条，用于下次家长记录。
- safety_note 一句话，说明照护可见和非诊断边界。"""


@router.post("/weekly-report")
async def weekly_report(request: WeeklyReportRequest):
    try:
        prompt = f"""家庭：{request.family_name or "未命名家庭"}
对象：{request.target or "家庭成员"}

成长观察记录：
{json.dumps(_compact_records(request.records), ensure_ascii=False)}

家族经验卡：
{json.dumps(_compact_memories(request.memories), ensure_ascii=False)}

请生成成长观察照护摘要。"""
        raw = await llm_client.chat(
            messages=[
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": prompt},
            ],
            temperature=0.2,
            max_tokens=1000,
            response_format=WEEKLY_REPORT_SCHEMA,
        )
        data = json.loads(raw)
        return {"success": True, "data": _sanitize_report(data)}
    except Exception as e:
        logger.error("Weekly growth report failed: %s", e)
        raise HTTPException(status_code=500, detail=str(e))


def _compact_records(records: list[dict]) -> list[dict]:
    result: list[dict] = []
    for item in records[-20:]:
        result.append({
            "category": str(item.get("category", ""))[:30],
            "content": str(item.get("content", ""))[:300],
            "severity": item.get("severity"),
            "follow_up_status": _metadata_follow_up_status(item),
            "observer_perspective": _metadata_value(item, "observerPerspective", "observer_perspective"),
            "evidence_type": _metadata_value(item, "evidenceType", "evidence_type"),
            "confidence_level": _metadata_value(item, "confidenceLevel", "confidence_level"),
            "self_confirmed": _metadata_value(item, "selfConfirmed", "self_confirmed"),
            "observed_at": str(item.get("observedAt") or item.get("observed_at") or "")[:30],
        })
    return result


def _compact_memories(memories: list[dict]) -> list[dict]:
    result: list[dict] = []
    for item in memories[-12:]:
        metadata = item.get("metadata") if isinstance(item.get("metadata"), dict) else {}
        card = metadata.get("memoryCard") if isinstance(metadata.get("memoryCard"), dict) else {}
        result.append({
            "type": str(item.get("type", ""))[:30],
            "summary": str(card.get("summary") or item.get("summary") or item.get("content") or "")[:240],
            "actions": card.get("action_suggestions", [])[:3] if isinstance(card.get("action_suggestions"), list) else [],
        })
    return result


def _sanitize_report(data: dict) -> dict:
    return {
        "title": str(data.get("title", "成长守护摘要")).strip()[:30] or "成长守护摘要",
        "summary": str(data.get("summary", "")).strip()[:160],
        "affirmations": _compact_string_list(data.get("affirmations"), 3, 100),
        "concerns": _compact_string_list(data.get("concerns"), 3, 100),
        "signals": _compact_string_list(data.get("signals"), 5, 100),
        "uncertainty_notes": _compact_string_list(
            data.get("uncertainty_notes"),
            3,
            120,
        ) or ["现有记录只代表已授权观察视角，不等于本人完整状态。"],
        "family_experience_refs": _compact_string_list(data.get("family_experience_refs"), 3, 100),
        "suggested_actions": _compact_string_list(data.get("suggested_actions"), 3, 120) or ["本周先补充 1-2 条观察记录。"],
        "follow_up_questions": _compact_string_list(data.get("follow_up_questions"), 3, 100) or ["下周最值得继续观察的一件小事是什么？"],
        "safety_note": str(
            data.get("safety_note", "这是一份照护者可见的成长观察摘要，不构成医疗或心理诊断。")
        ).strip()[:120],
    }


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


def _metadata_follow_up_status(item: dict) -> str:
    metadata = item.get("metadata") if isinstance(item.get("metadata"), dict) else {}
    return str(metadata.get("followUpStatus") or metadata.get("follow_up_status") or "PENDING")[:30]


def _metadata_value(item: dict, camel_key: str, snake_key: str) -> str:
    metadata = item.get("metadata") if isinstance(item.get("metadata"), dict) else {}
    return str(metadata.get(camel_key) or metadata.get(snake_key) or "")[:40]
