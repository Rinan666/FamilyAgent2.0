'use client';

import { ExternalLink } from 'lucide-react';
import type { ChatMessage, MirrorSourceRef, RagRecallSource } from '@/types';
import { personalMemoryEvidenceLabel } from '@/components/agent/answerEvidence';

export default function AnswerEvidenceDisclosure({ message }: { message: ChatMessage }) {
  const sections = evidenceSections(message);
  const status = evidenceStatus(message, sections);

  if (!status) return null;

  if (sections.length === 0) {
    return (
      <div className="mt-3 inline-flex rounded-full border border-stone-100 bg-white px-2.5 py-1 text-[11px] font-medium text-stone-500">
        {status}
      </div>
    );
  }

  return (
    <details className="mt-3 overflow-hidden rounded-2xl border border-sky-100 bg-sky-50/70 text-xs text-stone-600">
      <summary className="flex cursor-pointer list-none items-center justify-between gap-3 px-3 py-2.5">
        <span className="font-medium text-stone-800">回答依据</span>
        <span className="text-[11px] text-stone-500">{status}</span>
      </summary>
      <div className="space-y-3 border-t border-sky-100 px-3 py-3">
        {sections.map((section) => (
          <section key={section.title} className="space-y-2">
            <div className="flex items-center justify-between gap-3">
              <h4 className="text-[11px] font-semibold text-stone-700">{section.title}</h4>
              <span className="text-[10px] text-stone-400">{section.items.length} 条</span>
            </div>
            <div className="space-y-2">
              {section.items.map((item) => (
                <EvidenceItem key={item.key} item={item} />
              ))}
            </div>
          </section>
        ))}
      </div>
    </details>
  );
}

type EvidenceItemModel = {
  key: string;
  title: string;
  snippet?: string;
  url?: string;
  badges: string[];
  toneClass?: string;
};

type EvidenceSection = {
  title: string;
  items: EvidenceItemModel[];
};

type WebSearchSource = NonNullable<NonNullable<ChatMessage['metadata']>['webSearch']>['sources'][number];

function evidenceSections(message: ChatMessage): EvidenceSection[] {
  const metadata = message.metadata;
  if (!metadata) return [];

  return [
    sourceRefSection(message.id, metadata.sourceRefs),
    ...ragSections(metadata.rag?.sources || []),
    webSearchSection(metadata.webSearch?.sources || []),
    contextNoteSection(metadata),
  ].filter((section): section is EvidenceSection => Boolean(section && section.items.length));
}

function evidenceStatus(message: ChatMessage, sections: EvidenceSection[]) {
  const metadata = message.metadata;
  if (!metadata) return '';

  const rag = metadata.rag;
  const totalRagReferences = rag
    ? rag.totalReferenceCount
      ?? (rag.diaryCount + rag.memoryCount + (rag.growthRecordCount || 0) + (rag.libraryCount || 0) + (rag.sessionSavedCount || 0))
    : 0;
  const sourceRefCount = metadata.sourceRefs?.length || 0;
  const webSearch = metadata.webSearch;
  const webSearchCount = webSearch?.used ? webSearch.resultCount : 0;
  const totalVisibleItems = sections.reduce((sum, section) => sum + section.items.length, 0);

  if (totalVisibleItems > 0) {
    const parts = [
      totalRagReferences > 0 ? `${totalRagReferences} 条家庭记忆` : '',
      sourceRefCount > 0 ? `${sourceRefCount} 条授权资料` : '',
      webSearchCount > 0 ? `${webSearchCount} 个联网来源` : '',
    ].filter(Boolean);
    return parts.length > 0 ? `已参考 ${parts.join(' · ')}` : `已参考 ${totalVisibleItems} 条资料`;
  }

  if (metadata.insufficientSources) return '资料偏少，仅供参考';
  if (rag && totalRagReferences === 0) return '未匹配到相关家庭记忆';
  if (webSearch?.pending) return '联网搜索进行中，本轮先基于已有上下文回答';
  if (webSearch?.needed && !webSearch.used) return '暂未找到可靠的近期来源';
  return '';
}

function sourceRefSection(messageId: string, sourceRefs?: MirrorSourceRef[]): EvidenceSection | null {
  if (!sourceRefs?.length) return null;
  return {
    title: '授权镜像资料',
    items: sourceRefs.slice(0, 5).map((source) => ({
      key: `${messageId}-${source.code}`,
      title: source.title,
      badges: [source.code, source.sourceLabel, source.temporalLabel].filter(Boolean),
      toneClass: source.toneClass,
    })),
  };
}

function ragSections(sources: RagRecallSource[]): EvidenceSection[] {
  if (!sources.length) return [];
  const groups = new Map<string, EvidenceItemModel[]>();
  sources.slice(0, 8).forEach((source) => {
    const title = ragSectionTitle(source);
    const items = groups.get(title) || [];
    items.push({
      key: `rag-${source.id}`,
      title: source.title || '未命名片段',
      snippet: source.snippet,
      badges: [
        sourceTypeLabel(source.sourceType),
        personalMemoryBadge(source),
        participantBadge(source),
        source.temporalLayer ? temporalLabel(source.temporalLayer) : '',
        ...(source.scenes || []).slice(0, 1),
        ...(source.topics || []).slice(0, 1).map(topicLabel),
      ].filter(Boolean),
    });
    groups.set(title, items);
  });
  return Array.from(groups.entries()).map(([title, items]) => ({ title, items }));
}

function participantBadge(source: RagRecallSource) {
  const participant = source.author || source.observer;
  if (!participant) return '';
  if (participant.currentViewer) return '我记录的';
  return participant.relationshipToViewer || participant.name;
}

function personalMemoryBadge(source: RagRecallSource) {
  return personalMemoryEvidenceLabel(source);
}

function webSearchSection(sources: WebSearchSource[] = []): EvidenceSection | null {
  const safeSources = sources
    .map((source) => ({ ...source, url: safeHttpUrl(source.url) }))
    .filter((source): source is typeof source & { url: string } => Boolean(source.url));
  if (!safeSources.length) return null;
  return {
    title: '联网来源',
    items: safeSources.slice(0, 4).map((source) => ({
      key: `web-${source.url}`,
      title: source.title || source.url,
      snippet: source.snippet,
      url: source.url,
      badges: ['联网核验'],
    })),
  };
}

function contextNoteSection(metadata: NonNullable<ChatMessage['metadata']>): EvidenceSection | null {
  const sourceSummary = typeof metadata.sourceSummary === 'string' ? metadata.sourceSummary : '';
  const retrievalQuery = typeof metadata.retrievalQuery === 'string' ? metadata.retrievalQuery : '';
  const notes: EvidenceItemModel[] = [];

  if (sourceSummary) {
    notes.push({
      key: 'source-summary',
      title: '资料摘要',
      snippet: sourceSummary,
      badges: ['上下文'],
    });
  }
  if (retrievalQuery) {
    notes.push({
      key: 'retrieval-query',
      title: '检索方式',
      snippet: `本轮按“${retrievalQuery}”检索相关资料。`,
      badges: ['检索'],
    });
  }
  if (metadata.insufficientSources) {
    notes.push({
      key: 'insufficient-sources',
      title: '资料不足提醒',
      snippet: '当前资料不足，回答保留不确定性，不代表当事人的真实原话。',
      badges: ['提醒'],
    });
  }

  if (!notes.length) return null;
  return { title: '本轮说明', items: notes };
}

function EvidenceItem({ item }: { item: EvidenceItemModel }) {
  const content = (
    <div className="rounded-xl bg-white/85 px-3 py-2.5">
      <div className="flex flex-wrap items-center gap-2">
        {item.badges.map((badge, index) => (
          <span
            key={`${item.key}-${badge}`}
            className={index === 1 && item.toneClass
              ? `rounded-full px-2 py-0.5 ${item.toneClass}`
              : 'rounded-lg bg-stone-100 px-2 py-0.5 text-[10px] font-medium text-stone-500'}
          >
            {badge}
          </span>
        ))}
      </div>
      <div className="mt-1.5 flex items-start gap-1.5 text-sm font-medium text-stone-700">
        <span className="min-w-0 flex-1">{item.title}</span>
        {item.url && <ExternalLink className="mt-0.5 h-3.5 w-3.5 shrink-0 text-blue-500" />}
      </div>
      {item.snippet && <p className="mt-1 line-clamp-2 text-[11px] leading-5 text-stone-500">{item.snippet}</p>}
    </div>
  );

  if (!item.url) return content;
  return (
    <a href={item.url} target="_blank" rel="noopener noreferrer" className="block hover:opacity-90">
      {content}
    </a>
  );
}

function safeHttpUrl(value: string): string | null {
  try {
    const url = new URL(value);
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.toString() : null;
  } catch {
    return null;
  }
}

function ragSectionTitle(source: RagRecallSource) {
  const type = source.sourceType;
  if (type === 'PERSONAL_MEMORY') {
    return personalMemoryEvidenceLabel(source);
  }
  if (type === 'LIFE_RECORD') return '日记';
  if (type === 'GROWTH_OBSERVATION') return '成长观察';
  if (type === 'FAMILY_EXPERIENCE') return '家族经验';
  return '家庭记忆';
}

function sourceTypeLabel(type: string) {
  if (type === 'PERSONAL_MEMORY') return '个人记忆';
  if (type === 'LIFE_RECORD') return '日记';
  if (type === 'FAMILY_EXPERIENCE') return '经验';
  if (type === 'GROWTH_OBSERVATION') return '成长';
  return '记忆片段';
}

function temporalLabel(value: string) {
  if (value === 'RECENT') return '近期';
  if (value === 'RECENT_SIGNAL') return '近期信号';
  if (value === 'STABLE') return '稳定';
  if (value === 'FADING') return '衰减中';
  return value;
}

function topicLabel(value: string) {
  if (value === 'HEALTH') return '健康';
  if (value === 'EMOTION') return '情绪';
  if (value === 'FAMILY_HISTORY') return '家庭故事';
  if (value === 'CHOICE') return '选择';
  if (value === 'COMMUNICATION') return '沟通';
  return value;
}
