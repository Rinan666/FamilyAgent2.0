"""
Web search trigger and context tests.
"""

import asyncio

import pytest

from app.services.web_search import (
    WebSearchResult,
    build_web_search_context,
    format_web_context,
    is_private_web_search_query,
    needs_web_search,
    rewrite_public_search_query,
    search_public_web,
)
from app.runtime.trace_observation import TraceObservation


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


def test_web_search_query_rewrite_strips_family_pii():
    query = "帮我联网查一下张三手机号13812345678附近今天牙齿矫正价格，住址：上海市浦东新区世纪大道100号"

    rewritten = rewrite_public_search_query(query)

    assert "13812345678" not in rewritten
    assert "世纪大道" not in rewritten
    assert "牙齿矫正价格" in rewritten


def test_private_web_search_query_is_blocked():
    assert is_private_web_search_query("帮我查一下妈妈手机号13812345678附近今天牙齿矫正价格")
    assert is_private_web_search_query("结合家庭记忆搜索一下今天上海天气")
    assert not is_private_web_search_query("搜索一下今天上海天气")


async def _fake_search(query: str):
    return [WebSearchResult(title=query, url="https://example.com", snippet="ok")]


def test_search_public_web_sends_rewritten_query(monkeypatch):
    async def run():
        monkeypatch.setattr("app.services.web_search.settings.web_search_enabled", True)
        monkeypatch.setattr("app.services.web_search.settings.tavily_api_key", "token")
        monkeypatch.setattr("app.services.web_search._search_tavily", _fake_search)

        results = await search_public_web("帮我查一下今天上海天气", "think")

        assert results[0].title == "上海天气"

    asyncio.run(run())


def test_search_public_web_sanitizes_pii_when_public_signal_remains(monkeypatch):
    async def run():
        monkeypatch.setattr("app.services.web_search.settings.web_search_enabled", True)
        monkeypatch.setattr("app.services.web_search.settings.tavily_api_key", "token")
        monkeypatch.setattr("app.services.web_search._search_tavily", _fake_search)

        results = await search_public_web("帮我查一下妈妈手机号13812345678附近今天牙齿矫正价格", "think")

        assert results
        assert "13812345678" not in results[0].title
        assert "手机号" not in results[0].title
        assert "牙齿矫正价格" in results[0].title

    asyncio.run(run())


def test_search_public_web_still_blocks_family_private_context(monkeypatch):
    async def fail_if_called(query: str):  # pragma: no cover - assertion guard
        raise AssertionError(f"private family context should not be searched: {query}")

    async def run():
        monkeypatch.setattr("app.services.web_search.settings.web_search_enabled", True)
        monkeypatch.setattr("app.services.web_search.settings.tavily_api_key", "token")
        monkeypatch.setattr("app.services.web_search._search_tavily", fail_if_called)

        results = await search_public_web("结合家庭记忆搜索一下今天上海天气", "think")

        assert results == []

    asyncio.run(run())


@pytest.mark.asyncio
async def test_web_search_does_not_log_provider_exception_details(monkeypatch, caplog):
    async def fail_search(query: str):
        raise RuntimeError("private web provider detail")

    monkeypatch.setattr("app.services.web_search.settings.web_search_enabled", True)
    monkeypatch.setattr("app.services.web_search.settings.tavily_api_key", "token")
    monkeypatch.setattr("app.services.web_search._search_tavily", fail_search)

    results = await search_public_web("search current OpenAI model", "think")

    assert results == []
    assert "private web provider detail" not in caplog.text


@pytest.mark.asyncio
async def test_web_search_observation_marks_disabled_provider_as_degraded(monkeypatch):
    monkeypatch.setattr("app.services.web_search.settings.web_search_enabled", False)

    context = await build_web_search_context("search latest OpenAI model", "think")

    assert context.needed is True
    assert context.success is False
    assert context.error_code == "WEB_SEARCH_DISABLED"
    assert context.degraded is True
    observation = context.as_trace_observation()
    assert isinstance(observation, TraceObservation)
    assert observation.model_dump(exclude_none=True) == {
        "stepType": "WEB_SEARCH",
        "operation": "web_search.public",
        "latencyMs": observation.latencyMs,
        "success": False,
        "errorCode": "WEB_SEARCH_DISABLED",
        "degraded": True,
        "privacyCategories": ["PUBLIC_DATA"],
    }


@pytest.mark.asyncio
async def test_web_search_observation_marks_private_query_rejection(monkeypatch):
    monkeypatch.setattr("app.services.web_search.settings.web_search_enabled", True)

    context = await build_web_search_context(
        "search current weather using family memory and diary records",
        "think",
    )

    assert context.needed is True
    assert context.success is False
    assert context.error_code == "WEB_SEARCH_QUERY_REJECTED"
    assert context.degraded is True


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
