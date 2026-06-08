from app.utils.privacy_guard import redact_ai_bound_text, redact_with_note


def test_redacts_common_family_pii():
    result = redact_ai_bound_text(
        "爷爷电话是13812345678，邮箱 old@example.com，"
        "孩子身份证 110105201001011234，就读星河实验小学三年级2班。"
    )

    assert "13812345678" not in result.text
    assert "old@example.com" not in result.text
    assert "110105201001011234" not in result.text
    assert "星河实验小学" not in result.text
    assert "三年级2班" not in result.text
    assert "[手机号]" in result.text
    assert "[邮箱]" in result.text
    assert "[身份证号]" in result.text
    assert "[学校名称]" in result.text
    assert "[班级信息]" in result.text
    assert result.categories == ["PHONE", "ID_CARD", "EMAIL", "SCHOOL", "CLASS"]


def test_redacts_address_after_address_marker():
    result = redact_ai_bound_text("家庭住址：北京市海淀区某某路12号3单元，最近上学路上很累。")

    assert "北京市海淀区某某路12号3单元" not in result.text
    assert "家庭住址：[地址信息]" in result.text
    assert result.categories == ["ADDRESS"]


def test_redact_with_note_tells_model_not_to_recover_hidden_info():
    result = redact_with_note("妈妈手机号是13900001111，请以后提醒她。")

    assert "13900001111" not in result.text
    assert "【隐私处理】" in result.text
    assert "不要尝试还原或猜测这些信息" in result.text


def test_keeps_non_sensitive_learning_content():
    text = "今天复盘一元一次方程，主要卡在移项变号。"
    result = redact_ai_bound_text(text)

    assert result.text == text
    assert result.categories == []


def test_keeps_calendar_dates_when_redacting_class_info():
    result = redact_ai_bound_text("1月11日我记录了一件事，孩子现在是三年级2班。")

    assert "1月11日" in result.text
    assert "[班级信息]" in result.text
    assert "三年级2班" not in result.text
