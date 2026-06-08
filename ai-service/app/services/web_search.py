"""
Lightweight public web search for time-sensitive questions.

Only public query text is sent out. Family memory, diaries, and private context
must never be used as a search query.
"""
from __future__ import annotations

import html
import logging
import re
from dataclasses import dataclass
from typing import Any
from urllib.parse import quote_plus, urlparse

import httpx

from app.config import settings

logger = logging.getLogger("familyagent.ai.web_search")


EXPLICIT_WEB_SEARCH_PATTERNS = [
    r"联网",
    r"搜索",
    r"搜一下|搜搜",
    r"查一下|查查|帮我查",
    r"网上",
    r"资料",
    r"出处|来源",
    r"官网",
    r"实时",
    r"权威",
    r"\bsearch\b|\bgoogle\b|\bbrowse\b|\bweb\b",
]

PRIVATE_SAVE_OR_MEMORY_PATTERNS = [
    r"保存|存起来|记下来|记录一下|沉淀",
    r"刚才.*(说|讲|聊).*记",
]

TIME_SENSITIVE_PATTERNS = [
    r"最新",
    r"\blatest\b",
    r"现在",
    r"\bnow\b",
    r"当前",
    r"\bcurrent\b",
    r"今天",
    r"\btoday\b",
    r"昨日|昨天",
    r"\byesterday\b",
    r"明天",
    r"\btomorrow\b",
    r"本周|这周",
    r"\bthis\s+week\b",
    r"今年",
    r"\bthis\s+year\b",
    r"刚刚",
    r"\bjust\s+now\b",
    r"新闻|热点|发生了什么",
    r"\bnews\b|\bheadline\b|\bwhat happened\b",
    r"价格|报价|股价|汇率|利率|油价|金价|房价",
    r"\bprice\b|\bstock\b|\bexchange\s+rate\b|\brate\b|\boil\b|\bgold\b",
    r"天气|台风|地震|疫情",
    r"\bweather\b|\btyphoon\b|\bearthquake\b|\boutbreak\b",
    r"政策|法规|规定|标准|限行|税率",
    r"\bpolicy\b|\blaw\b|\bregulation\b|\bstandard\b|\btax\b",
    r"版本|发布|更新|上线|停服",
    r"\bversion\b|\brelease\b|\bupdate\b|\blaunched\b|\bshutdown\b",
    r"赛程|比分|排名|冠军",
    r"\bschedule\b|\bscore\b|\branking\b|\bchampion\b",
    r"总统|主席|总理|市长|CEO|负责人|现任",
    r"\bpresident\b|\bprime minister\b|\bmayor\b|\bceo\b|\bhead\b|\bincumbent\b",
]


@dataclass
class WebSearchResult:
    title: str
    url: str
    snippet: str


@dataclass
class WebSearchContext:
    needed: bool
    results: list[WebSearchResult]
    prompt_context: str


def needs_web_search(query: str) -> bool:
    """Return true when a public query likely needs fresh information."""
    normalized = (query or "").strip()
    if len(normalized) < 4:
        return False
    if any(re.search(pattern, normalized, re.IGNORECASE) for pattern in PRIVATE_SAVE_OR_MEMORY_PATTERNS):
        return False
    explicit_search = any(
        re.search(pattern, normalized, re.IGNORECASE)
        for pattern in EXPLICIT_WEB_SEARCH_PATTERNS
    )
    time_sensitive = any(
        re.search(pattern, normalized, re.IGNORECASE)
        for pattern in TIME_SENSITIVE_PATTERNS
    )
    return explicit_search or time_sensitive


def format_web_context(results: list[WebSearchResult], query: str) -> str:
    if not needs_web_search(query):
        return (
            "- 本轮问题未明显涉及公共时效信息，不需要联网搜索。\n"
            "- 如果用户追问最新事实、新闻、价格、政策、版本或现任人物，应提醒需要联网确认。"
        )
    if not results:
        return (
            "- 本轮问题可能涉及公共时效信息，但没有取得可用搜索结果。\n"
            "- 回答时必须明确说明无法确认最新信息；只能给通用判断或建议用户核对权威来源。"
        )
    lines = [
        "- 本轮问题可能涉及公共时效信息，以下是联网搜索得到的公开结果摘要。",
        "- 回答时优先使用搜索结果；涉及日期、价格、政策、人物任职、新闻事件时必须说明信息来源和不确定性。",
    ]
    for index, item in enumerate(results, start=1):
        title = _clean_text(item.title)[:120]
        snippet = _clean_text(item.snippet)[:240]
        url = item.url.strip()
        lines.append(f"{index}. {title} | {url} | {snippet}")
    return "\n".join(lines)


async def search_public_web(query: str) -> list[WebSearchResult]:
    """Search public web results when enabled and needed."""
    if not settings.web_search_enabled or not needs_web_search(query):
        return []
    try:
        if settings.tavily_api_key:
            return await _search_tavily(query)
        if settings.web_search_provider.lower() == "duckduckgo":
            return await _search_duckduckgo(query)
    except Exception as exc:
        logger.warning("Web search failed: %s", exc)
    return []


async def build_web_context(query: str) -> str:
    return format_web_context(await search_public_web(query), query)


async def build_web_search_context(query: str) -> WebSearchContext:
    needed = needs_web_search(query)
    results = await search_public_web(query) if needed else []
    return WebSearchContext(
        needed=needed,
        results=results,
        prompt_context=format_web_context(results, query),
    )


async def _search_tavily(query: str) -> list[WebSearchResult]:
    async with httpx.AsyncClient(timeout=settings.web_search_timeout_seconds) as client:
        response = await client.post(
            "https://api.tavily.com/search",
            json={
                "api_key": settings.tavily_api_key,
                "query": query,
                "search_depth": "basic",
                "max_results": settings.web_search_max_results,
                "include_answer": False,
                "include_raw_content": False,
            },
        )
        response.raise_for_status()
        data = response.json()
    return [
        WebSearchResult(
            title=str(item.get("title") or ""),
            url=str(item.get("url") or ""),
            snippet=str(item.get("content") or ""),
        )
        for item in data.get("results", [])
        if item.get("url")
    ][: settings.web_search_max_results]


async def _search_duckduckgo(query: str) -> list[WebSearchResult]:
    url = f"https://duckduckgo.com/html/?q={quote_plus(query)}"
    headers = {
        "User-Agent": "Mozilla/5.0 FamilyAgent/0.1 (+https://familyagent.cn)",
    }
    async with httpx.AsyncClient(timeout=settings.web_search_timeout_seconds, follow_redirects=True) as client:
        response = await client.get(url, headers=headers)
        response.raise_for_status()
    return _parse_duckduckgo_html(response.text)[: settings.web_search_max_results]


def _parse_duckduckgo_html(text: str) -> list[WebSearchResult]:
    results: list[WebSearchResult] = []
    blocks = re.split(r'<div[^>]+class="[^"]*result[^"]*"[^>]*>', text)
    for block in blocks:
        title_match = re.search(
            r'<a[^>]+class="[^"]*result__a[^"]*"[^>]+href="([^"]+)"[^>]*>(.*?)</a>',
            block,
            re.IGNORECASE | re.DOTALL,
        )
        if not title_match:
            continue
        snippet_match = re.search(
            r'<a[^>]+class="[^"]*result__snippet[^"]*"[^>]*>(.*?)</a>|'
            r'<div[^>]+class="[^"]*result__snippet[^"]*"[^>]*>(.*?)</div>',
            block,
            re.IGNORECASE | re.DOTALL,
        )
        raw_url = html.unescape(title_match.group(1))
        title = _clean_text(title_match.group(2))
        snippet = _clean_text(snippet_match.group(1) or snippet_match.group(2)) if snippet_match else ""
        clean_url = _normalize_duckduckgo_url(raw_url)
        if title and clean_url:
            results.append(WebSearchResult(title=title, url=clean_url, snippet=snippet))
    return _dedupe_results(results)


def _normalize_duckduckgo_url(url: str) -> str:
    if "uddg=" not in url:
        return url
    match = re.search(r"[?&]uddg=([^&]+)", url)
    if not match:
        return url
    try:
        from urllib.parse import unquote

        return unquote(match.group(1))
    except Exception:
        return url


def _dedupe_results(results: list[WebSearchResult]) -> list[WebSearchResult]:
    seen: set[str] = set()
    deduped: list[WebSearchResult] = []
    for item in results:
        netloc = urlparse(item.url).netloc
        key = item.url or netloc or item.title
        if key in seen:
            continue
        seen.add(key)
        deduped.append(item)
    return deduped


def _clean_text(value: Any) -> str:
    text = html.unescape(str(value or ""))
    text = re.sub(r"<[^>]+>", "", text)
    text = re.sub(r"\s+", " ", text)
    return text.strip()
