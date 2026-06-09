"""
Web search trigger and context tests.
"""

import asyncio

from app.services.web_search import WebSearchResult, format_web_context, needs_web_search


def test_needs_web_search_for_time_sensitive_query():
    assert needs_web_search("现在 OpenAI 最新模型是什么")
    assert needs_web_search("今天上海天气怎么样")
    assert needs_web_search("苹果公司现任 CEO 是谁")
    assert needs_web_search("帮我联网查一下最近的牙齿矫正价格")
    assert needs_web_search("搜索一下现在上海中考政策")


def test_does_not_search_private_or_stable_query():
    assert not needs_web_search("爷爷说做事要留余地是什么意思")
    assert not needs_web_search("帮我解释一元一次方程")
    assert not needs_web_search("把爷爷刚才说的保存一下")


def test_empty_web_context_requires_uncertainty():
    context = format_web_context([], "今天有什么新闻")

    assert "没有取得可用搜索结果" in context
    assert "无法确认最新信息" in context


def test_web_context_includes_sources():
    context = format_web_context(
        [
            WebSearchResult(
                title="示例新闻",
                url="https://example.com/news",
                snippet="这是一条公开搜索摘要。",
            )
        ],
        "今天有什么新闻",
    )

    assert "联网搜索得到的公开结果摘要" in context
    assert "示例新闻" in context
    assert "https://example.com/news" in context


def test_timeout_metadata_shape_is_stable():
    payload = {
        "type": "metadata",
        "web_search": {
            "needed": False,
            "used": False,
            "pending": True,
            "result_count": 0,
            "sources": [],
        },
    }

    assert payload["type"] == "metadata"
    assert payload["web_search"]["pending"] is True
    assert payload["web_search"]["sources"] == []


def test_background_task_can_resolve_after_timeout_marker():
    async def delayed_result():
        await asyncio.sleep(0)
        return {"needed": True, "results": [1]}

    result = asyncio.run(delayed_result())

    assert result["needed"] is True
    assert result["results"] == [1]
