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
import type { AgentResponseMode, ChatMessage, GrowthGuardRecord, HeritageTask } from '@/types';
import type { ViewerRole } from '@/lib/roles';
import {
  buildFamilyRecallQuery,
  buildLibrarySearchKeyword,
  currentTimeContext,
  detectFamilyActivationScene,
  formatMemoryContext,
  normalizeAssistantMetadata,
  withTimeout,
  type FamilyActivationScene,
  type SessionSavedMemory,
} from '@/hooks/chat/useChatHelpers';

export type { SessionSavedMemory } from '@/hooks/chat/useChatHelpers';

type MemoryContextResult = {
  context: string;
  metadata?: NonNullable<ChatMessage['metadata']>;
};

export type UseChatRequestConfig = {
  message: string;
  subject?: string;
  contextLabel?: string;
  memoryContext?: string;
  targetRole?: ViewerRole;
  responseMode?: AgentResponseMode;
};

const FAMILY_CONTEXT_TIMEOUT_MS = 1800;

interface UseChatOptions {
  viewerRole?: ViewerRole;
  targetRole?: ViewerRole;
  responseMode?: AgentResponseMode;
  activeFamilyId?: number | null;
  appendSessionMessages?: (messages: ChatMessage[]) => Promise<void>;
  onChatDone?: (message: string) => void;
  onActivationSceneChange?: (scene: { label: string; instruction: string } | null) => void;
  viewerIdentityContext?: string;
  getSessionSavedMemories?: () => SessionSavedMemory[];
  subject?: string;
  contextLabel?: string;
  memoryContextResolver?: (params: {
    query: string;
    history: Pick<ChatMessage, 'role' | 'content'>[];
    defaultRecall: () => Promise<MemoryContextResult>;
  }) => Promise<MemoryContextResult>;
  prepareRequest?: (params: {
    message: string;
    history: Pick<ChatMessage, 'role' | 'content'>[];
    memoryContext: MemoryContextResult;
    defaultRequest: UseChatRequestConfig;
  }) => Promise<UseChatRequestConfig> | UseChatRequestConfig;
  normalizeStreamMetadata?: (metadata: Record<string, unknown>) => NonNullable<ChatMessage['metadata']>;
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
    responseMode = 'think',
    activeFamilyId,
    appendSessionMessages,
    onChatDone,
    onActivationSceneChange,
    viewerIdentityContext,
    getSessionSavedMemories,
    subject = 'FamilyAgent',
    contextLabel = 'family_memory',
    memoryContextResolver,
    prepareRequest,
    normalizeStreamMetadata,
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

  const recallMemoryContext = useCallback(async (
    query: string,
    history: Pick<ChatMessage, 'role' | 'content'>[] = [],
  ) => {
    try {
      if (responseMode === 'quick') {
        onActivationSceneChange?.(null);
        return { context: '' } satisfies MemoryContextResult;
      }

      const recallQuery = buildFamilyRecallQuery(query, history);
      const activationScene = detectFamilyActivationScene(recallQuery);
      onActivationSceneChange?.(
        activationScene
          ? { label: activationScene.label, instruction: activationScene.instruction }
          : null,
      );

      const libraryKeyword = buildLibrarySearchKeyword(recallQuery, activationScene);

      const [familyRecall, libraryResult, growthRecords, heritageTasks] = await Promise.all([
        activeFamilyId
          ? withTimeout(memoryApi.recallFamily(activeFamilyId, {
              query: libraryKeyword || query,
              scene: 'FAMILY_AGENT',
              diaryLimit: 8,
              memoryLimit: 8,
            }), null, FAMILY_CONTEXT_TIMEOUT_MS).catch(() => null)
          : Promise.resolve(null),
        activeFamilyId
          ? withTimeout(memoryLibraryApi.search({
              familyId: activeFamilyId,
              keyword: libraryKeyword,
              pageSize: 12,
            }), null, FAMILY_CONTEXT_TIMEOUT_MS).catch(() => null)
          : Promise.resolve(null),
        activeFamilyId
          ? withTimeout(
              growthGuardApi.listFamilyRecords(activeFamilyId, 8),
              [] as GrowthGuardRecord[],
              FAMILY_CONTEXT_TIMEOUT_MS,
            ).catch(() => [] as GrowthGuardRecord[])
          : Promise.resolve([] as GrowthGuardRecord[]),
        activeFamilyId
          ? withTimeout(
              heritageTaskApi.listFamilyTasks(activeFamilyId, 8),
              [] as HeritageTask[],
              FAMILY_CONTEXT_TIMEOUT_MS,
            ).catch(() => [] as HeritageTask[])
          : Promise.resolve([] as HeritageTask[]),
      ]);

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
      const diaryCount = familyRecall?.diaryCount ?? familyRecall?.diaries?.length ?? 0;
      const memoryCount = familyRecall?.memoryCount ?? familyRecall?.memories?.length ?? 0;
      const growthRecordCount = familyRecall?.growthRecordCount ?? familyRecall?.growthRecords?.length ?? growthRecords.length;
      const libraryCount = libraryResult?.items?.length ?? 0;
      const heritageTaskCount = heritageTasks.length;
      const sessionSavedCount = (getSessionSavedMemories?.() || []).filter((item) => item.content.trim()).length;
      const totalReferenceCount = diaryCount + memoryCount + growthRecordCount + libraryCount + heritageTaskCount + sessionSavedCount;

      return {
        context,
        metadata: (familyRecall || totalReferenceCount > 0)
          ? {
              rag: {
                retrievalMode: familyRecall?.retrievalMode,
                embeddingReadyCount: familyRecall?.embeddingReadyCount || 0,
                diaryCount,
                memoryCount,
                growthRecordCount,
                libraryCount,
                heritageTaskCount,
                sessionSavedCount,
                totalReferenceCount,
                sources: familyRecall?.sources || [],
              },
            }
          : undefined,
      } satisfies MemoryContextResult;
    } catch (error) {
      console.log('Family context memories not loaded:', error);
      return { context: '' } satisfies MemoryContextResult;
    }
  }, [activeFamilyId, getSessionSavedMemories, onActivationSceneChange, responseMode, viewerIdentityContext]);

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
    const memoryContext = await (memoryContextResolver
      ? memoryContextResolver({
          query: message,
          history,
          defaultRecall: () => recallMemoryContext(message, history),
        })
      : recallMemoryContext(message, history));

    if (!isRunActive(runId) || stoppedRunsRef.current.has(runId)) {
      removeMessageById(assistantMessage.id);
      setStreaming(false);
      return;
    }

    if (memoryContext.metadata) {
      mergeLastAssistantMetadata(memoryContext.metadata);
    }

    const defaultRequest: UseChatRequestConfig = {
      message,
      subject,
      contextLabel,
      memoryContext: memoryContext.context,
      targetRole,
      responseMode,
    };
    const requestConfig = prepareRequest
      ? await prepareRequest({
          message,
          history,
          memoryContext,
          defaultRequest,
        })
      : defaultRequest;

    if (!isRunActive(runId) || stoppedRunsRef.current.has(runId)) {
      removeMessageById(assistantMessage.id);
      setStreaming(false);
      return;
    }

    const handle = agentApi.streamChat(
      {
        message: requestConfig.message,
        history,
        subject: requestConfig.subject || subject,
        contextLabel: requestConfig.contextLabel || contextLabel,
        memoryContext: requestConfig.memoryContext || '',
        viewerRole,
        targetRole: requestConfig.targetRole || targetRole,
        responseMode: requestConfig.responseMode || responseMode,
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
        mergeLastAssistantMetadata((normalizeStreamMetadata || normalizeAssistantMetadata)(metadata));
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
    memoryContextResolver,
    normalizeStreamMetadata,
    prepareRequest,
    recallMemoryContext,
    removeMessageById,
    setStreaming,
    subject,
    targetRole,
    viewerRole,
    responseMode,
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
