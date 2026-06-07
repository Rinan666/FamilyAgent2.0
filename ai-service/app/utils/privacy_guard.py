"""
Privacy guard for AI-bound family content.

This module is intentionally lightweight: it gives FamilyAgent a stable
privacy boundary before introducing heavier PII tooling such as Presidio.
"""
from __future__ import annotations

import re
from dataclasses import dataclass, field


@dataclass(frozen=True)
class PrivacyFinding:
    """A detected sensitive fragment category."""

    category: str
    label: str


@dataclass(frozen=True)
class PrivacyGuardResult:
    """Redaction result and compact metadata for logging or UI hints."""

    text: str
    findings: list[PrivacyFinding] = field(default_factory=list)

    @property
    def has_findings(self) -> bool:
        return bool(self.findings)

    @property
    def categories(self) -> list[str]:
        seen: set[str] = set()
        result: list[str] = []
        for finding in self.findings:
            if finding.category not in seen:
                seen.add(finding.category)
                result.append(finding.category)
        return result


_PATTERNS: list[tuple[str, str, re.Pattern[str], str]] = [
    (
        "PHONE",
        "手机号",
        re.compile(r"(?<!\d)(?:\+?86[-\s]?)?1[3-9]\d{9}(?!\d)"),
        "[手机号]",
    ),
    (
        "ID_CARD",
        "身份证号",
        re.compile(
            r"(?<![0-9Xx])\d{6}(?:18|19|20)\d{2}"
            r"(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\d|3[01])\d{3}[\dXx](?![0-9Xx])"
        ),
        "[身份证号]",
    ),
    (
        "EMAIL",
        "邮箱",
        re.compile(r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b"),
        "[邮箱]",
    ),
    (
        "SCHOOL",
        "学校名称",
        re.compile(r"[\u4e00-\u9fffA-Za-z0-9]{2,30}(?:学校|小学|中学|高中|幼儿园)"),
        "[学校名称]",
    ),
    (
        "CLASS",
        "班级信息",
        re.compile(
            r"(?:幼儿园)?(?:小|初|高)?[一二三四五六七八九十\d]+"
            r"(?:年级|年|班)[一二三四五六七八九十\d]*班?"
        ),
        "[班级信息]",
    ),
]

_ADDRESS_PATTERN = re.compile(
    r"(?P<prefix>家庭住址|住址|地址|住在|家住)[：:\s]*(?P<value>[^，。；;\n]{4,80})"
)


def redact_ai_bound_text(text: str, max_length: int = 12000) -> PrivacyGuardResult:
    """
    Redact sensitive information before content is sent to an LLM.

    The goal is to protect family and minor data while preserving enough
    semantic context for tutoring, mirror reference, and memory-card tasks.
    """
    if not text:
        return PrivacyGuardResult(text="")

    redacted = text[:max_length]
    findings: list[PrivacyFinding] = []

    for category, label, pattern, replacement in _PATTERNS:
        redacted, count = pattern.subn(replacement, redacted)
        if count:
            findings.append(PrivacyFinding(category=category, label=label))

    def replace_address(match: re.Match[str]) -> str:
        findings.append(PrivacyFinding(category="ADDRESS", label="地址"))
        return f"{match.group('prefix')}：[地址信息]"

    redacted = _ADDRESS_PATTERN.sub(replace_address, redacted)
    return PrivacyGuardResult(text=redacted, findings=findings)


def append_privacy_note(text: str, result: PrivacyGuardResult) -> str:
    """Append a compact note when AI context was redacted."""
    if not result.has_findings:
        return text
    labels = "、".join(finding.label for finding in _unique_findings(result.findings))
    note = f"\n\n【隐私处理】进入 AI 前已自动隐藏：{labels}。不要尝试还原或猜测这些信息。"
    return text.rstrip() + note


def redact_with_note(text: str, max_length: int = 12000) -> PrivacyGuardResult:
    """Redact text and include a model-facing safety note."""
    result = redact_ai_bound_text(text, max_length=max_length)
    if not result.has_findings:
        return result
    return PrivacyGuardResult(
        text=append_privacy_note(result.text, result),
        findings=result.findings,
    )


def _unique_findings(findings: list[PrivacyFinding]) -> list[PrivacyFinding]:
    seen: set[str] = set()
    result: list[PrivacyFinding] = []
    for finding in findings:
        if finding.category in seen:
            continue
        seen.add(finding.category)
        result.append(finding)
    return result
