"""
Input sanitization helpers.
"""
import re
from typing import Optional


def sanitize_text(text: str, max_length: int = 10000) -> str:
    """
    Sanitize free-form user input.
    - Limit maximum length
    - Remove dangerous markers
    """
    if not text:
        return ""

    # Truncate oversized payloads early.
    text = text[:max_length]

    # Remove obvious prompt-injection markers.
    text = re.sub(r"<script.*?</script>", "", text, flags=re.DOTALL | re.IGNORECASE)
    text = re.sub(r"```system.*?```", "", text, flags=re.DOTALL)

    return text.strip()


def sanitize_math_expression(expr: str) -> Optional[str]:
    """
    Validate and sanitize a math expression.
    Only allow safe sympy-style operations.
    """
    if not expr or len(expr) > 500:
        return None

    # Block unsafe code execution markers.
    dangerous = ["import", "__", "exec", "eval", "open", "os.", "sys.", "subprocess"]
    for d in dangerous:
        if d in expr.lower():
            return None

    return expr
