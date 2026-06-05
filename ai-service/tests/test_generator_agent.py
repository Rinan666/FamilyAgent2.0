"""
Generator agent tests.
"""

from app.agents.generator_agent import GeneratorAgent
from app.llm.schemas import GENERATE_QUESTIONS_SCHEMA


class TestGeneratorAgent:
    """Generator agent normalization."""

    def setup_method(self):
        self.agent = GeneratorAgent()

    def test_normalize_question_accepts_answer_aliases(self):
        result = self.agent._normalize_question(
            {
                "type": "calculation",
                "difficulty": "4",
                "stem": "解方程：3x - 6 = 9",
                "final_answer": "x = 5",
                "solution_steps": ["3x = 15", "x = 5"],
                "analysis": "移项后两边同除以 3。",
                "tags": ["方程"],
            },
            subject="math",
            grade="grade7",
            question_type="CALCULATION",
            difficulty=3,
            knowledge_point="一元一次方程",
        )

        assert result["type"] == "CALCULATION"
        assert result["difficulty"] == 4
        assert result["content"]["stem"] == "解方程：3x - 6 = 9"
        assert result["answer"] == {
            "value": "x = 5",
            "steps": ["3x = 15", "x = 5"],
            "explanation": "移项后两边同除以 3。",
        }
        assert result["tags"] == ["一元一次方程", "方程"]

    def test_normalize_question_extracts_nested_text_objects(self):
        result = self.agent._normalize_question(
            {
                "content": {"stem": {"text": "计算：12 ÷ 3 + 5"}},
                "answer": {
                    "value": {"text": "9"},
                    "steps": [{"text": "12 ÷ 3 = 4"}, {"text": "4 + 5 = 9"}],
                    "explanation": {"text": "先算除法，再算加法。"},
                },
            },
            subject="math",
            grade="grade4",
            question_type="CALCULATION",
            difficulty=2,
            knowledge_point="四则混合运算",
        )

        assert result["content"]["stem"] == "计算：12 ÷ 3 + 5"
        assert result["answer"] == {
            "value": "9",
            "steps": ["12 ÷ 3 = 4", "4 + 5 = 9"],
            "explanation": "先算除法，再算加法。",
        }

    def test_generate_questions_schema_requires_auditable_answer(self):
        answer_schema = (
            GENERATE_QUESTIONS_SCHEMA["json_schema"]["schema"]["properties"]["questions"]
            ["items"]["properties"]["answer"]
        )

        assert answer_schema["required"] == ["value", "steps", "explanation"]
        assert answer_schema["properties"]["value"]["minLength"] == 1
        assert answer_schema["properties"]["steps"]["minItems"] == 2
        assert answer_schema["properties"]["explanation"]["minLength"] == 1
