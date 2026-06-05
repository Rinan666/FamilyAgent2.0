"""
家教API路由 — 讲题、批改、出题
"""
import json
import logging
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from app.agents.tutor_agent import tutor_agent
from app.agents.grader_agent import grader_agent
from app.agents.generator_agent import generator_agent
from app.middleware.auth import verify_token
from app.utils.sanitizer import sanitize_text

logger = logging.getLogger("familyagent.ai.api.tutor")

router = APIRouter(dependencies=[Depends(verify_token)])


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

    async def generate():
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
                memory_context=request.memory_context,
            ):
                yield f"data: {json.dumps({'content': chunk})}\n\n"

            # 结束标记
            yield f"data: {json.dumps({'done': True})}\n\n"

        except Exception as e:
            logger.error(f"讲题流式错误: {e}")
            yield f"data: {json.dumps({'error': str(e)})}\n\n"

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
            memory_context=request.memory_context,
        )
        return {"success": True, "content": result}
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
