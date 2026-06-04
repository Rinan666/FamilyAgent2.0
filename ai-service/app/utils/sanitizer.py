"""
输入安全处理
"""
import re
from typing import Optional


def sanitize_text(text: str, max_length: int = 10000) -> str:
    """
    清理用户输入文本
    - 限制长度
    - 移除危险字符
    """
    if not text:
        return ""

    # 截断
    text = text[:max_length]

    # 移除潜在的注入标记
    text = re.sub(r"<script.*?</script>", "", text, flags=re.DOTALL | re.IGNORECASE)
    text = re.sub(r"```system.*?```", "", text, flags=re.DOTALL)

    return text.strip()


def sanitize_math_expression(expr: str) -> Optional[str]:
    """
    验证并清理数学表达式
    只允许安全的sympy操作
    """
    if not expr or len(expr) > 500:
        return None

    # 禁止危险操作
    dangerous = ["import", "__", "exec", "eval", "open", "os.", "sys.", "subprocess"]
    for d in dangerous:
        if d in expr.lower():
            return None

    return expr
