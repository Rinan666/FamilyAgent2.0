'use client';

import { useCallback, useRef } from 'react';
import { useChatStore } from '@/stores/chatStore';
import { agentApi, growthGuardApi, heritageTaskApi, memoryApi, memoryLibraryApi, type AIStreamHandle } from '@/lib/api';
import { enqueuePersistMessages } from '@/lib/sessionPersistence';
import type { ChatMessage, DiaryEntry, GrowthGuardRecord, HeritageTask, MemoryEntry, MemoryLibraryItem } from '@/types';
import type { ViewerRole } from '@/lib/roles';

type MemoryContextResult = {
  context: string;
  metadata?: NonNullable<ChatMessage['metadata']>;
};

const FAMILY_CONTEXT_TIMEOUT_MS = 1800;

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

interface UseChatOptions {
  viewerRole?: ViewerRole;
  targetRole?: ViewerRole;
  activeFamilyId?: number | null;
  appendSessionMessages?: (messages: ChatMessage[]) => Promise<void>;
  onChatDone?: (message: string) => void;
  onActivationSceneChange?: (scene: { label: string; instruction: string } | null) => void;
  viewerIdentityContext?: string;
  getSessionSavedMemories?: () => SessionSavedMemory[];
  subject?: string;
  contextLabel?: string;
}

type FamilyActivationScene = {
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

const familyContextTerms = [
  'family', 'diary', 'memory', 'growth', 'parent', 'child', 'health', 'sleep',
  'exercise', 'emotion', 'relationship', 'career', 'choice', 'record', 'save',
  '家庭', '家族', '家人', '亲子', '沟通', '成长', '观察', '记录', '经验', '传承',
  '健康', '睡眠', '视力', '牙', '屏幕', '情绪', '压力', '选择', '后悔', '复盘',
];

export function useChat(options: UseChatOptions = {}) {
  const {
    messages,
    isStreaming,
    addMessage,
    removeMessageById,
    appendToLastMessage,
    mergeLastAssistantMetadata,
    setStreaming,
    reset,
  } = useChatStore();

  const {
    viewerRole = 'MEMBER',
    targetRole = 'MEMBER',
    activeFamilyId,
    appendSessionMessages,
    onChatDone,
    onActivationSceneChange,
    viewerIdentityContext,
    getSessionSavedMemories,
    subject = 'FamilyAgent',
    contextLabel = 'family_memory',
  } = options;

  const activeStreamRef = useRef<AIStreamHandle | null>(null);
  const streamRunIdRef = useRef(0);
  const finalizedRunsRef = useRef<Set<number>>(new Set());
  const stoppedRunsRef = useRef<Set<number>>(new Set());
  const persistenceQueueRef = useRef<Promise<void>>(Promise.resolve());

  const enqueuePersist = useCallback((items: ChatMessage[]) => {
    const { task, nextQueue } = enqueuePersistMessages({
      queue: persistenceQueueRef.current,
      messages: items,
      persist: appendSessionMessages,
    });
    persistenceQueueRef.current = nextQueue;
    return task;
  }, [appendSessionMessages]);

  const clearActiveStream = useCallback((runId: number) => {
    if (streamRunIdRef.current === runId) {
      activeStreamRef.current = null;
    }
  }, []);

  const isRunActive = useCallback((runId: number) => streamRunIdRef.current === runId, []);

  const flushFinalAssistantMessage = useCallback(async (runId: number, assistantMessageId: string) => {
    if (finalizedRunsRef.current.has(runId)) return;
    finalizedRunsRef.current.add(runId);
    const targetAssistant = useChatStore.getState().messages
      .find((message) => message.id === assistantMessageId);
    if (targetAssistant?.role === 'assistant' && targetAssistant.content.trim()) {
      try {
        await enqueuePersist([targetAssistant]);
      } catch {
        // The page owns the visible error state for persistence failures.
      }
    }
  }, [enqueuePersist]);

  const stopStreaming = useCallback(() => {
    const runId = streamRunIdRef.current;
    if (runId > 0) {
      stoppedRunsRef.current.add(runId);
    }
    activeStreamRef.current?.abort();
    activeStreamRef.current = null;
    setStreaming(false);
  }, [setStreaming]);

  const discardStreaming = useCallback(() => {
    const runId = streamRunIdRef.current;
    if (runId > 0) {
      finalizedRunsRef.current.add(runId);
      stoppedRunsRef.current.delete(runId);
      streamRunIdRef.current += 1;
    }
    activeStreamRef.current?.abort();
    activeStreamRef.current = null;
    setStreaming(false);
  }, [setStreaming]);

  const recallMemoryContext = useCallback(async (query: string) => {
    try {
      const allowFamilyContext = shouldRecallFamilyContext(query);
      const activationScene = allowFamilyContext ? detectFamilyActivationScene(query) : null;
      onActivationSceneChange?.(
        activationScene
          ? { label: activationScene.label, instruction: activationScene.instruction }
          : null,
      );

      const libraryKeyword = activationScene
        ? `${query} ${activationScene.label} ${activationScene.searchKeywords.join(' ')}`
        : query;

      const [familyRecall, libraryResult, growthRecords, heritageTasks] = await Promise.all([
        activeFamilyId && allowFamilyContext
          ? withTimeout(memoryApi.recallFamily(activeFamilyId, {
              query: libraryKeyword,
              scene: 'FAMILY_AGENT',
              diaryLimit: 8,
              memoryLimit: 8,
            }), null, FAMILY_CONTEXT_TIMEOUT_MS).catch(() => null)
          : Promise.resolve(null),
        activeFamilyId && allowFamilyContext
          ? withTimeout(memoryLibraryApi.search({
              familyId: activeFamilyId,
              keyword: libraryKeyword,
              pageSize: 12,
            }), null, FAMILY_CONTEXT_TIMEOUT_MS).catch(() => null)
          : Promise.resolve(null),
        activeFamilyId && allowFamilyContext
          ? withTimeout(
              growthGuardApi.listFamilyRecords(activeFamilyId, 8),
              [] as GrowthGuardRecord[],
              FAMILY_CONTEXT_TIMEOUT_MS,
            ).catch(() => [] as GrowthGuardRecord[])
          : Promise.resolve([] as GrowthGuardRecord[]),
        activeFamilyId && allowFamilyContext
          ? withTimeout(
              heritageTaskApi.listFamilyTasks(activeFamilyId, 8),
              [] as HeritageTask[],
              FAMILY_CONTEXT_TIMEOUT_MS,
            ).catch(() => [] as HeritageTask[])
          : Promise.resolve([] as HeritageTask[]),
      ]);

      if (!allowFamilyContext) {
        return { context: '' } satisfies MemoryContextResult;
      }

      const context = formatMemoryContext({
        libraryItems: libraryResult?.items || [],
        familyMemories: familyRecall?.memories || [],
        diaryEntries: familyRecall?.diaries || [],
        growthRecords: familyRecall?.growthRecords?.length ? familyRecall.growthRecords : growthRecords,
        heritageTasks,
        sessionSavedMemories: getSessionSavedMemories?.() || [],
        retrievalMode: familyRecall?.retrievalMode,
        embeddingReadyCount: familyRecall?.embeddingReadyCount,
        viewerIdentityContext,
        activationScene,
      });

      return {
        context,
        metadata: familyRecall
          ? {
              rag: {
                retrievalMode: familyRecall.retrievalMode,
                embeddingReadyCount: familyRecall.embeddingReadyCount || 0,
                diaryCount: familyRecall.diaryCount ?? familyRecall.diaries?.length ?? 0,
                memoryCount: familyRecall.memoryCount ?? familyRecall.memories?.length ?? 0,
                growthRecordCount: familyRecall.growthRecordCount ?? familyRecall.growthRecords?.length ?? 0,
                sources: familyRecall.sources || [],
              },
            }
          : undefined,
      } satisfies MemoryContextResult;
    } catch (error) {
      console.log('Family context memories not loaded:', error);
      return { context: '' } satisfies MemoryContextResult;
    }
  }, [activeFamilyId, getSessionSavedMemories, onActivationSceneChange, viewerIdentityContext]);

  const sendMessage = useCallback(async (message: string) => {
    if (isStreaming) return;
    const runId = ++streamRunIdRef.current;
    finalizedRunsRef.current.delete(runId);
    stoppedRunsRef.current.delete(runId);

    const history = useChatStore.getState().messages
      .filter((item) => item.role !== 'system')
      .map((item) => ({ role: item.role, content: item.content }));

    const userMessage = addMessage('user', message);
    const assistantMessage = addMessage('assistant', '');
    setStreaming(true);

    try {
      await enqueuePersist([userMessage]);
    } catch (error) {
      removeMessageById(assistantMessage.id);
      setStreaming(false);
      throw error;
    }

    const timeContext = currentTimeContext();
    const memoryContext = await recallMemoryContext(message);

    if (!isRunActive(runId) || stoppedRunsRef.current.has(runId)) {
      removeMessageById(assistantMessage.id);
      setStreaming(false);
      return;
    }

    if (memoryContext.metadata) {
      mergeLastAssistantMetadata(memoryContext.metadata);
    }

    const handle = agentApi.streamChat(
      {
        message,
        history,
        subject,
        contextLabel,
        memoryContext: memoryContext.context,
        viewerRole,
        targetRole,
        ...timeContext,
      },
      (chunk) => {
        if (!isRunActive(runId)) return;
        appendToLastMessage(chunk);
      },
      () => {
        if (!isRunActive(runId)) return;
        stoppedRunsRef.current.delete(runId);
        clearActiveStream(runId);
        setStreaming(false);
        onChatDone?.(message);
        void flushFinalAssistantMessage(runId, assistantMessage.id);
      },
      (error) => {
        if (!isRunActive(runId)) return;
        stoppedRunsRef.current.delete(runId);
        clearActiveStream(runId);
        appendToLastMessage(`\n\n[Error] ${error}`);
        setStreaming(false);
        void flushFinalAssistantMessage(runId, assistantMessage.id);
      },
      (metadata) => {
        if (!isRunActive(runId)) return;
        mergeLastAssistantMetadata(normalizeAssistantMetadata(metadata));
      },
      () => {
        const isCurrentRun = isRunActive(runId);
        stoppedRunsRef.current.delete(runId);
        clearActiveStream(runId);
        if (isCurrentRun) {
          setStreaming(false);
          streamRunIdRef.current += 1;
        }
        const targetAssistant = useChatStore.getState().messages
          .find((item) => item.id === assistantMessage.id);
        if (targetAssistant?.role === 'assistant' && !targetAssistant.content.trim()) {
          removeMessageById(assistantMessage.id);
          return;
        }
        void flushFinalAssistantMessage(runId, assistantMessage.id);
      },
    );

    if (!isRunActive(runId)) {
      handle.abort();
      removeMessageById(assistantMessage.id);
      setStreaming(false);
      return;
    }

    activeStreamRef.current = handle;
  }, [
    addMessage,
    appendToLastMessage,
    clearActiveStream,
    contextLabel,
    flushFinalAssistantMessage,
    isRunActive,
    isStreaming,
    mergeLastAssistantMetadata,
    onChatDone,
    enqueuePersist,
    recallMemoryContext,
    removeMessageById,
    setStreaming,
    subject,
    targetRole,
    viewerRole,
  ]);

  return {
    messages,
    isStreaming,
    sendMessage,
    stopStreaming,
    discardStreaming,
    reset,
  };
}

function withTimeout<T>(promise: Promise<T>, fallback: T, timeoutMs: number): Promise<T> {
  let timeoutId: ReturnType<typeof setTimeout> | undefined;
  const timeout = new Promise<T>((resolve) => {
    timeoutId = setTimeout(() => resolve(fallback), timeoutMs);
  });
  return Promise.race([promise, timeout]).finally(() => {
    if (timeoutId) clearTimeout(timeoutId);
  });
}

function formatMemoryContext({
  libraryItems,
  familyMemories,
  diaryEntries,
  growthRecords,
  heritageTasks,
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
  heritageTasks: HeritageTask[];
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
      return `${index + 1}. [${item.sourceType}] ${item.title || 'Untitled'} ${preview}`;
    });

  const memoryHits = familyMemories
    .filter((item) => item.status === 'ACTIVE' && item.content?.trim())
    .slice(0, 6)
    .map((item, index) => `${index + 1}. [${item.type}] ${(item.summary || item.content).trim().slice(0, 220)}`);

  const diaryHits = diaryEntries
    .filter((item) => item.rawText?.trim())
    .slice(0, 6)
    .map((item, index) => `${index + 1}. [${item.structured?.entryType || 'DAILY'}] ${item.rawText.trim().slice(0, 220)}`);

  const growthHits = growthRecords
    .filter((item) => item.status === 'ACTIVE')
    .slice(0, 6)
    .map((item, index) => `${index + 1}. [${item.category || 'OTHER'} severity=${item.severity}] ${item.content.trim().slice(0, 220)}`);

  const taskHits = heritageTasks
    .filter((item) => item.status === 'PENDING' || item.status === 'DONE')
    .slice(0, 5)
    .map((item, index) => `${index + 1}. [${item.status}] ${item.title}: ${item.action}`);

  if (viewerIdentityContext?.trim()) {
    sections.push(`viewer_context:\n${viewerIdentityContext.trim()}`);
  }

  sections.push(
    `retrieval_summary: mode=${retrievalMode || 'TEXT_FALLBACK'} embedding_ready=${embeddingReadyCount ?? 0} library=${libraryHits.length} memories=${memoryHits.length} diaries=${diaryHits.length} growth=${growthHits.length} tasks=${taskHits.length} saved_this_session=${sessionSavedMemories.filter((item) => item.content.trim()).length}`,
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
  if (taskHits.length > 0) {
    sections.push(`task_hits:\n${taskHits.join('\n')}`);
  }

  const recentSavedMemories = sessionSavedMemories
    .filter((item) => item.content.trim())
    .slice(-5)
    .map((item, index) => `${index + 1}. [${item.tool}] ${item.title}: ${item.content.slice(0, 220)}`);
  if (recentSavedMemories.length > 0) {
    sections.push(`recent_saved_memories:\n${recentSavedMemories.join('\n')}`);
  }

  return sections.join('\n\n');
}

function shouldRecallFamilyContext(query: string) {
  const normalized = query.trim().toLowerCase();
  if (!normalized) return true;
  return familyContextTerms.some((term) => normalized.includes(term.toLowerCase()));
}

function detectFamilyActivationScene(query: string): FamilyActivationScene | null {
  const normalized = query.trim().toLowerCase();
  if (!normalized) return null;
  return activationScenes.find((scene) => scene.searchKeywords.some((keyword) => normalized.includes(keyword.toLowerCase()))) || null;
}

function normalizeAssistantMetadata(metadata: Record<string, unknown>): NonNullable<ChatMessage['metadata']> {
  const webSearch = metadata.web_search;
  if (!webSearch || typeof webSearch !== 'object') return {};
  const data = webSearch as Record<string, unknown>;
  const rawSources = Array.isArray(data.sources) ? data.sources : [];
  return {
    webSearch: {
      needed: Boolean(data.needed),
      used: Boolean(data.used),
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

function currentTimeContext() {
  return {
    clientTimestamp: new Date().toISOString(),
    clientTimezone: Intl.DateTimeFormat().resolvedOptions().timeZone || '',
  };
}
