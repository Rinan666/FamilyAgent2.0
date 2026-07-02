from app.llm.prompts.chat import build_family_agent_system_prompt
from app.llm.prompts.memory import (
    ORGANIZE_DRAFT_SYSTEM_PROMPT,
    PERSONA_MATERIAL_DRAFT_SYSTEM_PROMPT,
    SAVE_TOOL_PLAN_SYSTEM_PROMPT,
)
from app.llm.prompts.mirror import MIRROR_AGENT_MODE_RULES
from app.llm.prompts.persona import PERSONA_MEMBER_MODE_RULES


def test_build_family_agent_system_prompt_uses_defaults():
    prompt = build_family_agent_system_prompt(
        subject="",
        context_label="",
        memory_context="",
        viewer_role="",
        target_role="",
        response_mode="think",
        client_timestamp="",
        client_timezone="",
        public_web_context="",
    )

    assert "- subject: FamilyAgent" in prompt
    assert "- context_label: family_memory" in prompt
    assert "- viewer_role: MEMBER" in prompt
    assert "- target_role: MEMBER" in prompt
    assert "- 用户提问时间：未提供。" in prompt
    assert "未触发联网搜索。" in prompt
    assert "当前没有命中明确的已授权家族上下文。" in prompt
    assert "先回应用户真正关心的点" in prompt
    assert "不背模板" in prompt
    assert "不把真话说成刀子" in prompt
    assert "按用户当前问题自然回应" in prompt
    assert "不协助现实伤害" in prompt


def test_build_family_agent_system_prompt_includes_full_context():
    prompt = build_family_agent_system_prompt(
        subject="MirrorAgent",
        context_label="mirror_agent",
        memory_context="授权记忆片段",
        viewer_role="PARENT",
        target_role="MEMBER",
        response_mode="think",
        client_timestamp="2026-06-12T09:00:00+08:00",
        client_timezone="Asia/Shanghai",
        public_web_context="已联网搜索并整理公共信息。",
    )

    assert "- subject: MirrorAgent" in prompt
    assert "- context_label: mirror_agent" in prompt
    assert "- viewer_role: PARENT" in prompt
    assert "- target_role: MEMBER" in prompt
    assert "- 用户提问时间：2026-06-12T09:00:00+08:00" in prompt
    assert "- 用户本地时区：Asia/Shanghai" in prompt
    assert "当用户提到今天、明天、本周、最近、刚才等相对时间时，以这个时间为基准。" in prompt
    assert "已联网搜索并整理公共信息。" in prompt
    assert "授权记忆片段" in prompt


def test_build_family_agent_system_prompt_adds_self_intro_style_hint():
    prompt = build_family_agent_system_prompt(
        subject="FamilyAgent",
        context_label="family_memory",
        memory_context="",
        viewer_role="MEMBER",
        target_role="MEMBER",
        response_mode="quick",
        client_timestamp="2026-06-12T09:00:00+08:00",
        client_timezone="Asia/Shanghai",
        public_web_context="",
        member_message="介绍一下你自己",
    )

    assert "用户在问你是谁" in prompt
    assert "按用户当前问题自然回应" not in prompt


def test_build_family_agent_system_prompt_adds_mirror_rules_for_mirror_context():
    prompt = build_family_agent_system_prompt(
        subject="MirrorAgent",
        context_label="mirror_agent",
        memory_context="镜像参考对象：妈妈。",
        viewer_role="PARENT",
        target_role="MEMBER",
        response_mode="quick",
        client_timestamp="",
        client_timezone="",
        public_web_context="",
    )

    assert MIRROR_AGENT_MODE_RULES in prompt
    assert "高沉浸" in prompt
    assert "基于授权资料的视角模拟" in prompt
    assert "可以使用第一人称风格化表达" in prompt
    assert "镜像参考对象：妈妈。" in prompt


def test_build_family_agent_system_prompt_adds_persona_rules_for_persona_context():
    prompt = build_family_agent_system_prompt(
        subject="PersonaMemberAgent",
        context_label="persona_member",
        memory_context="精神成员：外公\n价值观：重视家风。",
        viewer_role="MEMBER",
        target_role="MEMBER",
        response_mode="think",
        client_timestamp="",
        client_timezone="",
        public_web_context="",
    )

    assert PERSONA_MEMBER_MODE_RULES in prompt
    assert "角色型成员" in prompt
    assert "忠于设定" in prompt
    assert "允许高沉浸角色表达" in prompt
    assert "简单问候不要扩写成独白" in prompt
    assert "不要写“你问……其实是在……”" in prompt
    assert "精神成员：外公" in prompt


def test_persona_and_mirror_rules_avoid_third_person_motive_analysis():
    assert "默认直接和用户对话" in PERSONA_MEMBER_MODE_RULES
    assert "避免第三者旁白和动机解读" in PERSONA_MEMBER_MODE_RULES
    assert "默认直接对用户说话" in MIRROR_AGENT_MODE_RULES
    assert "不要写“你问……其实是在……”" in MIRROR_AGENT_MODE_RULES


def test_memory_prompts_keep_human_quality_rules():
    assert "以后再看是否还能理解当时发生了什么、为什么重要" in SAVE_TOOL_PLAN_SYSTEM_PROMPT
    assert "保留原意和情绪质感" in SAVE_TOOL_PLAN_SYSTEM_PROMPT
    assert "像当事人愿意保存下来的记录" in ORGANIZE_DRAFT_SYSTEM_PROMPT
    assert "不要替换成泛泛的正确话" in ORGANIZE_DRAFT_SYSTEM_PROMPT
    assert "精神成员可以是虚构角色、家族象征、理想化人格" in PERSONA_MATERIAL_DRAFT_SYSTEM_PROMPT
    assert "不要把创作补全写成用户已经提供的既定设定" in PERSONA_MATERIAL_DRAFT_SYSTEM_PROMPT
    assert "稳定的价值取向、表达习惯、关系态度" in PERSONA_MATERIAL_DRAFT_SYSTEM_PROMPT
