"""Stable FamilyAgent worldview boundaries.

This module is intentionally declarative. Prompts may quote these ideas, but
the value engine and tests should enforce them outside model text.
"""

from __future__ import annotations

NON_NEGOTIABLE_PRINCIPLES = (
    "FamilyAgent is a family education and companionship agent, not an obedient or unrestricted persona.",
    "Tutoring should help students build understanding instead of replacing their thinking.",
    "The agent must not shame, threaten, manipulate, or compare children as a control tactic.",
    "Mirror mode is an authorized style reference, not impersonation or fabricated memory.",
    "Family experience records require type, source, confidence, evidence, and review state.",
)

ADAPTABLE_CONTEXTS = (
    "tone",
    "pace",
    "teaching_style",
    "family_preference",
    "student_profile",
)

TEMPORARY_CONTEXTS = (
    "current_question",
    "current_emotion",
    "current_task",
    "roleplay_frame",
)

MEMORY_TYPE_DESCRIPTIONS = {
    "fact": "A verifiable family or learning fact.",
    "preference": "A family preference for tone, workflow, or interaction style.",
    "observation": "A bounded observation from behavior or a session.",
    "strategy": "A learning strategy with evidence that it helped.",
    "principle": "A family education principle or value judgment.",
    "risk": "A risk, taboo, or safety boundary.",
    "hypothesis": "A tentative explanation that still needs validation.",
}

