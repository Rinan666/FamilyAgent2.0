from app.llm.prompts.chat import build_family_agent_system_prompt
from app.llm.prompts.mirror import MIRROR_AGENT_MODE_RULES


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
    assert "你是镜像参考 Agent，不是镜像对象本人" in prompt
    assert "镜像参考对象：妈妈。" in prompt
