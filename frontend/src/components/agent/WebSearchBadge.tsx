'use client';

import type { ChatMessage } from '@/types';

interface WebSearchBadgeProps {
  metadata?: ChatMessage['metadata'];
}

export default function WebSearchBadge({ metadata }: WebSearchBadgeProps) {
  const webSearch = metadata?.webSearch;
  if (!webSearch) return null;

  const label = webSearch.pending
    ? '联网搜索进行中，本轮先基于已有上下文回答'
    : webSearch.used
      ? `已联网核验 · ${webSearch.resultCount} 个来源`
      : webSearch.needed
        ? '暂未找到可靠的近期来源'
        : '离线回答 · 基于已有上下文';

  const toneClass = webSearch.pending
    ? 'border-amber-100 bg-amber-50 text-amber-700'
    : webSearch.used
      ? 'border-blue-100 bg-blue-50 text-blue-700'
      : webSearch.needed
        ? 'border-yellow-100 bg-yellow-50 text-yellow-700'
        : 'border-gray-100 bg-white/70 text-gray-500';

  return (
    <div className="mt-2 whitespace-normal text-[11px] leading-5">
      {webSearch.sources.length > 0 ? (
        <details className={`rounded-lg border px-2 py-1 ${toneClass}`}>
          <summary className="cursor-pointer list-none font-medium">{label}</summary>
          <div className="mt-1 space-y-1">
            {webSearch.sources.slice(0, 4).map((source) => (
              <a
                key={source.url}
                href={source.url}
                target="_blank"
                rel="noreferrer"
                className="block truncate text-blue-700 underline-offset-2 hover:underline"
                title={source.title}
              >
                {source.title || source.url}
              </a>
            ))}
          </div>
        </details>
      ) : (
        <div className={`inline-flex rounded-full border px-2 py-0.5 font-medium ${toneClass}`}>
          {label}
        </div>
      )}
    </div>
  );
}
