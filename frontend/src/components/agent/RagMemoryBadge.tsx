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
  const modeLabel = rag.retrievalMode === 'VECTOR_WITH_TEXT_FALLBACK' ? 'Vector + text' : 'Text fallback';
  const label = hasHits
    ? `Family memory referenced · Diary ${rag.diaryCount} · Experience ${rag.memoryCount} · Growth ${growthRecordCount}`
    : 'No matching family memory';
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
          {source.title || 'Untitled snippet'}
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
  if (type === 'LIFE_RECORD') return 'Diary';
  if (type === 'FAMILY_EXPERIENCE') return 'Experience';
  if (type === 'GROWTH_OBSERVATION') return 'Growth';
  return 'Memory snippet';
}

function temporalLabel(value: string) {
  if (value === 'RECENT') return 'Recent';
  if (value === 'RECENT_SIGNAL') return 'Recent signal';
  if (value === 'STABLE') return 'Stable';
  if (value === 'FADING') return 'Fading';
  return value;
}

function topicLabel(value: string) {
  if (value === 'HEALTH') return 'Health';
  if (value === 'EMOTION') return 'Emotion';
  if (value === 'FAMILY_STORY') return 'Family story';
  if (value === 'CHOICE') return 'Choice';
  if (value === 'COMMUNICATION') return 'Communication';
  return value;
}
