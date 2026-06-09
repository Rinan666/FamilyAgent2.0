"""
Primary FamilyAgent chat routes.
"""
from fastapi import APIRouter, Depends

from app.api.tutor import ExplainRequest, explain_question
from app.middleware.auth import verify_token
from app.utils.safety_limits import enforce_ai_concurrency, enforce_ai_rate_limit

router = APIRouter(dependencies=[
    Depends(verify_token),
    Depends(enforce_ai_rate_limit),
    Depends(enforce_ai_concurrency),
])


@router.post("/chat/stream")
async def stream_chat(request: ExplainRequest):
    """Primary SSE endpoint for FamilyAgent chat."""
    return await explain_question(request)
