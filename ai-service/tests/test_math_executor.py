"""
数学执行沙箱测试
"""
import pytest
from app.engine.math_executor import MathSandbox


class TestMathSandbox:
    """数学引擎测试"""

    def setup_method(self):
        self.sandbox = MathSandbox()

    def test_simple_expression(self):
        """简单表达式计算"""
        result = self.sandbox.evaluate("2 + 3 * 4")
        assert result["success"]
        assert result["numeric"] == pytest.approx(14.0)

    def test_solve_linear_equation(self):
        """解一元一次方程"""
        result = self.sandbox.solve_equation("2*x - 4", "x")
        assert result["success"]
        assert "2" in str(result["solutions"])

    def test_solve_quadratic(self):
        """解一元二次方程"""
        result = self.sandbox.solve_equation("x**2 - 4", "x")
        assert result["success"]
        assert len(result["solutions"]) == 2

    def test_verify_correct_answer(self):
        """验证正确答案"""
        result = self.sandbox.verify_answer(
            question_expr="x**2 - 4 = 0",
            student_answer="2",
            expected_answer="2",
        )
        assert result["is_correct"]

    def test_verify_wrong_answer(self):
        """验证错误答案"""
        result = self.sandbox.verify_answer(
            question_expr="x**2 - 4 = 0",
            student_answer="3",
            expected_answer="2",
        )
        assert not result["is_correct"]

    def test_equivalent_expressions(self):
        """等价数值表达式验证"""
        result = self.sandbox.verify_answer(
            question_expr="",
            student_answer="3**2",
            expected_answer="9",
        )
        assert result["is_correct"]

    def test_numeric_equivalence(self):
        """数值等价性验证"""
        result = self.sandbox.verify_answer(
            question_expr="",
            student_answer="1/2",
            expected_answer="0.5",
        )
        assert result["is_correct"]

    def test_invalid_expression(self):
        """无效表达式处理"""
        result = self.sandbox.evaluate("import os; os.system('ls')")
        assert not result["success"]
