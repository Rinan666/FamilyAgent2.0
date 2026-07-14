'use client';

import { useCallback, useRef } from 'react';
import { useChatStore } from '@/stores/chatStore';
import { agentApi } from '@/lib/api/agent';
import { memoryApi } from '@/lib/api/memory';
import type { AIStreamHandle } from '@/lib/api/shared';
import { enqueuePersistMessages } from '@/lib/sessionPersistence';
import type { AgentResponseMode, ChatMessage } from '@/types';
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

type ActiveStreamControl = {
  abort: (discard: boolean) => void;
};

let activeStreamControl: ActiveStreamControl | null = null;

type MemoryContextResult = {
  context: string;
  metadata?: NonNullable<ChatMessage['metadata']>;
};

export type UseChatRequestConfig = {
  message: string;
  familyId?: number | null;
  targetUserId?: number | null;
  targetPersonaId?: number | null;
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
  getInitialAssistantMetadata?: () => NonNullable<ChatMessage['metadata']>;
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
    viewerIdentityContext,
    getSessionSavedMemories,
    subject = 'FamilyAgent',
    contextLabel = 'family_memory',
    memoryContextResolver,
    prepareRequest,
    normalizeStreamMetadata,
    getInitialAssistantMetadata,
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
    activeStreamControl?.abort(false);
    activeStreamControl = null;
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
    activeStreamControl?.abort(true);
    activeStreamControl = null;
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
        return { context: '' } satisfies MemoryContextResult;
      }
      if (activeFamilyId && contextLabel === 'family_memory') {
        return { context: '' } satisfies MemoryContextResult;
      }

      const recallQuery = buildFamilyRecallQuery(query, history);
      const activationScene = detectFamilyActivationScene(recallQuery);

      const recallKeyword = buildLibrarySearchKeyword(recallQuery, activationScene);

      const familyRecall = activeFamilyId
        ? await withTimeout(memoryApi.recallFamily(activeFamilyId, {
            query: recallKeyword || query,
            scene: 'FAMILY_AGENT',
            diaryLimit: 8,
            memoryLimit: 8,
          }), null, FAMILY_CONTEXT_TIMEOUT_MS).catch(() => null)
        : null;

      const context = formatMemoryContext({
        libraryItems: [],
        familyMemories: familyRecall?.memories || [],
        diaryEntries: familyRecall?.diaries || [],
        growthRecords: familyRecall?.growthRecords || [],
        sessionSavedMemories: getSessionSavedMemories?.() || [],
        retrievalMode: familyRecall?.retrievalMode,
        embeddingReadyCount: familyRecall?.embeddingReadyCount,
        viewerIdentityContext,
        activationScene,
      });
      const diaryCount = familyRecall?.diaryCount ?? familyRecall?.diaries?.length ?? 0;
      const memoryCount = familyRecall?.memoryCount ?? familyRecall?.memories?.length ?? 0;
      const growthRecordCount = familyRecall?.growthRecordCount ?? familyRecall?.growthRecords?.length ?? 0;
      const libraryCount = 0;
      const sessionSavedCount = (getSessionSavedMemories?.() || []).filter((item) => item.content.trim()).length;
      const totalReferenceCount = diaryCount + memoryCount + growthRecordCount + libraryCount + sessionSavedCount;

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
  }, [activeFamilyId, contextLabel, getSessionSavedMemories, responseMode, viewerIdentityContext]);

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
    const initialAssistantMetadata = getInitialAssistantMetadata?.();
    if (initialAssistantMetadata) {
      mergeLastAssistantMetadata(initialAssistantMetadata);
    }
    setStreaming(true);

    const persistUserMessageTask = enqueuePersist([userMessage]).catch(() => undefined);

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
      familyId: activeFamilyId,
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

    let streamControl: ActiveStreamControl | null = null;
    const handle = agentApi.streamChat(
      {
        message: requestConfig.message,
        familyId: requestConfig.familyId ?? activeFamilyId,
        targetUserId: requestConfig.targetUserId,
        targetPersonaId: requestConfig.targetPersonaId,
        history,
        subject: requestConfig.subject || subject,
        contextLabel: requestConfig.contextLabel || contextLabel,
        memoryContext: shouldOmitClientMemoryContext(requestConfig, activeFamilyId, contextLabel)
          ? ''
          : requestConfig.memoryContext || '',
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
        if (activeStreamControl === streamControl) {
          activeStreamControl = null;
        }
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
    streamControl = {
      abort: (discard) => {
        if (discard) {
          finalizedRunsRef.current.add(runId);
        }
        stoppedRunsRef.current.add(runId);
        streamRunIdRef.current += 1;
        handle.abort();
      },
    };
    activeStreamControl = streamControl;
    void persistUserMessageTask;
  }, [
    activeFamilyId,
    addMessage,
    appendToLastMessage,
    clearActiveStream,
    contextLabel,
    flushFinalAssistantMessage,
    getInitialAssistantMetadata,
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

function shouldOmitClientMemoryContext(
  requestConfig: UseChatRequestConfig,
  activeFamilyId: number | null | undefined,
  defaultContextLabel: string,
) {
  const contextLabel = requestConfig.contextLabel || defaultContextLabel;
  const hasFamilyId = Boolean(requestConfig.familyId ?? activeFamilyId);
  if (!hasFamilyId) return false;
  if (contextLabel === 'family_memory') return requestConfig.responseMode !== 'quick';
  if (contextLabel === 'mirror_agent') return Boolean(requestConfig.targetUserId);
  if (contextLabel === 'persona_member') return Boolean(requestConfig.targetPersonaId);
  return false;
}
