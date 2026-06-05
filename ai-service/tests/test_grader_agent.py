"""
批改 Agent 测试
"""
import pytest

from app.agents.grader_agent import GraderAgent


class TestGraderAgent:
    """批改 Agent"""

    def setup_method(self):
        self.grader = GraderAgent()

    @pytest.mark.asyncio
    async def test_quick_grade_correct_answer(self):
        result = await self.grader.quick_grade(
            question="2*x + 5 = 13",
            answer="x = 4",
            student_answer="4",
        )

        assert result["is_correct"] is True
        assert result["overall_score"] >= 90
        assert result["grading_mode"] == "quick"
        assert result["needs_ai_review"] is False

    @pytest.mark.asyncio
    async def test_quick_grade_wrong_answer_marks_review_needed(self):
        result = await self.grader.quick_grade(
            question="2*x + 5 = 13",
            answer="x = 4",
            student_answer="5",
        )

        assert result["is_correct"] is False
        assert result["overall_score"] == 0
        assert result["grading_mode"] == "quick"
        assert result["needs_ai_review"] is True

    @pytest.mark.asyncio
    async def test_quick_grade_empty_answer(self):
        result = await self.grader.quick_grade(
            question="2*x + 5 = 13",
            answer="x = 4",
            student_answer="",
        )

        assert result["is_correct"] is False
        assert result["overall_score"] == 0
        assert result["math_verification"]["method"] == "empty_answer"
