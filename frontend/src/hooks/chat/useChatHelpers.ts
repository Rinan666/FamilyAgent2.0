'use client';

import type { ChatMessage, DiaryEntry, GrowthGuardRecord, MemoryEntry, MemoryLibraryItem } from '@/types';

export type SessionSavedMemory = {
  id: string;
  tool: 'DIARY' | 'FAMILY_MEMORY' | 'GROWTH_GUARD';
  label: string;
  title: string;
  content: string;
  visibility?: string;
  savedAt: string;
  reason?: string;
};

export type FamilyActivationScene = {
  label: string;
  searchKeywords: string[];
  instruction: string;
};

const activationScenes: FamilyActivationScene[] = [
  {
    label: 'choice',
    searchKeywords: ['choice', 'decision', 'major', 'school', 'career', '志愿', '升学', '选择'],
    instruction: 'Prioritize family experiences about choices, tradeoffs, and long-term consequences.',
  },
  {
    label: 'health',
    searchKeywords: ['health', 'sleep', 'exercise', 'vision', 'tooth', 'screen', '健康', '睡眠', '视力', '牙', '屏幕'],
    instruction: 'Prioritize observations, gentle reminders, and concrete follow-up actions. Do not overstate certainty.',
  },
  {
    label: 'communication',
    searchKeywords: ['family', 'parent', 'child', 'conflict', 'relationship', '沟通', '亲子', '关系'],
    instruction: 'Prioritize communication patterns, misunderstandings, and calm follow-up suggestions.',
  },
  {
    label: 'setback',
    searchKeywords: ['failure', 'regret', 'setback', 'stress', '复盘', '失败', '后悔', '压力'],
    instruction: 'Prioritize lessons, recovery, and the next small step.',
  },
];

export function withTimeout<T>(promise: Promise<T>, fallback: T, timeoutMs: number): Promise<T> {
  let timeoutId: ReturnType<typeof setTimeout> | undefined;
  const timeout = new Promise<T>((resolve) => {
    timeoutId = setTimeout(() => resolve(fallback), timeoutMs);
  });
  return Promise.race([promise, timeout]).finally(() => {
    if (timeoutId) clearTimeout(timeoutId);
  });
}

export function formatMemoryContext({
  libraryItems,
  familyMemories,
  diaryEntries,
  growthRecords,
  sessionSavedMemories,
  retrievalMode,
  embeddingReadyCount,
  viewerIdentityContext,
  activationScene,
}: {
  libraryItems: MemoryLibraryItem[];
  familyMemories: MemoryEntry[];
  diaryEntries: DiaryEntry[];
  growthRecords: GrowthGuardRecord[];
  sessionSavedMemories: SessionSavedMemory[];
  retrievalMode?: string;
  embeddingReadyCount?: number;
  viewerIdentityContext?: string;
  activationScene: FamilyActivationScene | null;
}) {
  const sections: string[] = [];
  const libraryHits = libraryItems
    .filter((item) => item.title?.trim() || item.body?.trim())
    .slice(0, 8)
    .map((item, index) => {
      const preview = (item.body || '').trim().slice(0, 220);
      const author = item.memberName?.trim() || authorLabel(item.authorUserId);
      return `${index + 1}. [${item.sourceType}] author=${author} ${item.title || 'Untitled'} ${preview}`;
    });

  const memoryHits = familyMemories
    .filter((item) => item.status === 'ACTIVE' && item.content?.trim())
    .slice(0, 6)
    .map((item, index) => `${index + 1}. [${item.type}] author=${authorLabel(item.userId)} ${(item.summary || item.content).trim().slice(0, 220)}`);

  const diaryHits = diaryEntries
    .filter((item) => item.rawText?.trim())
    .slice(0, 6)
    .map((item, index) => `${index + 1}. [${item.structured?.entryType || 'DAILY'}] author=${authorLabel(item.userId)} ${item.rawText.trim().slice(0, 220)}`);

  const growthHits = growthRecords
    .filter((item) => item.status === 'ACTIVE')
    .slice(0, 6)
    .map((item, index) => `${index + 1}. [${item.category || 'OTHER'} severity=${item.severity}] observer=${authorLabel(item.createdBy)} subject=${authorLabel(item.targetUserId)} ${item.content.trim().slice(0, 220)}`);

  if (viewerIdentityContext?.trim()) {
    sections.push(`viewer_context:\n${viewerIdentityContext.trim()}`);
  }

  sections.push(
    `retrieval_summary: mode=${retrievalMode || 'TEXT_FALLBACK'} embedding_ready=${embeddingReadyCount ?? 0} library=${libraryHits.length} memories=${memoryHits.length} diaries=${diaryHits.length} growth=${growthHits.length} saved_this_session=${sessionSavedMemories.filter((item) => item.content.trim()).length}`,
  );

  if (activationScene) {
    sections.push(`activation_scene: ${activationScene.label}\n${activationScene.instruction}`);
  }

  if (libraryHits.length > 0) {
    sections.push(`library_hits:\n${libraryHits.join('\n')}`);
  }
  if (memoryHits.length > 0) {
    sections.push(`family_memory_hits:\n${memoryHits.join('\n')}`);
  }
  if (diaryHits.length > 0) {
    sections.push(`diary_hits:\n${diaryHits.join('\n')}`);
  }
  if (growthHits.length > 0) {
    sections.push(`growth_hits:\n${growthHits.join('\n')}`);
  }
  const recentSavedMemories = sessionSavedMemories
    .filter((item) => item.content.trim())
    .slice(-5)
    .map((item, index) => `${index + 1}. [${item.tool}] author=current_conversation_user ${item.title}: ${item.content.slice(0, 220)}`);
  if (recentSavedMemories.length > 0) {
    sections.push(`recent_saved_memories:\n${recentSavedMemories.join('\n')}`);
  }

  return sections.join('\n\n');
}

function authorLabel(userId?: number) {
  return userId == null ? 'unknown_family_member' : `family_user_${userId}`;
}

export function buildFamilyRecallQuery(
  query: string,
  history: Pick<ChatMessage, 'role' | 'content'>[] = [],
) {
  const normalizedQuery = query.trim();
  const parts = history
    .filter((item) => item.role === 'user')
    .map((item) => item.content.trim())
    .filter(Boolean)
    .slice(-2);

  if (normalizedQuery) {
    parts.push(normalizedQuery);
  }

  return Array.from(new Set(parts)).join(' ').trim();
}

export function buildLibrarySearchKeyword(
  query: string,
  activationScene: FamilyActivationScene | null,
) {
  const parts = [query.trim()];
  if (activationScene) {
    parts.push(activationScene.label);
    parts.push(...activationScene.searchKeywords.slice(0, 4));
  }
  return Array.from(new Set(parts.filter(Boolean))).join(' ').trim();
}

export function detectFamilyActivationScene(query: string): FamilyActivationScene | null {
  const normalized = query.trim().toLowerCase();
  if (!normalized) return null;
  return activationScenes.find((scene) => scene.searchKeywords.some((keyword) => normalized.includes(keyword.toLowerCase()))) || null;
}

export function normalizeAssistantMetadata(metadata: Record<string, unknown>): NonNullable<ChatMessage['metadata']> {
  const webSearch = metadata.web_search;
  const responseMode = metadata.response_mode;
  const rag = normalizeRagMetadata(metadata.rag);
  const retrievalQuery = stringValue(metadata.retrievalQuery) || stringValue(metadata.retrieval_query);
  const sourceSummary = stringValue(metadata.sourceSummary) || stringValue(metadata.source_summary);
  const requestId = stringValue(metadata.requestId) || stringValue(metadata.request_id);
  const runId = positiveNumberValue(metadata.runId, metadata.run_id);
  const baseMetadata: NonNullable<ChatMessage['metadata']> = {
    ...(responseMode === 'quick' || responseMode === 'think'
      ? { responseMode }
      : {}),
    ...(typeof metadata.thinking_summary === 'string' && metadata.thinking_summary.trim()
      ? { thinkingSummary: metadata.thinking_summary.trim() }
      : {}),
    ...(rag ? { rag } : {}),
    ...(retrievalQuery ? { retrievalQuery } : {}),
    ...(sourceSummary ? { sourceSummary } : {}),
    ...(requestId ? { requestId } : {}),
    ...(runId ? { runId } : {}),
    ...(typeof metadata.insufficientSources === 'boolean'
      ? { insufficientSources: metadata.insufficientSources }
      : {}),
    ...(typeof metadata.insufficient_sources === 'boolean'
      ? { insufficientSources: metadata.insufficient_sources }
      : {}),
  };
  if (!webSearch || typeof webSearch !== 'object') return baseMetadata;
  const data = webSearch as Record<string, unknown>;
  const rawSources = Array.isArray(data.sources) ? data.sources : [];
  return {
    ...baseMetadata,
    webSearch: {
      needed: Boolean(data.needed),
      used: Boolean(data.used),
      pending: Boolean(data.pending),
      resultCount: Number(data.result_count) || 0,
      sources: rawSources
        .filter((item): item is Record<string, unknown> => Boolean(item) && typeof item === 'object')
        .map((item) => ({
          title: typeof item.title === 'string' ? item.title : 'Untitled',
          url: typeof item.url === 'string' ? item.url : '',
          snippet: typeof item.snippet === 'string' ? item.snippet : '',
        }))
        .filter((item) => item.url)
        .slice(0, 4),
    },
  };
}

function normalizeRagMetadata(value: unknown): NonNullable<NonNullable<ChatMessage['metadata']>['rag']> | null {
  if (!value || typeof value !== 'object') return null;
  const data = value as Record<string, unknown>;
  const rawSources = Array.isArray(data.sources) ? data.sources : [];
  return {
    retrievalMode: stringValue(data.retrievalMode) || stringValue(data.retrieval_mode) || undefined,
    embeddingReadyCount: numberValue(data.embeddingReadyCount, data.embedding_ready_count),
    diaryCount: numberValue(data.diaryCount, data.diary_count),
    memoryCount: numberValue(data.memoryCount, data.memory_count),
    growthRecordCount: numberValue(data.growthRecordCount, data.growth_record_count),
    libraryCount: numberValue(data.libraryCount, data.library_count),
    sessionSavedCount: numberValue(data.sessionSavedCount, data.session_saved_count),
    totalReferenceCount: numberValue(data.totalReferenceCount, data.total_reference_count),
    sources: rawSources
      .filter((item): item is Record<string, unknown> => Boolean(item) && typeof item === 'object')
      .map((item) => ({
        id: stringValue(item.id) || `${stringValue(item.sourceType) || stringValue(item.source_type) || 'source'}-${stringValue(item.title)}`,
        sourceType: stringValue(item.sourceType) || stringValue(item.source_type) || 'FAMILY_EXPERIENCE',
        title: stringValue(item.title) || '未命名资料',
        snippet: stringValue(item.snippet),
        visibility: stringValue(item.visibility) || undefined,
        temporalLayer: stringValue(item.temporalLayer) || stringValue(item.temporal_layer) || undefined,
        topics: stringList(item.topics),
        scenes: stringList(item.scenes),
      }))
      .slice(0, 8),
  };
}

function numberValue(...values: unknown[]) {
  const value = values.find((item) => item !== undefined && item !== null && item !== '');
  const number = Number(value ?? 0);
  return Number.isFinite(number) ? number : 0;
}

function positiveNumberValue(...values: unknown[]) {
  const value = numberValue(...values);
  return value > 0 ? value : 0;
}

function stringValue(value: unknown) {
  return typeof value === 'string' ? value.trim() : '';
}

function stringList(value: unknown) {
  return Array.isArray(value)
    ? value.filter((item): item is string => typeof item === 'string' && item.trim().length > 0)
    : [];
}

export function currentTimeContext() {
  return {
    clientTimestamp: new Date().toISOString(),
    clientTimezone: Intl.DateTimeFormat().resolvedOptions().timeZone || '',
  };
}
