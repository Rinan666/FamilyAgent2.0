"""
家教API路由 — 讲题、批改、出题
"""
import asyncio
import json
import logging
from contextlib import suppress
from typing import Optional

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from app.agents.tutor_agent import tutor_agent
from app.agents.grader_agent import grader_agent
from app.agents.generator_agent import generator_agent
from app.agents.skill_workflow_agent import skill_workflow_agent
from app.middleware.auth import verify_token
from app.services.content_extractor import extract_content
from app.utils.input_guard import InputGuardError, enforce_input_guard
from app.utils.privacy_guard import redact_with_note
from app.utils.safety_limits import enforce_ai_concurrency, enforce_ai_rate_limit
from app.utils.sanitizer import sanitize_text

logger = logging.getLogger("familyagent.ai.api.tutor")
SSE_KEEPALIVE_SECONDS = 10.0

router = APIRouter(dependencies=[
    Depends(verify_token),
    Depends(enforce_ai_rate_limit),
    Depends(enforce_ai_concurrency),
])


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


# ============================================
# 请求模型
# ============================================

class ExplainRequest(BaseModel):
    """讲题请求"""
    question_content: str = Field(..., description="题目内容")
    answer: str = Field(..., description="标准答案")
    steps: str = Field(default="", description="解题步骤")
    student_message: str = Field(default="我想学习这道题", description="学生消息")
    history: Optional[list[dict]] = Field(default=None, description="对话历史")
    grade: str = Field(default="", description="年级")
    subject: str = Field(default="数学", description="学科")
    knowledge_point: str = Field(default="未知", description="知识点")
    mastery_level: str = Field(default="中", description="掌握程度(弱/中/强)")
    common_errors: str = Field(default="无历史数据", description="常见错误类型")
    teaching_style: str = Field(default="guided", description="讲题风格: guided=引导式, direct=快速答案式")
    mode: str = Field(default="explain", description="对话模式: explain=讲题, chat=自由对话")
    memory_context: str = Field(default="", description="家族知识库/日记/关键事件等检索上下文")
    viewer_role: str = Field(default="STUDENT", description="当前查看/对话视图: STUDENT/PARENT/ADMIN")
    target_role: str = Field(default="STUDENT", description="当前学习对象角色: STUDENT/PARENT/ADMIN")
    client_timestamp: str = Field(default="", description="用户提问时的客户端时间戳 ISO")
    client_timezone: str = Field(default="", description="用户客户端时区")


class GradeRequest(BaseModel):
    """批改请求"""
    question_content: str = Field(..., description="题目内容")
    answer: str = Field(..., description="标准答案")
    steps: str = Field(default="", description="解题步骤")
    student_answer: str = Field(..., description="学生答案")
    subject: str = Field(default="数学")
    grade: str = Field(default="初中")


class GenerateRequest(BaseModel):
    """出题请求"""
    subject: str = Field(default="数学")
    grade: str = Field(default="初中")
    knowledge_point: str = Field(..., description="知识点")
    question_type: str = Field(default="CALCULATION", description="题型")
    difficulty: int = Field(default=3, ge=1, le=5, description="难度1-5")
    count: int = Field(default=5, ge=1, le=10, description="数量")
    additional_requirements: str = Field(default="无")


class MistakeReviewRequest(BaseModel):
    """错题复盘请求"""
    question_content: str = Field(..., description="题目内容")
    answer: str = Field(..., description="标准答案")
    student_answer: str = Field(..., description="学生原答案")
    steps: str = Field(default="", description="参考解题步骤")
    grade_result: Optional[dict] = Field(default=None, description="批改结果/历史错因")
    grade: str = Field(default="初中")
    subject: str = Field(default="数学")
    knowledge_point: str = Field(default="未知")
    weak_points: list[str] = Field(default_factory=list, description="最近薄弱点")


class DailyPracticeRequest(BaseModel):
    """每日短练请求"""
    knowledge_point: str = Field(..., description="今日知识点/单元")
    grade: str = Field(default="初中")
    subject: str = Field(default="数学")
    mastery_level: str = Field(default="中", description="掌握程度(弱/中/强)")
    available_minutes: int = Field(default=15, ge=5, le=60)
    difficulty: str = Field(default="标准", description="基础/标准/提高")
    question_count: int = Field(default=5, ge=3, le=8)
    weak_points: list[str] = Field(default_factory=list, description="最近错题或薄弱点")
    scenario: str = Field(default="学生自练", description="学生自练/家长陪练/老师布置")


class ExamReviewRequest(BaseModel):
    """测评后复习建议请求"""
    exam_goal: str = Field(default="阶段测评提升", description="考试目标")
    score_summary: str = Field(..., description="当前得分/正确率摘要")
    grade: str = Field(default="初中")
    subject: str = Field(default="数学")
    profiles: dict = Field(default_factory=dict, description="历史表现")
    weak_points: list[str] = Field(default_factory=list, description="薄弱知识点")
    recent_mistakes: list[dict] = Field(default_factory=list, description="最近错题摘要")
    available_minutes: int = Field(default=30, ge=10, le=180)
    review_days: int = Field(default=7, ge=1, le=30)


class StudyPlanRequest(BaseModel):
    """学习计划请求"""
    learning_goal: str = Field(..., description="学习目标")
    grade: str = Field(default="初中")
    subject: str = Field(default="数学")
    profiles: dict = Field(default_factory=dict, description="历史表现")
    weak_points: list[str] = Field(default_factory=list, description="薄弱知识点")
    available_minutes: int = Field(default=30, ge=10, le=180)
    plan_days: int = Field(default=7, ge=1, le=30)
    constraints: str = Field(default="无", description="学习偏好/限制")


# ============================================
# API 端点
# ============================================

@router.post("/explain")
async def explain_question(request: ExplainRequest):
    """
    讲题 — SSE流式输出

    前端通过EventSource接收，实现打字机效果
    """
    # 输入清理
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

            except Exception as e:
                logger.error(f"璁查娴佸紡閿欒: {e}")
                await queue.put({"error": str(e)})

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
    """
    讲题 — 同步模式（非流式，供Java后端内部调用）
    """
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
    except Exception as e:
        logger.error(f"讲题同步错误: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/grade")
async def grade_answer(request: GradeRequest):
    """
    批改学生答案

    Returns:
        dict: 步骤级评分结果
    """
    try:
        student_answer = sanitize_text(request.student_answer)
        question_content = sanitize_text(request.question_content)

        result = await grader_agent.grade(
            question_content=question_content,
            answer=request.answer,
            steps=request.steps,
            student_answer=student_answer,
            subject=request.subject,
            grade=request.grade,
        )
        return {"success": True, "data": result}
    except Exception as e:
        logger.error(f"批改错误: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/grade/quick")
async def quick_grade_answer(request: GradeRequest):
    """快速批改（轻量版）"""
    try:
        student_answer = sanitize_text(request.student_answer)
        question_content = sanitize_text(request.question_content)
        result = await grader_agent.quick_grade(
            question=question_content,
            answer=request.answer,
            student_answer=student_answer,
        )
        return {"success": True, "data": result}
    except Exception as e:
        logger.error(f"快速批改错误: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/generate")
async def generate_questions(request: GenerateRequest):
    """
    AI出题

    Returns:
        dict: {"success": bool, "questions": list[dict]}
    """
    try:
        questions = await generator_agent.generate(
            subject=request.subject,
            grade=request.grade,
            knowledge_point=request.knowledge_point,
            question_type=request.question_type,
            difficulty=request.difficulty,
            count=request.count,
            additional_requirements=request.additional_requirements,
        )
        return {"success": True, "questions": questions, "count": len(questions)}
    except Exception as e:
        logger.error(f"出题错误: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/skills/mistake-review")
async def run_mistake_review(request: MistakeReviewRequest):
    """错题复盘 workflow."""
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
    except Exception as e:
        logger.error(f"错题复盘错误: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/skills/daily-practice")
async def run_daily_practice(request: DailyPracticeRequest):
    """每日数学短练 workflow."""
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
    except Exception as e:
        logger.error(f"每日短练错误: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/skills/exam-review")
async def run_exam_review(request: ExamReviewRequest):
    """测评后复习建议 workflow."""
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
    except Exception as e:
        logger.error(f"测评复习建议错误: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/skills/study-plan")
async def run_study_plan(request: StudyPlanRequest):
    """学习计划 workflow."""
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
    except Exception as e:
        logger.error(f"学习计划错误: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/extract")
async def extract_uploaded_content(file: UploadFile = File(...)):
    """Extract readable learning content from an uploaded file."""
    try:
        data = await file.read()
        result = extract_content(
            filename=file.filename or "upload",
            content_type=file.content_type,
            data=data,
        )
        return {"success": True, "data": result.to_dict()}
    except Exception as e:
        logger.error(f"文件内容提取失败: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/generate/variation")
async def generate_variation(
    original_question: str,
    original_difficulty: int = 3,
    target_difficulty: int = 3,
):
    """生成变式题"""
    try:
        result = await generator_agent.generate_variation(
            original_question=original_question,
            original_difficulty=original_difficulty,
            target_difficulty=target_difficulty,
        )
        return {"success": True, "data": result}
    except Exception as e:
        logger.error(f"变式题生成错误: {e}")
        raise HTTPException(status_code=500, detail=str(e))
