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

logger = logging.getLogger("familyagent.ai.api.memory")

router = APIRouter(dependencies=[Depends(verify_token)])


class ExtractMemoryRequest(BaseModel):
    session_id: int
    subject: str = ""
    knowledge_point_id: Optional[int | str] = None
    messages: list[dict] = Field(default_factory=list)
    summary: str = ""


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


SYSTEM_PROMPT = """你是 FamilyAgent 的学习记忆提取器。
只提取对后续 AI 家教有帮助、可验证、低敏感度的学习记忆。

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
