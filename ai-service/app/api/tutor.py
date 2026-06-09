"""
Supported tutor routes for FamilyAgent.

Legacy grading, quick-grading, question-generation, and math-verify routes
have been removed from the public AI service surface.
"""

from app.api.family_tutor import (
    DailyPracticeRequest,
    ExamReviewRequest,
    ExplainRequest,
    MistakeReviewRequest,
    StudyPlanRequest,
    explain_question,
    explain_question_sync,
    extract_uploaded_content,
    generate_variation,
    router,
    run_daily_practice,
    run_exam_review,
    run_mistake_review,
    run_study_plan,
)

__all__ = [
    "DailyPracticeRequest",
    "ExamReviewRequest",
    "ExplainRequest",
    "MistakeReviewRequest",
    "StudyPlanRequest",
    "explain_question",
    "explain_question_sync",
    "extract_uploaded_content",
    "generate_variation",
    "router",
    "run_daily_practice",
    "run_exam_review",
    "run_mistake_review",
    "run_study_plan",
]
