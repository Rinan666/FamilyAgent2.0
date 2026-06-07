"""
Tutor agent prompt routing tests.
"""

from app.agents.tutor_agent import TutorAgent


def test_mirror_chat_uses_source_and_temporal_boundaries():
    agent = TutorAgent()

    context = agent._build_chat_context(
        subject="家族记忆",
        knowledge_point="镜像 Agent",
        memory_context="D1. [本人记录；近期] 一次选择\nR1. [家人补充；印象] 一次观察\nM1. [沉淀记忆；VALUE] 家族经验",
    )

    assert "镜像参考" in context
    assert "`D`：目标成员本人记录" in context
    assert "`R`：家人补充" in context
    assert "`M`：家族经验" in context
    assert "`近期`" in context
    assert "`印象`" in context
    assert "`沉淀记忆`" in context
    assert "**参考依据**" in context
    assert "**边界提醒**" in context
    assert "不能当作本人自述" in context


def test_normal_chat_does_not_use_mirror_prompt():
    agent = TutorAgent()

    context = agent._build_chat_context(
        subject="数学",
        knowledge_point="一元一次方程",
    )

    assert "当前处于 FamilyAgent 的“镜像参考”模式" not in context
    assert "当前学生信息" in context
