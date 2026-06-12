'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Bot, History, Loader2, Plus, Send, Sparkles, Square, UserRound } from 'lucide-react';
import AgentContextPanel from '@/components/agent/AgentContextPanel';
import AgentMessageList from '@/components/agent/AgentMessageList';
import AgentSessionDrawer from '@/components/agent/AgentSessionDrawer';
import {
  normalizeTargetSelection,
  selectionFromRequestedTargetUserId,
  selectionLabel,
  selectionMirrorTargetUserId,
  selectionMode,
  selectionTargetMember,
  type AgentTargetSelection,
} from '@/components/agent/agentTarget';
import {
  diarySourceCode,
  diarySourceLabel,
  diaryTitle,
  getSessionTitle,
  memberName,
  memorySourceLabel,
  memoryTitle,
  normalizeAgentSessionMetadata,
  parsePositiveNumber,
  readinessLevel,
  temporalLayerClass,
  temporalLayerLabel,
  type ActivationSceneState,
  type SaveFeedback,
  isRelatedDiary,
} from '@/components/agent/agentDisplay';
import VoiceInputButton from '@/components/voice/VoiceInputButton';
import { useChat, type SessionSavedMemory, type UseChatRequestConfig } from '@/hooks/useChat';
import { useViewerRole } from '@/hooks/useViewerRole';
import { normalizeAssistantMetadata } from '@/hooks/chat/useChatHelpers';
import { useAuthStore } from '@/stores/authStore';
import { useChatStore } from '@/stores/chatStore';
import { cn, generateId } from '@/lib/utils';
import { familyApi, memoryApi, mirrorApi, sessionApi, skillRunApi, writeMemoryApi } from '@/lib/api';
import { loadSessionMessagesChronologically } from '@/lib/sessionHistory';
import {
  buildWriteMemorySaveRequest,
  normalizeSaveToolPlan,
  saveMemorySkillMetadata,
  savePlanDetail,
  savedRecordType,
  todayString,
  toolLabel,
  truncateAuditText,
} from '@/lib/savePlan';
import type {
  AgentMode,
  AgentResponseMode,
  AgentSaveToolPlan,
  AgentSessionMetadata,
  ChatMessage,
  ChatSessionDetail,
  ChatSessionSummary,
  FamilyMember,
  MirrorContextResponse,
  MirrorSourceRef,
} from '@/types';

function buildSessionMetadata(
  mode: AgentMode,
  targetLabel: string,
  targetMember?: FamilyMember | null,
  hasTargetSwitches = false,
): AgentSessionMetadata {
  return {
    entry: 'agent',
    contextLabel: mode === 'mirror' ? 'mirror_agent' : 'family_memory',
    agentMode: mode,
    targetUserId: mode === 'mirror' ? targetMember?.userId ?? null : null,
    targetMemberName: mode === 'mirror' ? targetLabel : null,
    hasTargetSwitches,
  };
}

function fallbackSavePlan(content: string): AgentSaveToolPlan {
  const cleaned = content.trim();
  return {
    should_save: true,
    tool: 'DIARY',
    content: cleaned,
    title: cleaned.slice(0, 24) || '聊天记录',
    summary: cleaned.slice(0, 80),
    visibility: 'PRIVATE',
    entry_type: 'DAILY',
    memory_type: 'ELDER_ADVICE',
    scope: 'PRIVATE',
    category: 'OTHER',
    severity: 2,
    importance: 3,
    tags: ['family-agent-save'],
    reason: '用户明确选择保存这条消息。',
    confirmation_message: '已保存为日记。',
  };
}

function savedMemoryHref(plan: AgentSaveToolPlan, familyId?: number | null) {
  const familyQuery = familyId ? `?familyId=${familyId}` : '';
  if (plan.tool === 'DIARY') return `/dashboard/diary${familyQuery}`;
  if (plan.tool === 'FAMILY_MEMORY') {
    return `/dashboard/diary${familyId ? `?familyId=${familyId}&writeCategory=EXPERIENCE` : '?writeCategory=EXPERIENCE'}`;
  }
  if (plan.tool === 'GROWTH_GUARD') {
    return `/dashboard/diary${familyId ? `?familyId=${familyId}&writeCategory=OBSERVATION` : '?writeCategory=OBSERVATION'}`;
  }
  return `/dashboard/memory${familyQuery}`;
}

function savedMemoryFromPlan(plan: AgentSaveToolPlan, savedAt: string): SessionSavedMemory | null {
  if (!plan.should_save || plan.tool === 'NONE' || !plan.content.trim()) return null;
  return {
    id: `saved-${plan.tool}-${savedAt}`,
    tool: plan.tool,
    label: toolLabel(plan.tool),
    title: plan.title || toolLabel(plan.tool),
    content: plan.content.trim(),
    visibility: String(plan.visibility || plan.scope || 'PRIVATE'),
    savedAt,
    reason: plan.reason,
  };
}

function hasMirrorProfile(context?: MirrorContextResponse | null) {
  return Boolean(context?.mirrorProfile && Object.keys(context.mirrorProfile).length > 0);
}

function sourceLead(context?: MirrorContextResponse | null) {
  const diaries = context?.diaries || [];
  const relatedDiaryCount = diaries.filter(isRelatedDiary).length;
  const selfDiaryCount = diaries.length - relatedDiaryCount;
  const memoryCount = context?.memories?.length || 0;
  const libraryCount = context?.libraryItems?.length || 0;
  const profileText = hasMirrorProfile(context) ? '，并参考了授权画像摘要' : '';
  return `本轮可参考 ${selfDiaryCount} 条本人记录、${relatedDiaryCount} 条家人补充、${memoryCount} 条可见经验沉淀、${libraryCount} 条额外匹配片段${profileText}。`;
}

function buildSourceRefs(context?: MirrorContextResponse | null): MirrorSourceRef[] {
  if (!context) return [];

  const diaryRefs = (context.diaries || []).map((entry, index) => ({
    code: diarySourceCode(entry, index),
    title: diaryTitle(entry),
    sourceLabel: diarySourceLabel(entry),
    temporalLabel: temporalLayerLabel(entry),
    toneClass: temporalLayerClass(entry),
  }));

  const memoryRefs = (context.memories || []).map((memory, index) => ({
    code: `M${index + 1}`,
    title: memoryTitle(memory),
    sourceLabel: memorySourceLabel(memory),
    temporalLabel: temporalLayerLabel(memory),
    toneClass: temporalLayerClass(memory),
  }));

  const libraryRefs = (context.libraryItems || []).map((item, index) => ({
    code: `L${index + 1}`,
    title: item.title || '记忆库片段',
    sourceLabel: '家族记忆库',
    temporalLabel: '已授权',
    toneClass: 'bg-indigo-50 text-indigo-700',
  }));

  return [...diaryRefs, ...memoryRefs, ...libraryRefs].slice(0, 8);
}

function buildMirrorAnswerMetadata(
  context: MirrorContextResponse | null | undefined,
  targetMember?: FamilyMember | null,
): NonNullable<ChatMessage['metadata']> {
  const sourceRefs = buildSourceRefs(context);
  return {
    agentMode: 'mirror',
    targetUserId: targetMember?.userId ?? context?.targetMember?.userId ?? null,
    targetMemberName: memberName(targetMember || context?.targetMember),
    sourceRefs,
    sourceSummary: context ? sourceLead(context) : '',
    insufficientSources: Boolean(context?.insufficientRecords || sourceRefs.length === 0),
    retrievalQuery: context?.retrievalQuery,
  };
}

function normalizeMirrorAssistantMetadata(metadata: Record<string, unknown>): NonNullable<ChatMessage['metadata']> {
  const webSearch = metadata.web_search;
  const responseMode = metadata.response_mode;
  const baseMetadata: NonNullable<ChatMessage['metadata']> = {
    ...(responseMode === 'quick' || responseMode === 'think'
      ? { responseMode }
      : {}),
    ...(typeof metadata.thinking_summary === 'string' && metadata.thinking_summary.trim()
      ? { thinkingSummary: metadata.thinking_summary.trim() }
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
          title: typeof item.title === 'string' ? item.title : '未命名来源',
          url: typeof item.url === 'string' ? item.url : '',
          snippet: typeof item.snippet === 'string' ? item.snippet : '',
        }))
        .filter((item) => item.url)
        .slice(0, 4),
    },
  };
}

let cachedAgentMembersByFamilyId: Record<number, FamilyMember[]> = {};
let cachedAgentSessionsByFamilyId: Record<number, ChatSessionSummary[]> = {};
let cachedMirrorContextByFamilyTarget: Record<string, MirrorContextResponse> = {};

function buildTargetSwitchMessage(
  nextMode: AgentMode,
  nextTargetLabel: string,
  nextTarget: FamilyMember | null,
): ChatMessage {
  const targetLabel = nextMode === 'mirror'
    ? `已切换到 “${nextTargetLabel}” 的镜像参考模式。后续回答只基于授权可见记录，不代表本人真实意图。`
    : '已切回家庭 Agent 自身上下文。后续回答将基于当前家庭共享记忆与记录。';
  const sessionContextPatch = buildSessionMetadata(nextMode, nextTargetLabel, nextTarget, true);
  return {
    id: generateId(),
    role: 'system',
    content: targetLabel,
    timestamp: new Date().toISOString(),
    metadata: {
      ...sessionContextPatch,
      switchMarker: true,
      sessionContextPatch,
    },
  };
}

export default function AgentPage() {
  const searchParams = useSearchParams();
  const user = useAuthStore((state) => state.user);
  const routePrompt = searchParams.get('prompt')?.trim() || '';
  const requestedFamilyId = useMemo(() => parsePositiveNumber(searchParams.get('familyId')), [searchParams]);
  const requestedTargetUserId = useMemo(() => parsePositiveNumber(searchParams.get('targetUserId')), [searchParams]);

  const routePromptAppliedRef = useRef('');
  const sessionSavedMemoriesRef = useRef<SessionSavedMemory[]>([]);
  const createSessionPromiseRef = useRef<Promise<ChatSessionDetail> | null>(null);
  const sessionGenerationRef = useRef(0);
  const sessionIdRef = useRef<number | null>(null);
  const activeSessionDetailRef = useRef<ChatSessionDetail | null>(null);

  const [input, setInput] = useState('');
  const [members, setMembers] = useState<FamilyMember[]>([]);
  const [targetSelection, setTargetSelection] = useState<AgentTargetSelection>('NONE');
  const [mirrorContext, setMirrorContext] = useState<MirrorContextResponse | null>(null);
  const [isLoadingMembers, setIsLoadingMembers] = useState(false);
  const [isLoadingMirrorContext, setIsLoadingMirrorContext] = useState(false);
  const [sessions, setSessions] = useState<ChatSessionSummary[]>([]);
  const [activeSessionDetail, setActiveSessionDetail] = useState<ChatSessionDetail | null>(null);
  const [isLoadingSessions, setIsLoadingSessions] = useState(false);
  const [isLoadingMessages, setIsLoadingMessages] = useState(false);
  const [sessionError, setSessionError] = useState('');
  const [contextError, setContextError] = useState('');
  const [activationScene, setActivationScene] = useState<ActivationSceneState | null>(null);
  const [saveFeedback, setSaveFeedback] = useState<Record<string, SaveFeedback>>({});
  const [isSessionsOpen, setIsSessionsOpen] = useState(false);
  const [isContextOpen, setIsContextOpen] = useState(false);
  const [responseMode, setResponseMode] = useState<AgentResponseMode>('think');
  const [sessionsLoaded, setSessionsLoaded] = useState(false);
  const [contextLoaded, setContextLoaded] = useState(false);

  const {
    viewerRole,
    activeFamilyId,
    activeFamily,
    activeMembership,
    setActiveFamilyId,
    isLoading,
  } = useViewerRole();
  const sessionId = useChatStore((state) => state.sessionId);
  const setSessionId = useChatStore((state) => state.setSessionId);
  const setMessages = useChatStore((state) => state.setMessages);
  const selfUserId = activeMembership?.userId ?? user?.id ?? null;

  useEffect(() => {
    sessionIdRef.current = sessionId;
  }, [sessionId]);

  useEffect(() => {
    activeSessionDetailRef.current = activeSessionDetail;
  }, [activeSessionDetail]);

  useEffect(() => {
    if (requestedFamilyId && requestedFamilyId !== activeFamilyId) {
      setActiveFamilyId(requestedFamilyId);
    }
  }, [activeFamilyId, requestedFamilyId, setActiveFamilyId]);

  const mirrorTargetUserId = useMemo(
    () => selectionMirrorTargetUserId(targetSelection, selfUserId),
    [selfUserId, targetSelection],
  );
  const targetMember = useMemo(
    () => selectionTargetMember(targetSelection, members, mirrorTargetUserId, mirrorContext?.targetMember || null),
    [members, mirrorContext?.targetMember, mirrorTargetUserId, targetSelection],
  );
  const mode = useMemo<AgentMode>(
    () => selectionMode(targetSelection, selfUserId),
    [selfUserId, targetSelection],
  );
  const targetLabel = useMemo(
    () => selectionLabel(targetSelection, targetMember),
    [targetMember, targetSelection],
  );
  const modeReadiness = useMemo(() => readinessLevel(mirrorContext), [mirrorContext]);
  const activeSessionMetadata = useMemo(
    () => normalizeAgentSessionMetadata(activeSessionDetail?.metadata),
    [activeSessionDetail?.metadata],
  );

  const upsertSession = useCallback((session: ChatSessionSummary) => {
    setSessions((current) => {
      const next = current.filter((item) => item.id !== session.id);
      const updated = [session, ...next];
      if (session.familyId) {
        cachedAgentSessionsByFamilyId[session.familyId] = updated;
      }
      return updated;
    });
  }, []);

  const loadMembers = useCallback(async (familyId: number, forceRefresh: boolean = false) => {
    if (!forceRefresh && cachedAgentMembersByFamilyId[familyId]) {
      const cachedMembers = cachedAgentMembersByFamilyId[familyId];
      setMembers(cachedMembers);
      setTargetSelection((current) => {
        const preferred = selectionFromRequestedTargetUserId(requestedTargetUserId, selfUserId, cachedMembers);
        if (preferred !== 'NONE') return preferred;
        const normalizedCurrent = normalizeTargetSelection(current, selfUserId);
        if (normalizedCurrent === 'SELF') return 'SELF';
        if (typeof normalizedCurrent === 'number' && cachedMembers.some((member) => member.userId === normalizedCurrent)) {
          return normalizedCurrent;
        }
        return 'NONE';
      });
      return;
    }

    setIsLoadingMembers(true);
    setContextError('');
    try {
      const memberList = await familyApi.getMembers(familyId);
      const nextMembers = Array.isArray(memberList) ? memberList : [];
      cachedAgentMembersByFamilyId[familyId] = nextMembers;
      setMembers(nextMembers);
      setTargetSelection((current) => {
        const preferred = selectionFromRequestedTargetUserId(requestedTargetUserId, selfUserId, nextMembers);
        if (preferred !== 'NONE') return preferred;
        const normalizedCurrent = normalizeTargetSelection(current, selfUserId);
        if (normalizedCurrent === 'SELF') return 'SELF';
        if (typeof normalizedCurrent === 'number' && nextMembers.some((member) => member.userId === normalizedCurrent)) {
          return normalizedCurrent;
        }
        return 'NONE';
      });
    } catch (error) {
      setMembers([]);
      setTargetSelection('NONE');
      setContextError(error instanceof Error ? error.message : '加载家庭成员失败。');
    } finally {
      setIsLoadingMembers(false);
    }
  }, [requestedTargetUserId, selfUserId]);

  useEffect(() => {
    if (!activeFamilyId) {
      setMembers([]);
      setTargetSelection('NONE');
      setMirrorContext(null);
      setContextError('');
      return;
    }
    void loadMembers(activeFamilyId);
  }, [activeFamilyId, loadMembers]);

  const refreshMirrorContext = useCallback(async (familyId: number, userId: number, query?: string) => {
    const cacheKey = `${familyId}:${userId}`;
    if (!query && cachedMirrorContextByFamilyTarget[cacheKey]) {
      const cached = cachedMirrorContextByFamilyTarget[cacheKey];
      setMirrorContext(cached);
      setContextLoaded(true);
      return cached;
    }
    const context = await mirrorApi.getContext(familyId, userId, query);
    if (!query) {
      cachedMirrorContextByFamilyTarget[cacheKey] = context;
    }
    setMirrorContext(context);
    setContextLoaded(true);
    return context;
  }, []);

  useEffect(() => {
    if (mode !== 'mirror' || !activeFamilyId || !mirrorTargetUserId || responseMode === 'quick') {
      setMirrorContext(null);
      setIsLoadingMirrorContext(false);
      setContextLoaded(false);
      if (mode === 'family') {
        setContextError('');
      }
      return;
    }
    let active = true;
    setIsLoadingMirrorContext(true);
    setContextError('');
    refreshMirrorContext(activeFamilyId, mirrorTargetUserId)
      .then((context) => {
        if (active) setMirrorContext(context);
      })
      .catch((error) => {
        if (active) {
          setMirrorContext(null);
          setContextError(error instanceof Error ? error.message : '镜像上下文加载失败。');
        }
      })
      .finally(() => {
        if (active) setIsLoadingMirrorContext(false);
      });
    return () => {
      active = false;
    };
  }, [activeFamilyId, mirrorTargetUserId, mode, refreshMirrorContext, responseMode]);

  const memoryContextResolver = useCallback(async ({
    query,
    defaultRecall,
  }: {
    query: string;
    history: Pick<ChatMessage, 'role' | 'content'>[];
    defaultRecall: () => Promise<{ context: string; metadata?: NonNullable<ChatMessage['metadata']> }>;
  }) => {
    if (responseMode === 'quick') {
      const quickMetadata: NonNullable<ChatMessage['metadata']> = {
        agentMode: 'mirror',
        responseMode: 'quick',
        targetUserId: targetMember?.userId ?? mirrorTargetUserId,
        targetMemberName: targetLabel,
      };
      return {
        context: '',
        metadata: quickMetadata,
      };
    }
    if (mode !== 'mirror' || !activeFamilyId || !mirrorTargetUserId) {
      return defaultRecall();
    }
    try {
      const context = await refreshMirrorContext(activeFamilyId, mirrorTargetUserId, query);
      setContextError('');
      return {
        context: context.memoryContext || '',
        metadata: buildMirrorAnswerMetadata(context, targetMember),
      };
    } catch (error) {
      setContextError(error instanceof Error ? `镜像上下文刷新失败，已使用当前资料：${error.message}` : '镜像上下文刷新失败，已使用当前资料。');
      return {
        context: mirrorContext?.memoryContext || '',
        metadata: buildMirrorAnswerMetadata(mirrorContext, targetMember),
      };
    }
  }, [activeFamilyId, mirrorContext, mirrorTargetUserId, mode, refreshMirrorContext, responseMode, targetLabel, targetMember]);

  const prepareRequest = useCallback(async ({ message, defaultRequest }: {
    message: string;
    history: Pick<ChatMessage, 'role' | 'content'>[];
    memoryContext: { context: string; metadata?: NonNullable<ChatMessage['metadata']> };
    defaultRequest: UseChatRequestConfig;
  }) => {
    if (mode !== 'mirror' || !targetMember) {
      return defaultRequest;
    }
    return {
      ...defaultRequest,
      message: `请以 "${memberName(targetMember)}" 的镜像参考模式回答：${message}`,
      subject: 'MirrorAgent',
      contextLabel: 'mirror_agent',
      targetRole: 'MEMBER' as const,
    };
  }, [mode, targetMember]);

  const normalizeStreamMetadata = useCallback((metadata: Record<string, unknown>): NonNullable<ChatMessage['metadata']> => (
    mode === 'mirror'
      ? normalizeMirrorAssistantMetadata(metadata)
      : {
          agentMode: 'family',
          ...normalizeAssistantMetadata(metadata),
        }
  ), [mode]);

  const ensureSessionHeader = useCallback(async () => {
    if (!activeFamilyId) {
      throw new Error('请先选择一个家庭。');
    }
    const generation = sessionGenerationRef.current;
    const currentSessionId = sessionIdRef.current;
    const currentSessionDetail = activeSessionDetailRef.current;
    if (currentSessionId && currentSessionDetail?.id === currentSessionId) {
      return currentSessionDetail;
    }
    if (currentSessionId) {
      const detail = await sessionApi.getSession(currentSessionId);
      if (sessionGenerationRef.current === generation) {
        activeSessionDetailRef.current = detail;
        setActiveSessionDetail(detail);
        upsertSession(detail);
      }
      return detail;
    }
    if (!createSessionPromiseRef.current) {
      const createGeneration = generation;
      const createPromise = sessionApi.createSession({
        familyId: activeFamilyId,
        subject: 'FamilyAgent',
        source: 'FAMILY_AGENT',
        metadata: buildSessionMetadata(mode, targetLabel, targetMember, false),
      });
      let createRequest: Promise<ChatSessionDetail>;
      createRequest = createPromise
        .then((detail) => {
          if (sessionGenerationRef.current === createGeneration) {
            sessionIdRef.current = detail.id;
            activeSessionDetailRef.current = detail;
            setSessionId(detail.id);
            setActiveSessionDetail(detail);
            upsertSession(detail);
          }
          return detail;
        })
        .finally(() => {
          if (createSessionPromiseRef.current === createRequest) {
            createSessionPromiseRef.current = null;
          }
        });
      createSessionPromiseRef.current = createRequest;
    }
    return createSessionPromiseRef.current;
  }, [activeFamilyId, mode, setSessionId, targetLabel, targetMember, upsertSession]);

  const appendSessionMessages = useCallback(async (newMessages: ChatMessage[]) => {
    if (!newMessages.length || !activeFamilyId) return;
    const generation = sessionGenerationRef.current;
    setSessionError('');
    try {
      const detail = await ensureSessionHeader();
      const updated = await sessionApi.appendMessages(detail.id, newMessages);
      if (sessionGenerationRef.current !== generation) {
        return;
      }
      sessionIdRef.current = updated.id;
      activeSessionDetailRef.current = updated;
      setSessionId(updated.id);
      setActiveSessionDetail(updated);
      upsertSession(updated);
    } catch (error) {
      if (sessionGenerationRef.current === generation) {
        setSessionError(error instanceof Error ? error.message : '自动保存聊天记录失败。');
      }
      throw error;
    }
  }, [activeFamilyId, ensureSessionHeader, setSessionId, upsertSession]);

  const {
    messages,
    isStreaming,
    sendMessage,
    stopStreaming,
    discardStreaming,
    reset,
  } = useChat({
    viewerRole,
    targetRole: 'MEMBER',
    activeFamilyId,
    appendSessionMessages,
    onActivationSceneChange: setActivationScene,
    getSessionSavedMemories: () => sessionSavedMemoriesRef.current,
    subject: 'FamilyAgent',
    contextLabel: 'family_memory',
    responseMode,
    memoryContextResolver,
    prepareRequest,
    normalizeStreamMetadata,
  });

  const recentMessages = useMemo(
    () => messages.filter((message) => message.role !== 'system').slice(-10),
    [messages],
  );

  const loadSessions = useCallback(async () => {
    if (!activeFamilyId) {
      setSessions([]);
      setSessionsLoaded(false);
      return;
    }

    const cachedSessions = cachedAgentSessionsByFamilyId[activeFamilyId];
    if (cachedSessions) {
      setSessions(cachedSessions);
      setSessionsLoaded(true);
      return;
    }

    setIsLoadingSessions(true);
    setSessionError('');
    try {
      const list = await sessionApi.getUserSessions(undefined, 30);
      const filtered = (list || []).filter((session) => (
        session.familyId === activeFamilyId
          && (!session.source || session.source === 'FAMILY_AGENT' || session.source === 'TUTOR')
      ));
      cachedAgentSessionsByFamilyId[activeFamilyId] = filtered;
      setSessions(filtered);
      setSessionsLoaded(true);
    } catch (error) {
      setSessionError(error instanceof Error ? error.message : '加载会话历史失败。');
    } finally {
      setIsLoadingSessions(false);
    }
  }, [activeFamilyId]);

  useEffect(() => {
    sessionGenerationRef.current += 1;
    createSessionPromiseRef.current = null;
    sessionIdRef.current = null;
    activeSessionDetailRef.current = null;
    discardStreaming();
    setSessionId(null);
    setActiveSessionDetail(null);
    setMessages([]);
    setSessionError('');
    setSaveFeedback({});
    setActivationScene(null);
    sessionSavedMemoriesRef.current = [];
    setIsSessionsOpen(false);
    setIsContextOpen(false);

    if (!activeFamilyId) {
      setSessions([]);
      setSessionsLoaded(false);
      return;
    }
    if (cachedAgentSessionsByFamilyId[activeFamilyId]) {
      setSessions(cachedAgentSessionsByFamilyId[activeFamilyId]);
      setSessionsLoaded(true);
    } else {
      setSessions([]);
      setSessionsLoaded(false);
    }
  }, [activeFamilyId, discardStreaming, setMessages, setSessionId]);

  useEffect(() => {
    if (!isSessionsOpen || sessionsLoaded || !activeFamilyId) return;
    void loadSessions();
  }, [activeFamilyId, isSessionsOpen, loadSessions, sessionsLoaded]);

  useEffect(() => {
    if (!routePrompt || routePromptAppliedRef.current === routePrompt) return;
    routePromptAppliedRef.current = routePrompt;
    setInput(routePrompt);
  }, [routePrompt]);

  const handleNewChat = useCallback(() => {
    discardStreaming();
    sessionGenerationRef.current += 1;
    createSessionPromiseRef.current = null;
    sessionIdRef.current = null;
    activeSessionDetailRef.current = null;
    reset();
    setSessionId(null);
    setActiveSessionDetail(null);
    setInput('');
    setSessionError('');
    setSaveFeedback({});
    setActivationScene(null);
    sessionSavedMemoriesRef.current = [];
  }, [discardStreaming, reset, setSessionId]);

  const handleSubmit = useCallback(async () => {
    const content = input.trim();
    if (!content || isStreaming) return;
    setInput('');
    try {
      await sendMessage(content);
    } catch {
      // The append pipeline already surfaces save failures inline.
    }
  }, [input, isStreaming, sendMessage]);

  const loadAllSessionMessages = useCallback((targetSessionId: number) => (
    loadSessionMessagesChronologically(sessionApi.getSessionMessages, targetSessionId, 40)
  ), []);

  const handleLoadSession = useCallback(async (targetSessionId: number) => {
    discardStreaming();
    const generation = sessionGenerationRef.current + 1;
    sessionGenerationRef.current = generation;
    createSessionPromiseRef.current = null;
    setIsLoadingMessages(true);
    setSessionError('');
    try {
      const detail = await sessionApi.getSession(targetSessionId);
      const restoredMessages = await loadAllSessionMessages(targetSessionId);
      if (sessionGenerationRef.current !== generation) {
        return;
      }
      sessionIdRef.current = detail.id;
      activeSessionDetailRef.current = detail;
      setSessionId(detail.id);
      setMessages(restoredMessages);
      setActiveSessionDetail(detail);
      setSaveFeedback({});
      setActivationScene(null);
      sessionSavedMemoriesRef.current = [];
      upsertSession(detail);
    } catch (error) {
      if (sessionGenerationRef.current === generation) {
        setSessionError(error instanceof Error ? error.message : '加载所选会话失败。');
      }
    } finally {
      if (sessionGenerationRef.current === generation) {
        setIsLoadingMessages(false);
      }
    }
  }, [discardStreaming, loadAllSessionMessages, setMessages, setSessionId, upsertSession]);

  const handleDeleteSession = useCallback(async (targetSessionId: number) => {
    try {
      await sessionApi.deleteSession(targetSessionId);
      setSessions((current) => {
        const next = current.filter((session) => session.id !== targetSessionId);
        if (activeFamilyId) {
          cachedAgentSessionsByFamilyId[activeFamilyId] = next;
        }
        return next;
      });
      if (sessionId === targetSessionId) {
        handleNewChat();
      }
    } catch (error) {
      setSessionError(error instanceof Error ? error.message : '删除会话失败。');
    }
  }, [activeFamilyId, handleNewChat, sessionId]);

  const handleTargetChange = useCallback(async (nextTargetSelection: AgentTargetSelection) => {
    const normalizedSelection = normalizeTargetSelection(nextTargetSelection, selfUserId);
    const currentSelection = normalizeTargetSelection(targetSelection, selfUserId);
    if (normalizedSelection === currentSelection) {
      return;
    }
    if (isStreaming) {
      stopStreaming();
    }

    const nextMode: AgentMode = selectionMode(normalizedSelection, selfUserId);
    const nextMirrorTargetUserId = selectionMirrorTargetUserId(normalizedSelection, selfUserId);
    const nextTargetMember = selectionTargetMember(normalizedSelection, members, nextMirrorTargetUserId, null);
    const nextTargetLabel = selectionLabel(normalizedSelection, nextTargetMember);
    const hasConversation = useChatStore.getState().messages.length > 0 || Boolean(activeSessionDetailRef.current?.messageCount);

    setTargetSelection(normalizedSelection);
    setSaveFeedback({});

    if (!hasConversation) {
      return;
    }

    const marker = buildTargetSwitchMessage(nextMode, nextTargetLabel, nextTargetMember);
    setMessages([...useChatStore.getState().messages, marker]);
    try {
      await appendSessionMessages([marker]);
    } catch {
      // appendSessionMessages already exposes the failure in page state.
    }
    if (activeSessionDetailRef.current?.id) {
      try {
        await sessionApi.patchSession(activeSessionDetailRef.current.id, {
          metadata: { ...activeSessionDetailRef.current.metadata, hasTargetSwitches: true },
        });
      } catch {
        // non-critical
      }
    }
  }, [appendSessionMessages, isStreaming, members, selfUserId, setMessages, stopStreaming, targetSelection]);

  const handleSaveMessage = useCallback(async (message: ChatMessage) => {
    if (!activeFamilyId) {
      setSaveFeedback((current) => ({
        ...current,
        [message.id]: { status: 'error', detail: '请先选择一个家庭再保存。' },
      }));
      return;
    }

    const originalContent = message.content.trim();
    if (!originalContent) return;

    setSaveFeedback((current) => ({
      ...current,
      [message.id]: { status: 'saving', detail: '保存中...' },
    }));

    const currentMode = mode;
    const currentTargetName = currentMode === 'mirror' ? memberName(targetMember) : (activeMembership?.relationshipLabel || '');
    let skillRunId: number | null = null;

    try {
      const skillRun = await skillRunApi.create({
        familyId: activeFamilyId,
        skillName: 'save_memory',
        status: 'RUNNING',
        source: currentMode === 'mirror' ? 'MIRROR_AGENT_CHAT' : 'FAMILY_AGENT_CHAT',
        inputSummary: truncateAuditText(originalContent),
        saved: false,
      });
      skillRunId = skillRun.id;

      const planResult = await memoryApi.planSaveTool({
        message: originalContent,
        familyContext: activeFamily?.name || '',
        conversationContext: recentMessages,
        targetMemberName: currentTargetName,
        viewerRole,
      });

      const normalized = normalizeSaveToolPlan(planResult.data);
      const plan = normalized.should_save && normalized.tool !== 'NONE'
        ? normalized
        : normalizeSaveToolPlan(fallbackSavePlan(originalContent));

      const savedAt = new Date().toISOString();
      const commonMetadata = currentMode === 'mirror'
        ? {
            source: 'MIRROR_AGENT_TOOL',
            relationSource: 'MIRROR_AGENT_TOOL',
            relatedUserId: mirrorTargetUserId,
            relatedMemberName: currentTargetName,
            savedFromMessageRole: message.role,
            familyName: activeFamily?.name || '',
            viewerRole,
            ...saveMemorySkillMetadata(plan, savedAt),
          }
        : {
            source: 'FAMILY_COMPANION_TOOL',
            relationSource: 'FAMILY_AGENT_TOOL',
            savedFromMessageRole: message.role,
            familyName: activeFamily?.name || '',
            viewerRole,
            ...saveMemorySkillMetadata(plan, savedAt),
          };

      const saved = await writeMemoryApi.create(buildWriteMemorySaveRequest(
        activeFamilyId,
        plan,
        {
          ...commonMetadata,
          ...(plan.tool === 'FAMILY_MEMORY' && currentMode === 'mirror'
            ? {
                sourceType: 'FAMILY_EXPERIENCE',
                scenario: '镜像对话保存',
                target: currentTargetName,
              }
            : {}),
          ...(plan.tool === 'GROWTH_GUARD' && currentMode === 'mirror'
            ? {
                sourceType: 'GROWTH_OBSERVATION',
                followUpStatus: 'PENDING',
              }
            : {}),
        },
        currentMode === 'mirror' ? (mirrorTargetUserId || undefined) : undefined,
      ));
      const savedRecordId = saved.savedRecordId;

      if (skillRunId) {
        await skillRunApi.update(skillRunId, {
          status: 'SUCCEEDED',
          saved: true,
          outputSummary: savePlanDetail(plan, savedRecordId),
          metadata: {
            savedRecordType: savedRecordType(plan.tool),
            savedRecordId,
          },
        });
      }

      const savedMemory = savedMemoryFromPlan(plan, savedAt);
      if (savedMemory) {
        sessionSavedMemoriesRef.current = [...sessionSavedMemoriesRef.current, savedMemory].slice(-10);
      }

      setSaveFeedback((current) => ({
        ...current,
        [message.id]: {
          status: 'saved',
          detail: savePlanDetail(plan, savedRecordId),
          href: savedMemoryHref(plan, activeFamilyId),
        },
      }));
    } catch (error) {
      if (skillRunId) {
        try {
          await skillRunApi.update(skillRunId, {
            status: 'FAILED',
            saved: false,
            outputSummary: error instanceof Error ? error.message : '保存失败',
          });
        } catch {
          // ignore secondary failure
        }
      }

      setSaveFeedback((current) => ({
        ...current,
        [message.id]: {
          status: 'error',
          detail: error instanceof Error ? error.message : '保存失败，请稍后重试。',
        },
      }));
    }
  }, [activeFamily?.name, activeFamilyId, activeMembership?.relationshipLabel, mirrorTargetUserId, mode, recentMessages, targetMember, viewerRole]);

  const selectorOptions = useMemo(
    () => members.filter((member) => member.userId !== selfUserId),
    [members, selfUserId],
  );

  if (isLoading) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center text-sm text-stone-500">
        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
        正在加载家庭上下文...
      </div>
    );
  }

  if (!activeFamilyId) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-10">
        <div className="rounded-[32px] border border-dashed border-stone-300 bg-white/88 p-10 text-center shadow-[0_18px_48px_rgba(24,39,32,0.06)]">
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-emerald-100 text-emerald-800">
            <Sparkles className="h-6 w-6" />
          </div>
          <h1 className="mt-5 text-2xl font-semibold text-stone-950">请先选择家庭</h1>
          <p className="mt-3 text-sm leading-7 text-stone-500">
            FamilyAgent 会把家庭记忆、日记和成长记录作为对话上下文，先进入一个家庭空间再开始聊天。
          </p>
          <Link
            href="/dashboard/family"
            className="mt-6 inline-flex h-11 items-center rounded-full bg-stone-950 px-5 text-sm font-medium text-white transition hover:bg-stone-800"
          >
            打开家庭空间
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-[calc(100vh-4rem)] px-4 py-4 md:px-5 lg:px-6">
      <div className="mx-auto flex max-w-[1440px] flex-col gap-4">
        <div className="rounded-[28px] border border-white/80 bg-white/82 px-5 py-4 shadow-[0_18px_48px_rgba(24,39,32,0.08)] backdrop-blur-xl">
          <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
            <div className="min-w-0">
              <p className="text-xs font-semibold uppercase tracking-[0.24em] text-stone-400">
                {activeFamily?.name || 'FamilyAgent'}
              </p>
              <div className="mt-2 flex flex-wrap items-center gap-2">
                <h1 className="text-2xl font-semibold tracking-tight text-stone-950">FamilyAgent</h1>
                <span className={cn(
                  'rounded-full px-2.5 py-1 text-xs font-medium',
                  mode === 'mirror' ? 'bg-emerald-100 text-emerald-800' : 'bg-stone-200 text-stone-700',
                )}>
                  {mode === 'mirror' ? '镜像参考' : '家庭对话'}
                </span>
              </div>
              <div className="mt-2 flex flex-wrap items-center gap-2 text-sm text-stone-500">
                <span className="inline-flex items-center gap-1">
                  <UserRound className="h-4 w-4" />
                  当前对象：{targetLabel}
                </span>
                <span className="rounded-full bg-stone-100 px-2.5 py-1 text-xs text-stone-600">
                  {responseMode === 'quick' ? '快速模式' : '思考模式'}
                </span>
                {activeSessionDetail && (
                  <span className="truncate rounded-full bg-stone-100 px-2.5 py-1 text-xs text-stone-600">
                    会话：{getSessionTitle(activeSessionDetail)}
                  </span>
                )}
                {!!(activeSessionDetail?.messageCount || messages.filter((message) => message.role !== 'system').length) && (
                  <span className="text-xs text-stone-400">
                    {activeSessionDetail?.messageCount || messages.filter((message) => message.role !== 'system').length} 条消息
                    {activeSessionDetail?.archives?.length ? ` · ${activeSessionDetail.archives.length} 段归档` : ''}
                  </span>
                )}
                {activeSessionMetadata.hasTargetSwitches && (
                  <span className="rounded-full bg-amber-100 px-2.5 py-1 text-xs text-amber-800">
                    该会话已切换过对象
                  </span>
                )}
              </div>
            </div>

            <div className="flex flex-wrap items-center gap-2">
              <button
                type="button"
                onClick={() => setIsSessionsOpen((current) => !current)}
                className={cn(
                  'inline-flex h-11 items-center gap-2 rounded-full border px-4 text-sm font-medium transition',
                  isSessionsOpen
                    ? 'border-emerald-200 bg-emerald-50 text-emerald-800'
                    : 'border-stone-200 bg-white text-stone-700 hover:border-stone-300 hover:bg-stone-50',
                )}
              >
                <History className="h-4 w-4" />
                会话历史
              </button>
              <button
                type="button"
                onClick={() => setIsContextOpen((current) => !current)}
                className={cn(
                  'inline-flex h-11 items-center gap-2 rounded-full border px-4 text-sm font-medium transition',
                  isContextOpen
                    ? 'border-emerald-200 bg-emerald-50 text-emerald-800'
                    : 'border-stone-200 bg-white text-stone-700 hover:border-stone-300 hover:bg-stone-50',
                )}
              >
                <Bot className="h-4 w-4" />
                上下文
              </button>
              <button
                type="button"
                onClick={handleNewChat}
                className="inline-flex h-11 items-center gap-2 rounded-full bg-stone-950 px-4 text-sm font-medium text-white transition hover:bg-stone-800"
              >
                <Plus className="h-4 w-4" />
                新会话
              </button>
            </div>
          </div>
        </div>

        {sessionError && (
          <div className="rounded-[24px] border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
            {sessionError}
          </div>
        )}

        <section className="flex min-h-[70vh] flex-col overflow-hidden rounded-[32px] border border-white/80 bg-white/84 shadow-[0_22px_60px_rgba(24,39,32,0.08)] backdrop-blur-xl">
          <div className="border-b border-stone-200/70 px-5 py-4 md:px-6">
            <div className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
              <div className="min-w-0">
                <h2 className="truncate text-lg font-semibold text-stone-950">
                  {activeSessionDetail
                    ? getSessionTitle(activeSessionDetail)
                    : (mode === 'mirror' ? `与 ${targetLabel} 的镜像对话` : '新的家庭对话')}
                </h2>
                <p className="mt-1 text-sm text-stone-500">
                  {mode === 'mirror'
                    ? '基于授权可见资料提供镜像参考，并明确保留不确定性边界。'
                    : '围绕当前家庭的共享记忆、会话归档和长期上下文继续对话。'}
                </p>
              </div>

              <div className="flex flex-wrap items-center gap-2 text-xs text-stone-500">
                <span className="rounded-full bg-stone-100 px-2.5 py-1">对象：{targetLabel}</span>
                <span className="rounded-full bg-stone-100 px-2.5 py-1">
                  {activeSessionDetail?.messageCount || messages.filter((message) => message.role !== 'system').length} 条消息
                </span>
                {!!activeSessionDetail?.archives?.length && (
                  <span className="rounded-full bg-stone-100 px-2.5 py-1">
                    {activeSessionDetail.archives.length} 段归档
                  </span>
                )}
                {activeSessionMetadata.hasTargetSwitches && (
                  <span className="rounded-full bg-amber-100 px-2.5 py-1 text-amber-800">含对象切换</span>
                )}
              </div>
            </div>
          </div>

          <AgentMessageList
            messages={messages}
            isLoadingMessages={isLoadingMessages}
            mode={mode}
            targetLabel={targetLabel}
            saveFeedback={saveFeedback}
            onSaveMessage={(message) => { void handleSaveMessage(message); }}
            onOpenContext={() => setIsContextOpen(true)}
          />

          <form
            onSubmit={(event) => {
              event.preventDefault();
              if (isStreaming) {
                stopStreaming();
                return;
              }
              void handleSubmit();
            }}
            className="border-t border-stone-200/70 bg-white/88 px-4 py-4 md:px-6"
          >
            <div className="mx-auto max-w-3xl">
              <div className="mb-3 flex items-center justify-between gap-3">
                <div className="space-y-2">
                  <div className="flex flex-wrap items-center gap-2">
                    <button
                      type="button"
                      onClick={() => setResponseMode('quick')}
                      disabled={isStreaming}
                      className={cn(
                        'inline-flex h-9 items-center rounded-full px-3 text-xs font-medium transition',
                        responseMode === 'quick'
                          ? 'bg-stone-950 text-white'
                          : 'bg-stone-100 text-stone-600 hover:bg-stone-200',
                      )}
                    >
                      快速
                    </button>
                    <button
                      type="button"
                      onClick={() => setResponseMode('think')}
                      disabled={isStreaming}
                      className={cn(
                        'inline-flex h-9 items-center rounded-full px-3 text-xs font-medium transition',
                        responseMode === 'think'
                          ? 'bg-emerald-700 text-white'
                          : 'bg-emerald-50 text-emerald-700 hover:bg-emerald-100',
                      )}
                    >
                      思考
                    </button>
                  </div>
                  <p className="text-xs text-stone-500">
                    {responseMode === 'quick'
                      ? '快速模式不会联网，也不会触发家族记忆、记忆库、成长记录或历史会话召回。'
                      : mode === 'mirror'
                        ? `思考模式会结合 ${targetLabel} 的授权资料，并在必要时联网核验。`
                        : '思考模式会优先参考家庭共享记忆、记忆库、归档会话与长期上下文。'}
                  </p>
                </div>
                <VoiceInputButton
                  compact
                  onTranscript={(text) => setInput((current) => (current ? `${current}\n${text}` : text))}
                  disabled={isStreaming}
                />
              </div>

              <div className="flex items-end gap-3">
                <textarea
                  value={input}
                  onChange={(event) => setInput(event.target.value)}
                  placeholder={mode === 'mirror'
                    ? `可以问：“如果站在 ${targetLabel} 的经历里，会怎么建议我？”`
                    : '可以聊家庭记忆、成长观察、照护安排，或任何值得长期保留的内容...'}
                  disabled={isStreaming}
                  rows={4}
                  className="min-h-[116px] flex-1 resize-none rounded-[26px] border border-stone-200 bg-stone-50/80 px-4 py-3 text-sm text-stone-900 outline-none transition placeholder:text-stone-400 focus:border-emerald-500 focus:bg-white focus:ring-4 focus:ring-emerald-100 disabled:bg-stone-100"
                />
                <button
                  type="submit"
                  disabled={isStreaming ? false : !input.trim()}
                  className="inline-flex h-12 w-12 items-center justify-center rounded-2xl bg-stone-950 text-white transition hover:bg-stone-800 disabled:cursor-not-allowed disabled:opacity-50"
                  aria-label={isStreaming ? '停止输出' : '发送消息'}
                >
                  {isStreaming ? <Square className="h-4 w-4" /> : <Send className="h-4 w-4" />}
                </button>
              </div>
            </div>
          </form>
        </section>
      </div>

      <AgentSessionDrawer
        open={isSessionsOpen}
        familyName={activeFamily?.name}
        sessions={sessions}
        sessionId={sessionId}
        isLoadingSessions={isLoadingSessions}
        sessionError={sessionError}
        onClose={() => setIsSessionsOpen(false)}
        onRefresh={() => {
          if (!activeFamilyId) return;
          delete cachedAgentSessionsByFamilyId[activeFamilyId];
          setSessionsLoaded(false);
          void loadSessions();
        }}
        onLoadSession={(targetSessionId) => { void handleLoadSession(targetSessionId); }}
        onDeleteSession={(targetSessionId) => { void handleDeleteSession(targetSessionId); }}
      />

      <AgentContextPanel
        open={isContextOpen}
        mode={mode}
        targetLabel={targetLabel}
        targetSelection={targetSelection}
        selectorOptions={selectorOptions}
        isLoadingMembers={isLoadingMembers}
        activationScene={activationScene}
        modeReadiness={modeReadiness}
        mirrorContext={mirrorContext}
        isLoadingMirrorContext={isLoadingMirrorContext}
        contextLoaded={contextLoaded}
        contextError={contextError}
        activeFamilyId={activeFamilyId}
        onClose={() => setIsContextOpen(false)}
        onTargetChange={(nextTargetSelection) => { void handleTargetChange(nextTargetSelection); }}
        onSuggestedQuestion={(question) => {
          setInput(question);
          setIsContextOpen(false);
        }}
      />
    </div>
  );
}
