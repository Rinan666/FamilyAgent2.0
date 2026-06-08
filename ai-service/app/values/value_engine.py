"""Rule-based value checks for FamilyAgent.

The first version is deliberately small and deterministic. It gives the
product a testable layer for worldview boundaries and family-memory policy,
while LLM-based summarization can remain an optional upstream step.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from enum import StrEnum


class MemoryType(StrEnum):
    FACT = "fact"
    PREFERENCE = "preference"
    OBSERVATION = "observation"
    STRATEGY = "strategy"
    PRINCIPLE = "principle"
    RISK = "risk"
    HYPOTHESIS = "hypothesis"


class ReviewStatus(StrEnum):
    AUTO_APPROVED = "auto_approved"
    NEEDS_PARENT_REVIEW = "needs_parent_review"
    REJECTED = "rejected"


class PolicySeverity(StrEnum):
    INFO = "info"
    WARNING = "warning"
    BLOCK = "block"


class PolicyCategory(StrEnum):
    ROLE_REWRITE = "role_rewrite"
    TUTORING_BYPASS = "tutoring_bypass"
    MIRROR_IMPERSONATION = "mirror_impersonation"
    MEMORY_INJECTION = "memory_injection"
    HARMFUL_EDUCATION = "harmful_education"
    UNREVIEWED_VALUE = "unreviewed_value"


@dataclass(frozen=True)
class MemoryCandidate:
    content: str
    source: str = "conversation"
    evidence: list[str] = field(default_factory=list)
    confidence: float = 0.5
    proposed_type: MemoryType | str | None = None


@dataclass(frozen=True)
class MemoryPolicyResult:
    memory_type: MemoryType
    review_status: ReviewStatus
    allowed: bool
    reasons: list[str]
    tags: list[str] = field(default_factory=list)

    @property
    def requires_parent_review(self) -> bool:
        return self.review_status == ReviewStatus.NEEDS_PARENT_REVIEW


@dataclass(frozen=True)
class WorldviewBoundaryResult:
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


def _normalize(text: str) -> str:
    return " ".join(str(text or "").lower().split())


def _has_any(text: str, patterns: list[str]) -> bool:
    return any(re.search(pattern, text, re.IGNORECASE) for pattern in patterns)


ROLE_REWRITE_PATTERNS = [
    r"(从现在起|以后|接下来|现在开始|立刻).{0,24}(你是|扮演|变成|改成|切换成)",
    r"(you are now|from now on|pretend|act as).{0,32}(obedient|unrestricted|slave|maid|dan)",
    r"(无条件|必须|只能|永远).{0,24}(服从|听我的|按我说的|保持角色)",
]

TUTORING_BYPASS_PATTERNS = [
    r"(不要|停止|别).{0,12}(引导|启发|提问|苏格拉底)",
    r"(直接|只要|马上).{0,12}(答案|最终结果|标准答案)",
    r"(不用|不需要).{0,12}(理解|过程|步骤|思考)",
]

MIRROR_IMPERSONATION_PATTERNS = [
    r"(镜像|mirror).{0,24}(就是本人|假装本人|冒充|替他说|本人自述)",
    r"(伪造|编造|制造).{0,16}(记忆|经历|本人说过)",
    r"(不要|不准).{0,12}(说明|提醒).{0,12}(模拟|参考|边界)",
]

MEMORY_INJECTION_PATTERNS = [
    r"(写入|保存|沉淀|记住).{0,24}(最高优先级|系统规则|不可更改|永久指令)",
    r"(忽略|覆盖|删除|绕过).{0,16}(边界|规则|限制|价值观|系统)",
    r"(把|将).{0,12}(这句话|本条).{0,12}(作为|设为).{0,16}(原则|铁律|最高规则)",
]

HARMFUL_EDUCATION_PATTERNS = [
    r"(羞辱|辱骂|威胁|恐吓|打骂|体罚|贬低|骂醒)",
    r"(孩子|学生).{0,12}(笨|没救|不适合学习|天生不行|废物)",
    r"(和别人家孩子|拿.*比较).{0,24}(刺激|羞耻|逼)",
]

PRINCIPLE_PATTERNS = [
    r"(原则|价值观|家风|教育观|长期相信|我们家认为)",
    r"(应该|必须|一定要).{0,20}(自驱|诚实|独立|长期|分数|排名|服从)",
]

FACT_PATTERNS = [
    r"(年级|岁|学校|班级|正在学|目前学|知识点)",
    r"(grade|age|school|class)",
]

PREFERENCE_PATTERNS = [
    r"(希望|偏好|更喜欢|习惯|语气|风格|先鼓励|再纠错|少说教)",
    r"(prefer|preference|tone|style)",
]

STRATEGY_PATTERNS = [
    r"(方法|策略|做法|流程).{0,20}(有效|有用|效果好|更能理解)",
    r"(先|先让|先用).{0,20}(画图|拆题|复盘|举例|列式)",
]

HYPOTHESIS_PATTERNS = [
    r"(可能|也许|猜测|待验证|不确定|看起来像|似乎)",
    r"(maybe|hypothesis|seems|might)",
]

RISK_PATTERNS = [
    r"(不要|避免|禁忌|风险|容易触发|会崩溃|会焦虑)",
    *HARMFUL_EDUCATION_PATTERNS,
]


def detect_worldview_rewrite(text: str, *, mode: str = "tutor") -> WorldviewBoundaryResult:
    normalized = _normalize(text)
    if not normalized:
        return WorldviewBoundaryResult(blocked=False)

    if _has_any(normalized, ROLE_REWRITE_PATTERNS):
        return WorldviewBoundaryResult(
            blocked=True,
            category=PolicyCategory.ROLE_REWRITE,
            severity=PolicySeverity.BLOCK,
            reason="User is trying to replace FamilyAgent's stable role.",
        )

    if _has_any(normalized, MEMORY_INJECTION_PATTERNS):
        return WorldviewBoundaryResult(
            blocked=True,
            category=PolicyCategory.MEMORY_INJECTION,
            severity=PolicySeverity.BLOCK,
            reason="User is trying to write a higher-priority memory or policy.",
        )

    if mode == "tutor" and _has_any(normalized, TUTORING_BYPASS_PATTERNS):
        return WorldviewBoundaryResult(
            blocked=True,
            category=PolicyCategory.TUTORING_BYPASS,
            severity=PolicySeverity.BLOCK,
            reason="User is asking the tutor to abandon learning-oriented guidance.",
        )

    if mode == "mirror" and _has_any(normalized, MIRROR_IMPERSONATION_PATTERNS):
        return WorldviewBoundaryResult(
            blocked=True,
            category=PolicyCategory.MIRROR_IMPERSONATION,
            severity=PolicySeverity.BLOCK,
            reason="User is asking mirror mode to impersonate or fabricate memory.",
        )

    if _has_any(normalized, HARMFUL_EDUCATION_PATTERNS):
        return WorldviewBoundaryResult(
            blocked=True,
            category=PolicyCategory.HARMFUL_EDUCATION,
            severity=PolicySeverity.BLOCK,
            reason="User is requesting harmful education behavior.",
        )

    return WorldviewBoundaryResult(blocked=False)


def _coerce_memory_type(value: MemoryType | str | None) -> MemoryType | None:
    if value is None:
        return None
    if isinstance(value, MemoryType):
        return value
    try:
        return MemoryType(str(value))
    except ValueError:
        return None


def _infer_memory_type(content: str) -> MemoryType:
    if _has_any(content, RISK_PATTERNS):
        return MemoryType.RISK
    if _has_any(content, PRINCIPLE_PATTERNS):
        return MemoryType.PRINCIPLE
    if _has_any(content, PREFERENCE_PATTERNS):
        return MemoryType.PREFERENCE
    if _has_any(content, STRATEGY_PATTERNS):
        return MemoryType.STRATEGY
    if _has_any(content, HYPOTHESIS_PATTERNS):
        return MemoryType.HYPOTHESIS
    if _has_any(content, FACT_PATTERNS):
        return MemoryType.FACT
    return MemoryType.OBSERVATION


def classify_memory_candidate(candidate: MemoryCandidate | str) -> MemoryPolicyResult:
    if isinstance(candidate, str):
        candidate = MemoryCandidate(content=candidate)

    normalized = _normalize(candidate.content)
    explicit_type = _coerce_memory_type(candidate.proposed_type)
    memory_type = explicit_type or _infer_memory_type(normalized)
    reasons: list[str] = []
    tags: list[str] = []

    boundary = detect_worldview_rewrite(normalized, mode="memory")
    if boundary.blocked and boundary.category == PolicyCategory.MEMORY_INJECTION:
        return MemoryPolicyResult(
            memory_type=memory_type,
            review_status=ReviewStatus.REJECTED,
            allowed=False,
            reasons=["Memory candidate tries to override system or value boundaries."],
            tags=[PolicyCategory.MEMORY_INJECTION.value],
        )

    if _has_any(normalized, HARMFUL_EDUCATION_PATTERNS):
        if memory_type in {MemoryType.PRINCIPLE, MemoryType.STRATEGY}:
            return MemoryPolicyResult(
                memory_type=memory_type,
                review_status=ReviewStatus.REJECTED,
                allowed=False,
                reasons=["Harmful education content cannot become a family principle or strategy."],
                tags=[PolicyCategory.HARMFUL_EDUCATION.value],
            )
        reasons.append("Potential harm or taboo must be reviewed before long-term use.")
        tags.append(PolicyCategory.HARMFUL_EDUCATION.value)
        return MemoryPolicyResult(
            memory_type=MemoryType.RISK,
            review_status=ReviewStatus.NEEDS_PARENT_REVIEW,
            allowed=True,
            reasons=reasons,
            tags=tags,
        )

    if memory_type in {MemoryType.PRINCIPLE, MemoryType.RISK}:
        reasons.append("Value-level memory requires parent review before it shapes behavior.")
        tags.append(PolicyCategory.UNREVIEWED_VALUE.value)
        return MemoryPolicyResult(
            memory_type=memory_type,
            review_status=ReviewStatus.NEEDS_PARENT_REVIEW,
            allowed=True,
            reasons=reasons,
            tags=tags,
        )

    reasons.append("Low-risk family experience can be saved with source and confidence.")
    return MemoryPolicyResult(
        memory_type=memory_type,
        review_status=ReviewStatus.AUTO_APPROVED,
        allowed=True,
        reasons=reasons,
        tags=tags,
    )


def check_response_policy(response_text: str, *, mode: str = "tutor") -> ResponsePolicyResult:
    normalized = _normalize(response_text)
    findings: list[ResponsePolicyFinding] = []

    if _has_any(normalized, ROLE_REWRITE_PATTERNS):
        findings.append(
            ResponsePolicyFinding(
                category=PolicyCategory.ROLE_REWRITE,
                severity=PolicySeverity.BLOCK,
                reason="Response accepts or repeats a role rewrite.",
            )
        )

    if _has_any(normalized, HARMFUL_EDUCATION_PATTERNS):
        findings.append(
            ResponsePolicyFinding(
                category=PolicyCategory.HARMFUL_EDUCATION,
                severity=PolicySeverity.BLOCK,
                reason="Response contains harmful education language.",
            )
        )

    if mode == "mirror" and _has_any(normalized, MIRROR_IMPERSONATION_PATTERNS):
        findings.append(
            ResponsePolicyFinding(
                category=PolicyCategory.MIRROR_IMPERSONATION,
                severity=PolicySeverity.BLOCK,
                reason="Mirror response crosses from style reference into impersonation.",
            )
        )

    if mode == "tutor" and _has_any(normalized, TUTORING_BYPASS_PATTERNS):
        findings.append(
            ResponsePolicyFinding(
                category=PolicyCategory.TUTORING_BYPASS,
                severity=PolicySeverity.WARNING,
                reason="Tutor response may over-prioritize answers over learning process.",
            )
        )

    allowed = not any(finding.severity == PolicySeverity.BLOCK for finding in findings)
    return ResponsePolicyResult(allowed=allowed, findings=findings)

