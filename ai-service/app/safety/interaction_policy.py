"""Deterministic checks for high-confidence Agent identity attacks.

These checks do not score memory value and must not decide whether ordinary
family content deserves a draft. Contextual content continues to the normal
model and confirmation flow.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from enum import StrEnum


class PolicySeverity(StrEnum):
    INFO = "info"
    BLOCK = "block"


class PolicyCategory(StrEnum):
    ROLE_REWRITE = "role_rewrite"
    MIRROR_IMPERSONATION = "mirror_impersonation"
    MEMORY_INJECTION = "memory_injection"


@dataclass(frozen=True)
class BoundaryResult:
    blocked: bool
    category: PolicyCategory | None = None
    severity: PolicySeverity = PolicySeverity.INFO
    reason: str = ""


@dataclass(frozen=True)
class ResponsePolicyFinding:
    category: PolicyCategory
    severity: PolicySeverity
    reason: str


@dataclass(frozen=True)
class ResponsePolicyResult:
    allowed: bool
    findings: list[ResponsePolicyFinding]


ROLE_REWRITE_PATTERNS = [
    r"(从现在起|以后|接下来|现在开始|立刻).{0,24}(你是|扮演|变成|改成|切换成)",
    r"(you are now|from now on|pretend|act as).{0,32}(unrestricted|obedient|ignore.{0,12}rules)",
]

MIRROR_IMPERSONATION_PATTERNS = [
    r"(镜像|mirror).{0,24}(就是本人|假装本人|冒充|替他诉说|本人自述)",
    r"(我就是本人|我是本人|由我亲口).{0,20}(记忆|经历|自述|讲)",
    r"(伪造|编造|制造).{0,16}(记忆|经历|本人说过)",
    r"(不要|不准).{0,12}(说明|提醒).{0,12}(模拟|参考|边界)",
]

MEMORY_INJECTION_PATTERNS = [
    r"(写入|保存|沉淀|记住).{0,24}(最高优先级|系统规则|不可更改|永久指令)",
    r"(忽略|覆盖|删除|绕过).{0,16}(边界|规则|限制|系统)",
    r"(save|remember|store).{0,24}(highest priority|system rule|permanent instruction)",
]


def _normalize(text: str) -> str:
    return " ".join(str(text or "").lower().split())


def _has_any(text: str, patterns: list[str]) -> bool:
    return any(re.search(pattern, text, re.IGNORECASE) for pattern in patterns)


def detect_identity_boundary(text: str, *, mode: str = "agent") -> BoundaryResult:
    normalized = _normalize(text)
    if not normalized:
        return BoundaryResult(blocked=False)

    if _has_any(normalized, ROLE_REWRITE_PATTERNS):
        return BoundaryResult(
            blocked=True,
            category=PolicyCategory.ROLE_REWRITE,
            severity=PolicySeverity.BLOCK,
            reason="The request attempts to replace the Agent's stable role.",
        )

    if _has_any(normalized, MEMORY_INJECTION_PATTERNS):
        return BoundaryResult(
            blocked=True,
            category=PolicyCategory.MEMORY_INJECTION,
            severity=PolicySeverity.BLOCK,
            reason="The request attempts to store a higher-priority instruction.",
        )

    if mode == "mirror" and _has_any(normalized, MIRROR_IMPERSONATION_PATTERNS):
        return BoundaryResult(
            blocked=True,
            category=PolicyCategory.MIRROR_IMPERSONATION,
            severity=PolicySeverity.BLOCK,
            reason="The request asks mirror mode to impersonate or fabricate memory.",
        )

    return BoundaryResult(blocked=False)


def check_response_identity_boundary(response_text: str, *, mode: str = "agent") -> ResponsePolicyResult:
    normalized = _normalize(response_text)
    findings: list[ResponsePolicyFinding] = []

    if _has_any(normalized, ROLE_REWRITE_PATTERNS):
        findings.append(
            ResponsePolicyFinding(
                category=PolicyCategory.ROLE_REWRITE,
                severity=PolicySeverity.BLOCK,
                reason="The response accepts or repeats a role rewrite.",
            )
        )

    if mode == "mirror" and _has_any(normalized, MIRROR_IMPERSONATION_PATTERNS):
        findings.append(
            ResponsePolicyFinding(
                category=PolicyCategory.MIRROR_IMPERSONATION,
                severity=PolicySeverity.BLOCK,
                reason="The mirror response crosses into impersonation.",
            )
        )

    return ResponsePolicyResult(allowed=not findings, findings=findings)
