'use client';

import { useCallback, useRef } from 'react';
import { useChatStore } from '@/stores/chatStore';
import { agentApi } from '@/lib/api/agent';
import { growthGuardApi } from '@/lib/api/growth';
import { heritageTaskApi } from '@/lib/api/heritage';
import { memoryApi } from '@/lib/api/memory';
import { memoryLibraryApi } from '@/lib/api/memoryLibrary';
import type { AIStreamHandle } from '@/lib/api/shared';
import { enqueuePersistMessages } from '@/lib/sessionPersistence';
import type { ChatMessage, GrowthGuardRecord, HeritageTask } from '@/types';
import type { ViewerRole } from '@/lib/roles';
import {
  currentTimeContext,
  detectFamilyActivationScene,
  formatMemoryContext,
  normalizeAssistantMetadata,
  shouldRecallFamilyContext,
  withTimeout,
  type FamilyActivationScene,
  type SessionSavedMemory,
} from '@/hooks/chat/useChatHelpers';

export type { SessionSavedMemory } from '@/hooks/chat/useChatHelpers';

type MemoryContextResult = {
  context: string;
  metadata?: NonNullable<ChatMessage['metadata']>;
};

const FAMILY_CONTEXT_TIMEOUT_MS = 1800;

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
