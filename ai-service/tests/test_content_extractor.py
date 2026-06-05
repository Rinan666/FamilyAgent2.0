from app.services.content_extractor import extract_content


def test_extract_utf8_text_file():
    result = extract_content(
        filename="question.txt",
        content_type="text/plain",
        data="已知 2x + 5 = 13，求 x。".encode("utf-8"),
    )

    assert result.supported is True
    assert result.source_type == "text"
    assert "2x + 5 = 13" in result.text
    assert "【文件解析结果】" in result.structured_text


def test_extract_detects_question_answer_and_steps():
    result = extract_content(
        filename="worksheet.md",
        content_type="text/markdown",
        data=(
            "题目：已知 2x + 5 = 13，求 x。\n"
            "答案：x = 4\n"
            "解析：两边同时减 5，再除以 2。"
        ).encode("utf-8"),
    )

    assert result.supported is True
    assert result.detected_questions == ["已知 2x + 5 = 13，求 x。"]
    assert result.detected_answers == ["x = 4"]
    assert result.detected_steps == ["两边同时减 5，再除以 2。"]


def test_image_returns_ocr_guidance():
    result = extract_content(
        filename="question.png",
        content_type="image/png",
        data=b"not really an image",
    )

    assert result.supported is False
    assert result.source_type == "image"
    assert "OCR" in result.message
