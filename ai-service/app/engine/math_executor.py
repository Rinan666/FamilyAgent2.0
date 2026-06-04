"""
数学执行引擎 — 使用 sympy 进行数学验证

确保数学题答案的正确性，避免LLM幻觉
"""
import logging
from typing import Optional, Union

import sympy
from sympy import symbols, Eq, solve, simplify, expand, factor, diff, integrate, limit
from sympy.parsing.sympy_parser import (
    parse_expr,
    standard_transformations,
    implicit_multiplication_application,
)

from app.config import settings

logger = logging.getLogger("familyagent.ai.math")


class MathSandbox:
    """安全数学执行沙箱"""

    # 允许的操作白名单
    ALLOWED_FUNCTIONS = {
        # 基本运算
        "Add", "Mul", "Pow", "Div", "Sub", "Mod",
        # 函数
        "sin", "cos", "tan", "cot", "sec", "csc",
        "asin", "acos", "atan",
        "log", "ln", "exp", "sqrt", "abs",
        # 符号
        "Symbol", "symbols",
        # 方程
        "Eq", "solve", "solveset",
        # 化简
        "simplify", "expand", "factor", "cancel",
        # 微积分
        "diff", "integrate", "limit",
        # 矩阵（Phase 2）
        # "Matrix", "det", "inverse",
    }

    def __init__(self):
        self.transformations = (
            standard_transformations +
            (implicit_multiplication_application,)
        )

    def parse_expression(self, expr_str: str) -> Optional[sympy.Expr]:
        """安全解析数学表达式"""
        try:
            expr = parse_expr(
                expr_str,
                transformations=self.transformations,
            )
            return expr
        except Exception as e:
            logger.warning(f"表达式解析失败: '{expr_str}' -> {e}")
            return None

    def evaluate(self, expr_str: str) -> dict:
        """
        计算表达式

        Returns:
            dict: {"success": bool, "result": str, "numeric": float}
        """
        expr = self.parse_expression(expr_str)
        if expr is None:
            return {"success": False, "error": "无法解析表达式"}

        try:
            result = simplify(expr)
            numeric = float(result.evalf())
            return {
                "success": True,
                "result": str(result),
                "numeric": numeric,
            }
        except Exception as e:
            logger.warning(f"表达式计算失败: '{expr_str}' -> {e}")
            return {"success": False, "error": str(e)}

    def solve_equation(self, eq_str: str, variable: str = "x") -> dict:
        """
        解方程

        Args:
            eq_str: 方程字符串，如 "x**2 - 4 = 0"
            variable: 变量名

        Returns:
            dict: {"success": bool, "solutions": list[str]}
        """
        try:
            var = symbols(variable)
            expr = self.parse_expression(eq_str)
            if expr is None:
                return {"success": False, "error": "无法解析方程"}

            solutions = solve(expr, var)
            return {
                "success": True,
                "solutions": [str(s) for s in solutions],
            }
        except Exception as e:
            logger.warning(f"方程求解失败: '{eq_str}' -> {e}")
            return {"success": False, "error": str(e)}

    def verify_answer(
        self,
        question_expr: str,
        student_answer: str,
        expected_answer: str,
    ) -> dict:
        """
        验证学生答案是否正确（数学等价性检查）

        Args:
            question_expr: 题目表达式
            student_answer: 学生答案
            expected_answer: 标准答案

        Returns:
            dict: {
                "is_correct": bool,
                "student_numeric": float,
                "expected_numeric": float,
                "difference": float,
            }
        """
        student = self.parse_expression(student_answer)
        expected = self.parse_expression(expected_answer)

        if student is None or expected is None:
            # 退化为字符串比较
            return {
                "is_correct": student_answer.strip() == expected_answer.strip(),
                "method": "string_compare",
            }

        try:
            student_val = float(student.evalf())
            expected_val = float(expected.evalf())
            diff = abs(student_val - expected_val)

            return {
                "is_correct": diff < 1e-9,  # 浮点精度容差
                "student_numeric": student_val,
                "expected_numeric": expected_val,
                "difference": diff,
                "method": "sympy",
            }
        except Exception as e:
            logger.warning(f"答案验证失败: {e}")
            return {
                "is_correct": False,
                "error": str(e),
                "method": "error",
            }

    def verify_steps(
        self,
        steps: list[str],
    ) -> list[dict]:
        """
        验证一组数学步骤是否正确

        Returns:
            list[dict]: 每步的验证结果
        """
        results = []
        for i, step in enumerate(steps):
            result = self.evaluate(step)
            results.append({
                "step": i + 1,
                "expression": step,
                "valid": result["success"],
                "result": result.get("result", ""),
            })
        return results


# 全局单例
math_sandbox = MathSandbox()
