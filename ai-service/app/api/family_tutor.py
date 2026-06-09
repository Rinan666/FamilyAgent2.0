"""
Current tutor-facing AI routes that remain supported in FamilyAgent.
"""
import asyncio
import json
import logging
from contextlib import suppress
from typing import Optional

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from app.agents.generator_agent import generator_agent
from app.agents.skill_workflow_agent import skill_workflow_agent
from app.agents.tutor_agent import tutor_agent
from app.middleware.auth import verify_token
from app.services.content_extractor import extract_content
from app.utils.input_guard import InputGuardError, enforce_input_guard
from app.utils.privacy_guard import redact_with_note
from app.utils.safety_limits import enforce_ai_concurrency, enforce_ai_rate_limit
from app.utils.sanitizer import sanitize_text

logger = logging.getLogger("familyagent.ai.api.family_tutor")
SSE_KEEPALIVE_SECONDS = 10.0

router = APIRouter(
    dependencies=[
        Depends(verify_token),
        Depends(enforce_ai_rate_limit),
        Depends(enforce_ai_concurrency),
    ]
)


def _sse_data(payload: dict) -> str:
    return f"data: {json.dumps(payload, ensure_ascii=False)}\n\n"


def _sse_comment(comment: str) -> str:
    return f": {comment}\n\n"


async def _stream_sse_events(queue: asyncio.Queue[dict]):
    yield _sse_comment("connected")
    while True:
        try:
            payload = await asyncio.wait_for(queue.get(), timeout=SSE_KEEPALIVE_SECONDS)
        except asyncio.TimeoutError:
            yield _sse_comment("keep-alive")
            continue

        yield _sse_data(payload)
        if payload.get("done") or payload.get("error"):
            break


class ExplainRequest(BaseModel):
    question_content: str = Field(..., description="Question content")
    answer: str = Field(..., description="Reference answer")
    steps: str = Field(default="", description="Reference steps")
    student_message: str = Field(default="我想学习这道题", description="Student message")
    history: Optional[list[dict]] = Field(default=None, description="Conversation history")
    grade: str = Field(default="", description="Grade level")
    subject: str = Field(default="数学", description="Subject")
    knowledge_point: str = Field(default="未知", description="Knowledge point")
    mastery_level: str = Field(default="中", description="Mastery level")
    common_errors: str = Field(default="无历史数据", description="Common errors")
    teaching_style: str = Field(default="guided", description="guided or direct")
    mode: str = Field(default="explain", description="explain or chat")
    memory_context: str = Field(default="", description="Authorized memory context")
    viewer_role: str = Field(default="STUDENT", description="Viewer role label")
    target_role: str = Field(default="STUDENT", description="Target role label")
    client_timestamp: str = Field(default="", description="Client timestamp in ISO format")
    client_timezone: str = Field(default="", description="Client timezone")


class MistakeReviewRequest(BaseModel):
    question_content: str = Field(..., description="Question content")
    answer: str = Field(..., description="Reference answer")
    student_answer: str = Field(..., description="Student answer")
    steps: str = Field(default="", description="Reference steps")
    grade_result: Optional[dict] = Field(default=None, description="Existing grading result")
    grade: str = Field(default="初中")
    subject: str = Field(default="数学")
    knowledge_point: str = Field(default="未知")
    weak_points: list[str] = Field(default_factory=list, description="Recent weak points")


class DailyPracticeRequest(BaseModel):
    knowledge_point: str = Field(..., description="Today's knowledge point")
    grade: str = Field(default="初中")
    subject: str = Field(default="数学")
    mastery_level: str = Field(default="中", description="Mastery level")
    available_minutes: int = Field(default=15, ge=5, le=60)
    difficulty: str = Field(default="标准", description="Difficulty band")
    question_count: int = Field(default=5, ge=3, le=8)
    weak_points: list[str] = Field(default_factory=list, description="Recent weak points")
    scenario: str = Field(default="学生自练", description="Practice scenario")


class ExamReviewRequest(BaseModel):
    exam_goal: str = Field(default="阶段测评提升", description="Exam goal")
    score_summary: str = Field(..., description="Score summary")
    grade: str = Field(default="初中")
    subject: str = Field(default="数学")
    profiles: dict = Field(default_factory=dict, description="History profiles")
    weak_points: list[str] = Field(default_factory=list, description="Weak points")
    recent_mistakes: list[dict] = Field(default_factory=list, description="Recent mistakes")
    available_minutes: int = Field(default=30, ge=10, le=180)
    review_days: int = Field(default=7, ge=1, le=30)


class StudyPlanRequest(BaseModel):
    learning_goal: str = Field(..., description="Learning goal")
    grade: str = Field(default="初中")
    subject: str = Field(default="数学")
    profiles: dict = Field(default_factory=dict, description="History profiles")
    weak_points: list[str] = Field(default_factory=list, description="Weak points")
    available_minutes: int = Field(default=30, ge=10, le=180)
    plan_days: int = Field(default=7, ge=1, le=30)
    constraints: str = Field(default="", description="Schedule constraints")


@router.post("/explain")
async def explain_question(request: ExplainRequest):
    question_content = sanitize_text(request.question_content)
    student_message = sanitize_text(request.student_message)
    enforce_input_guard(student_message)
    memory_context = redact_with_note(request.memory_context).text

    async def generate():
        queue: asyncio.Queue[dict] = asyncio.Queue()

        async def produce():
            try:
                async for chunk in tutor_agent.explain_stream(
                    question_content=question_content,
                    answer=request.answer,
                    steps=request.steps,
                    student_message=student_message,
                    history=request.history,
                    grade=request.grade,
                    subject=request.subject,
                    knowledge_point=request.knowledge_point,
                    mastery_level=request.mastery_level,
                    common_errors=request.common_errors,
                    teaching_style=request.teaching_style,
                    mode=request.mode,
                    memory_context=memory_context,
                    viewer_role=request.viewer_role,
                    target_role=request.target_role,
                    client_timestamp=request.client_timestamp,
                    client_timezone=request.client_timezone,
                ):
                    if isinstance(chunk, dict) and chunk.get("type") == "metadata":
                        await queue.put({"metadata": chunk})
                    elif isinstance(chunk, dict):
                        await queue.put({"content": chunk.get("content", "")})
                    else:
                        await queue.put({"content": chunk})

                await queue.put({"done": True})
            except Exception as exc:
                logger.error("Tutor stream failed: %s", exc)
                await queue.put({"error": str(exc)})

        task = asyncio.create_task(produce())
        try:
            async for event in _stream_sse_events(queue):
                yield event
        finally:
            if not task.done():
                task.cancel()
                with suppress(asyncio.CancelledError):
                    await task

    return StreamingResponse(
        generate(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


@router.post("/explain/sync")
async def explain_question_sync(request: ExplainRequest):
    try:
        question_content = sanitize_text(request.question_content)
        student_message = sanitize_text(request.student_message)
        enforce_input_guard(student_message)
        memory_context = redact_with_note(request.memory_context).text

        result = await tutor_agent.explain(
            question_content=question_content,
            answer=request.answer,
            steps=request.steps,
            student_message=student_message,
            history=request.history,
            grade=request.grade,
            subject=request.subject,
            knowledge_point=request.knowledge_point,
            mastery_level=request.mastery_level,
            common_errors=request.common_errors,
            teaching_style=request.teaching_style,
            mode=request.mode,
            memory_context=memory_context,
            viewer_role=request.viewer_role,
            target_role=request.target_role,
            client_timestamp=request.client_timestamp,
            client_timezone=request.client_timezone,
        )
        return {"success": True, "content": result}
    except InputGuardError:
        raise
    except Exception as exc:
        logger.error("Tutor sync explain failed: %s", exc)
        raise HTTPException(status_code=500, detail=str(exc)) from exc


@router.post("/skills/mistake-review")
async def run_mistake_review(request: MistakeReviewRequest):
    try:
        result = await skill_workflow_agent.mistake_review(
            question_content=sanitize_text(request.question_content),
            answer=request.answer,
            student_answer=sanitize_text(request.student_answer),
            steps=request.steps,
            grade_result=request.grade_result,
            grade=request.grade,
            subject=request.subject,
            knowledge_point=request.knowledge_point,
            weak_points=request.weak_points,
        )
        return {"success": True, "skill": "mistake_review", "data": result}
    except Exception as exc:
        logger.error("Mistake review failed: %s", exc)
        raise HTTPException(status_code=500, detail=str(exc)) from exc


@router.post("/skills/daily-practice")
async def run_daily_practice(request: DailyPracticeRequest):
    try:
        result = await skill_workflow_agent.daily_practice(
            knowledge_point=request.knowledge_point,
            grade=request.grade,
            subject=request.subject,
            mastery_level=request.mastery_level,
            available_minutes=request.available_minutes,
            difficulty=request.difficulty,
            question_count=request.question_count,
            weak_points=request.weak_points,
            scenario=request.scenario,
        )
        return {"success": True, "skill": "daily_practice", "data": result}
    except Exception as exc:
        logger.error("Daily practice failed: %s", exc)
        raise HTTPException(status_code=500, detail=str(exc)) from exc


@router.post("/skills/exam-review")
async def run_exam_review(request: ExamReviewRequest):
    try:
        result = await skill_workflow_agent.exam_review(
            exam_goal=request.exam_goal,
            score_summary=request.score_summary,
            grade=request.grade,
            subject=request.subject,
            profiles=request.profiles,
            weak_points=request.weak_points,
            recent_mistakes=request.recent_mistakes,
            available_minutes=request.available_minutes,
            review_days=request.review_days,
        )
        return {"success": True, "skill": "exam_review", "data": result}
    except Exception as exc:
        logger.error("Exam review failed: %s", exc)
        raise HTTPException(status_code=500, detail=str(exc)) from exc


@router.post("/skills/study-plan")
async def run_study_plan(request: StudyPlanRequest):
    try:
        result = await skill_workflow_agent.study_plan(
            learning_goal=request.learning_goal,
            grade=request.grade,
            subject=request.subject,
            profiles=request.profiles,
            weak_points=request.weak_points,
            available_minutes=request.available_minutes,
            plan_days=request.plan_days,
            constraints=request.constraints,
        )
        return {"success": True, "skill": "study_plan", "data": result}
    except Exception as exc:
        logger.error("Study plan failed: %s", exc)
        raise HTTPException(status_code=500, detail=str(exc)) from exc


@router.post("/extract")
async def extract_uploaded_content(file: UploadFile = File(...)):
    try:
        data = await file.read()
        result = extract_content(
            filename=file.filename or "upload",
            content_type=file.content_type,
            data=data,
        )
        return {"success": True, "data": result.to_dict()}
    except Exception as exc:
        logger.error("Content extraction failed: %s", exc)
        raise HTTPException(status_code=500, detail=str(exc)) from exc


@router.post("/generate/variation")
async def generate_variation(
    original_question: str,
    original_difficulty: int = 3,
    target_difficulty: int = 3,
):
    try:
        result = await generator_agent.generate_variation(
            original_question=original_question,
            original_difficulty=original_difficulty,
            target_difficulty=target_difficulty,
        )
        return {"success": True, "data": result}
    except Exception as exc:
        logger.error("Variation generation failed: %s", exc)
        raise HTTPException(status_code=500, detail=str(exc)) from exc
