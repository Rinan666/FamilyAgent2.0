"""
数学执行引擎 — 使用 sympy 进行数学验证

确保数学题答案的正确性，避免LLM幻觉
"""
import logging
import re
from typing import Optional

import sympy
from sympy import symbols, solve, simplify
from sympy.parsing.sympy_parser import (
    parse_expr,
    standard_transformations,
    implicit_multiplication_application,
)

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

    def _normalize_answer_text(self, answer: str) -> str:
        """Normalize common final-answer forms before symbolic parsing."""
        text = (answer or "").strip().lower()
        replacements = {
            " ": "",
            "\n": "",
            "\t": "",
            "，": ",",
            "。": "",
            "；": ";",
            "：": ":",
            ":": "",
            "（": "(",
            "）": ")",
            "＝": "=",
            "×": "*",
            "÷": "/",
            "答案": "",
            "解": "",
        }
        for source, target in replacements.items():
            text = text.replace(source, target)
        text = re.sub(r"^(x|y|z|k|a|b|c|m|n)=", "", text)
        return text

    def _answer_candidates(self, answer: str) -> list[str]:
        """Generate candidate expressions from forms like 'x = 4' and '答案：4'."""
        raw = (answer or "").strip()
        if not raw:
            return []

        candidates = {raw, self._normalize_answer_text(raw)}
        for part in re.split(r"[,;，；\n]", raw):
            part = part.strip()
            if part:
                candidates.add(part)
                candidates.add(self._normalize_answer_text(part))

        for candidate in list(candidates):
            if "=" in candidate and "==" not in candidate:
                left, right = candidate.split("=", 1)
                if re.fullmatch(r"\s*[a-zA-Z]\s*", left):
                    candidates.add(right.strip())
                    candidates.add(self._normalize_answer_text(right))

        return [candidate for candidate in candidates if candidate]

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
        if self._normalize_answer_text(student_answer) == self._normalize_answer_text(expected_answer):
            return {
                "is_correct": True,
                "method": "normalized_string_compare",
            }

        student_candidates = self._answer_candidates(student_answer)
        expected_candidates = self._answer_candidates(expected_answer)

        for student_text in student_candidates:
            for expected_text in expected_candidates:
                if self._normalize_answer_text(student_text) == self._normalize_answer_text(expected_text):
                    return {
                        "is_correct": True,
                        "method": "normalized_string_compare",
                    }

                student = self.parse_expression(student_text)
                expected = self.parse_expression(expected_text)
                if student is None or expected is None:
                    continue

                try:
                    if simplify(student - expected) == 0:
                        return {
                            "is_correct": True,
                            "method": "sympy",
                        }

                    student_val = float(student.evalf())
                    expected_val = float(expected.evalf())
                    diff = abs(student_val - expected_val)
                    if diff < 1e-9:
                        return {
                            "is_correct": True,
                            "student_numeric": student_val,
                            "expected_numeric": expected_val,
                            "difference": diff,
                            "method": "sympy_numeric",
                        }
                except Exception as e:
                    logger.debug(f"跳过答案候选验证 {student_text} vs {expected_text}: {e}")

        return {
            "is_correct": False,
            "method": "math_or_string_compare",
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
