"""
评估API路由 — 学力评估与知识追踪
"""
import logging

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
from typing import Optional

from app.engine.knowledge_tracker import knowledge_tracker

logger = logging.getLogger("familyagent.ai.api.assessment")

router = APIRouter()


# ============================================
# 请求模型
# ============================================

class BKTUpdateRequest(BaseModel):
    """BKT知识追踪更新请求"""
    prior_mastery: float = Field(..., ge=0.0, le=1.0, description="先验掌握概率")
    is_correct: bool = Field(..., description="本次答题是否正确")
    days_since_last: int = Field(default=0, ge=0, description="距上次答题天数")


class AdaptiveSelectRequest(BaseModel):
    """自适应抽题请求"""
    question_pool: list = Field(..., description="题目池")
    profiles: dict = Field(..., description="学力档案 {kp_id: mastery}")
    n: int = Field(default=5, ge=1, le=20)
    zpd_ratio: float = Field(default=0.7, ge=0.0, le=1.0)


class RadarDataRequest(BaseModel):
    """雷达图数据请求"""
    profiles: dict = Field(..., description="学力档案 {kp_id: mastery}")
    kp_names: dict = Field(default={}, description="知识点名称映射 {kp_id: name}")


# ============================================
# API 端点
# ============================================

@router.post("/bkt/update")
async def update_bkt(request: BKTUpdateRequest):
    """
    BKT更新：根据一次答题结果更新掌握概率
    """
    posterior = knowledge_tracker.update(
        prior_mastery=request.prior_mastery,
        is_correct=request.is_correct,
        days_since_last=request.days_since_last,
    )

    return {
        "success": True,
        "prior_mastery": request.prior_mastery,
        "is_correct": request.is_correct,
        "posterior_mastery": round(posterior, 4),
        "mastery_level": knowledge_tracker.get_mastery_level(posterior),
        "delta": round(posterior - request.prior_mastery, 4),
    }


@router.post("/bkt/zpd")
async def get_zpd(request: AdaptiveSelectRequest):
    """
    获取最近发展区题目
    """
    selected = knowledge_tracker.adaptive_select(
        question_pool=request.question_pool,
        profiles=request.profiles,
        n=request.n,
        zpd_ratio=request.zpd_ratio,
    )

    zpd_kps = knowledge_tracker.get_zpd(request.profiles)

    return {
        "success": True,
        "zpd_kps": zpd_kps,
        "zpd_count": len(zpd_kps),
        "selected_questions": selected,
        "selected_count": len(selected),
    }


@router.post("/radar")
async def get_radar_data(request: RadarDataRequest):
    """
    生成学力雷达图数据
    """
    data = knowledge_tracker.generate_radar_data(
        profiles=request.profiles,
        kp_names=request.kp_names,
    )

    return {
        "success": True,
        "data": data,
    }


@router.get("/profile/analyze")
async def analyze_profile(
    user_id: int,
    profiles_json: Optional[str] = None,
):
    """
    分析学力档案，生成学习建议

    使用LLM对学力数据进行解读
    """
    from app.llm.client import llm_client
    from app.llm.schemas import ASSESSMENT_PROFILE_SCHEMA

    if not profiles_json:
        return {"success": False, "error": "请提供 profiles_json 参数"}

    prompt = f"""基于以下学生的学力档案数据，生成一份学习评估报告：

学力数据（JSON）：{profiles_json}

请分析：
1. 整体学习水平
2. 优势知识点
3. 薄弱环节
4. 具体的改进建议
5. 推荐优先学习顺序"""

    try:
        result = await llm_client.chat(
            [{"role": "user", "content": prompt}],
            temperature=0.5,
            max_tokens=4096,
            response_format=ASSESSMENT_PROFILE_SCHEMA,
        )
        import json
        return {"success": True, "data": json.loads(result)}
    except Exception as e:
        logger.error(f"学力分析错误: {e}")
        raise HTTPException(status_code=500, detail=str(e))
