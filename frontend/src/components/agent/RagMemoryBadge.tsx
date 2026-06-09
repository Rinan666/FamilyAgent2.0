'use client';

import type { ChatMessage, RagRecallSource } from '@/types';

interface RagMemoryBadgeProps {
  metadata?: ChatMessage['metadata'];
}

export default function RagMemoryBadge({ metadata }: RagMemoryBadgeProps) {
  const rag = metadata?.rag;
  if (!rag) return null;

  const growthRecordCount = rag.growthRecordCount || 0;
  const hasHits = rag.diaryCount > 0 || rag.memoryCount > 0 || growthRecordCount > 0 || rag.sources.length > 0;
  const modeLabel = rag.retrievalMode === 'VECTOR_WITH_TEXT_FALLBACK' ? '向量+文本' : '文本兜底';
  const label = hasHits
    ? `已参考家族记忆 · 每日记录 ${rag.diaryCount} · 经验沉淀 ${rag.memoryCount} · 成长观察 ${growthRecordCount}`
    : '未命中明确家族记忆';
  const toneClass = hasHits
    ? 'border-emerald-100 bg-emerald-50 text-emerald-700'
    : 'border-gray-100 bg-white/70 text-gray-500';

  return (
    <div className="mt-2 whitespace-normal text-[11px] leading-5">
      {rag.sources.length > 0 ? (
        <details className={`rounded-lg border px-2 py-1 ${toneClass}`}>
          <summary className="cursor-pointer list-none font-medium">
            {label}
            <span className="ml-1 text-current/70">RAG: {modeLabel}</span>
          </summary>
          <div className="mt-1 space-y-1.5">
            {rag.sources.slice(0, 6).map((source) => (
              <SourceItem key={source.id} source={source} />
            ))}
          </div>
        </details>
      ) : (
        <div className={`inline-flex rounded-full border px-2 py-0.5 font-medium ${toneClass}`}>
          {label} · RAG: {modeLabel}
        </div>
      )}
    </div>
  );
}

function SourceItem({ source }: { source: RagRecallSource }) {
  const tags = [
    source.temporalLayer ? temporalLabel(source.temporalLayer) : '',
    ...(source.scenes || []).slice(0, 2),
    ...(source.topics || []).slice(0, 2).map(topicLabel),
  ].filter(Boolean);

  return (
    <div className="rounded-md bg-white/70 px-2 py-1 text-gray-600">
      <div className="flex items-center gap-1.5">
        <span className="shrink-0 rounded bg-gray-100 px-1.5 py-0.5 text-[10px] text-gray-500">
          {sourceTypeLabel(source.sourceType)}
        </span>
        <span className="min-w-0 flex-1 truncate font-medium text-gray-700">
          {source.title || '未命名片段'}
        </span>
      </div>
      {source.snippet && (
        <div className="mt-0.5 line-clamp-2 text-[11px] text-gray-500">{source.snippet}</div>
      )}
      {tags.length > 0 && (
        <div className="mt-1 flex flex-wrap gap-1">
          {tags.map((tag) => (
            <span key={tag} className="rounded bg-gray-50 px-1.5 py-0.5 text-[10px] text-gray-500">
              {tag}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}

function sourceTypeLabel(type: string) {
  if (type === 'LIFE_RECORD') return '每日记录';
  if (type === 'FAMILY_EXPERIENCE') return '经验沉淀';
  if (type === 'GROWTH_OBSERVATION') return '成长观察';
  return '记忆片段';
}

function temporalLabel(value: string) {
  if (value === 'RECENT') return '近期';
  if (value === 'RECENT_SIGNAL') return '近期信号';
  if (value === 'STABLE') return '稳定经验';
  if (value === 'FADING') return '淡化记忆';
  return value;
}

function topicLabel(value: string) {
  if (value === 'HEALTH') return '健康';
  if (value === 'EMOTION') return '情绪';
  if (value === 'FAMILY_STORY') return '家族故事';
  if (value === 'CHOICE') return '选择';
  if (value === 'COMMUNICATION') return '沟通';
  return value;
}
