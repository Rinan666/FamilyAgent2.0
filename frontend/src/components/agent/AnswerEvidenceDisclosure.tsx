'use client';

import type { ChatMessage } from '@/types';

export default function AnswerEvidenceDisclosure({ message }: { message: ChatMessage }) {
  const sourceRefs = message.metadata?.sourceRefs || [];
  const insufficientSources = Boolean(message.metadata?.insufficientSources);
  const sourceSummary = typeof message.metadata?.sourceSummary === 'string' ? message.metadata.sourceSummary : '';
  const retrievalQuery = typeof message.metadata?.retrievalQuery === 'string' ? message.metadata.retrievalQuery : '';

  if (!sourceRefs.length && !insufficientSources) return null;

  const summaryText = insufficientSources
    ? '资料偏少，仅供参考'
    : `已参考 ${sourceRefs.length} 条授权资料`;

  return (
    <details className="mt-3 overflow-hidden rounded-2xl border border-emerald-100 bg-emerald-50/70 text-xs text-stone-600">
      <summary className="flex cursor-pointer list-none items-center justify-between gap-3 px-3 py-2.5">
        <span className="font-medium text-stone-800">查看回答依据</span>
        <span className="text-[11px] text-stone-500">{summaryText}</span>
      </summary>
      <div className="space-y-2 border-t border-emerald-100 px-3 py-3">
        {sourceSummary && <p className="leading-5 text-stone-600">{sourceSummary}</p>}
        {retrievalQuery && (
          <p className="rounded-xl bg-white/80 px-3 py-2 text-[11px] leading-5 text-stone-600">
            本轮按 “{retrievalQuery}” 检索相关资料。
          </p>
        )}
        {insufficientSources && (
          <p className="rounded-xl bg-amber-50 px-3 py-2 text-[11px] leading-5 text-amber-700">
            当前资料不足，回答保留不确定性，不代表当事人的真实原话。
          </p>
        )}
        {sourceRefs.slice(0, 5).map((source) => (
          <div key={`${message.id}-${source.code}`} className="rounded-xl bg-white/85 px-3 py-2.5">
            <div className="flex flex-wrap items-center gap-2">
              <span className="rounded-lg bg-stone-100 px-2 py-0.5 font-medium text-stone-600">
                {source.code}
              </span>
              <span className={`rounded-full px-2 py-0.5 ${source.toneClass}`}>
                {source.sourceLabel}
              </span>
              <span className="text-[11px] text-stone-400">{source.temporalLabel}</span>
            </div>
            <p className="mt-1.5 text-sm text-stone-700">{source.title}</p>
          </div>
        ))}
      </div>
    </details>
  );
}
