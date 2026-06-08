"""File content extraction helpers for the tutor upload MVP."""
from __future__ import annotations

import re
import zipfile
from dataclasses import asdict, dataclass
from io import BytesIO
from pathlib import Path
from typing import Any
from xml.etree import ElementTree

from app.utils.sanitizer import sanitize_text


MAX_FILE_BYTES = 10 * 1024 * 1024
MAX_EXTRACTED_CHARS = 12000

TEXT_EXTENSIONS = {".txt", ".md", ".markdown", ".csv", ".json", ".tex"}
PDF_EXTENSIONS = {".pdf"}
DOCX_EXTENSIONS = {".docx"}
IMAGE_EXTENSIONS = {".png", ".jpg", ".jpeg", ".webp", ".bmp", ".gif", ".heic", ".heif"}


@dataclass
class ExtractedContent:
    filename: str
    source_type: str
    content_type: str
    text: str
    structured_text: str
    detected_questions: list[str]
    detected_answers: list[str]
    detected_steps: list[str]
    supported: bool
    message: str

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


def extract_content(filename: str, content_type: str | None, data: bytes) -> ExtractedContent:
    """Extract readable text from a small uploaded learning file."""
    safe_filename = Path(filename or "upload").name
    suffix = Path(safe_filename).suffix.lower()
    mime = content_type or "application/octet-stream"

    if len(data) > MAX_FILE_BYTES:
        return ExtractedContent(
            filename=safe_filename,
            source_type="unsupported",
            content_type=mime,
            text="",
            structured_text="",
            detected_questions=[],
            detected_answers=[],
            detected_steps=[],
            supported=False,
            message="文件超过 10MB，当前版本请先压缩或拆分后再上传。",
        )

    if suffix in IMAGE_EXTENSIONS or mime.startswith("image/"):
        return ExtractedContent(
            filename=safe_filename,
            source_type="image",
            content_type=mime,
            text="",
            structured_text="",
            detected_questions=[],
            detected_answers=[],
            detected_steps=[],
            supported=False,
            message="图片识别需要接入 OCR 或视觉模型。DeepSeek V4 Pro 适合基于识别后的文本讲题，但不能直接读取图片。",
        )

    try:
        if suffix in TEXT_EXTENSIONS or mime.startswith("text/"):
            text = _extract_text_file(data)
            source_type = "text"
        elif suffix in PDF_EXTENSIONS or mime == "application/pdf":
            text = _extract_pdf(data)
            source_type = "pdf"
        elif suffix in DOCX_EXTENSIONS or mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
            text = _extract_docx(data)
            source_type = "docx"
        else:
            return ExtractedContent(
                filename=safe_filename,
                source_type="unsupported",
                content_type=mime,
                text="",
                structured_text="",
                detected_questions=[],
                detected_answers=[],
                detected_steps=[],
                supported=False,
                message="暂不支持这种文件格式。请先上传 txt、md、pdf 或 docx 文件。",
            )
    except Exception:
        return ExtractedContent(
            filename=safe_filename,
            source_type="unsupported",
            content_type=mime,
            text="",
            structured_text="",
            detected_questions=[],
            detected_answers=[],
            detected_steps=[],
            supported=False,
            message="文件解析失败。请确认文件没有损坏，或先复制题目文本后发送给学习陪伴 AI。",
        )

    text = sanitize_text(_normalize_text(text), max_length=MAX_EXTRACTED_CHARS)
    parsed = _parse_learning_text(text)
    if not text:
        return ExtractedContent(
            filename=safe_filename,
            source_type=source_type,
            content_type=mime,
            text="",
            structured_text="",
            detected_questions=[],
            detected_answers=[],
            detected_steps=[],
            supported=False,
            message="没有提取到可读文本。扫描版 PDF 或图片题目需要 OCR/视觉模型支持。",
        )

    return ExtractedContent(
        filename=safe_filename,
        source_type=source_type,
        content_type=mime,
        text=text,
        structured_text=parsed["structured_text"],
        detected_questions=parsed["questions"],
        detected_answers=parsed["answers"],
        detected_steps=parsed["steps"],
        supported=True,
        message=_build_success_message(parsed),
    )


def _extract_text_file(data: bytes) -> str:
    for encoding in ("utf-8-sig", "utf-8", "gb18030"):
        try:
            return data.decode(encoding)
        except UnicodeDecodeError:
            continue
    return data.decode("utf-8", errors="ignore")


def _extract_pdf(data: bytes) -> str:
    try:
        from pypdf import PdfReader
    except ImportError as exc:
        raise RuntimeError("pypdf is not installed") from exc

    reader = PdfReader(BytesIO(data))
    pages = []
    for page in reader.pages[:20]:
        pages.append(page.extract_text() or "")
    return "\n\n".join(pages)


def _extract_docx(data: bytes) -> str:
    paragraphs: list[str] = []
    with zipfile.ZipFile(BytesIO(data)) as archive:
        document = archive.read("word/document.xml")

    root = ElementTree.fromstring(document)
    namespace = {"w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main"}
    for paragraph in root.findall(".//w:p", namespace):
        parts = [node.text or "" for node in paragraph.findall(".//w:t", namespace)]
        line = "".join(parts).strip()
        if line:
            paragraphs.append(line)
    return "\n".join(paragraphs)


def _normalize_text(text: str) -> str:
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def _parse_learning_text(text: str) -> dict[str, Any]:
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    questions: list[str] = []
    answers: list[str] = []
    steps: list[str] = []

    question_patterns = [
        r"^(?:第?\s*\d+\s*[题、.)]|题目[:：]|问题[:：]|例题[:：])\s*(.+)",
        r"^(.+[？?])$",
    ]
    answer_pattern = re.compile(r"^(?:答案|正确答案|解答|结果|答)[:：]\s*(.+)")
    step_pattern = re.compile(r"^(?:步骤|解题过程|过程|解析|思路|证明)[:：]\s*(.+)")

    for line in lines:
        answer_match = answer_pattern.match(line)
        if answer_match:
            answers.append(answer_match.group(1).strip())
            continue

        step_match = step_pattern.match(line)
        if step_match:
            steps.append(step_match.group(1).strip())
            continue

        for pattern in question_patterns:
            match = re.match(pattern, line)
            if match:
                questions.append(match.group(1).strip())
                break

    if not questions and text:
        questions = _split_possible_questions(text)

    questions = _dedupe_keep_order(questions)[:10]
    answers = _dedupe_keep_order(answers)[:10]
    steps = _dedupe_keep_order(steps)[:10]

    structured_lines = ["【文件解析结果】"]
    if questions:
        structured_lines.append("\n【识别到的题目】")
        structured_lines.extend(f"{index + 1}. {question}" for index, question in enumerate(questions))
    if answers:
        structured_lines.append("\n【识别到的答案】")
        structured_lines.extend(f"{index + 1}. {answer}" for index, answer in enumerate(answers))
    if steps:
        structured_lines.append("\n【识别到的解题过程/解析】")
        structured_lines.extend(f"{index + 1}. {step}" for index, step in enumerate(steps))
    structured_lines.append("\n【原始文本】")
    structured_lines.append(text)

    return {
        "questions": questions,
        "answers": answers,
        "steps": steps,
        "structured_text": "\n".join(structured_lines),
    }


def _split_possible_questions(text: str) -> list[str]:
    chunks = re.split(r"(?:\n\s*){2,}|(?=第?\s*\d+\s*[题、.)])", text)
    return [chunk.strip() for chunk in chunks if 8 <= len(chunk.strip()) <= 800][:10]


def _dedupe_keep_order(items: list[str]) -> list[str]:
    seen: set[str] = set()
    result: list[str] = []
    for item in items:
        normalized = re.sub(r"\s+", " ", item).strip()
        if normalized and normalized not in seen:
            seen.add(normalized)
            result.append(normalized)
    return result


def _build_success_message(parsed: dict[str, Any]) -> str:
    question_count = len(parsed["questions"])
    answer_count = len(parsed["answers"])
    step_count = len(parsed["steps"])
    if question_count or answer_count or step_count:
        return f"已提取文本，并识别到 {question_count} 个题目、{answer_count} 个答案、{step_count} 段解析。"
    return "已提取文本，但未识别出明确题目结构，可让学习陪伴 AI 先整理后讲解。"
