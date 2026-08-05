'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Bot, Loader2, Menu, Plus, Send, Sparkles, Square } from 'lucide-react';
import AgentContextPanel from '@/components/agent/AgentContextPanel';
import AgentMessageList from '@/components/agent/AgentMessageList';
import AgentSessionDrawer from '@/components/agent/AgentSessionDrawer';
import { contextSwitchMessage } from '@/components/agent/agentResponseContext';
import {
  normalizeTargetSelection,
  selectionFromRequestedTargetUserId,
  selectionFromRequestedPersonaId,
  selectionLabel,
  selectionMirrorTargetUserId,
  selectionPersonaId,
  selectionMode,
  selectionTargetMember,
  selectionTargetPersona,
  type AgentTargetSelection,
} from '@/components/agent/agentTarget';
import {
  diarySourceCode,
  diarySourceLabel,
  diaryTitle,
  getSessionTitle,
  memberName,
  normalizeAgentSessionMetadata,
  parsePositiveNumber,
  readinessLevel,
  temporalLayerClass,
  temporalLayerLabel,
  isRelatedDiary,
} from '@/components/agent/agentDisplay';
import VoiceInputButton from '@/components/voice/VoiceInputButton';
import { useChat, type SessionSavedMemory, type UseChatRequestConfig } from '@/hooks/useChat';
import { useAgentSaveDraft } from '@/hooks/agent/useAgentSaveDraft';
import { useViewerRole } from '@/hooks/useViewerRole';
import { normalizeAssistantMetadata } from '@/hooks/chat/useChatHelpers';
import { useAuthStore } from '@/stores/authStore';
import { useChatStore } from '@/stores/chatStore';
import { cn, generateId } from '@/lib/utils';
import { familyApi, mirrorApi, sessionApi } from '@/lib/api';
import { isPlainEnter } from '@/lib/formKeyboard';
import { loadSessionMessagesChronologically } from '@/lib/sessionHistory';
import { memoryLibraryLabel, routeAgentSubmission, todayString } from '@/lib/savePlan';
import type {
  AgentMode,
  AgentMemorySavePlan,
  AgentSessionMetadata,
  ChatMessage,
  ChatSessionDetail,
  ChatSessionSummary,
  FamilyMember,
  MirrorContextResponse,
  MirrorSourceRef,
  PersonaMember,
} from '@/types';

function buildSessionMetadata(
  mode: AgentMode,
  targetLabel: string,
  targetMember?: FamilyMember | null,
  targetPersona?: PersonaMember | null,
  hasTargetSwitches = false,
): AgentSessionMetadata {
  return {
    entry: 'agent',
    contextLabel:
      mode === 'mirror' ? 'mirror_agent' : mode === 'persona' ? 'persona_member' : 'family_memory',
    agentMode: mode,
    targetUserId: mode === 'mirror' ? (targetMember?.userId ?? null) : null,
    targetPersonaId: mode === 'persona' ? (targetPersona?.id ?? null) : null,
    targetMemberName: mode === 'mirror' ? targetLabel : null,
    targetPersonaName: mode === 'persona' ? targetLabel : null,
    hasTargetSwitches,
  };
}

function savedMemoryFromPlan(plan: AgentMemorySavePlan, savedAt: string): SessionSavedMemory | null {
  if (!plan.should_save || !plan.content.trim()) return null;
  return {
    id: `saved-${plan.memory_library}-${plan.memory_type}-${savedAt}`,
    memoryLibrary: plan.memory_library,
    memoryType: plan.memory_type,
    label: memoryLibraryLabel(plan.memory_library),
    title: plan.title || memoryLibraryLabel(plan.memory_library),
    content: plan.content.trim(),
    visibility: String(plan.visibility || 'PRIVATE'),
    savedAt,
    reason: plan.reason,
  };
}

function hasMirrorProfile(context?: MirrorContextResponse | null) {
  return Boolean(context?.mirrorProfile && Object.keys(context.mirrorProfile).length > 0);
}

function sourceLead(context?: MirrorContextResponse | null) {
  if (context?.sourceSummary?.trim()) {
    return context.sourceSummary.trim();
  }
  const diaries = context?.diaries || [];
  const relatedDiaryCount = diaries.filter(isRelatedDiary).length;
  const selfDiaryCount = diaries.length - relatedDiaryCount;
  const growthCount = context?.growthRecords?.length || 0;
  const profileText = hasMirrorProfile(context) ? '，并参考了授权画像摘要' : '';
  return `本轮可参考 ${selfDiaryCount} 条本人记录、${relatedDiaryCount} 条家人补充、${growthCount} 条成长观察${profileText}。`;
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

  const growthRefs = (context.growthRecords || []).map((record, index) => ({
    code: `G${index + 1}`,
    title: record.content?.slice(0, 28) || record.category || '成长观察',
    sourceLabel: '成长观察',
    temporalLabel: temporalLayerLabel(record),
    toneClass: temporalLayerClass(record),
  }));

  return [...diaryRefs, ...growthRefs].slice(0, 8);
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

function normalizeMirrorAssistantMetadata(
  metadata: Record<string, unknown>,
): NonNullable<ChatMessage['metadata']> {
  return normalizeAssistantMetadata(metadata);
}

let cachedAgentMembersByFamilyId: Record<number, FamilyMember[]> = {};
let cachedAgentPersonasByFamilyId: Record<number, PersonaMember[]> = {};
let cachedAgentSessionsByFamilyId: Record<number, ChatSessionSummary[]> = {};
let cachedMirrorContextByFamilyTarget: Record<string, MirrorContextResponse> = {};

function buildTargetSwitchMessage(
  nextMode: AgentMode,
  nextTargetLabel: string,
  nextTarget: FamilyMember | null,
  nextPersona: PersonaMember | null,
): ChatMessage {
  const targetLabel = contextSwitchMessage(nextMode, nextTargetLabel);
  const sessionContextPatch = buildSessionMetadata(
    nextMode,
    nextTargetLabel,
    nextTarget,
    nextPersona,
    true,
  );
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

function selectionFromSessionContext(
  session: ChatSessionDetail,
  selfUserId: number | null,
): AgentTargetSelection {
  const metadata = normalizeAgentSessionMetadata(session.metadata);
  if (metadata.agentMode === 'mirror' && metadata.targetUserId) {
    return normalizeTargetSelection(metadata.targetUserId, selfUserId);
  }
  if (metadata.agentMode === 'persona' && metadata.targetPersonaId) {
    return `PERSONA:${metadata.targetPersonaId}`;
  }
  if (session.agentContextType === 'MIRROR' && session.targetUserId) {
    return normalizeTargetSelection(session.targetUserId, selfUserId);
  }
  if (session.agentContextType === 'PERSONA' && session.targetPersonaId) {
    return `PERSONA:${session.targetPersonaId}`;
  }
  return 'NONE';
}

export default function AgentPage() {
  const searchParams = useSearchParams();
  const user = useAuthStore((state) => state.user);
  const routePrompt = searchParams.get('prompt')?.trim() || '';
  const requestedFamilyId = useMemo(
    () => parsePositiveNumber(searchParams.get('familyId')),
    [searchParams],
  );
  const requestedTargetUserId = useMemo(
    () => parsePositiveNumber(searchParams.get('targetUserId')),
    [searchParams],
  );
  const requestedPersonaId = useMemo(
    () => parsePositiveNumber(searchParams.get('targetPersonaId')),
    [searchParams],
  );

  const routePromptAppliedRef = useRef('');
  const sessionSavedMemoriesRef = useRef<SessionSavedMemory[]>([]);
  const createSessionPromiseRef = useRef<Promise<ChatSessionDetail> | null>(null);
  const sessionGenerationRef = useRef(0);
  const sessionIdRef = useRef<number | null>(null);
  const activeSessionDetailRef = useRef<ChatSessionDetail | null>(null);
  const autoRestoreFamilyIdRef = useRef<number | null>(null);
  const inputTextareaRef = useRef<HTMLTextAreaElement | null>(null);

  const [input, setInput] = useState('');
  const [members, setMembers] = useState<FamilyMember[]>([]);
  const [personas, setPersonas] = useState<PersonaMember[]>([]);
  const [targetSelection, setTargetSelection] = useState<AgentTargetSelection>('NONE');
  const [mirrorContext, setMirrorContext] = useState<MirrorContextResponse | null>(null);
  const [isLoadingMembers, setIsLoadingMembers] = useState(false);
  const [sessions, setSessions] = useState<ChatSessionSummary[]>([]);
  const [activeSessionDetail, setActiveSessionDetail] = useState<ChatSessionDetail | null>(null);
  const [isLoadingSessions, setIsLoadingSessions] = useState(false);
  const [isClearingSessions, setIsClearingSessions] = useState(false);
  const [isLoadingMessages, setIsLoadingMessages] = useState(false);
  const [sessionError, setSessionError] = useState('');
  const [contextError, setContextError] = useState('');
  const [isSessionsOpen, setIsSessionsOpen] = useState(false);
  const [isContextOpen, setIsContextOpen] = useState(false);
  const [sessionsLoaded, setSessionsLoaded] = useState(false);
  const [isProcessingSaveCommand, setIsProcessingSaveCommand] = useState(false);

  const {
    families,
    viewerRole,
    activeFamilyId,
    activeFamily,
    activeMembership,
    setActiveFamilyId,
    isLoading,
  } = useViewerRole();
  const sessionId = useChatStore((state) => state.sessionId);
  const chatFamilyId = useChatStore((state) => state.familyId);
  const setChatFamilyId = useChatStore((state) => state.setFamilyId);
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
    () =>
      selectionTargetMember(
        targetSelection,
        members,
        mirrorTargetUserId,
        mirrorContext?.targetMember || null,
      ),
    [members, mirrorContext?.targetMember, mirrorTargetUserId, targetSelection],
  );
  const selfMember = useMemo(
    () => members.find((member) => member.userId === selfUserId) || null,
    [members, selfUserId],
  );
  const targetPersonaId = useMemo(() => selectionPersonaId(targetSelection), [targetSelection]);
  const targetPersona = useMemo(
    () => selectionTargetPersona(targetSelection, personas),
    [personas, targetSelection],
  );
  const mode = useMemo<AgentMode>(
    () => selectionMode(targetSelection, selfUserId),
    [selfUserId, targetSelection],
  );
  const selfTargetLabel = useMemo(
    () =>
      selfMember?.username?.trim() ||
      user?.username?.trim() ||
      selfMember?.nickname?.trim() ||
      user?.nickname?.trim() ||
      (selfUserId ? `用户 ${selfUserId}` : '我'),
    [selfMember, selfUserId, user?.nickname, user?.username],
  );
  const targetLabel = useMemo(
    () =>
      targetSelection === 'SELF'
        ? selfTargetLabel
        : selectionLabel(targetSelection, targetMember, targetPersona),
    [selfTargetLabel, targetMember, targetPersona, targetSelection],
  );
  const modeReadiness = useMemo(() => readinessLevel(mirrorContext), [mirrorContext]);
  const saveTargetName =
    mode === 'mirror'
      ? memberName(targetMember)
      : mode === 'persona'
        ? targetPersona?.name || targetLabel
        : activeMembership?.relationshipLabel || '';
  const handleDraftSaved = useCallback((plan: AgentMemorySavePlan, savedAt: string) => {
    const savedMemory = savedMemoryFromPlan(plan, savedAt);
    if (savedMemory) {
      sessionSavedMemoriesRef.current = [...sessionSavedMemoriesRef.current, savedMemory].slice(
        -10,
      );
    }
  }, []);
  const { saveFeedback, resetSaveDrafts, prepareSaveDraft, confirmSaveDraft, cancelSaveDraft } =
    useAgentSaveDraft({
      activeFamilyId,
      familyName: activeFamily?.name,
      viewerRole,
      mode,
      targetName: saveTargetName,
      targetUserId: mirrorTargetUserId,
      targetPersonaId: targetPersona?.id ?? targetPersonaId,
      targetPersonaName: mode === 'persona' ? saveTargetName : '',
      sessionId: () => sessionIdRef.current,
      onSaved: handleDraftSaved,
    });

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

  const loadMembers = useCallback(
    async (familyId: number, forceRefresh: boolean = false) => {
      const applySelection = (
        current: AgentTargetSelection,
        nextMembers: FamilyMember[],
        nextPersonas: PersonaMember[],
      ) => {
        const preferredPersona = selectionFromRequestedPersonaId(requestedPersonaId, nextPersonas);
        if (preferredPersona !== 'NONE') return preferredPersona;
        const preferredMember = selectionFromRequestedTargetUserId(
          requestedTargetUserId,
          selfUserId,
          nextMembers,
        );
        if (preferredMember !== 'NONE') return preferredMember;

        const normalizedCurrent = normalizeTargetSelection(current, selfUserId);
        if (normalizedCurrent === 'SELF') return 'SELF';
        const currentPersonaId = selectionPersonaId(normalizedCurrent);
        if (currentPersonaId && nextPersonas.some((persona) => persona.id === currentPersonaId)) {
          return normalizedCurrent;
        }
        if (
          typeof normalizedCurrent === 'number' &&
          nextMembers.some((member) => member.userId === normalizedCurrent)
        ) {
          return normalizedCurrent;
        }
        return 'NONE';
      };

      const cachedMembersForFamily = cachedAgentMembersByFamilyId[familyId];
      const cachedPersonasForFamily = cachedAgentPersonasByFamilyId[familyId];
      const cacheHasRequestedPersona =
        !requestedPersonaId ||
        cachedPersonasForFamily?.some((persona) => persona.id === requestedPersonaId);
      if (
        !forceRefresh &&
        cachedMembersForFamily &&
        cachedPersonasForFamily &&
        cacheHasRequestedPersona
      ) {
        const cachedMembers = cachedMembersForFamily;
        const cachedPersonas = cachedPersonasForFamily;
        setMembers(cachedMembers);
        setPersonas(cachedPersonas);
        setTargetSelection((current) => applySelection(current, cachedMembers, cachedPersonas));
        return;
      }

      setIsLoadingMembers(true);
      setContextError('');
      try {
        const personaRequest = familyApi.listPersonaMembers(familyId).catch((error) => {
          if (requestedPersonaId) {
            throw error;
          }
          return [] as PersonaMember[];
        });
        const [memberList, personaList] = await Promise.all([
          familyApi.getMembers(familyId),
          personaRequest,
        ]);
        const nextMembers = Array.isArray(memberList) ? memberList : [];
        const nextPersonas = Array.isArray(personaList) ? personaList : [];
        cachedAgentMembersByFamilyId[familyId] = nextMembers;
        cachedAgentPersonasByFamilyId[familyId] = nextPersonas;
        setMembers(nextMembers);
        setPersonas(nextPersonas);
        setTargetSelection((current) => applySelection(current, nextMembers, nextPersonas));
      } catch (error) {
        setMembers([]);
        setPersonas([]);
        setTargetSelection('NONE');
        setContextError(error instanceof Error ? error.message : '加载对话对象失败。');
      } finally {
        setIsLoadingMembers(false);
      }
    },
    [requestedPersonaId, requestedTargetUserId, selfUserId],
  );

  useEffect(() => {
    if (!activeFamilyId) {
      setMembers([]);
      setPersonas([]);
      setTargetSelection('NONE');
      setMirrorContext(null);
      setContextError('');
      return;
    }
    void loadMembers(activeFamilyId);
  }, [activeFamilyId, loadMembers]);

  const refreshMirrorContext = useCallback(
    async (familyId: number, userId: number, query?: string) => {
      const cacheKey = `${familyId}:${userId}`;
      if (!query && cachedMirrorContextByFamilyTarget[cacheKey]) {
        const cached = cachedMirrorContextByFamilyTarget[cacheKey];
        setMirrorContext(cached);
        return cached;
      }
      const context = await mirrorApi.getContext(familyId, userId, query);
      if (!query) {
        cachedMirrorContextByFamilyTarget[cacheKey] = context;
      }
      setMirrorContext(context);
      return context;
    },
    [],
  );

  useEffect(() => {
    if (mode !== 'mirror' || !activeFamilyId || !mirrorTargetUserId) {
      setMirrorContext(null);
      if (mode === 'family' || mode === 'persona') {
        setContextError('');
      }
      return;
    }
    let active = true;
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
        if (!active) return;
      });
    return () => {
      active = false;
    };
  }, [activeFamilyId, mirrorTargetUserId, mode, refreshMirrorContext]);

  const memoryContextResolver = useCallback(
    async ({
      query,
      defaultRecall,
    }: {
      query: string;
      history: Pick<ChatMessage, 'role' | 'content'>[];
      defaultRecall: () => Promise<{
        context: string;
        metadata?: NonNullable<ChatMessage['metadata']>;
      }>;
    }) => {
      if (mode === 'persona' && targetPersona) {
        const metadata: NonNullable<ChatMessage['metadata']> = {
          agentMode: 'persona',
          targetPersonaId: targetPersona.id,
          targetPersonaName: targetPersona.name,
          targetMemberName: targetPersona.name,
          sourceSummary: '由后端基于精神成员档案、材料卡和当前家庭可见经验沉淀生成上下文。',
        };
        return {
          context: '',
          metadata,
        };
      }
      if (mode !== 'mirror' || !activeFamilyId || !mirrorTargetUserId) {
        return defaultRecall();
      }
      try {
        const context = await refreshMirrorContext(activeFamilyId, mirrorTargetUserId, query);
        setContextError('');
        return {
          context: '',
          metadata: buildMirrorAnswerMetadata(context, targetMember),
        };
      } catch (error) {
        setContextError(
          error instanceof Error
            ? `镜像上下文刷新失败，已使用当前资料：${error.message}`
            : '镜像上下文刷新失败，已使用当前资料。',
        );
        return {
          context: '',
          metadata: buildMirrorAnswerMetadata(mirrorContext, targetMember),
        };
      }
    },
    [
      activeFamilyId,
      mirrorContext,
      mirrorTargetUserId,
      mode,
      refreshMirrorContext,
      targetMember,
      targetPersona,
    ],
  );

  const prepareRequest = useCallback(
    async ({
      defaultRequest,
    }: {
      message: string;
      history: Pick<ChatMessage, 'role' | 'content'>[];
      memoryContext: { context: string; metadata?: NonNullable<ChatMessage['metadata']> };
      defaultRequest: UseChatRequestConfig;
    }) => {
      if (mode === 'persona' && targetPersona) {
        return {
          ...defaultRequest,
          subject: 'PersonaMemberAgent',
          contextLabel: 'persona_member',
          targetPersonaId: targetPersona.id,
          targetRole: 'MEMBER' as const,
        };
      }
      if (mode !== 'mirror' || !targetMember) {
        return defaultRequest;
      }
      return {
        ...defaultRequest,
        subject: 'MirrorAgent',
        contextLabel: 'mirror_agent',
        targetUserId: targetMember.userId,
        targetRole: 'MEMBER' as const,
      };
    },
    [mode, targetMember, targetPersona],
  );

  const getInitialAssistantMetadata = useCallback(
    () => buildSessionMetadata(mode, targetLabel, targetMember, targetPersona, false),
    [mode, targetLabel, targetMember, targetPersona],
  );

  const normalizeStreamMetadata = useCallback(
    (metadata: Record<string, unknown>): NonNullable<ChatMessage['metadata']> => {
      const normalized = mode === 'mirror'
        ? normalizeMirrorAssistantMetadata(metadata)
        : normalizeAssistantMetadata(metadata);
      if (normalized.contextChanged) {
        if (normalized.effectiveContext === 'FAMILY') {
          setTargetSelection('NONE');
        } else if (normalized.effectiveContext === 'MIRROR' && normalized.targetUserId) {
          setTargetSelection(normalized.targetUserId);
        } else if (normalized.effectiveContext === 'PERSONA' && normalized.targetPersonaId) {
          setTargetSelection(`PERSONA:${normalized.targetPersonaId}`);
        }
      }
      const effectiveMode: AgentMode = normalized.effectiveContext === 'MIRROR'
        ? 'mirror'
        : normalized.effectiveContext === 'PERSONA'
          ? 'persona'
          : normalized.effectiveContext === 'FAMILY'
            ? 'family'
            : mode;
      const effectiveTargetLabel = normalized.targetPersonaName
        || normalized.targetMemberName
        || targetLabel;
      return {
        ...getInitialAssistantMetadata(),
        ...normalized,
        ...(normalized.contextSwitchAcknowledged
          ? { switchMessage: contextSwitchMessage(effectiveMode, effectiveTargetLabel) }
          : {}),
      };
    },
    [getInitialAssistantMetadata, mode, targetLabel],
  );

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
        subject: mode === 'persona' ? 'PersonaMemberAgent' : 'FamilyAgent',
        source: 'FAMILY_AGENT',
        agentContextType: mode === 'mirror' ? 'MIRROR' : mode === 'persona' ? 'PERSONA' : 'FAMILY',
        targetUserId: mode === 'mirror' ? targetMember?.userId : undefined,
        targetPersonaId: mode === 'persona' ? targetPersona?.id : undefined,
        metadata: buildSessionMetadata(mode, targetLabel, targetMember, targetPersona, false),
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
  }, [activeFamilyId, mode, setSessionId, targetLabel, targetMember, targetPersona, upsertSession]);

  const appendSessionMessages = useCallback(
    async (newMessages: ChatMessage[]) => {
      if (!newMessages.length || !activeFamilyId) return;
      const generation = sessionGenerationRef.current;
      const isUserMessageDraft = newMessages.length === 1 && newMessages[0].role === 'user';
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
        if (sessionGenerationRef.current === generation && !isUserMessageDraft) {
          setSessionError(error instanceof Error ? error.message : '自动保存聊天记录失败。');
        }
        throw error;
      }
    },
    [activeFamilyId, ensureSessionHeader, setSessionId, upsertSession],
  );

  const { messages, isStreaming, sendMessage, stopStreaming, discardStreaming, reset } = useChat({
    viewerRole,
    targetRole: 'MEMBER',
    activeFamilyId,
    appendSessionMessages,
    getSessionSavedMemories: () => sessionSavedMemoriesRef.current,
    getSessionId: () => sessionIdRef.current,
    subject: 'FamilyAgent',
    contextLabel: 'family_memory',
    memoryContextResolver,
    prepareRequest,
    normalizeStreamMetadata,
    getInitialAssistantMetadata,
  });

  const loadSessions = useCallback(async () => {
    if (!activeFamilyId) {
      setSessions([]);
      setSessionsLoaded(false);
      return [];
    }

    const cachedSessions = cachedAgentSessionsByFamilyId[activeFamilyId];
    if (cachedSessions) {
      setSessions(cachedSessions);
      setSessionsLoaded(true);
      return cachedSessions;
    }

    setIsLoadingSessions(true);
    setSessionError('');
    try {
      const list = await sessionApi.getUserSessions(undefined, 30);
      const filtered = (list || []).filter(
        (session) =>
          session.familyId === activeFamilyId &&
          (!session.source || session.source === 'FAMILY_AGENT' || session.source === 'TUTOR'),
      );
      cachedAgentSessionsByFamilyId[activeFamilyId] = filtered;
      setSessions(filtered);
      setSessionsLoaded(true);
      return filtered;
    } catch (error) {
      setSessionError(error instanceof Error ? error.message : '加载会话历史失败。');
      return [];
    } finally {
      setIsLoadingSessions(false);
    }
  }, [activeFamilyId]);

  useEffect(() => {
    const nextFamilyId = activeFamilyId ?? null;
    if (chatFamilyId === nextFamilyId) {
      return;
    }

    sessionGenerationRef.current += 1;
    createSessionPromiseRef.current = null;
    sessionIdRef.current = null;
    activeSessionDetailRef.current = null;
    discardStreaming();
    setSessionId(null);
    setActiveSessionDetail(null);
    setMessages([]);
    setSessionError('');
    resetSaveDrafts();
    sessionSavedMemoriesRef.current = [];
    setIsSessionsOpen(false);
    setIsContextOpen(false);
    autoRestoreFamilyIdRef.current = null;
    setChatFamilyId(nextFamilyId);

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
  }, [
    activeFamilyId,
    chatFamilyId,
    discardStreaming,
    resetSaveDrafts,
    setChatFamilyId,
    setMessages,
    setSessionId,
  ]);

  useEffect(() => {
    if (!isSessionsOpen || sessionsLoaded || !activeFamilyId) return;
    void loadSessions();
  }, [activeFamilyId, isSessionsOpen, loadSessions, sessionsLoaded]);

  useEffect(() => {
    if (!routePrompt || routePromptAppliedRef.current === routePrompt) return;
    routePromptAppliedRef.current = routePrompt;
    setInput(routePrompt);
  }, [routePrompt]);

  useEffect(() => {
    const textarea = inputTextareaRef.current;
    if (!textarea) return;

    textarea.style.height = 'auto';
    textarea.style.height = `${Math.min(textarea.scrollHeight, 144)}px`;
  }, [input]);

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
    resetSaveDrafts();
    sessionSavedMemoriesRef.current = [];
  }, [discardStreaming, reset, resetSaveDrafts, setSessionId]);

  const loadAllSessionMessages = useCallback(
    (targetSessionId: number) =>
      loadSessionMessagesChronologically(sessionApi.getSessionMessages, targetSessionId, 40),
    [],
  );

  const handleLoadSession = useCallback(
    async (targetSessionId: number) => {
      discardStreaming();
      const generation = sessionGenerationRef.current + 1;
      sessionGenerationRef.current = generation;
      createSessionPromiseRef.current = null;
      setIsLoadingMessages(true);
      setSessionError('');
      try {
        const detail = await sessionApi.getSession(targetSessionId);
        const restoredMessages = (await loadAllSessionMessages(targetSessionId)).map((message) =>
          message.role === 'assistant' && message.metadata
            ? { ...message, metadata: normalizeAssistantMetadata(message.metadata) }
            : message,
        );
        if (sessionGenerationRef.current !== generation) {
          return;
        }
        sessionIdRef.current = detail.id;
        activeSessionDetailRef.current = detail;
        setSessionId(detail.id);
        setMessages(restoredMessages);
        setActiveSessionDetail(detail);
        setTargetSelection(selectionFromSessionContext(detail, selfUserId));
        resetSaveDrafts();
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
    },
    [
      discardStreaming,
      loadAllSessionMessages,
      resetSaveDrafts,
      selfUserId,
      setMessages,
      setSessionId,
      upsertSession,
    ],
  );

  useEffect(() => {
    if (!activeFamilyId || autoRestoreFamilyIdRef.current === activeFamilyId) return;

    autoRestoreFamilyIdRef.current = activeFamilyId;
    const generation = sessionGenerationRef.current;
    let cancelled = false;

    const restoreRecentSession = async () => {
      const availableSessions = await loadSessions();
      if (
        cancelled ||
        sessionGenerationRef.current !== generation ||
        sessionIdRef.current ||
        activeSessionDetailRef.current ||
        messages.length > 0 ||
        isStreaming
      ) {
        return;
      }

      const recentSession = availableSessions
        .filter((session) => (session.messageCount || 0) > 0)
        .sort((left, right) => {
          const rightTime = Date.parse(right.lastMessageAt || right.startedAt || '');
          const leftTime = Date.parse(left.lastMessageAt || left.startedAt || '');
          return (
            (Number.isFinite(rightTime) ? rightTime : 0) -
            (Number.isFinite(leftTime) ? leftTime : 0)
          );
        })[0];

      if (recentSession) {
        await handleLoadSession(recentSession.id);
      }
    };

    void restoreRecentSession();
    return () => {
      cancelled = true;
    };
  }, [activeFamilyId, handleLoadSession, isStreaming, loadSessions, messages.length]);

  const handleDeleteSession = useCallback(
    async (targetSessionId: number) => {
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
    },
    [activeFamilyId, handleNewChat, sessionId],
  );

  const handleClearSessions = useCallback(async () => {
    if (!activeFamilyId || sessions.length === 0 || isClearingSessions) return;

    setIsClearingSessions(true);
    setSessionError('');
    try {
      await sessionApi.deleteFamilyAgentSessions(activeFamilyId);
      cachedAgentSessionsByFamilyId[activeFamilyId] = [];
      setSessions([]);
      setSessionsLoaded(true);
      if (sessionId && sessions.some((session) => session.id === sessionId)) {
        handleNewChat();
      }
    } catch (error) {
      delete cachedAgentSessionsByFamilyId[activeFamilyId];
      setSessionsLoaded(false);
      setSessionError(error instanceof Error ? error.message : '删除全部会话失败。');
      void loadSessions();
    } finally {
      setIsClearingSessions(false);
    }
  }, [activeFamilyId, handleNewChat, isClearingSessions, loadSessions, sessionId, sessions]);

  const handleTargetChange = useCallback(
    async (nextTargetSelection: AgentTargetSelection) => {
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
      const nextTargetMember = selectionTargetMember(
        normalizedSelection,
        members,
        nextMirrorTargetUserId,
        null,
      );
      const nextPersona = selectionTargetPersona(normalizedSelection, personas);
      const nextTargetLabel = selectionLabel(normalizedSelection, nextTargetMember, nextPersona);
      const hasConversation =
        useChatStore.getState().messages.length > 0 ||
        Boolean(activeSessionDetailRef.current?.messageCount);

      setTargetSelection(normalizedSelection);
      resetSaveDrafts();

      if (!hasConversation) {
        return;
      }

      const marker = buildTargetSwitchMessage(
        nextMode,
        nextTargetLabel,
        nextTargetMember,
        nextPersona,
      );
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
    },
    [
      appendSessionMessages,
      isStreaming,
      members,
      personas,
      resetSaveDrafts,
      selfUserId,
      setMessages,
      stopStreaming,
      targetSelection,
    ],
  );

  const handleSubmit = useCallback(async () => {
    if (isStreaming || isProcessingSaveCommand) return;
    const submission = routeAgentSubmission(input, useChatStore.getState().messages);
    if (!submission.content) return;

    setInput('');
    if (submission.kind === 'explicit_save') {
      setIsProcessingSaveCommand(true);
      const userMessage = useChatStore.getState().addMessage('user', submission.content);
      const persistenceTask = appendSessionMessages([userMessage]).catch(() => undefined);
      try {
        await prepareSaveDraft(
          userMessage,
          submission.conversationContext,
          submission.requestedSave,
          submission.saveContent,
        );
      } finally {
        setIsProcessingSaveCommand(false);
        void persistenceTask;
      }
      return;
    }

    try {
      await ensureSessionHeader();
      await sendMessage(submission.content);
    } catch {
      // The chat pipeline already surfaces provider failures inline.
    }
  }, [
    appendSessionMessages,
    input,
    isProcessingSaveCommand,
    isStreaming,
    ensureSessionHeader,
    prepareSaveDraft,
    sendMessage,
  ]);

  const handleInputKeyDown = useCallback(
    (event: React.KeyboardEvent<HTMLTextAreaElement>) => {
      if (!isPlainEnter(event)) return;

      event.preventDefault();
      if (isStreaming) {
        stopStreaming();
        return;
      }
      if (!isProcessingSaveCommand) {
        void handleSubmit();
      }
    },
    [handleSubmit, isProcessingSaveCommand, isStreaming, stopStreaming],
  );

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
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-sky-100 text-sky-800">
            <Sparkles className="h-6 w-6" />
          </div>
          <h1 className="mt-5 text-2xl font-semibold text-stone-950">请先选择家庭</h1>
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

  const currentSessionTitle = activeSessionDetail
    ? getSessionTitle(activeSessionDetail)
    : mode === 'mirror'
      ? `与 ${targetLabel} 的镜像对话`
      : mode === 'persona'
        ? `请教 ${targetLabel}`
        : '新的家庭对话';

  return (
    <div className="h-[calc(100dvh-0.75rem)] overflow-hidden px-0 py-0 lg:h-[calc(100dvh-2rem)]">
      <div className="glass-panel-strong mx-auto flex h-full max-w-[1600px] min-h-0 overflow-hidden rounded-[28px]">
        <section className="flex min-w-0 flex-1 flex-col overflow-hidden bg-white/35">
          {sessionError && (
            <div className="mx-3 mt-3 shrink-0 rounded-md border border-rose-200 bg-rose-50 px-4 py-2 text-sm text-rose-700 lg:hidden">
              {sessionError}
            </div>
          )}

          <div className="sticky top-0 z-10 shrink-0 border-b border-white/70 bg-white/56 px-14 py-3.5 backdrop-blur-xl">
            <button
              type="button"
              onClick={() => setIsSessionsOpen(true)}
              className="absolute left-3 top-1/2 inline-flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded-full text-stone-950 transition hover:bg-stone-100 hover:text-sky-700 md:left-5"
              aria-label="会话历史"
              title="会话历史"
            >
              <Menu className="h-6 w-6" />
            </button>
            <h1 className="mx-auto max-w-[min(34rem,calc(100%-10rem))] truncate text-center text-base font-semibold leading-5 text-stone-950">
              {currentSessionTitle}
            </h1>
            <button
              type="button"
              onClick={handleNewChat}
              className="absolute right-3 top-1/2 inline-flex h-9 -translate-y-1/2 items-center gap-1.5 rounded-full border border-stone-200 bg-white px-3 text-sm font-medium text-stone-900 shadow-sm transition hover:border-sky-200 hover:bg-sky-50 hover:text-sky-700 md:right-5"
              aria-label="新会话"
              title="新会话"
            >
              <Plus className="h-4 w-4" />
              <span>新会话</span>
            </button>
          </div>

          <AgentMessageList
            messages={messages}
            isLoadingMessages={isLoadingMessages}
            isStreaming={isStreaming}
            saveFeedback={saveFeedback}
            families={families}
            activeFamilyId={activeFamilyId}
            onConfirmSaveDraft={(message, plan) => void confirmSaveDraft(message, plan)}
            onCancelSaveDraft={(message) => void cancelSaveDraft(message)}
            onOpenContext={() => setIsContextOpen(true)}
          />

          <form
            onSubmit={(event) => {
              event.preventDefault();
              if (isStreaming) {
                stopStreaming();
                return;
              }
              if (!isProcessingSaveCommand) void handleSubmit();
            }}
            className="shrink-0 bg-white px-3 pb-3 pt-2 md:px-5 md:pb-5"
          >
            <div className="glass-panel-strong mx-auto max-w-4xl rounded-[26px] p-2.5 md:p-3">
              <textarea
                ref={inputTextareaRef}
                value={input}
                onChange={(event) => setInput(event.target.value)}
                onKeyDown={handleInputKeyDown}
                placeholder="发消息或按住说话"
                disabled={isStreaming || isProcessingSaveCommand}
                rows={1}
                className="max-h-36 min-h-12 w-full resize-none overflow-y-auto rounded-[20px] border-0 bg-white px-3 py-2 text-base leading-7 text-stone-900 outline-none transition placeholder:text-stone-400 disabled:bg-stone-50"
              />

              <div className="mt-1.5 flex items-center justify-between gap-2 px-1 pb-1 pt-1">
                <div className="flex min-w-0 flex-wrap items-center gap-2">
                  <button
                    type="button"
                    onClick={() => setIsContextOpen(true)}
                    className={cn(
                      'inline-flex h-8 items-center gap-1.5 rounded-full border px-2.5 text-xs font-medium transition sm:px-3',
                      isContextOpen
                        ? 'border-blue-200 bg-blue-50 text-blue-700'
                        : 'border-blue-200 bg-blue-50 text-blue-700 hover:border-blue-300 hover:bg-blue-100',
                    )}
                  >
                    <Bot className="h-3.5 w-3.5" />
                    <span className="max-w-40 truncate">
                      {mode === 'mirror'
                        ? `${targetLabel} · 镜像参考`
                        : mode === 'persona'
                          ? `${targetLabel} · 补充视角`
                          : '家庭上下文'}
                    </span>
                  </button>
                </div>

                <div className="flex shrink-0 items-center justify-end gap-2">
                  <VoiceInputButton
                    compact
                    className="[&>button]:h-9 [&>button]:w-9 [&>button]:rounded-full [&>button]:border-stone-200 [&>button]:bg-stone-50 [&>button]:text-stone-600 [&>button:hover]:bg-stone-100"
                    onTranscript={(text) =>
                      setInput((current) => (current ? `${current}\n${text}` : text))
                    }
                    disabled={isStreaming || isProcessingSaveCommand}
                  />
                  <button
                    type="submit"
                    disabled={isProcessingSaveCommand || (isStreaming ? false : !input.trim())}
                    className={cn(
                      'inline-flex h-9 min-w-9 items-center justify-center gap-2 rounded-full px-3 text-sm font-medium text-white transition disabled:cursor-not-allowed disabled:opacity-50',
                      isStreaming ? 'bg-rose-600 hover:bg-rose-700' : 'bg-sky-700 hover:bg-sky-800',
                    )}
                    aria-label={
                      isStreaming ? '停止输出' : isProcessingSaveCommand ? '正在保存' : '发送消息'
                    }
                  >
                    {isStreaming ? (
                      <Square className="h-4 w-4" />
                    ) : isProcessingSaveCommand ? (
                      <Loader2 className="h-4 w-4 animate-spin" />
                    ) : (
                      <Send className="h-4 w-4" />
                    )}
                  </button>
                </div>
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
        isClearingSessions={isClearingSessions}
        onClose={() => setIsSessionsOpen(false)}
        onRefresh={() => {
          if (!activeFamilyId) return;
          delete cachedAgentSessionsByFamilyId[activeFamilyId];
          setSessionsLoaded(false);
          void loadSessions();
        }}
        onLoadSession={(targetSessionId) => {
          void handleLoadSession(targetSessionId);
        }}
        onDeleteSession={(targetSessionId) => {
          void handleDeleteSession(targetSessionId);
        }}
        onClearSessions={() => {
          void handleClearSessions();
        }}
      />

      <AgentContextPanel
        open={isContextOpen}
        mode={mode}
        familyName={activeFamily?.name?.trim() || '当前家族'}
        targetLabel={targetLabel}
        selfTargetLabel={selfTargetLabel}
        targetSelection={targetSelection}
        selectorOptions={selectorOptions}
        personaOptions={personas}
        targetPersona={targetPersona}
        isLoadingMembers={isLoadingMembers}
        modeReadiness={modeReadiness}
        mirrorContext={mirrorContext}
        contextError={contextError}
        activeFamilyId={activeFamilyId}
        onClose={() => setIsContextOpen(false)}
        onTargetChange={(nextTargetSelection) => {
          void handleTargetChange(nextTargetSelection);
        }}
      />
    </div>
  );
}
