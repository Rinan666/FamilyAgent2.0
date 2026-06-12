'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import {
  BookHeart,
  Bot,
  CheckCircle,
  History,
  Loader2,
  Plus,
  Save,
  Send,
  Sparkles,
  Square,
  Trash2,
  UserRound,
  Users,
} from 'lucide-react';
import MathRenderer from '@/components/agent/MathRenderer';
import RagMemoryBadge from '@/components/agent/RagMemoryBadge';
import WebSearchBadge from '@/components/agent/WebSearchBadge';
import VoiceInputButton from '@/components/voice/VoiceInputButton';
import { useChat, type SessionSavedMemory, type UseChatRequestConfig } from '@/hooks/useChat';
import { useViewerRole } from '@/hooks/useViewerRole';
import { normalizeAssistantMetadata } from '@/hooks/chat/useChatHelpers';
import { useAuthStore } from '@/stores/authStore';
import { useChatStore } from '@/stores/chatStore';
import { cn, generateId } from '@/lib/utils';
import { diaryApi, familyApi, growthGuardApi, memoryApi, mirrorApi, sessionApi, skillRunApi } from '@/lib/api';
import { loadSessionMessagesChronologically } from '@/lib/sessionHistory';
import {
  buildDiarySaveRequest,
  buildFamilyMemorySaveRequest,
  buildGrowthGuardSaveRequest,
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
  AgentSessionMetadata,
  ChatMessage,
  ChatSessionDetail,
  ChatSessionSummary,
  DiaryEntry,
  FamilyMember,
  MemoryEntry,
  MirrorContextResponse,
  MirrorSourceRef,
  AgentSaveToolPlan,
} from '@/types';

type SaveFeedback = {
  status: 'saving' | 'saved' | 'error';
  detail: string;
  href?: string;
};

type ActivationSceneState = {
  label: string;
  instruction: string;
};

function formatSessionTime(value?: string) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleString('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function getSessionTitle(session: Pick<ChatSessionSummary, 'title' | 'summary'>) {
  return (session.title || session.summary || '未命名会话').slice(0, 32);
}

function parsePositiveNumber(value: unknown) {
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? number : null;
}

function memberName(member?: FamilyMember | null) {
  if (!member) return '家庭成员';
  return member.relationshipLabel?.trim()
    || member.nickname?.trim()
    || member.username?.trim()
    || `用户 ${member.userId}`;
}

function normalizeAgentSessionMetadata(metadata?: Record<string, unknown> | null): AgentSessionMetadata {
  const targetUserId = parsePositiveNumber(metadata?.targetUserId);
  const targetMemberName = typeof metadata?.targetMemberName === 'string' && metadata.targetMemberName.trim()
    ? metadata.targetMemberName.trim()
    : null;
  return {
    ...(metadata || {}),
    entry: typeof metadata?.entry === 'string' ? metadata.entry : 'agent',
    contextLabel: typeof metadata?.contextLabel === 'string' ? metadata.contextLabel : undefined,
    agentMode: metadata?.agentMode === 'mirror' ? 'mirror' : 'family',
    targetUserId,
    targetMemberName,
    hasTargetSwitches: Boolean(metadata?.hasTargetSwitches),
  };
}

function buildSessionMetadata(
  mode: AgentMode,
  targetMember?: FamilyMember | null,
  hasTargetSwitches = false,
): AgentSessionMetadata {
  return {
    entry: 'agent',
    contextLabel: mode === 'mirror' ? 'mirror_agent' : 'family_memory',
    agentMode: mode,
    targetUserId: mode === 'mirror' ? targetMember?.userId ?? null : null,
    targetMemberName: mode === 'mirror' ? memberName(targetMember) : null,
    hasTargetSwitches,
  };
}

function sessionBadge(metadata?: Record<string, unknown> | null) {
  const normalized = normalizeAgentSessionMetadata(metadata);
  if (normalized.hasTargetSwitches) {
    return {
      label: '曾切换对象',
      className: 'bg-amber-50 text-amber-700 ring-amber-200',
    };
  }
  if (normalized.agentMode === 'mirror') {
    return {
      label: '镜像参考',
      className: 'bg-purple-50 text-purple-700 ring-purple-200',
    };
  }
  return {
    label: '家族 Agent',
    className: 'bg-blue-50 text-blue-700 ring-blue-200',
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
  if (plan.tool === 'FAMILY_MEMORY') return `/dashboard/heritage${familyQuery}`;
  if (plan.tool === 'GROWTH_GUARD') {
    return `/dashboard/diary${familyId ? `?familyId=${familyId}&tab=growth` : '?tab=growth'}`;
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

function diaryTitle(entry: DiaryEntry) {
  return entry.structured?.title || entry.structured?.summary || entry.rawText.slice(0, 28) || '未命名记录';
}

function isRelatedDiary(entry: DiaryEntry) {
  return entry.metadata?.mirrorSourceType === 'RELATED_BY_FAMILY' || Boolean(entry.metadata?.relatedUserId);
}

function diarySourceCode(entry: DiaryEntry, index: number) {
  return `${isRelatedDiary(entry) ? 'R' : 'D'}${index + 1}`;
}

function diarySourceLabel(entry: DiaryEntry) {
  return isRelatedDiary(entry) ? '家人补充' : '本人记录';
}

function temporalLayerLabel(item: DiaryEntry | MemoryEntry) {
  const label = item.metadata?.temporalLayerLabel;
  return typeof label === 'string' && label.trim() ? label.trim() : '未分层';
}

function temporalLayerClass(item: DiaryEntry | MemoryEntry) {
  switch (item.metadata?.temporalLayer) {
    case 'FRESH':
      return 'bg-green-50 text-green-700';
    case 'FADING':
      return 'bg-yellow-50 text-yellow-700';
    case 'CORE_MEMORY':
      return 'bg-purple-50 text-purple-700';
    case 'IMPRESSION':
      return 'bg-gray-100 text-gray-600';
    default:
      return 'bg-gray-100 text-gray-500';
  }
}

function memoryTitle(memory: MemoryEntry) {
  if (memory.summary?.trim()) return memory.summary.trim();
  if (typeof memory.metadata?.scenario === 'string' && memory.metadata.scenario.trim()) {
    return `${memory.metadata.scenario.trim()}相关经验`;
  }
  return memory.content.slice(0, 28) || '未命名经验';
}

function memorySourceLabel(memory: MemoryEntry) {
  return memory.metadata?.coreMemory === true ? '核心记忆' : '经验沉淀';
}

function hasMirrorProfile(context?: MirrorContextResponse | null) {
  return Boolean(context?.mirrorProfile && Object.keys(context.mirrorProfile).length > 0);
}

function readinessLevel(context?: MirrorContextResponse | null) {
  if (!context) return { label: '等待资料', tone: 'gray' as const };
  const diaryCount = context.diaries?.length || 0;
  const memoryCount = context.memories?.length || 0;
  const libraryCount = context.libraryItems?.length || 0;
  const profileBonus = hasMirrorProfile(context) ? 1 : 0;
  const score = Math.min(5, diaryCount + memoryCount + libraryCount + profileBonus);
  if (score >= 5) return { label: '资料较充足', tone: 'green' as const };
  if (score >= 3) return { label: '可谨慎参考', tone: 'blue' as const };
  return { label: '资料偏少', tone: 'yellow' as const };
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
          title: typeof item.title === 'string' ? item.title : '未命名来源',
          url: typeof item.url === 'string' ? item.url : '',
          snippet: typeof item.snippet === 'string' ? item.snippet : '',
        }))
        .filter((item) => item.url)
        .slice(0, 4),
    },
  };
}

function buildTargetSwitchMessage(
  nextMode: AgentMode,
  nextTarget: FamilyMember | null,
): ChatMessage {
  const targetLabel = nextMode === 'mirror'
    ? `已切换到“${memberName(nextTarget)}”镜像参考模式。后续回答只基于已授权可见记录，不代表本人真实意图。`
    : '已切回家族 Agent 自身上下文。后续回答将基于当前家庭共享记忆与记录。';
  const sessionContextPatch = buildSessionMetadata(nextMode, nextTarget, true);
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

function AnswerEvidenceDisclosure({ message }: { message: ChatMessage }) {
  const sourceRefs = message.metadata?.sourceRefs || [];
  const insufficientSources = Boolean(message.metadata?.insufficientSources);
  const sourceSummary = typeof message.metadata?.sourceSummary === 'string' ? message.metadata.sourceSummary : '';
  const retrievalQuery = typeof message.metadata?.retrievalQuery === 'string' ? message.metadata.retrievalQuery : '';

  if (!sourceRefs.length && !insufficientSources) return null;

  const summaryText = insufficientSources
    ? '资料偏少，回答仅供参考'
    : `已参考 ${sourceRefs.length} 条授权资料`;

  return (
    <details className="mt-2 overflow-hidden rounded-lg border border-purple-100 bg-white/80 text-xs text-gray-600">
      <summary className="flex cursor-pointer list-none items-center justify-between gap-2 px-3 py-2">
        <span className="inline-flex items-center gap-1.5">
          <Bot className="h-3.5 w-3.5 text-purple-600" />
          <span className="font-medium text-gray-700">查看回答依据</span>
        </span>
        <span className="text-[11px] text-gray-500">{summaryText}</span>
      </summary>
      <div className="space-y-2 border-t border-purple-100 px-3 py-3">
        {sourceSummary && <p className="leading-5 text-gray-600">{sourceSummary}</p>}
        {retrievalQuery && (
          <p className="rounded-md bg-purple-50 px-2 py-1.5 text-[11px] leading-5 text-purple-700">
            本轮按“{retrievalQuery}”召回相关资料。
          </p>
        )}
        {insufficientSources && (
          <p className="rounded-md bg-amber-50 px-2 py-1.5 text-[11px] leading-5 text-amber-700">
            资料较少，回答仅供参考，不视为当事人原话。
          </p>
        )}
        {sourceRefs.slice(0, 5).map((source) => (
          <div key={`${message.id}-${source.code}`} className="rounded-md bg-gray-50 px-2.5 py-2">
            <div className="flex flex-wrap items-center gap-2">
              <span className="rounded bg-white px-1.5 py-0.5 font-medium text-gray-500 ring-1 ring-gray-200">
                {source.code}
              </span>
              <span className={`rounded-full px-2 py-0.5 ${source.toneClass}`}>
                {source.sourceLabel}
              </span>
              <span className="text-[11px] text-gray-400">{source.temporalLabel}</span>
            </div>
            <p className="mt-1 text-sm text-gray-700">{source.title}</p>
          </div>
        ))}
      </div>
    </details>
  );
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
  const [targetUserId, setTargetUserId] = useState<number | null>(null);
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

  const targetMember = useMemo(
    () => members.find((member) => member.userId === targetUserId) || mirrorContext?.targetMember || null,
    [members, mirrorContext?.targetMember, targetUserId],
  );
  const mode = useMemo<AgentMode>(
    () => (targetUserId && selfUserId && targetUserId !== selfUserId ? 'mirror' : 'family'),
    [selfUserId, targetUserId],
  );
  const modeReadiness = useMemo(() => readinessLevel(mirrorContext), [mirrorContext]);
  const activeSessionMetadata = useMemo(
    () => normalizeAgentSessionMetadata(activeSessionDetail?.metadata),
    [activeSessionDetail?.metadata],
  );

  const upsertSession = useCallback((session: ChatSessionSummary) => {
    setSessions((current) => {
      const next = current.filter((item) => item.id !== session.id);
      return [session, ...next];
    });
  }, []);

  const loadMembers = useCallback(async (familyId: number) => {
    setIsLoadingMembers(true);
    setContextError('');
    try {
      const memberList = await familyApi.getMembers(familyId);
      const nextMembers = Array.isArray(memberList) ? memberList : [];
      setMembers(nextMembers);
      setTargetUserId((current) => {
        const preferred = requestedTargetUserId
          && requestedTargetUserId !== selfUserId
          && nextMembers.some((member) => member.userId === requestedTargetUserId)
          ? requestedTargetUserId
          : null;
        if (preferred) return preferred;
        if (current && nextMembers.some((member) => member.userId === current && member.userId !== selfUserId)) {
          return current;
        }
        return null;
      });
    } catch (error) {
      setMembers([]);
      setTargetUserId(null);
      setContextError(error instanceof Error ? error.message : '加载家庭成员失败。');
    } finally {
      setIsLoadingMembers(false);
    }
  }, [requestedTargetUserId, selfUserId]);

  useEffect(() => {
    if (!activeFamilyId) {
      setMembers([]);
      setTargetUserId(null);
      setMirrorContext(null);
      setContextError('');
      return;
    }
    void loadMembers(activeFamilyId);
  }, [activeFamilyId, loadMembers]);

  const refreshMirrorContext = useCallback(async (familyId: number, userId: number, query?: string) => {
    const context = await mirrorApi.getContext(familyId, userId, query);
    setMirrorContext(context);
    return context;
  }, []);

  useEffect(() => {
    if (mode !== 'mirror' || !activeFamilyId || !targetUserId) {
      setMirrorContext(null);
      setIsLoadingMirrorContext(false);
      if (mode === 'family') {
        setContextError('');
      }
      return;
    }
    let active = true;
    setIsLoadingMirrorContext(true);
    setContextError('');
    mirrorApi.getContext(activeFamilyId, targetUserId)
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
  }, [activeFamilyId, mode, targetUserId]);

  const memoryContextResolver = useCallback(async ({
    query,
    defaultRecall,
  }: {
    query: string;
    history: Pick<ChatMessage, 'role' | 'content'>[];
    defaultRecall: () => Promise<{ context: string; metadata?: NonNullable<ChatMessage['metadata']> }>;
  }) => {
    if (mode !== 'mirror' || !activeFamilyId || !targetUserId) {
      return defaultRecall();
    }
    try {
      const context = await refreshMirrorContext(activeFamilyId, targetUserId, query);
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
  }, [activeFamilyId, mirrorContext, mode, refreshMirrorContext, targetMember, targetUserId]);

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
      message: `请以"${memberName(targetMember)}的镜像参考"模式回答：${message}`,
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
        metadata: buildSessionMetadata(mode, targetMember, false),
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
  }, [activeFamilyId, mode, setSessionId, targetMember, upsertSession]);

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
    memoryContextResolver,
    prepareRequest,
    normalizeStreamMetadata,
  });

  const recentMessages = useMemo(
    () => messages.filter((message) => message.role !== 'system').slice(-10),
    [messages],
  );

  const loadSessions = useCallback(async () => {
    setIsLoadingSessions(true);
    setSessionError('');
    try {
      const list = await sessionApi.getUserSessions(undefined, 30);
      const filtered = (list || []).filter((session) => (
        session.familyId === activeFamilyId
          && (!session.source || session.source === 'FAMILY_AGENT' || session.source === 'TUTOR')
      ));
      setSessions(filtered);
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

    if (!activeFamilyId) {
      setSessions([]);
      return;
    }
    void loadSessions();
  }, [activeFamilyId, discardStreaming, loadSessions, setMessages, setSessionId]);

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
      setSessions((current) => current.filter((session) => session.id !== targetSessionId));
      if (sessionId === targetSessionId) {
        handleNewChat();
      }
    } catch (error) {
      setSessionError(error instanceof Error ? error.message : '删除会话失败。');
    }
  }, [handleNewChat, sessionId]);

  const handleTargetChange = useCallback(async (nextTargetUserId: number | null) => {
    const normalizedTargetUserId = nextTargetUserId && nextTargetUserId !== selfUserId ? nextTargetUserId : null;
    if (normalizedTargetUserId === targetUserId || (!normalizedTargetUserId && !targetUserId)) {
      return;
    }
    if (isStreaming) {
      stopStreaming();
    }

    const nextMode: AgentMode = normalizedTargetUserId ? 'mirror' : 'family';
    const nextTargetMember = normalizedTargetUserId
      ? members.find((member) => member.userId === normalizedTargetUserId) || null
      : null;
    const hasConversation = useChatStore.getState().messages.length > 0 || Boolean(activeSessionDetailRef.current?.messageCount);

    setTargetUserId(normalizedTargetUserId);
    setSaveFeedback({});

    if (!hasConversation) {
      return;
    }

    const marker = buildTargetSwitchMessage(nextMode, nextTargetMember);
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
  }, [appendSessionMessages, isStreaming, members, selfUserId, setMessages, stopStreaming, targetUserId]);

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
            relatedUserId: targetUserId,
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

      let savedRecordId: number | undefined;
      if (plan.tool === 'DIARY') {
        const saved = await diaryApi.create(buildDiarySaveRequest(activeFamilyId, plan, commonMetadata));
        savedRecordId = saved.id;
      } else if (plan.tool === 'FAMILY_MEMORY') {
        const saved = await memoryApi.createFamilyMemory(buildFamilyMemorySaveRequest(activeFamilyId, plan, {
          ...commonMetadata,
          ...(currentMode === 'mirror'
            ? {
                sourceType: 'FAMILY_EXPERIENCE',
                scenario: '镜像对话保存',
                target: currentTargetName,
              }
            : {}),
        }));
        savedRecordId = saved.id;
      } else if (plan.tool === 'GROWTH_GUARD') {
        const saved = await growthGuardApi.createRecord(
          buildGrowthGuardSaveRequest(
            activeFamilyId,
            plan,
            todayString(),
            {
              ...commonMetadata,
              ...(currentMode === 'mirror'
                ? {
                    sourceType: 'GROWTH_OBSERVATION',
                    followUpStatus: 'PENDING',
                  }
                : {}),
            },
            currentMode === 'mirror' ? (targetUserId || undefined) : undefined,
          ),
        );
        savedRecordId = saved.id;
      }

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
  }, [activeFamily?.name, activeFamilyId, activeMembership?.relationshipLabel, mode, recentMessages, targetMember, targetUserId, viewerRole]);

  const selectorOptions = useMemo(
    () => members.filter((member) => member.userId !== selfUserId),
    [members, selfUserId],
  );

  if (isLoading) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center text-sm text-gray-500">
        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
        正在加载家庭上下文...
      </div>
    );
  }

  if (!activeFamilyId) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-10">
        <div className="rounded-2xl border border-dashed border-gray-300 bg-white p-8 text-center">
          <Sparkles className="mx-auto h-10 w-10 text-blue-600" />
          <h1 className="mt-4 text-xl font-semibold text-gray-900">请先选择家庭</h1>
          <p className="mt-2 text-sm text-gray-600">
            FamilyAgent 会把家庭记忆、日记和成长记录作为对话上下文。
          </p>
          <Link
            href="/dashboard/family"
            className="mt-5 inline-flex items-center rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
          >
            打开家庭空间
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="flex min-h-[calc(100vh-4rem)] flex-col gap-4 px-4 py-4 lg:flex-row">
      <aside className="w-full rounded-2xl border border-gray-200 bg-white lg:w-80">
        <div className="flex items-center justify-between border-b border-gray-100 px-4 py-3">
          <div>
            <div className="text-sm font-semibold text-gray-900">FamilyAgent</div>
            <div className="text-xs text-gray-500">{activeFamily?.name || '当前家庭'}</div>
          </div>
          <button
            type="button"
            onClick={handleNewChat}
            className="inline-flex items-center gap-1 rounded-lg border border-gray-200 px-3 py-1.5 text-xs text-gray-700 hover:bg-gray-50"
          >
            <Plus className="h-3.5 w-3.5" />
            新对话
          </button>
        </div>

        <div className="border-b border-gray-100 px-4 py-3">
          <div className={cn(
            'rounded-xl px-3 py-3 text-sm',
            mode === 'mirror' ? 'bg-purple-50 text-purple-900' : 'bg-blue-50 text-blue-900',
          )}
          >
            <div className="flex items-center gap-2 font-medium">
              {mode === 'mirror' ? <Bot className="h-4 w-4" /> : <BookHeart className="h-4 w-4" />}
              {mode === 'mirror' ? '镜像参考模式' : '家族记忆陪伴'}
            </div>
            <p className={cn(
              'mt-2 text-xs leading-5',
              mode === 'mirror' ? 'text-purple-800' : 'text-blue-800',
            )}
            >
              {mode === 'mirror'
                ? `正在参考 ${memberName(targetMember)} 的授权可见记录，回答不会冒充本人。`
                : '轻松聊天、无缝恢复长历史，并把重要时刻保存回家庭资产里。'}
            </p>
          </div>
        </div>

        <div className="px-4 py-3">
          <div className="mb-2 flex items-center gap-2 text-sm font-medium text-gray-900">
            <History className="h-4 w-4" />
            会话历史
          </div>

          {sessionError && (
            <div className="mb-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">
              {sessionError}
            </div>
          )}

          {isLoadingSessions ? (
            <div className="py-8 text-center text-xs text-gray-500">
              <Loader2 className="mx-auto mb-2 h-4 w-4 animate-spin" />
              正在加载会话...
            </div>
          ) : sessions.length === 0 ? (
            <div className="rounded-xl border border-dashed border-gray-200 px-3 py-6 text-center text-xs text-gray-500">
              还没有保存的会话，开始第一次家庭对话吧。
            </div>
          ) : (
            <div className="space-y-2">
              {sessions.map((session) => {
                const badge = sessionBadge(session.metadata);
                const metadata = normalizeAgentSessionMetadata(session.metadata);
                return (
                  <div
                    key={session.id}
                    className={cn(
                      'rounded-xl border px-3 py-2',
                      sessionId === session.id ? 'border-blue-200 bg-blue-50' : 'border-gray-200 bg-white',
                    )}
                  >
                    <button
                      type="button"
                      onClick={() => { void handleLoadSession(session.id); }}
                      className="w-full text-left"
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div className="text-sm font-medium text-gray-900">{getSessionTitle(session)}</div>
                        <span className={`rounded-full px-2 py-0.5 text-[10px] font-medium ring-1 ${badge.className}`}>
                          {badge.label}
                        </span>
                      </div>
                      {metadata.targetMemberName && metadata.agentMode === 'mirror' && !metadata.hasTargetSwitches && (
                        <div className="mt-1 text-[11px] text-purple-600">
                          对象：{metadata.targetMemberName}
                        </div>
                      )}
                      {session.summary && (
                        <div className="mt-1 line-clamp-2 text-xs text-gray-500">{session.summary}</div>
                      )}
                      <div className="mt-2 flex items-center justify-between text-[11px] text-gray-500">
                        <span>{formatSessionTime(session.lastMessageAt || session.startedAt)}</span>
                        <span>{session.messageCount || 0} 条消息</span>
                      </div>
                    </button>
                    <div className="mt-2 flex justify-end">
                      <button
                        type="button"
                        onClick={() => { void handleDeleteSession(session.id); }}
                        className="inline-flex items-center gap-1 text-xs text-gray-500 hover:text-rose-600"
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                        删除
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </aside>

      <section className="flex min-h-[70vh] min-w-0 flex-1 overflow-hidden rounded-2xl border border-gray-200 bg-white">
        <div className="flex min-w-0 flex-1 flex-col">
          <div className="border-b border-gray-100 px-5 py-4">
            <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
              <div>
                <div className="flex items-center gap-2">
                  <h1 className="text-lg font-semibold text-gray-900">
                    {mode === 'mirror' ? '镜像参考对话' : '家庭对话'}
                  </h1>
                  <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${
                    mode === 'mirror' ? 'bg-purple-50 text-purple-700' : 'bg-blue-50 text-blue-700'
                  }`}
                  >
                    {mode === 'mirror' ? '镜像参考' : '家族 Agent'}
                  </span>
                </div>
                <p className="mt-1 text-sm text-gray-600">
                  {mode === 'mirror'
                    ? '统一入口下的镜像参考模式，会继续使用同一会话列表，但后续回答只基于当前选择对象的授权记录。'
                    : '当前会话会继续沿用家庭共享记忆、成长记录和会话归档历史。'}
                </p>
              </div>

              <div className="w-full max-w-xl space-y-2">
                <label htmlFor="agent-target" className="text-xs font-medium text-gray-500">
                  对话对象
                </label>
                <div className="flex flex-col gap-2 sm:flex-row">
                  <select
                    id="agent-target"
                    value={targetUserId ?? ''}
                    onChange={(event) => {
                      const nextTargetId = parsePositiveNumber(event.target.value);
                      void handleTargetChange(nextTargetId);
                    }}
                    className="h-10 flex-1 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
                  >
                    <option value="">自己 / 家族 Agent</option>
                    {selectorOptions.map((member) => (
                      <option key={member.userId} value={member.userId}>
                        {memberName(member)}
                      </option>
                    ))}
                  </select>
                  <div className="flex items-center gap-2 rounded-lg border border-gray-200 bg-gray-50 px-3 py-2 text-xs text-gray-600">
                    <Users className="h-3.5 w-3.5 text-gray-400" />
                    {isLoadingMembers
                      ? '正在加载成员...'
                      : selectorOptions.length > 0
                        ? `可切换 ${selectorOptions.length} 位家庭成员`
                        : '当前暂无其他可切换成员'}
                  </div>
                </div>
                {activeSessionDetail && (
                  <div className="rounded-xl border border-gray-200 bg-gray-50 px-3 py-2 text-xs text-gray-600">
                    <div className="font-medium text-gray-800">{getSessionTitle(activeSessionDetail)}</div>
                    <div className="mt-1">
                      {activeSessionDetail.messageCount || 0} 条消息
                      {activeSessionDetail.archives?.length ? ` · ${activeSessionDetail.archives.length} 段归档` : ''}
                    </div>
                    {activeSessionMetadata.hasTargetSwitches && (
                      <div className="mt-1 text-amber-700">这条会话里已经发生过对象切换。</div>
                    )}
                  </div>
                )}
              </div>
            </div>
          </div>

          <div className="flex min-h-0 flex-1 flex-col xl:flex-row">
            <div className="flex min-h-0 flex-1 flex-col">
              <div className="flex-1 space-y-4 overflow-y-auto bg-gray-50/60 px-4 py-4">
                {isLoadingMessages && (
                  <div className="py-6 text-center text-sm text-gray-500">
                    <Loader2 className="mx-auto mb-2 h-4 w-4 animate-spin" />
                    正在恢复会话...
                  </div>
                )}

                {!isLoadingMessages && messages.length === 0 ? (
                  <div className="mx-auto max-w-2xl rounded-2xl border border-dashed border-gray-200 bg-white px-6 py-10 text-center">
                    <Sparkles className="mx-auto h-10 w-10 text-blue-600" />
                    <h2 className="mt-4 text-lg font-semibold text-gray-900">
                      {mode === 'mirror' ? `开始和 ${memberName(targetMember)} 的镜像参考对话` : '开始一段家庭对话'}
                    </h2>
                    <p className="mt-2 text-sm leading-6 text-gray-600">
                      {mode === 'mirror'
                        ? '你可以追问“如果以他的经历来看，我现在该怎么选择”，也可以直接讨论关系、照护和家族经验。'
                        : '可以聊家庭记忆、成长记录、照护、价值观，或任何值得留下来的内容。'}
                    </p>
                  </div>
                ) : (
                  messages.map((message) => {
                    const feedback = saveFeedback[message.id];
                    if (message.role === 'system') {
                      return (
                        <div key={message.id} className="mx-auto max-w-2xl text-center">
                          <div className="inline-flex rounded-full border border-amber-200 bg-amber-50 px-4 py-2 text-xs leading-5 text-amber-800">
                            {message.content}
                          </div>
                        </div>
                      );
                    }

                    const isAssistant = message.role === 'assistant';
                    const isMirrorAssistant = message.metadata?.agentMode === 'mirror' || Boolean(message.metadata?.sourceRefs?.length);
                    return (
                      <div
                        key={message.id}
                        className={`mx-auto max-w-3xl rounded-2xl border px-4 py-3 shadow-sm ${isAssistant ? 'border-blue-100 bg-white' : 'border-gray-200 bg-gray-900 text-white'}`}
                      >
                        <div className="mb-2 flex items-center justify-between gap-3">
                          <div className={`text-xs font-medium ${isAssistant ? 'text-blue-700' : 'text-gray-200'}`}>
                            {isAssistant ? (isMirrorAssistant ? 'MirrorAgent' : 'FamilyAgent') : '你'}
                          </div>
                          <div className={`text-[11px] ${isAssistant ? 'text-gray-400' : 'text-gray-300'}`}>
                            {formatSessionTime(message.timestamp)}
                          </div>
                        </div>

                        <div className={`text-sm leading-7 ${isAssistant ? 'text-gray-800' : 'text-white'}`}>
                          {isAssistant ? <MathRenderer content={message.content} /> : message.content}
                        </div>

                        {isAssistant && <RagMemoryBadge metadata={message.metadata} />}
                        {isAssistant && <WebSearchBadge metadata={message.metadata} />}
                        {isAssistant && isMirrorAssistant && <AnswerEvidenceDisclosure message={message} />}

                        <div className="mt-3 flex items-center justify-between gap-3">
                          <button
                            type="button"
                            onClick={() => { void handleSaveMessage(message); }}
                            disabled={feedback?.status === 'saving'}
                            className={`inline-flex items-center gap-1 rounded-lg px-3 py-1.5 text-xs font-medium ${
                              isAssistant
                                ? 'border border-gray-200 text-gray-700 hover:bg-gray-50'
                                : 'border border-white/20 text-white hover:bg-white/10'
                            }`}
                          >
                            {feedback?.status === 'saving'
                              ? <Loader2 className="h-3.5 w-3.5 animate-spin" />
                              : <Save className="h-3.5 w-3.5" />}
                            保存这条消息
                          </button>

                          {feedback && (
                            <div className={`text-xs ${feedback.status === 'error' ? 'text-rose-600' : 'text-emerald-600'}`}>
                              {feedback.status === 'saved' && <CheckCircle className="mr-1 inline h-3.5 w-3.5" />}
                              {feedback.detail}
                              {feedback.href && (
                                <Link href={feedback.href} className="ml-2 underline underline-offset-2">
                                  打开
                                </Link>
                              )}
                            </div>
                          )}
                        </div>
                      </div>
                    );
                  })
                )}
              </div>

              <form
                onSubmit={(event) => {
                  event.preventDefault();
                  if (isStreaming) {
                    stopStreaming();
                    return;
                  }
                  void handleSubmit();
                }}
                className="border-t border-gray-100 bg-white px-4 py-3"
              >
                <div className="mx-auto max-w-3xl">
                  <div className="mb-2 flex justify-end">
                    <VoiceInputButton
                      onTranscript={(text) => setInput((current) => (current ? `${current}\n${text}` : text))}
                      disabled={isStreaming}
                    />
                  </div>
                  <div className="flex items-end gap-3">
                    <textarea
                      value={input}
                      onChange={(event) => setInput(event.target.value)}
                      placeholder={mode === 'mirror'
                        ? `可以问“如果站在 ${memberName(targetMember)} 的经验里，会怎么建议我？”`
                        : '可以聊家庭记忆、成长记录、照护、价值观，或任何值得保存下来的内容...'}
                      disabled={isStreaming}
                      rows={4}
                      className="min-h-[96px] flex-1 resize-none rounded-2xl border border-gray-200 px-4 py-3 text-sm outline-none transition focus:border-blue-400 focus:ring-2 focus:ring-blue-100 disabled:bg-gray-50"
                    />
                    <button
                      type="submit"
                      disabled={isStreaming ? false : !input.trim()}
                      className="inline-flex h-12 items-center justify-center rounded-xl bg-blue-600 px-4 text-sm font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      {isStreaming ? <Square className="h-4 w-4" /> : <Send className="h-4 w-4" />}
                    </button>
                  </div>
                </div>
              </form>
            </div>

            <aside className="w-full shrink-0 border-t border-gray-100 bg-white xl:w-80 xl:border-l xl:border-t-0">
              <div className="h-full space-y-4 overflow-y-auto px-4 py-4">
                <div className="rounded-xl border border-gray-200 bg-gray-50 p-4">
                  <div className="mb-2 flex items-center gap-2 text-sm font-semibold text-gray-900">
                    <UserRound className="h-4 w-4 text-purple-600" />
                    当前对象
                  </div>
                  <p className="text-sm font-medium text-gray-900">
                    {mode === 'mirror' ? memberName(targetMember) : '自己 / 家族 Agent'}
                  </p>
                  <p className="mt-1 text-xs leading-5 text-gray-500">
                    {mode === 'mirror'
                      ? '镜像模式只参考已授权可见资料，并明确保留“不确定”的边界。'
                      : '普通模式聚焦当前家庭共享记忆、成长记录、记忆库和本次会话历史。'}
                  </p>
                </div>

                {mode === 'family' ? (
                  <>
                    {activationScene && (
                      <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-xs text-amber-800">
                        <div className="font-medium">已激活上下文：{activationScene.label}</div>
                        <div className="mt-1 leading-5">{activationScene.instruction}</div>
                      </div>
                    )}
                    <div className="rounded-xl border border-blue-100 bg-blue-50 p-4">
                      <div className="flex items-center gap-2 text-sm font-semibold text-blue-900">
                        <BookHeart className="h-4 w-4" />
                        家族 Agent 说明
                      </div>
                      <p className="mt-2 text-xs leading-5 text-blue-800">
                        这里会综合家庭记忆、会话归档、记忆库、成长守护和待办传承任务，尽量把建议落到当前家庭语境。
                      </p>
                    </div>
                  </>
                ) : (
                  <>
                    {contextError && (
                      <div className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-xs text-rose-700">
                        {contextError}
                      </div>
                    )}
                    <div className="rounded-xl border border-purple-100 bg-purple-50 p-4">
                      <div className="flex items-center justify-between gap-2">
                        <div className="text-sm font-semibold text-purple-900">资料充分度</div>
                        <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${
                          modeReadiness.tone === 'green'
                            ? 'bg-green-50 text-green-700'
                            : modeReadiness.tone === 'blue'
                              ? 'bg-blue-50 text-blue-700'
                              : modeReadiness.tone === 'yellow'
                                ? 'bg-yellow-50 text-yellow-700'
                                : 'bg-gray-100 text-gray-600'
                        }`}
                        >
                          {modeReadiness.label}
                        </span>
                      </div>
                      <p className="mt-2 text-xs leading-5 text-purple-800">
                        {isLoadingMirrorContext
                          ? '正在刷新镜像资料...'
                          : mirrorContext?.sourceSummary || '当前还没有可展示的镜像来源摘要。'}
                      </p>
                    </div>

                    {mirrorContext?.disclaimer && (
                      <div className="rounded-xl border border-yellow-100 bg-yellow-50 p-4 text-xs leading-5 text-yellow-800">
                        {mirrorContext.disclaimer}
                      </div>
                    )}

                    {mirrorContext?.suggestedQuestions?.length ? (
                      <div className="rounded-xl border border-gray-200 p-4">
                        <div className="text-sm font-semibold text-gray-900">可以这样问</div>
                        <div className="mt-3 space-y-2">
                          {mirrorContext.suggestedQuestions.slice(0, 4).map((question) => (
                            <button
                              key={question}
                              type="button"
                              onClick={() => setInput(question)}
                              className="w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-left text-xs leading-5 text-gray-600 transition-colors hover:border-blue-200 hover:bg-blue-50 hover:text-blue-700"
                            >
                              {question}
                            </button>
                          ))}
                        </div>
                      </div>
                    ) : null}

                    {mirrorContext?.missingRecordSuggestions?.length ? (
                      <div className="rounded-xl border border-dashed border-gray-200 p-4">
                        <div className="text-sm font-semibold text-gray-900">让镜像更准确</div>
                        <div className="mt-3 space-y-2">
                          {mirrorContext.missingRecordSuggestions.slice(0, 3).map((suggestion) => (
                            <p key={suggestion} className="text-xs leading-5 text-gray-600">
                              {suggestion}
                            </p>
                          ))}
                        </div>
                      </div>
                    ) : null}

                    <div className="rounded-xl border border-gray-200 bg-gray-50 p-4 text-xs text-gray-600">
                      <div className="font-medium text-gray-800">快速入口</div>
                      <div className="mt-2 space-y-2">
                        <Link
                          href={`/dashboard/family/member?familyId=${activeFamilyId}&userId=${targetUserId || ''}`}
                          className="block rounded-lg border border-gray-200 bg-white px-3 py-2 text-gray-700 hover:border-purple-200 hover:bg-purple-50 hover:text-purple-700"
                        >
                          查看成员授权资料
                        </Link>
                        <Link
                          href={`/dashboard/diary?familyId=${activeFamilyId}${targetUserId ? `&relatedUserId=${targetUserId}&relatedMemberName=${encodeURIComponent(memberName(targetMember))}` : ''}`}
                          className="block rounded-lg border border-gray-200 bg-white px-3 py-2 text-gray-700 hover:border-purple-200 hover:bg-purple-50 hover:text-purple-700"
                        >
                          去补充相关记录
                        </Link>
                      </div>
                    </div>
                  </>
                )}
              </div>
            </aside>
          </div>
        </div>
      </section>
    </div>
  );
}
