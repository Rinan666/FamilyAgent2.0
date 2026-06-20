from app.api.memory_generation_helpers import _sanitize_persona_material_draft
from app.llm.prompts.memory import build_persona_material_draft_user_prompt


def test_persona_material_draft_sanitizer_keeps_raw_text_out_of_fallback_cards():
    raw_content = "这是一整段用户粘贴的原始材料，里面可能很长，不能在模型失败时自动变成可保存材料卡。"

    result = _sanitize_persona_material_draft(
        {"profile": {}, "materials": [], "reason": ""},
        {"name": "外公"},
        raw_content,
    )

    assert result["profile"]["name"] == "外公"
    assert result["materials"] == []
    assert raw_content not in result["reason"]


def test_persona_material_draft_sanitizer_bounds_generated_material_cards():
    result = _sanitize_persona_material_draft(
        {
            "profile": {"name": "先生"},
            "materials": [{
                "title": " 处事提醒 ",
                "content": "有" * 700,
                "tags": [" 家风 ", "家风", "处事"],
            }],
            "reason": "从原文提炼",
        },
        {},
        "raw",
    )

    assert result["materials"][0]["title"] == "处事提醒"
    assert len(result["materials"][0]["content"]) == 600
    assert result["materials"][0]["tags"][:2] == ["家风", "家风"]
    assert all(tag == tag.strip() for tag in result["materials"][0]["tags"])


def test_persona_material_draft_prompt_includes_profile_and_source_text():
    prompt = build_persona_material_draft_user_prompt(
        {
            "name": "外公",
            "description": "做事稳",
            "era_identity": "",
            "values": "",
            "speaking_style": "",
            "personality": "",
        },
        "三口之家",
        "先看事实，再下判断。",
    )

    assert "外公" in prompt
    assert "三口之家" in prompt
    assert "先看事实，再下判断。" in prompt
