'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Bot, ChevronDown, History, Loader2, MoreHorizontal, Plus, Send, Sparkles, Square, UserRound } from 'lucide-react';
import AgentContextPanel from '@/components/agent/AgentContextPanel';
import AgentMessageList from '@/components/agent/AgentMessageList';
import AgentSessionDrawer from '@/components/agent/AgentSessionDrawer';
import { buildPersonaProfileContext, personaSwitchMessage } from '@/components/agent/personaContext';
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
  type SaveFeedback,
  isRelatedDiary,
} from '@/components/agent/agentDisplay';
import VoiceInputButton from '@/components/voice/VoiceInputButton';
import { useChat, type SessionSavedMemory, type UseChatRequestConfig } from '@/hooks/useChat';
import { useViewerRole } from '@/hooks/useViewerRole';
import { normalizeAssistantMetadata, withTimeout } from '@/hooks/chat/useChatHelpers';
import { useAuthStore } from '@/stores/authStore';
import { useChatStore } from '@/stores/chatStore';
import { cn, generateId } from '@/lib/utils';
import { familyApi, memoryApi, mirrorApi, sessionApi, skillRunApi, writeMemoryApi } from '@/lib/api';
import { loadSessionMessagesChronologically } from '@/lib/sessionHistory';
import {
  buildWriteMemorySaveRequest,
  saveMemorySkillMetadata,
  savePlanDetail,
  savePlanPersistenceDecision,
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
  PersonaMaterial,
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
    contextLabel: mode === 'mirror' ? 'mirror_agent' : mode === 'persona' ? 'persona_member' : 'family_memory',
    agentMode: mode,
    targetUserId: mode === 'mirror' ? targetMember?.userId ?? null : null,
    targetPersonaId: mode === 'persona' ? targetPersona?.id ?? null : null,
    targetMemberName: mode === 'mirror' ? targetLabel : null,
    targetPersonaName: mode === 'persona' ? targetLabel : null,
    hasTargetSwitches,
  };
}

function savedMemoryHref(familyId?: number | null) {
  return `/dashboard/family?tab=library${familyId ? `&familyId=${familyId}` : ''}`;
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
let cachedAgentPersonasByFamilyId: Record<number, PersonaMember[]> = {};
let cachedAgentSessionsByFamilyId: Record<number, ChatSessionSummary[]> = {};
let cachedMirrorContextByFamilyTarget: Record<string, MirrorContextResponse> = {};
const PERSONA_MATERIALS_TIMEOUT_MS = 800;

function buildTargetSwitchMessage(
  nextMode: AgentMode,
  nextTargetLabel: string,
  nextTarget: FamilyMember | null,
  nextPersona: PersonaMember | null,
): ChatMessage {
  const targetLabel = nextMode === 'mirror'
    ? `已切换到 “${nextTargetLabel}” 的镜像参考模式。后续回答只基于授权可见记录，不代表本人真实意图。`
    : nextMode === 'persona'
      ? personaSwitchMessage(nextTargetLabel)
      : '已切回家庭 Agent 自身上下文。后续回答将基于当前家庭共享记忆与记录。';
  const sessionContextPatch = buildSessionMetadata(nextMode, nextTargetLabel, nextTarget, nextPersona, true);
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
  const requestedPersonaId = useMemo(() => parsePositiveNumber(searchParams.get('targetPersonaId')), [searchParams]);

  const routePromptAppliedRef = useRef('');
  const sessionSavedMemoriesRef = useRef<SessionSavedMemory[]>([]);
  const createSessionPromiseRef = useRef<Promise<ChatSessionDetail> | null>(null);
  const sessionGenerationRef = useRef(0);
  const sessionIdRef = useRef<number | null>(null);
  const activeSessionDetailRef = useRef<ChatSessionDetail | null>(null);
  const autoRestoreFamilyIdRef = useRef<number | null>(null);
  const actionsMenuRef = useRef<HTMLDivElement | null>(null);

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
  const [saveFeedback, setSaveFeedback] = useState<Record<string, SaveFeedback>>({});
  const [isSessionsOpen, setIsSessionsOpen] = useState(false);
  const [isContextOpen, setIsContextOpen] = useState(false);
  const [isActionsOpen, setIsActionsOpen] = useState(false);
  const [responseMode, setResponseMode] = useState<AgentResponseMode>('think');
  const [sessionsLoaded, setSessionsLoaded] = useState(false);

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
    if (!isActionsOpen) return;

    const closeOnOutsidePointerDown = (event: PointerEvent) => {
      if (actionsMenuRef.current?.contains(event.target as Node)) return;
      setIsActionsOpen(false);
    };

    document.addEventListener('pointerdown', closeOnOutsidePointerDown);
    return () => document.removeEventListener('pointerdown', closeOnOutsidePointerDown);
  }, [isActionsOpen]);

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
  const targetPersonaId = useMemo(
    () => selectionPersonaId(targetSelection),
    [targetSelection],
  );
  const targetPersona = useMemo(
    () => selectionTargetPersona(targetSelection, personas),
    [personas, targetSelection],
  );
  const mode = useMemo<AgentMode>(
    () => selectionMode(targetSelection, selfUserId),
    [selfUserId, targetSelection],
  );
  const targetLabel = useMemo(
    () => selectionLabel(targetSelection, targetMember, targetPersona),
    [targetMember, targetPersona, targetSelection],
  );
  const inputPlaceholder = useMemo(() => {
    const modeHint = responseMode === 'quick'
      ? '当前为快速模式：更快，少检索上下文，适合简单问题。思考模式会先整理家庭记忆和身份资料，回答更完整但稍慢。'
      : '当前为思考模式：会先整理家庭记忆和身份资料，回答更完整但稍慢。快速模式更快，少检索上下文，适合简单问题。';
    const promptHint = mode === 'mirror'
      ? `可以问 ${targetLabel} 的日常记录和成长观察里有什么线索...`
      : mode === 'persona'
        ? `可以向 ${targetLabel} 请教一个家庭问题...`
        : '可以聊需要家庭经验沉淀来参考的问题...';
    return `${modeHint}\n${promptHint}`;
  }, [mode, responseMode, targetLabel]);
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
    const applySelection = (current: AgentTargetSelection, nextMembers: FamilyMember[], nextPersonas: PersonaMember[]) => {
      const preferredPersona = selectionFromRequestedPersonaId(requestedPersonaId, nextPersonas);
      if (preferredPersona !== 'NONE') return preferredPersona;
      const preferredMember = selectionFromRequestedTargetUserId(requestedTargetUserId, selfUserId, nextMembers);
      if (preferredMember !== 'NONE') return preferredMember;

      const normalizedCurrent = normalizeTargetSelection(current, selfUserId);
      if (normalizedCurrent === 'SELF') return 'SELF';
      const currentPersonaId = selectionPersonaId(normalizedCurrent);
      if (currentPersonaId && nextPersonas.some((persona) => persona.id === currentPersonaId)) {
        return normalizedCurrent;
      }
      if (typeof normalizedCurrent === 'number' && nextMembers.some((member) => member.userId === normalizedCurrent)) {
        return normalizedCurrent;
      }
      return 'NONE';
    };

    const cachedMembersForFamily = cachedAgentMembersByFamilyId[familyId];
    const cachedPersonasForFamily = cachedAgentPersonasByFamilyId[familyId];
    const cacheHasRequestedPersona = !requestedPersonaId
      || cachedPersonasForFamily?.some((persona) => persona.id === requestedPersonaId);
    if (!forceRefresh && cachedMembersForFamily && cachedPersonasForFamily && cacheHasRequestedPersona) {
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
  }, [requestedPersonaId, requestedTargetUserId, selfUserId]);

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

  const refreshMirrorContext = useCallback(async (familyId: number, userId: number, query?: string) => {
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
  }, []);

  const loadPersonaMaterials = useCallback(async (familyId: number, personaId: number) => {
    const materials = await familyApi.listPersonaMaterials(familyId, personaId);
    return Array.isArray(materials) ? materials : [];
  }, []);

  useEffect(() => {
    if (mode !== 'mirror' || !activeFamilyId || !mirrorTargetUserId || responseMode === 'quick') {
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
  }, [activeFamilyId, mirrorTargetUserId, mode, refreshMirrorContext, responseMode]);

  const memoryContextResolver = useCallback(async ({
    query,
    defaultRecall,
  }: {
    query: string;
    history: Pick<ChatMessage, 'role' | 'content'>[];
    defaultRecall: () => Promise<{ context: string; metadata?: NonNullable<ChatMessage['metadata']> }>;
  }) => {
    if (mode === 'persona' && targetPersona) {
      const personaMaterials = activeFamilyId
        ? await withTimeout(
            loadPersonaMaterials(activeFamilyId, targetPersona.id),
            [] as PersonaMaterial[],
            PERSONA_MATERIALS_TIMEOUT_MS,
          ).catch(() => [] as PersonaMaterial[])
        : [];
      const personaContext = buildPersonaProfileContext(targetPersona, personaMaterials);
      const recalled: { context: string; metadata?: NonNullable<ChatMessage['metadata']> } = responseMode === 'quick'
        ? { context: '' }
        : await defaultRecall();
      const metadata: NonNullable<ChatMessage['metadata']> = {
        ...(recalled.metadata || {}),
        agentMode: 'persona',
        responseMode,
        targetPersonaId: targetPersona.id,
        targetPersonaName: targetPersona.name,
        targetMemberName: targetPersona.name,
        sourceSummary: recalled.context
          ? `基于精神成员档案、${personaMaterials.length} 张材料卡，并参考当前家庭可见经验沉淀。`
          : `基于精神成员档案和 ${personaMaterials.length} 张材料卡。当前未附加家庭经验沉淀。`,
      };
      return {
        context: [
          personaContext,
          recalled.context ? `家庭可见参考资料：\n${recalled.context}` : '',
        ].filter(Boolean).join('\n\n'),
        metadata,
      };
    }
    if (mode === 'mirror' && responseMode === 'quick') {
      const quickMetadata: NonNullable<ChatMessage['metadata']> = {
        agentMode: 'mirror',
        responseMode: 'quick',
        targetUserId: targetMember?.userId ?? mirrorTargetUserId,
        targetMemberName: targetLabel,
      };
      return {
        context: `镜像参考对象：${targetLabel}。当前为快速模式，未加载完整镜像资料；回答必须说明资料有限，不代表本人真实想法。`,
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
  }, [activeFamilyId, loadPersonaMaterials, mirrorContext, mirrorTargetUserId, mode, refreshMirrorContext, responseMode, targetLabel, targetMember, targetPersona]);

  const prepareRequest = useCallback(async ({ defaultRequest }: {
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
      targetRole: 'MEMBER' as const,
    };
  }, [mode, targetMember, targetPersona]);

  const getInitialAssistantMetadata = useCallback(
    () => buildSessionMetadata(mode, targetLabel, targetMember, targetPersona, false),
    [mode, targetLabel, targetMember, targetPersona],
  );

  const normalizeStreamMetadata = useCallback((metadata: Record<string, unknown>): NonNullable<ChatMessage['metadata']> => ({
    ...getInitialAssistantMetadata(),
    ...(mode === 'mirror'
      ? normalizeMirrorAssistantMetadata(metadata)
      : normalizeAssistantMetadata(metadata)),
  }), [getInitialAssistantMetadata, mode]);

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

  const appendSessionMessages = useCallback(async (newMessages: ChatMessage[]) => {
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
    getSessionSavedMemories: () => sessionSavedMemoriesRef.current,
    subject: 'FamilyAgent',
    contextLabel: 'family_memory',
    responseMode,
    memoryContextResolver,
    prepareRequest,
    normalizeStreamMetadata,
    getInitialAssistantMetadata,
  });

  const recentMessages = useMemo(
    () => messages.filter((message) => message.role !== 'system').slice(-10),
    [messages],
  );

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
      const filtered = (list || []).filter((session) => (
        session.familyId === activeFamilyId
          && (!session.source || session.source === 'FAMILY_AGENT' || session.source === 'TUTOR')
      ));
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
    sessionSavedMemoriesRef.current = [];
    setIsSessionsOpen(false);
    setIsContextOpen(false);
    setIsActionsOpen(false);
    autoRestoreFamilyIdRef.current = null;

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

  useEffect(() => {
    if (!activeFamilyId || autoRestoreFamilyIdRef.current === activeFamilyId) return;

    autoRestoreFamilyIdRef.current = activeFamilyId;
    const generation = sessionGenerationRef.current;
    let cancelled = false;

    const restoreRecentSession = async () => {
      const availableSessions = await loadSessions();
      if (
        cancelled
        || sessionGenerationRef.current !== generation
        || sessionIdRef.current
        || activeSessionDetailRef.current
      ) {
        return;
      }

      const recentSession = availableSessions
        .filter((session) => (session.messageCount || 0) > 0)
        .sort((left, right) => {
          const rightTime = Date.parse(right.lastMessageAt || right.startedAt || '');
          const leftTime = Date.parse(left.lastMessageAt || left.startedAt || '');
          return (Number.isFinite(rightTime) ? rightTime : 0) - (Number.isFinite(leftTime) ? leftTime : 0);
        })[0];

      if (recentSession) {
        await handleLoadSession(recentSession.id);
      }
    };

    void restoreRecentSession();
    return () => {
      cancelled = true;
    };
  }, [activeFamilyId, handleLoadSession, loadSessions]);

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
    const nextPersona = selectionTargetPersona(normalizedSelection, personas);
    const nextTargetLabel = selectionLabel(normalizedSelection, nextTargetMember, nextPersona);
    const hasConversation = useChatStore.getState().messages.length > 0 || Boolean(activeSessionDetailRef.current?.messageCount);

    setTargetSelection(normalizedSelection);
    setSaveFeedback({});

    if (!hasConversation) {
      return;
    }

    const marker = buildTargetSwitchMessage(nextMode, nextTargetLabel, nextTargetMember, nextPersona);
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
  }, [appendSessionMessages, isStreaming, members, personas, selfUserId, setMessages, stopStreaming, targetSelection]);

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
    const currentTargetName = currentMode === 'mirror'
      ? memberName(targetMember)
      : currentMode === 'persona'
        ? targetPersona?.name || targetLabel
        : (activeMembership?.relationshipLabel || '');
    let skillRunId: number | null = null;

    try {
      const skillRun = await skillRunApi.create({
        familyId: activeFamilyId,
        skillName: 'save_memory',
        status: 'RUNNING',
        source: currentMode === 'mirror'
          ? 'MIRROR_AGENT_CHAT'
          : currentMode === 'persona'
            ? 'PERSONA_MEMBER_CHAT'
            : 'FAMILY_AGENT_CHAT',
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

      const decision = savePlanPersistenceDecision(planResult.data);
      const plan = decision.plan;
      if (!decision.shouldPersist) {
        if (skillRunId) {
          await skillRunApi.update(skillRunId, {
            status: 'SUCCEEDED',
            saved: false,
            outputSummary: decision.skippedDetail,
            metadata: {
              savedRecordType: 'NONE',
              plannedTool: plan.tool,
              plannedReason: plan.reason,
            },
          });
        }

        setSaveFeedback((current) => ({
          ...current,
          [message.id]: {
            status: 'skipped',
            detail: decision.skippedDetail,
          },
        }));
        return;
      }

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
        : currentMode === 'persona'
          ? {
              source: 'PERSONA_MEMBER_TOOL',
              relationSource: 'PERSONA_MEMBER_TOOL',
              relatedPersonaId: targetPersona?.id ?? targetPersonaId,
              relatedPersonaName: currentTargetName,
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
          ...(plan.tool === 'FAMILY_MEMORY' && currentMode === 'persona'
            ? {
                sourceType: 'FAMILY_EXPERIENCE',
                scenario: '精神成员对话保存',
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
          href: savedMemoryHref(activeFamilyId),
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
  }, [
    activeFamily?.name,
    activeFamilyId,
    activeMembership?.relationshipLabel,
    mirrorTargetUserId,
    mode,
    recentMessages,
    targetLabel,
    targetMember,
    targetPersona,
    targetPersonaId,
    viewerRole,
  ]);

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

  const visibleMessageCount = activeSessionDetail?.messageCount || messages.filter((message) => message.role !== 'system').length;
  const archiveCount = activeSessionDetail?.archives?.length || 0;
  const currentSessionTitle = activeSessionDetail
    ? getSessionTitle(activeSessionDetail)
    : (mode === 'mirror'
        ? `与 ${targetLabel} 的镜像对话`
        : mode === 'persona'
          ? `请教 ${targetLabel}`
          : '新的家庭对话');

  return (
    <div className="h-[calc(100dvh-6rem)] overflow-hidden px-0 py-0 lg:h-[calc(100dvh-2rem)]">
      <div className="mx-auto flex h-full max-w-[1600px] min-h-0 flex-col gap-2">
        {sessionError && (
          <div className="shrink-0 rounded-md border border-rose-200 bg-rose-50 px-4 py-2 text-sm text-rose-700">
            {sessionError}
          </div>
        )}

        <section className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-md border border-stone-200 bg-white">
          <div className="sticky top-0 z-10 shrink-0 border-b border-stone-200 bg-white px-3 py-2 pr-16 md:px-4 md:pr-20">
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <h1 className="truncate text-base font-semibold text-stone-950">FamilyAgent</h1>
                  <span className={cn(
                    'rounded-full px-2.5 py-1 text-xs font-medium',
                    mode === 'mirror'
                      ? 'bg-emerald-100 text-emerald-800'
                      : mode === 'persona'
                        ? 'bg-violet-100 text-violet-800'
                        : 'bg-stone-200 text-stone-700',
                  )}>
                    {mode === 'mirror' ? '镜像参考' : mode === 'persona' ? '精神成员' : '家庭对话'}
                  </span>
                  <span className="text-xs text-stone-400">{activeFamily?.name}</span>
                </div>
                <div className="mt-1 flex min-w-0 flex-wrap items-center gap-1.5 text-xs text-stone-500">
                  <span className="max-w-[18rem] truncate rounded bg-stone-100 px-2 py-0.5 text-stone-700">
                    {currentSessionTitle}
                  </span>
                  <span className="inline-flex items-center gap-1 rounded bg-stone-100 px-2 py-0.5">
                    <UserRound className="h-3.5 w-3.5" />
                    {targetLabel}
                  </span>
                  <span className="rounded bg-stone-100 px-2 py-0.5">
                    {responseMode === 'quick' ? '快速' : '思考'}
                  </span>
                  <span className="rounded bg-stone-100 px-2 py-0.5">{visibleMessageCount} 条消息</span>
                  {!!archiveCount && (
                    <span className="rounded bg-stone-100 px-2 py-0.5">{archiveCount} 段归档</span>
                  )}
                  {activeSessionMetadata.hasTargetSwitches && (
                    <span className="rounded bg-amber-100 px-2 py-0.5 text-amber-800">含对象切换</span>
                  )}
                </div>
              </div>

              <div ref={actionsMenuRef} className="absolute right-2 top-2 shrink-0">
                <button
                  type="button"
                  onClick={() => setIsActionsOpen((current) => !current)}
                  aria-label="打开助手操作"
                  aria-expanded={isActionsOpen}
                  className={cn(
                    'inline-flex h-9 items-center justify-center gap-1 rounded-lg border px-2.5 text-xs font-medium shadow-sm transition',
                    isActionsOpen
                      ? 'border-emerald-200 bg-emerald-50 text-emerald-800'
                      : 'border-stone-200 bg-white text-stone-700 hover:border-stone-300 hover:bg-stone-50',
                  )}
                >
                  <MoreHorizontal className="h-4 w-4" />
                  <ChevronDown className={cn('h-3.5 w-3.5 transition-transform', isActionsOpen && 'rotate-180')} />
                </button>

                {isActionsOpen && (
                  <div className="absolute right-0 top-11 z-20 w-56 overflow-hidden rounded-md border border-stone-200 bg-white py-1 shadow-xl">
                    <button
                      type="button"
                      onClick={() => {
                        setIsSessionsOpen(true);
                        setIsActionsOpen(false);
                      }}
                      className={cn(
                        'flex w-full items-center gap-3 px-3 py-2.5 text-left text-sm transition',
                        isSessionsOpen ? 'bg-emerald-50 text-emerald-800' : 'text-stone-700 hover:bg-stone-50',
                      )}
                    >
                      <History className="h-4 w-4" />
                      <span className="min-w-0 flex-1 truncate">会话历史</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setIsContextOpen(true);
                        setIsActionsOpen(false);
                      }}
                      className={cn(
                        'flex w-full items-center gap-3 px-3 py-2.5 text-left text-sm transition',
                        isContextOpen ? 'bg-emerald-50 text-emerald-800' : 'text-stone-700 hover:bg-stone-50',
                      )}
                    >
                      <Bot className="h-4 w-4" />
                      <span className="min-w-0 flex-1 truncate">上下文</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        handleNewChat();
                        setIsActionsOpen(false);
                      }}
                      className="flex w-full items-center gap-3 px-3 py-2.5 text-left text-sm text-stone-700 transition hover:bg-stone-50"
                    >
                      <Plus className="h-4 w-4" />
                      <span className="min-w-0 flex-1 truncate">新会话</span>
                    </button>
                  </div>
                )}
              </div>
            </div>
          </div>

          <AgentMessageList
            messages={messages}
            isLoadingMessages={isLoadingMessages}
            isStreaming={isStreaming}
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
            className="shrink-0 border-t border-stone-200 bg-stone-50 px-2 py-2 md:px-3"
          >
            <div className="mx-auto max-w-5xl rounded-md border border-stone-200 bg-white p-2">
              <div className="hidden">
                <div className="flex flex-wrap items-center gap-1.5 text-[11px] text-stone-500">
                  <span className="rounded-full bg-stone-100 px-2.5 py-1 font-medium text-stone-700">
                    {mode === 'mirror' ? '镜像 AI' : mode === 'persona' ? targetLabel : 'FamilyAgent'}
                  </span>
                </div>
              </div>
              <textarea
                value={input}
                onChange={(event) => setInput(event.target.value)}
                placeholder={inputPlaceholder}
                disabled={isStreaming}
                rows={3}
                className="min-h-[88px] max-h-40 w-full resize-none rounded-md border border-stone-100 bg-stone-50/70 px-3 py-2 text-sm leading-6 text-stone-900 outline-none transition placeholder:text-stone-500 focus:border-emerald-400 focus:bg-white focus:ring-2 focus:ring-emerald-100 disabled:bg-stone-100"
              />

              <div className="mt-2 flex flex-col gap-2 px-1 pb-1 sm:flex-row sm:items-center sm:justify-between">
                <div className="min-w-0 space-y-2">
                  <div className="inline-flex rounded-md bg-stone-100/90 p-1">
                    <button
                      type="button"
                      onClick={() => setResponseMode('quick')}
                      disabled={isStreaming}
                      className={cn(
                        'inline-flex h-8 items-center rounded px-3 text-xs font-medium transition',
                        responseMode === 'quick'
                          ? 'bg-stone-950 text-white shadow-sm'
                          : 'text-stone-600 hover:bg-white/80',
                      )}
                    >
                      快速
                    </button>
                    <button
                      type="button"
                      onClick={() => setResponseMode('think')}
                      disabled={isStreaming}
                      className={cn(
                        'inline-flex h-8 items-center rounded px-3 text-xs font-medium transition',
                        responseMode === 'think'
                          ? 'bg-emerald-700 text-white shadow-sm'
                          : 'text-emerald-700 hover:bg-white/80',
                      )}
                    >
                      思考
                    </button>
                  </div>
                </div>

                <div className="flex shrink-0 items-center justify-end gap-2">
                  <VoiceInputButton
                    compact
                    className="[&>button]:h-9 [&>button]:w-9 [&>button]:rounded-md [&>button]:border-stone-200 [&>button]:bg-stone-50 [&>button]:text-stone-600 [&>button:hover]:bg-stone-100"
                    onTranscript={(text) => setInput((current) => (current ? `${current}\n${text}` : text))}
                    disabled={isStreaming}
                  />
                  <button
                    type="submit"
                    disabled={isStreaming ? false : !input.trim()}
                    className={cn(
                      'inline-flex h-9 min-w-9 items-center justify-center gap-2 rounded-md px-3 text-sm font-medium text-white transition disabled:cursor-not-allowed disabled:opacity-50',
                      isStreaming ? 'bg-rose-600 hover:bg-rose-700' : 'bg-stone-950 hover:bg-stone-800',
                    )}
                    aria-label={isStreaming ? '停止输出' : '发送消息'}
                  >
                    {isStreaming ? <Square className="h-4 w-4" /> : <Send className="h-4 w-4" />}
                    <span className="hidden sm:inline">{isStreaming ? '停止' : '发送'}</span>
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
        onLoadSession={(targetSessionId) => { void handleLoadSession(targetSessionId); }}
        onDeleteSession={(targetSessionId) => { void handleDeleteSession(targetSessionId); }}
        onClearSessions={() => { void handleClearSessions(); }}
      />

      <AgentContextPanel
        open={isContextOpen}
        mode={mode}
        targetLabel={targetLabel}
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
        onTargetChange={(nextTargetSelection) => { void handleTargetChange(nextTargetSelection); }}
      />
    </div>
  );
}
