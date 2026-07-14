"""
Family skill registry.

This module describes FamilyAgent-native AI workflows and the memory layers
they are allowed to read or write. It is intentionally declarative: execution,
authorization, and persistence still live in existing services.
"""
from copy import deepcopy
from typing import Any

from app.runtime.skill_manifest import SAVE_MEMORY_PLAN_MANIFEST


MEMORY_LAYERS: list[dict[str, Any]] = [
    {
        "id": "L0",
        "name": "raw_records",
        "title": "原始记录层",
        "stores": [
            "diary_entries",
            "family_memories",
            "growth_guard_records",
            "chat_sessions",
        ],
        "authority": "backend",
        "description": "保存真实来源、作者、家庭、可见范围、撤回和删除状态。",
    },
    {
        "id": "L1",
        "name": "structured_summaries",
        "title": "结构化摘要层",
        "stores": [
            "diary_entries.structured",
            "family_memories.summary",
            "growth_guard_records.metadata",
        ],
        "authority": "backend+ai-service",
        "description": "把原文整理成标题、主题、场景、风险点、行动建议和标签。",
    },
    {
        "id": "L2",
        "name": "vector_indexes",
        "title": "向量索引层",
        "stores": ["memory_embeddings"],
        "authority": "backend+ai-service",
        "description": "在后端权限过滤后的候选集合内做语义召回。",
    },
    {
        "id": "L3",
        "name": "member_profiles",
        "title": "成员画像层",
        "stores": ["member_profiles", "mirror_agent_data"],
        "authority": "backend+ai-service",
        "description": "基于授权记录生成成员画像、表达风格摘要和镜像上下文。",
    },
    {
        "id": "L4",
        "name": "session_working_memory",
        "title": "会话工作记忆层",
        "stores": ["frontend.sessionSavedMemories", "sse.metadata"],
        "authority": "frontend+ai-service",
        "description": "当前会话刚保存的内容，索引尚未生成时也可在本轮继续使用。",
    },
]


FAMILY_SKILLS: list[dict[str, Any]] = [
    {
        **SAVE_MEMORY_PLAN_MANIFEST.as_dict(),
        "title": "对话记忆沉淀",
        "status": "ACTIVE",
        "category": "memory",
        "endpoint": "/ai/memory/save-plan",
        "confirmation_policy": "USER_CONFIRMATION_OR_EXPLICIT_SAVE_COMMAND",
        "permission_model": "backend_filters_family_scope_before_ai_context",
        "audit_events": [
            "SKILL_PLAN_CREATED",
            "USER_CONFIRMED_SAVE",
            "MEMORY_WRITTEN",
        ],
    },
    {
        "name": "organize_draft",
        "title": "口述草稿整理",
        "status": "ACTIVE",
        "category": "memory",
        "endpoint": "/ai/memory/organize-draft",
        "description": "把口述内容整理成日记、经验沉淀或成长观察草稿。",
        "reads": ["L0"],
        "writes": ["L1"],
        "requires_confirmation": True,
        "confirmation_policy": "RETURNS_DRAFT_ONLY",
        "permission_model": "backend_controls_final_persistence",
        "audit_events": ["SKILL_DRAFT_CREATED"],
    },
    {
        "name": "persona_material_draft",
        "title": "Persona material draft",
        "status": "ACTIVE",
        "category": "profile",
        "endpoint": "/ai/memory/persona-material-draft",
        "description": "Organizes pasted persona source text into editable profile suggestions and material cards.",
        "reads": ["L0"],
        "writes": ["L1"],
        "requires_confirmation": True,
        "confirmation_policy": "RETURNS_DRAFT_ONLY",
        "permission_model": "backend_controls_final_persona_material_persistence",
        "audit_events": ["PERSONA_MATERIAL_DRAFT_CREATED"],
    },
    {
        "name": "member_profile_rebuild",
        "title": "成员画像重建",
        "status": "PLANNED",
        "category": "profile",
        "endpoint": "",
        "description": "基于授权记录重建成员画像和镜像 Agent 可用摘要。",
        "reads": ["L0", "L1", "L2"],
        "writes": ["L3"],
        "requires_confirmation": False,
        "confirmation_policy": "BACKEND_ADMIN_OR_MEMBER_AUTHORIZATION_REQUIRED",
        "permission_model": "backend_filters_by_family_and_care_authorization",
        "audit_events": ["PROFILE_REBUILD_REQUESTED", "PROFILE_REBUILT"],
    },
    {
        "name": "mirror_context_prepare",
        "title": "镜像上下文准备",
        "status": "PLANNED",
        "category": "mirror",
        "endpoint": "",
        "description": "为镜像 Agent 准备授权上下文、边界提示和来源摘要。",
        "reads": ["L0", "L1", "L2", "L3"],
        "writes": [],
        "requires_confirmation": False,
        "confirmation_policy": "READ_ONLY_AUTHORIZED_CONTEXT",
        "permission_model": "backend_supplies_authorized_context_only",
        "audit_events": ["MIRROR_CONTEXT_PREPARED"],
    },
]


def list_memory_layers() -> list[dict[str, Any]]:
    """Return long-term memory layer definitions."""
    return deepcopy(MEMORY_LAYERS)


def list_family_skills(status: str | None = None) -> list[dict[str, Any]]:
    """Return registered family skills, optionally filtered by status."""
    normalized_status = status.upper() if status else ""
    skills = FAMILY_SKILLS
    if normalized_status:
        skills = [
            skill for skill in skills
            if str(skill.get("status", "")).upper() == normalized_status
        ]
    return deepcopy(skills)


def get_family_skill(name: str) -> dict[str, Any] | None:
    """Return a registered skill by stable name."""
    normalized_name = name.strip().lower()
    for skill in FAMILY_SKILLS:
        if str(skill.get("name", "")).lower() == normalized_name:
            return deepcopy(skill)
    return None


def family_skill_registry(status: str | None = None) -> dict[str, Any]:
    """Return the complete registry payload."""
    return {
        "memory_layers": list_memory_layers(),
        "skills": list_family_skills(status),
        "principles": [
            "Backend is the authority for family permissions and persistence.",
            "AI service organizes, plans, summarizes, and prepares context only.",
            "Agent-proposed memory writes require user confirmation.",
            "Explicit save commands may write after save planning and backend checks.",
        ],
    }
