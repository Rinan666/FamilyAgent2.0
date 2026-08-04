"""
Lightweight public web search for time-sensitive questions.

Only public query text is sent out. Family memory, diaries, and private context
must never be used as a search query.
"""
from __future__ import annotations

import html
import logging
import re
import time
from dataclasses import dataclass
from typing import Any
from urllib.parse import quote_plus, urlparse

import httpx

from app.config import settings
from app.runtime.trace_observation import TraceObservation
from app.utils.privacy_guard import redact_ai_bound_text

logger = logging.getLogger("familyagent.ai.web_search")


EXPLICIT_WEB_SEARCH_PATTERNS = [
    r"联网",
    r"搜索",
    r"搜一下|搜搜",
    r"查一下|查查|帮我查",
    r"网上查|网上搜",
    r"出处|来源",
    r"官网",
    r"实时",
    r"权威来源",
    r"\bsearch\b|\bgoogle\b|\bbrowse\b|\bweb\b",
]

PRIVATE_SAVE_OR_MEMORY_PATTERNS = [
    r"保存|存起来|记下来|记录一下|沉淀",
    r"刚才.*(说|讲|聊).*记",
]

HARD_PRIVATE_WEB_SEARCH_PATTERNS = [
    r"日记|聊天记录|家庭记忆|家族记忆|照护记录|病历|体检报告",
    r"\bdiar(?:y|ies)\b|\bchat\s+(?:history|records?)\b|"
    r"\bfamily\s+memor(?:y|ies)\b|\bcare\s+records?\b|"
    r"\bmedical\s+records?\b|\bhealth\s+reports?\b",
]

PRIVATE_IDENTIFIER_LABEL_PATTERN = re.compile(
    r"手机号|身份证|住址|地址|家住|住在|邮箱|"
    r"\bphone(?:\s+number)?\b|\bmobile(?:\s+number)?\b|"
    r"\bidentity\s+number\b|\baddress\b|\bemail\b",
    re.IGNORECASE,
)

PRIVATE_WEB_SEARCH_BLOCK_PATTERNS = [
    *HARD_PRIVATE_WEB_SEARCH_PATTERNS,
    PRIVATE_IDENTIFIER_LABEL_PATTERN.pattern,
]

_SEARCH_FILLER_PATTERN = re.compile(
    r"帮我|请|麻烦|联网|搜索一下|搜一下|搜搜|查一下|查查|帮我查|网上查|网上搜|最新|现在|今天|实时"
)

TIME_SENSITIVE_PATTERNS = [
    r"最新",
    r"最新消息|最新进展|最新情况|最新政策|最新版本",
    r"\blatest\b",
    r"\bnow\b",
    r"\bcurrent\b",
    r"现在",
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
    r"价格|报价",
    r"股价|汇率|油价|金价|实时报价",
    r"\bprice\b",
    r"\bstock price\b|\bexchange rate\b|\boil price\b|\bgold price\b",
    r"天气|台风|地震|疫情",
    r"\bweather\b|\btyphoon\b|\bearthquake\b|\boutbreak\b",
    r"政策|法规|规定|标准|版本|发布|更新|上线|停服|模型",
    r"\bpolicy\b|\blaw\b|\bregulation\b|\bstandard\b|\bversion\b|\brelease\b|\bupdate\b|\bmodel\b",
    r"限行|税率调整|最新限购",
    r"\btax rate\b|\bdriving ban\b",
    r"赛程|比分|排名|冠军",
    r"\bschedule\b|\bscore\b|\branking\b|\bchampion\b",
    r"总统|主席|总理|市长|CEO|负责人|现任",
    r"\bpresident\b|\bprime minister\b|\bmayor\b|\bceo\b|\bhead\b",
    r"现任总统|现任主席|现任总理|现任市长|现任CEO",
    r"\bincumbent president\b|\bcurrent prime minister\b|\bcurrent ceo\b",
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
    provider: str | None = None
    latency_ms: int = 0
    success: bool = True
    error_code: str | None = None
    degraded: bool = False

    def as_trace_observation(self) -> TraceObservation:
        return TraceObservation(
            stepType="WEB_SEARCH",
            operation="web_search.public",
            provider=self.provider,
            latencyMs=self.latency_ms,
            success=self.success,
            errorCode=self.error_code,
            degraded=self.degraded,
            privacyCategories=["PUBLIC_DATA"],
        )


def is_thinking_mode(response_mode: str | None) -> bool:
    return (response_mode or "").strip().lower() == "think"


def needs_web_search(
    query: str,
    response_mode: str | None = "think",
    web_search_policy: str | None = None,
) -> bool:
    """Return true when a public query likely needs fresh information."""
    policy = (web_search_policy or "").strip().upper()
    if policy == "NONE":
        return False
    if not policy and not is_thinking_mode(response_mode):
        return False
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
    return policy == "REQUIRED" or explicit_search or time_sensitive


def rewrite_public_search_query(query: str) -> str:
    """Strip private family details before a query is sent to an external search provider."""
    normalized = _clean_text(query)[:240]
    if not normalized:
        return ""

    guarded = redact_ai_bound_text(normalized, max_length=240)
    public_query = guarded.text
    public_query = re.sub(r"\[[^\]]+\]", " ", public_query)
    public_query = PRIVATE_IDENTIFIER_LABEL_PATTERN.sub(" ", public_query)
    public_query = _SEARCH_FILLER_PATTERN.sub(" ", public_query)
    public_query = re.sub(r"\s+", " ", public_query).strip(" ，。！？,.?;；：:")
    return public_query[:160]


def is_private_web_search_query(query: str) -> bool:
    """Return true when a query contains private markers that should not leave the service."""
    normalized = (query or "").strip()
    if not normalized:
        return False
    guarded = redact_ai_bound_text(normalized, max_length=240)
    if guarded.has_findings:
        return True
    return any(re.search(pattern, normalized, re.IGNORECASE) for pattern in PRIVATE_WEB_SEARCH_BLOCK_PATTERNS)


def format_web_context(
    results: list[WebSearchResult],
    query: str,
    response_mode: str | None = "think",
    web_search_policy: str | None = None,
) -> str:
    if not needs_web_search(query, response_mode, web_search_policy):
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


async def search_public_web(
    query: str,
    response_mode: str | None = "think",
    web_search_policy: str | None = None,
) -> list[WebSearchResult]:
    """Search public web results when enabled and needed."""
    results, _, _, _ = await _search_public_web_observed(query, response_mode, web_search_policy)
    return results


async def _search_public_web_observed(
    query: str,
    response_mode: str | None = "think",
    web_search_policy: str | None = None,
) -> tuple[list[WebSearchResult], str | None, bool, str | None]:
    if not needs_web_search(query, response_mode, web_search_policy):
        return [], None, True, None
    if not settings.web_search_enabled:
        return [], None, False, "WEB_SEARCH_DISABLED"
    public_query = rewrite_public_search_query(query)
    if len(public_query) < 3 or is_private_web_search_query(public_query):
        logger.info("Web search skipped because query rewrite produced no public query")
        return [], None, False, "WEB_SEARCH_QUERY_REJECTED"
    provider = "tavily" if settings.tavily_api_key else settings.web_search_provider.lower()
    try:
        if settings.tavily_api_key:
            return await _search_tavily(public_query), provider, True, None
        if provider == "duckduckgo":
            return await _search_duckduckgo(public_query), provider, True, None
    except Exception as exc:
        logger.warning(
            "Web search failed: provider=%s errorType=%s",
            provider,
            type(exc).__name__,
        )
        return [], provider, False, "WEB_SEARCH_PROVIDER_ERROR"
    return [], provider, False, "WEB_SEARCH_PROVIDER_UNAVAILABLE"


async def build_web_context(query: str, response_mode: str | None = "think") -> str:
    return format_web_context(await search_public_web(query, response_mode), query, response_mode)


async def build_web_search_context(
    query: str,
    response_mode: str | None = "think",
    web_search_policy: str | None = None,
) -> WebSearchContext:
    needed = needs_web_search(query, response_mode, web_search_policy)
    started_at = time.monotonic()
    results, provider, success, error_code = await _search_public_web_observed(
        query, response_mode, web_search_policy
    )
    return WebSearchContext(
        needed=needed,
        results=results,
        prompt_context=format_web_context(results, query, response_mode, web_search_policy),
        provider=provider,
        latency_ms=max(0, round((time.monotonic() - started_at) * 1000)),
        success=success,
        error_code=error_code,
        degraded=needed and not success,
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
