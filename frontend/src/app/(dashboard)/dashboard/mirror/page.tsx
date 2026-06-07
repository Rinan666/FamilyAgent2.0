'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Bot, CheckCircle, FileText, Layers, Loader2, RefreshCw, Send, UserRound, Users, XCircle } from 'lucide-react';
import { diaryApi, familyApi, growthGuardApi, memoryApi, mirrorApi, tutorApi } from '@/lib/api';
import { useViewerRole } from '@/hooks/useViewerRole';
import VoiceInputButton from '@/components/voice/VoiceInputButton';
import type {
  AgentSaveToolPlan,
  ChatMessage,
  DiaryEntry,
  DiaryEntryType,
  DiaryVisibility,
  FamilyMember,
  GrowthGuardCategory,
  MemoryEntry,
  MemoryEntryType,
  MemoryScope,
  MirrorContextResponse,
} from '@/types';

type MirrorSourceRef = {
  code: string;
  title: string;
  sourceLabel: string;
  temporalLabel: string;
  toneClass: string;
};

type MirrorChatMessage = ChatMessage & {
  sourceRefs?: MirrorSourceRef[];
  sourceSummary?: string;
  insufficientSources?: boolean;
  retrievalQuery?: string;
  toolResult?: {
    label: string;
    detail: string;
  };
  pendingTool?: {
    plan: AgentSaveToolPlan;
    originalContent: string;
  };
};

function memberName(member?: FamilyMember | null) {
  if (!member) return '家族成员';
  return member.relationshipLabel?.trim() || member.nickname?.trim() || member.username?.trim() || `用户 ${member.userId}`;
}

function diaryTitle(entry: DiaryEntry) {
  return entry.structured?.title || entry.structured?.summary || entry.rawText.slice(0, 28) || '未命名记录';
}

function isRelatedDiary(entry: DiaryEntry) {
  return entry.metadata?.mirrorSourceType === 'RELATED_BY_FAMILY'
    || Boolean(entry.metadata?.relatedUserId);
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

function temporalNote(item: DiaryEntry | MemoryEntry) {
  const note = item.metadata?.temporalNote;
  return typeof note === 'string' ? note : '';
}

function memoryTitle(memory: MemoryEntry) {
  if (memory.summary?.trim()) return memory.summary.trim();
  if (typeof memory.metadata?.scenario === 'string' && memory.metadata.scenario.trim()) {
    return `${memory.metadata.scenario.trim()}相关经验`;
  }
  return memory.content.slice(0, 28) || '未命名经验';
}

function hasMirrorProfile(context?: MirrorContextResponse | null) {
  return Boolean(context?.mirrorProfile && Object.keys(context.mirrorProfile).length > 0);
}

function readinessLevel(context?: MirrorContextResponse | null) {
  if (!context) return { label: '加载中', tone: 'gray', score: 0 };
  const diaryCount = context.diaries?.length || 0;
  const memoryCount = context.memories?.length || 0;
  const profileBonus = hasMirrorProfile(context) ? 1 : 0;
  const score = Math.min(5, diaryCount + memoryCount + profileBonus);
  if (score >= 5) return { label: '资料较充分', tone: 'green', score };
  if (score >= 3) return { label: '可谨慎参考', tone: 'blue', score };
  return { label: '资料偏少', tone: 'yellow', score };
}

function sourceLead(context?: MirrorContextResponse | null) {
  const diaries = context?.diaries || [];
  const relatedDiaryCount = diaries.filter(isRelatedDiary).length;
  const selfDiaryCount = diaries.length - relatedDiaryCount;
  const memoryCount = context?.memories?.length || 0;
  const profileText = hasMirrorProfile(context) ? '，并参考了授权画像摘要' : '';
  return `本轮可参考 ${selfDiaryCount} 条本人记录、${relatedDiaryCount} 条家人补充、${memoryCount} 条可见家族经验${profileText}。`;
}

function memorySourceLabel(memory: MemoryEntry) {
  return memory.metadata?.coreMemory === true ? '核心记忆' : '家族经验';
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

  return [...diaryRefs, ...memoryRefs].slice(0, 6);
}

function buildAnswerSourceMetadata(context?: MirrorContextResponse | null) {
  const sourceRefs = buildSourceRefs(context);
  return {
    sourceRefs,
    sourceSummary: context ? sourceLead(context) : '',
    insufficientSources: Boolean(context?.insufficientRecords || sourceRefs.length === 0),
    retrievalQuery: context?.retrievalQuery,
  };
}

function shouldPlanSaveTool(content: string) {
  return /(保存|存起来|记下来|记录一下|记录下来|沉淀|加入经验|写进|帮我记|帮我存)/.test(content);
}

function scopeFromPlan(plan: AgentSaveToolPlan): MemoryScope {
  const scope = String(plan.scope || plan.visibility || 'PRIVATE').toUpperCase();
  if (scope === 'CARE_VISIBLE' || scope === 'FAMILY_VISIBLE' || scope === 'PARENT_VISIBLE') return scope;
  return 'PRIVATE';
}

function visibilityFromPlan(plan: AgentSaveToolPlan): DiaryVisibility {
  const visibility = String(plan.visibility || plan.scope || 'PRIVATE').toUpperCase();
  if (visibility === 'FAMILY_VISIBLE' || visibility === 'CARE_VISIBLE' || visibility === 'LEGACY_VISIBLE') return visibility;
  return 'PRIVATE';
}

function entryTypeFromPlan(plan: AgentSaveToolPlan): DiaryEntryType {
  const entryType = String(plan.entry_type || 'DAILY').toUpperCase();
  if (
    entryType === 'IMPORTANT_EVENT'
    || entryType === 'LESSON'
    || entryType === 'EMOTION'
    || entryType === 'MESSAGE_TO_FAMILY'
    || entryType === 'SELF_REFLECTION'
  ) {
    return entryType;
  }
  return 'DAILY';
}

function todayString() {
  return new Date().toISOString().slice(0, 10);
}

function toolLabel(tool: AgentSaveToolPlan['tool']) {
  if (tool === 'DIARY') return '人生记录';
  if (tool === 'FAMILY_MEMORY') return '家族经验';
  if (tool === 'GROWTH_GUARD') return '成长守护';
  return '未保存';
}

function requiresSaveConfirmation(plan: AgentSaveToolPlan) {
  const visibility = String(plan.visibility || '').toUpperCase();
  const scope = String(plan.scope || '').toUpperCase();
  return plan.tool !== 'DIARY'
    || visibility !== 'PRIVATE'
    || (scope !== '' && scope !== 'PRIVATE');
}

function savePlanDetail(plan: AgentSaveToolPlan) {
  return `${toolLabel(plan.tool)} · ${plan.title} · ${plan.visibility || plan.scope}`;
}

export default function MirrorPage() {
  const searchParams = useSearchParams();
  const { families, activeFamilyId, setActiveFamilyId, viewerRole, isLoading: loadingFamilies } = useViewerRole();
  const [selectedFamilyId, setSelectedFamilyId] = useState<number | null>(null);
  const [members, setMembers] = useState<FamilyMember[]>([]);
  const [targetUserId, setTargetUserId] = useState<number | null>(null);
  const [mirrorContext, setMirrorContext] = useState<MirrorContextResponse | null>(null);
  const [diaries, setDiaries] = useState<DiaryEntry[]>([]);
  const [memories, setMemories] = useState<MemoryEntry[]>([]);
  const [messages, setMessages] = useState<MirrorChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [loadingContext, setLoadingContext] = useState(false);
  const [isStreaming, setIsStreaming] = useState(false);
  const [executingToolMessageId, setExecutingToolMessageId] = useState<string | null>(null);
  const [error, setError] = useState('');
  const endRef = useRef<HTMLDivElement>(null);
  const requestedFamilyId = useMemo(() => {
    const value = Number(searchParams.get('familyId'));
    return Number.isFinite(value) && value > 0 ? value : null;
  }, [searchParams]);
  const requestedTargetUserId = useMemo(() => {
    const value = Number(searchParams.get('targetUserId'));
    return Number.isFinite(value) && value > 0 ? value : null;
  }, [searchParams]);

  const selectedFamily = useMemo(
    () => families.find((family) => family.id === selectedFamilyId) || null,
    [families, selectedFamilyId],
  );
  const targetMember = useMemo(
    () => {
      const selectedMember = members.find((member) => member.userId === targetUserId);
      if (selectedMember) return selectedMember;
      if (mirrorContext?.targetMember?.userId === targetUserId) return mirrorContext.targetMember;
      return null;
    },
    [members, mirrorContext, targetUserId],
  );
  const targetDiaries = useMemo(
    () => diaries,
    [diaries],
  );
  const suggestedQuestions = mirrorContext?.suggestedQuestions || [];
  const missingRecordSuggestions = mirrorContext?.missingRecordSuggestions || [];
  const readiness = useMemo(() => readinessLevel(mirrorContext), [mirrorContext]);
  const relatedQuery = targetUserId
    ? `&relatedUserId=${targetUserId}&relatedMemberName=${encodeURIComponent(memberName(targetMember))}`
    : '';

  const loadMembers = useCallback(async (familyId: number) => {
    setLoadingContext(true);
    setError('');
    try {
      const memberList = await familyApi.getMembers(familyId);
      const nextMembers = Array.isArray(memberList) ? memberList : [];
      setMembers(nextMembers);
      setTargetUserId((current) => (
        requestedTargetUserId && nextMembers.some((member) => member.userId === requestedTargetUserId)
          ? requestedTargetUserId
          : current && nextMembers.some((member) => member.userId === current)
          ? current
          : nextMembers[0]?.userId ?? null
      ));
    } catch (err) {
      setError(err instanceof Error ? err.message : '镜像上下文加载失败');
    } finally {
      setLoadingContext(false);
    }
  }, [requestedTargetUserId]);

  useEffect(() => {
    const queryFamilyId = requestedFamilyId && families.some((family) => family.id === requestedFamilyId)
      ? requestedFamilyId
      : null;
    const nextFamilyId = queryFamilyId || (activeFamilyId && families.some((family) => family.id === activeFamilyId)
      ? activeFamilyId
      : families[0]?.id ?? null);
    setSelectedFamilyId(nextFamilyId);
    if (queryFamilyId && activeFamilyId !== queryFamilyId) {
      setActiveFamilyId(queryFamilyId);
    }
  }, [activeFamilyId, families, requestedFamilyId, setActiveFamilyId]);

  useEffect(() => {
    if (selectedFamilyId) {
      setMirrorContext(null);
      setDiaries([]);
      setMemories([]);
      setMembers([]);
      setTargetUserId(null);
      void loadMembers(selectedFamilyId);
    }
  }, [loadMembers, selectedFamilyId]);

  useEffect(() => {
    if (!selectedFamilyId || !targetUserId) {
      setMirrorContext(null);
      setDiaries([]);
      setMemories([]);
      return;
    }
    let active = true;
    setLoadingContext(true);
    setError('');
    setMirrorContext(null);
    setDiaries([]);
    setMemories([]);
    mirrorApi.getContext(selectedFamilyId, targetUserId)
      .then((context) => {
        if (!active) return;
        setMirrorContext(context);
        setDiaries(Array.isArray(context.diaries) ? context.diaries : []);
        setMemories(Array.isArray(context.memories) ? context.memories : []);
      })
      .catch((err) => {
        if (!active) return;
        setMirrorContext(null);
        setDiaries([]);
        setMemories([]);
        setError(err instanceof Error ? err.message : '镜像上下文加载失败');
      })
      .finally(() => {
        if (active) setLoadingContext(false);
      });
    return () => {
      active = false;
    };
  }, [selectedFamilyId, targetUserId]);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const refreshMirrorContext = useCallback(async (familyId: number, userId: number, query?: string) => {
    const context = await mirrorApi.getContext(familyId, userId, query);
    setMirrorContext(context);
    setDiaries(Array.isArray(context.diaries) ? context.diaries : []);
    setMemories(Array.isArray(context.memories) ? context.memories : []);
    return context;
  }, []);

  const executeSavePlan = useCallback(async (plan: AgentSaveToolPlan, originalContent: string) => {
    if (!selectedFamilyId || !targetUserId || !targetMember) {
      throw new Error('缺少家族或成员上下文');
    }

    const commonMetadata = {
      source: 'MIRROR_AGENT_TOOL',
      plannedTool: plan.tool,
      plannedToolReason: plan.reason,
      relatedUserId: targetUserId,
      relatedMemberName: memberName(targetMember),
      savedFromMirrorChatAt: new Date().toISOString(),
    };

    if (plan.tool === 'DIARY') {
      await diaryApi.create({
        familyId: selectedFamilyId,
        content: plan.content,
        entryType: entryTypeFromPlan(plan),
        title: plan.title,
        tags: plan.tags,
        visibility: visibilityFromPlan(plan),
        metadata: {
          ...commonMetadata,
          relationSource: 'MIRROR_AGENT_TOOL',
        },
      });
    } else if (plan.tool === 'FAMILY_MEMORY') {
      await memoryApi.createFamilyMemory({
        familyId: selectedFamilyId,
        content: plan.content,
        type: plan.memory_type as MemoryEntryType,
        scope: scopeFromPlan(plan),
        summary: plan.summary,
        importance: plan.importance,
        metadata: {
          ...commonMetadata,
          scenario: '镜像对话保存',
          target: memberName(targetMember),
        },
      });
    } else if (plan.tool === 'GROWTH_GUARD') {
      await growthGuardApi.createRecord({
        familyId: selectedFamilyId,
        targetUserId,
        category: plan.category as GrowthGuardCategory,
        content: plan.content,
        severity: plan.severity,
        observedAt: todayString(),
        visibility: scopeFromPlan(plan),
        metadata: {
          ...commonMetadata,
          followUpStatus: 'PENDING',
        },
      });
    }

    return refreshMirrorContext(selectedFamilyId, targetUserId, originalContent);
  }, [refreshMirrorContext, selectedFamilyId, targetMember, targetUserId]);

  const runSaveTool = useCallback(async (
    content: string,
    assistantMessageId: string,
  ) => {
    if (!selectedFamilyId || !targetUserId || !targetMember) return false;
    if (!shouldPlanSaveTool(content)) return false;

    const planResult = await memoryApi.planSaveTool({
      message: content,
      familyContext: selectedFamily?.description || selectedFamily?.name || '',
      targetMemberName: memberName(targetMember),
      viewerRole,
    });
    const plan = planResult.data;
    if (!plan.should_save || plan.tool === 'NONE') return false;

    if (requiresSaveConfirmation(plan)) {
      setMessages((prev) => prev.map((message) => (
        message.id === assistantMessageId
          ? {
              ...message,
              content: `我建议把这条内容保存为${toolLabel(plan.tool)}，保存前需要你确认。`,
              pendingTool: { plan, originalContent: content },
              toolResult: undefined,
            }
          : message
      )));
      return true;
    }

    const refreshedContext = await executeSavePlan(plan, content);
    setMessages((prev) => prev.map((message) => (
      message.id === assistantMessageId
        ? {
            ...message,
            content: plan.confirmation_message || `已保存为${toolLabel(plan.tool)}。`,
            toolResult: {
              label: toolLabel(plan.tool),
              detail: `${plan.title} · ${plan.visibility || plan.scope}`,
            },
            ...buildAnswerSourceMetadata(refreshedContext),
          }
        : message
    )));
    return true;
  }, [executeSavePlan, selectedFamily, selectedFamilyId, targetMember, targetUserId, viewerRole]);

  const confirmSaveTool = useCallback(async (
    messageId: string,
    plan: AgentSaveToolPlan,
    originalContent: string,
  ) => {
    setExecutingToolMessageId(messageId);
    try {
      const refreshedContext = await executeSavePlan(plan, originalContent);
      setMessages((prev) => prev.map((message) => (
        message.id === messageId
          ? {
              ...message,
              content: plan.confirmation_message || `已保存为${toolLabel(plan.tool)}。`,
              pendingTool: undefined,
              toolResult: {
                label: toolLabel(plan.tool),
                detail: savePlanDetail(plan),
              },
              ...buildAnswerSourceMetadata(refreshedContext),
            }
          : message
      )));
    } catch (err) {
      setMessages((prev) => prev.map((message) => (
        message.id === messageId
          ? {
              ...message,
              content: err instanceof Error
                ? `保存失败：${err.message}`
                : '保存失败，请稍后重试。',
            }
          : message
      )));
    } finally {
      setExecutingToolMessageId(null);
    }
  }, [executeSavePlan]);

  const cancelSaveTool = useCallback((messageId: string) => {
    setMessages((prev) => prev.map((message) => (
      message.id === messageId
        ? {
            ...message,
            content: '已取消保存，这条内容不会写入家族数据。',
            pendingTool: undefined,
            toolResult: undefined,
          }
        : message
    )));
  }, []);

  const appendVoiceTranscript = useCallback((text: string) => {
    setInput((current) => (current.trim() ? `${current.trim()} ${text}` : text));
  }, []);

  const sendMessage = async () => {
    const content = input.trim();
    if (!content || isStreaming || !targetMember || !mirrorContext || !selectedFamilyId || !targetUserId) return;
    setInput('');
    setError('');

    const userMessage: MirrorChatMessage = {
      id: `u-${Date.now()}`,
      role: 'user',
      content,
      timestamp: new Date().toISOString(),
    };
    const assistantMessage: MirrorChatMessage = {
      id: `a-${Date.now()}`,
      role: 'assistant',
      content: '',
      timestamp: new Date().toISOString(),
      ...buildAnswerSourceMetadata(mirrorContext),
    };
    const history = messages
      .filter((message) => message.role !== 'system')
      .map((message) => ({ role: message.role, content: message.content }));

    setMessages((prev) => [...prev, userMessage, assistantMessage]);
    setIsStreaming(true);

    let contextForAnswer = mirrorContext;
    try {
      const savedByTool = await runSaveTool(content, assistantMessage.id);
      if (savedByTool) {
        setIsStreaming(false);
        return;
      }
    } catch (err) {
      setMessages((prev) => prev.map((message) => (
        message.id === assistantMessage.id
          ? {
              ...message,
              content: err instanceof Error
                ? `我判断这条消息适合保存，但执行保存失败：${err.message}`
                : '我判断这条消息适合保存，但执行保存失败。',
            }
          : message
      )));
      setIsStreaming(false);
      return;
    }

    try {
      const recalledContext = await mirrorApi.getContext(selectedFamilyId, targetUserId, content);
      contextForAnswer = recalledContext;
      setMirrorContext(recalledContext);
      setDiaries(Array.isArray(recalledContext.diaries) ? recalledContext.diaries : []);
      setMemories(Array.isArray(recalledContext.memories) ? recalledContext.memories : []);
    } catch (err) {
      setError(err instanceof Error ? `相关记忆召回失败，已使用当前上下文：${err.message}` : '相关记忆召回失败，已使用当前上下文');
    }
    setMessages((prev) => prev.map((message) => (
      message.id === assistantMessage.id ? { ...message, ...buildAnswerSourceMetadata(contextForAnswer) } : message
    )));

    tutorApi.explainStream(
      {
        questionContent: '',
        answer: '',
        steps: '',
        studentMessage: `请以“${memberName(targetMember)}的镜像参考”模式回答：${content}`,
        history,
        subject: '家族记忆',
        grade: '',
        knowledgePoint: '镜像 Agent',
        masteryLevel: '中',
        mode: 'chat',
        memoryContext: contextForAnswer?.memoryContext || '',
        viewerRole,
        targetRole: 'STUDENT',
      },
      (chunk) => {
        setMessages((prev) => prev.map((message) => (
          message.id === assistantMessage.id ? { ...message, content: `${message.content}${chunk}` } : message
        )));
      },
      () => setIsStreaming(false),
      (streamError) => {
        setMessages((prev) => prev.map((message) => (
          message.id === assistantMessage.id ? { ...message, content: `${message.content}\n\n[错误] ${streamError}` } : message
        )));
        setIsStreaming(false);
      },
    );
  };

  if (loadingFamilies) {
    return (
      <div className="flex h-60 items-center justify-center text-gray-400">
        <RefreshCw className="mr-2 h-5 w-5 animate-spin" />
        加载中...
      </div>
    );
  }

  if (families.length === 0) {
    return (
      <div className="mx-auto max-w-3xl rounded-lg border border-gray-200 bg-white p-10 text-center">
        <Users className="mx-auto mb-3 h-10 w-10 text-gray-300" />
        <h1 className="text-lg font-semibold text-gray-900">还没有家族空间</h1>
        <p className="mt-2 text-sm text-gray-500">先创建或加入家族，再基于授权记录使用镜像参考。</p>
        <Link
          href="/dashboard/family"
          className="mt-5 inline-flex h-10 items-center rounded-lg bg-blue-600 px-4 text-sm font-medium text-white hover:bg-blue-700"
        >
          前往家族空间
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto flex h-[calc(100dvh-7.5rem)] max-w-7xl flex-col overflow-hidden sm:h-[calc(100dvh-9.5rem)] lg:h-[calc(100vh-8rem)]">
      <div className="mb-2 flex shrink-0 flex-col gap-2 sm:mb-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-lg font-bold text-gray-900 sm:text-xl">镜像 Agent</h1>
          <p className="mt-0.5 line-clamp-1 text-xs text-gray-500 sm:mt-1 sm:text-sm">基于授权日记和家族经验进行风格参考，不代表本人真实意图。</p>
        </div>
        <select
          value={selectedFamilyId ?? ''}
          onChange={(event) => {
            const familyId = Number(event.target.value);
            setSelectedFamilyId(familyId);
            setActiveFamilyId(familyId);
            setMessages([]);
          }}
          className="h-10 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
        >
          {families.map((family) => (
            <option key={family.id} value={family.id}>{family.name}</option>
          ))}
        </select>
      </div>

      {error && <div className="mb-2 shrink-0 rounded-lg bg-red-50 px-4 py-2 text-sm text-red-600 sm:mb-3">{error}</div>}

      <div className="mb-2 shrink-0 rounded-xl border border-gray-200 bg-white p-2 lg:hidden">
        <label className="mb-1.5 block text-xs font-medium text-gray-500" htmlFor="mirror-target-mobile">
          镜像对象
        </label>
        <select
          id="mirror-target-mobile"
          value={targetUserId ?? ''}
          onChange={(event) => {
            setTargetUserId(Number(event.target.value) || null);
            setMessages([]);
          }}
          className="h-9 w-full rounded-lg border border-gray-200 bg-white px-2.5 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
        >
          {members.map((member) => (
            <option key={member.userId} value={member.userId}>{memberName(member)}</option>
          ))}
        </select>
        {mirrorContext && (
          <div className="mt-2 flex items-center gap-1.5 text-[11px] text-gray-500">
            <span className={`shrink-0 rounded-full px-2 py-0.5 font-medium ${
              readiness.tone === 'green'
                ? 'bg-green-50 text-green-700'
                : readiness.tone === 'blue'
                  ? 'bg-blue-50 text-blue-700'
                  : 'bg-yellow-50 text-yellow-700'
            }`}
            >
              {readiness.label}
            </span>
            <span className="truncate">
              {diaries.length} 条日记 · {memories.length} 条经验 · 仅参考授权记录
            </span>
          </div>
        )}
      </div>

      <div className="flex min-h-0 flex-1 gap-3 overflow-hidden">
        <aside className="hidden w-80 shrink-0 flex-col overflow-hidden rounded-xl border border-gray-200 bg-white lg:flex">
          <div className="border-b border-gray-100 px-4 py-3">
            <div className="mb-2 flex items-center gap-2 text-sm font-semibold text-gray-900">
              <UserRound className="h-4 w-4 text-purple-600" />
              镜像对象
            </div>
            <select
              aria-label="镜像对象"
              value={targetUserId ?? ''}
              onChange={(event) => {
                setTargetUserId(Number(event.target.value) || null);
                setMessages([]);
              }}
              className="h-10 w-full rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
            >
              {members.map((member) => (
                <option key={member.userId} value={member.userId}>{memberName(member)}</option>
              ))}
            </select>
          </div>

          <div className="flex-1 overflow-y-auto p-4">
            {loadingContext ? (
              <div className="flex h-32 items-center justify-center text-gray-400">
                <Loader2 className="h-5 w-5 animate-spin" />
              </div>
            ) : (
              <div className="space-y-4">
                <div className="rounded-lg bg-purple-50 p-3">
                  <p className="text-xs font-medium text-purple-700">当前家族</p>
                  <p className="mt-1 text-sm font-semibold text-gray-900">{selectedFamily?.name}</p>
                </div>
                <div className="rounded-lg border border-gray-200 p-3">
                  <div className="mb-2 flex items-center justify-between gap-2">
                    <p className="text-xs font-medium text-gray-500">资料完整度</p>
                    <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${
                      readiness.tone === 'green'
                        ? 'bg-green-50 text-green-700'
                        : readiness.tone === 'blue'
                          ? 'bg-blue-50 text-blue-700'
                          : 'bg-yellow-50 text-yellow-700'
                    }`}
                    >
                      {readiness.label}
                    </span>
                  </div>
                  <div className="grid grid-cols-3 gap-2 text-center">
                    <div className="rounded-lg bg-gray-50 p-2">
                      <p className="text-sm font-semibold text-gray-900">{diaries.length}</p>
                      <p className="mt-0.5 text-[11px] text-gray-500">日记</p>
                    </div>
                    <div className="rounded-lg bg-gray-50 p-2">
                      <p className="text-sm font-semibold text-gray-900">{memories.length}</p>
                      <p className="mt-0.5 text-[11px] text-gray-500">经验</p>
                    </div>
                    <div className="rounded-lg bg-gray-50 p-2">
                      <p className="text-sm font-semibold text-gray-900">{hasMirrorProfile(mirrorContext) ? '有' : '无'}</p>
                      <p className="mt-0.5 text-[11px] text-gray-500">画像</p>
                    </div>
                  </div>
                  <p className="mt-2 text-xs leading-5 text-gray-500">
                    {sourceLead(mirrorContext)}
                    {mirrorContext?.insufficientRecords ? ' 资料偏少时，回答只能作为低置信参考。' : ''}
                  </p>
                </div>
                <div>
                  <p className="mb-2 flex items-center gap-1.5 text-xs font-medium text-gray-500">
                    <FileText className="h-3.5 w-3.5" />
                    授权日记来源
                  </p>
                  <div className="space-y-2">
                    {targetDiaries.slice(0, 5).map((entry, index) => (
                      <div key={entry.id} className="rounded-lg border border-gray-100 p-2">
                        <div className="mb-1 flex items-center gap-1.5">
                          <span className={`rounded px-1.5 py-0.5 text-[10px] font-semibold ${
                            isRelatedDiary(entry) ? 'bg-purple-50 text-purple-700' : 'bg-blue-50 text-blue-700'
                          }`}
                          >
                            {diarySourceCode(entry, index)}
                          </span>
                          <span className="truncate text-xs font-medium text-gray-800">{diaryTitle(entry)}</span>
                        </div>
                        <p className="mt-1 line-clamp-2 text-xs leading-5 text-gray-500">{entry.rawText}</p>
                        <div className="mt-1 flex flex-wrap items-center gap-1.5">
                          <span className="text-[11px] text-gray-400">{diarySourceLabel(entry)}</span>
                          <span className={`rounded px-1.5 py-0.5 text-[10px] font-medium ${temporalLayerClass(entry)}`}>
                            {temporalLayerLabel(entry)}
                          </span>
                        </div>
                        {temporalNote(entry) && (
                          <p className="mt-1 line-clamp-2 text-[11px] leading-4 text-gray-400">{temporalNote(entry)}</p>
                        )}
                      </div>
                    ))}
                    {targetDiaries.length === 0 && (
                      <p className="rounded-lg border border-dashed border-gray-200 p-3 text-center text-xs text-gray-400">
                        暂无该成员授权日记
                      </p>
                    )}
                  </div>
                </div>
                <div>
                  <p className="mb-2 flex items-center gap-1.5 text-xs font-medium text-gray-500">
                    <Layers className="h-3.5 w-3.5" />
                    家族经验来源
                  </p>
                  <div className="mb-2 space-y-2">
                    {memories.slice(0, 5).map((memory, index) => (
                      <div key={memory.id} className="rounded-lg border border-gray-100 p-2">
                        <p className="truncate text-xs font-medium text-gray-800">M{index + 1} · {memoryTitle(memory)}</p>
                        <p className="mt-1 line-clamp-2 text-xs leading-5 text-gray-500">{memory.content}</p>
                        <div className="mt-1 flex flex-wrap items-center gap-1.5">
                          <span className={`rounded px-1.5 py-0.5 text-[10px] font-medium ${temporalLayerClass(memory)}`}>
                            {temporalLayerLabel(memory)}
                          </span>
                          {temporalNote(memory) && (
                            <span className="line-clamp-1 text-[11px] text-gray-400">{temporalNote(memory)}</span>
                          )}
                        </div>
                      </div>
                    ))}
                    {memories.length === 0 && (
                      <p className="rounded-lg border border-dashed border-gray-200 p-3 text-center text-xs text-gray-400">
                        暂无可见家族经验
                      </p>
                    )}
                  </div>
                  <p className="rounded-lg bg-gray-50 p-3 text-xs text-gray-500">
                    {mirrorContext?.sourceSummary || `已加载 ${memories.length} 条后端权限过滤后的可见经验。`}
                    AI 只能基于这些记录谨慎推断。
                    {mirrorContext?.retrievalQuery && (
                      <span className="mt-1 block text-gray-400">
                        本轮已按“{mirrorContext.retrievalQuery}”召回相关记录。
                      </span>
                    )}
                    {mirrorContext?.retrievalMode && (
                      <span className="mt-1 block text-gray-400">
                        召回模式：{mirrorContext.retrievalMode === 'TEXT_FALLBACK' ? '文本相关性' : '向量索引待接入'}
                      </span>
                    )}
                  </p>
                </div>
                {missingRecordSuggestions.length > 0 && (
                  <div>
                    <p className="mb-2 text-xs font-medium text-gray-500">让镜像更准确</p>
                    <div className="space-y-2">
                      {missingRecordSuggestions.slice(0, 3).map((suggestion) => (
                        <p key={suggestion} className="rounded-lg border border-dashed border-gray-200 p-2 text-xs leading-5 text-gray-500">
                          {suggestion}
                        </p>
                      ))}
                    </div>
                    <div className="mt-3 grid gap-2">
                      <Link
                        href={`/dashboard/diary?template=choice${selectedFamilyId ? `&familyId=${selectedFamilyId}` : ''}${relatedQuery}`}
                        className="rounded-lg border border-blue-100 bg-blue-50 px-3 py-2 text-xs font-medium text-blue-700 hover:bg-blue-100"
                      >
                        补一条重要选择
                      </Link>
                      <Link
                        href={`/dashboard/diary?template=family-message${selectedFamilyId ? `&familyId=${selectedFamilyId}` : ''}${relatedQuery}`}
                        className="rounded-lg border border-purple-100 bg-purple-50 px-3 py-2 text-xs font-medium text-purple-700 hover:bg-purple-100"
                      >
                        补一条给家人的话
                      </Link>
                      <Link
                        href={`/dashboard/heritage?type=ELDER_ADVICE&scenario=${encodeURIComponent('长者经验')}${selectedFamilyId ? `&familyId=${selectedFamilyId}` : ''}`}
                        className="rounded-lg border border-amber-100 bg-amber-50 px-3 py-2 text-xs font-medium text-amber-700 hover:bg-amber-100"
                      >
                        补一条长者经验
                      </Link>
                      <Link
                        href={`/dashboard/growth?category=VISION${targetUserId ? `&targetUserId=${targetUserId}` : ''}${selectedFamilyId ? `&familyId=${selectedFamilyId}` : ''}`}
                        className="rounded-lg border border-green-100 bg-green-50 px-3 py-2 text-xs font-medium text-green-700 hover:bg-green-100"
                      >
                        补一条成长观察
                      </Link>
                    </div>
                  </div>
                )}
                {mirrorContext?.disclaimer && (
                  <div className="rounded-lg border border-yellow-100 bg-yellow-50 p-3 text-xs leading-5 text-yellow-700">
                    {mirrorContext.disclaimer}
                  </div>
                )}
              </div>
            )}
          </div>
        </aside>

        <section className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden rounded-xl border border-gray-200 bg-white">
          <div className="shrink-0 border-b border-gray-100 px-3 py-2 sm:px-4 sm:py-3">
            <div className="flex flex-wrap items-center gap-2">
              <Bot className="h-5 w-5 text-purple-600" />
              <span className="min-w-0 flex-1 truncate text-sm font-semibold text-gray-900 sm:flex-none">{memberName(targetMember)}的镜像参考</span>
              <span className="hidden rounded-full bg-yellow-50 px-2 py-0.5 text-[11px] font-medium text-yellow-700 sm:inline-flex">
                非本人 · 后端过滤授权记录
              </span>
              {mirrorContext?.insufficientRecords && (
                <span className="rounded-full bg-gray-100 px-2 py-0.5 text-[11px] font-medium text-gray-500">
                  记录偏少
                </span>
              )}
            </div>
            {mirrorContext && (
              <div className="mt-2 hidden rounded-lg border border-gray-100 bg-gray-50 p-2 sm:mt-3 sm:block sm:p-3">
                <div className="flex flex-wrap items-center gap-2">
                  <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${
                    readiness.tone === 'green'
                      ? 'bg-green-50 text-green-700'
                      : readiness.tone === 'blue'
                        ? 'bg-blue-50 text-blue-700'
                        : 'bg-yellow-50 text-yellow-700'
                  }`}
                  >
                    {readiness.label}
                  </span>
                  <span className="line-clamp-2 text-xs text-gray-500">{sourceLead(mirrorContext)}</span>
                </div>
                {(diaries.length > 0 || memories.length > 0) && (
                  <div className="mt-2 flex flex-wrap gap-1.5">
                    {diaries.slice(0, 2).map((entry, index) => (
                      <span key={`d-${entry.id}`} className="rounded bg-white px-2 py-1 text-[11px] text-gray-500">
                        {diarySourceCode(entry, index)} · {temporalLayerLabel(entry)} · {diaryTitle(entry)}
                      </span>
                    ))}
                    {memories.slice(0, 2).map((memory, index) => (
                      <span key={`m-${memory.id}`} className="rounded bg-white px-2 py-1 text-[11px] text-gray-500">
                        M{index + 1} · {temporalLayerLabel(memory)} · {memoryTitle(memory)}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>

          <div className="min-h-0 flex-1 space-y-3 overflow-y-auto overscroll-contain px-2.5 py-2.5 pb-4 sm:space-y-4 sm:p-4">
            {messages.length === 0 ? (
              <div className="flex h-full items-center justify-center text-center">
                <div className="w-full max-w-xl text-gray-400">
                  <Bot className="mx-auto mb-3 h-12 w-12 opacity-30" />
                  <p className="text-sm">可以问：“如果以他的经历看，我现在该怎么选择？”</p>
                  <p className="mt-1 text-xs">记录越多，镜像参考越稳；记录不足时 AI 会说明不确定。</p>
                  {suggestedQuestions.length > 0 && (
                    <div className="mt-5 grid gap-2 text-left sm:grid-cols-2">
                      {suggestedQuestions.slice(0, 4).map((question) => (
                        <button
                          key={question}
                          type="button"
                          onClick={() => setInput(question)}
                          className="rounded-lg border border-gray-200 bg-white px-3 py-2 text-left text-xs leading-5 text-gray-600 transition-colors hover:border-blue-200 hover:bg-blue-50 hover:text-blue-700"
                        >
                          {question}
                        </button>
                      ))}
                    </div>
                  )}
                  {mirrorContext?.insufficientRecords && (
                    <div className="mt-5 grid gap-2 text-left sm:grid-cols-2">
                      <Link
                        href={`/dashboard/diary?template=choice${selectedFamilyId ? `&familyId=${selectedFamilyId}` : ''}${relatedQuery}`}
                        className="rounded-lg border border-blue-100 bg-blue-50 px-3 py-2 text-xs font-medium text-blue-700 hover:bg-blue-100"
                      >
                        补一条重要选择
                      </Link>
                      <Link
                        href={`/dashboard/heritage?type=ELDER_ADVICE&scenario=${encodeURIComponent('长者经验')}${selectedFamilyId ? `&familyId=${selectedFamilyId}` : ''}`}
                        className="rounded-lg border border-amber-100 bg-amber-50 px-3 py-2 text-xs font-medium text-amber-700 hover:bg-amber-100"
                      >
                        补一条长者经验
                      </Link>
                    </div>
                  )}
                </div>
              </div>
            ) : (
              messages.map((message) => (
                <div key={message.id} className={`flex ${message.role === 'user' ? 'justify-end' : 'justify-start'}`}>
                  <div className={`max-w-[94%] whitespace-pre-wrap break-words rounded-2xl px-3 py-2.5 text-sm leading-6 sm:max-w-[86%] sm:px-4 ${
                    message.role === 'user'
                      ? 'rounded-br-md bg-blue-600 text-white'
                      : 'rounded-bl-md bg-gray-100 text-gray-900'
                  } ${message.role === 'assistant' && !message.content ? 'animate-pulse' : ''}`}
                  >
                    {message.content || (message.role === 'assistant' ? '思考中...' : '')}
                    {message.role === 'assistant' && message.toolResult && (
                      <div className="mt-3 flex flex-wrap items-center gap-2 rounded-lg border border-green-100 bg-white/70 px-3 py-2 text-xs text-green-700">
                        <CheckCircle className="h-3.5 w-3.5" />
                        <span className="font-medium">已调用工具：{message.toolResult.label}</span>
                        <span className="text-green-600">{message.toolResult.detail}</span>
                      </div>
                    )}
                    {message.role === 'assistant' && message.pendingTool && (
                      <div className="mt-3 rounded-lg border border-yellow-100 bg-white/80 p-3 text-xs text-gray-700">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="rounded bg-yellow-50 px-2 py-0.5 font-medium text-yellow-700">
                            待确认工具
                          </span>
                          <span>{savePlanDetail(message.pendingTool.plan)}</span>
                        </div>
                        {message.pendingTool.plan.reason && (
                          <p className="mt-2 leading-5 text-gray-500">
                            判断依据：{message.pendingTool.plan.reason}
                          </p>
                        )}
                        <p className="mt-2 line-clamp-3 rounded bg-gray-50 px-2 py-1.5 leading-5 text-gray-500">
                          {message.pendingTool.plan.content}
                        </p>
                        <div className="mt-3 flex flex-wrap gap-2">
                          <button
                            type="button"
                            onClick={() => confirmSaveTool(
                              message.id,
                              message.pendingTool!.plan,
                              message.pendingTool!.originalContent,
                            )}
                            disabled={executingToolMessageId === message.id}
                            className="inline-flex h-8 items-center gap-1.5 rounded-lg bg-green-600 px-3 text-xs font-medium text-white hover:bg-green-700 disabled:opacity-60"
                          >
                            {executingToolMessageId === message.id ? (
                              <Loader2 className="h-3.5 w-3.5 animate-spin" />
                            ) : (
                              <CheckCircle className="h-3.5 w-3.5" />
                            )}
                            确认保存
                          </button>
                          <button
                            type="button"
                            onClick={() => cancelSaveTool(message.id)}
                            disabled={executingToolMessageId === message.id}
                            className="inline-flex h-8 items-center gap-1.5 rounded-lg border border-gray-200 bg-white px-3 text-xs font-medium text-gray-600 hover:bg-gray-50 disabled:opacity-60"
                          >
                            <XCircle className="h-3.5 w-3.5" />
                            取消
                          </button>
                        </div>
                      </div>
                    )}
                    {message.role === 'assistant' && !message.toolResult && !message.pendingTool && (message.sourceRefs?.length || message.insufficientSources) && (
                      <div className="mt-3 border-t border-gray-200 pt-2">
                        {message.sourceRefs && message.sourceRefs.length > 0 && (
                          <div className="flex flex-wrap gap-1.5">
                            {message.sourceRefs.map((source) => (
                              <span
                                key={`${message.id}-${source.code}-${source.title}`}
                                className="rounded bg-white px-2 py-1 text-[11px] leading-4 text-gray-500"
                                title={`${source.sourceLabel} · ${source.title}`}
                              >
                                <span className="font-semibold text-gray-700">{source.code}</span>
                                <span> · </span>
                                <span className={source.toneClass}>{source.temporalLabel}</span>
                                <span> · {source.title}</span>
                              </span>
                            ))}
                          </div>
                        )}
                        {message.retrievalQuery && (
                          <p className="mt-1 text-[11px] leading-4 text-gray-400">
                            本轮按“{message.retrievalQuery}”召回相关记忆。
                          </p>
                        )}
                        {message.insufficientSources && (
                          <p className="mt-1 text-[11px] leading-4 text-yellow-700">
                            本轮资料不足，请谨慎参考。
                          </p>
                        )}
                      </div>
                    )}
                  </div>
                </div>
              ))
            )}
            <div ref={endRef} />
          </div>

          <form
            onSubmit={(event) => {
              event.preventDefault();
              sendMessage();
            }}
            className="flex shrink-0 items-end gap-1.5 border-t border-gray-200 bg-white p-1.5 pb-[max(env(safe-area-inset-bottom),0.375rem)] sm:gap-2 sm:p-3 sm:pb-3"
          >
            <div className="min-w-0 flex-1">
              <div className="hidden sm:mb-2 sm:flex sm:justify-end">
                <VoiceInputButton
                  onTranscript={appendVoiceTranscript}
                  disabled={isStreaming || loadingContext || !targetMember || !mirrorContext}
                />
              </div>
              <textarea
                rows={1}
                value={input}
                onChange={(event) => setInput(event.target.value)}
                placeholder="输入你想让镜像参考回应的问题..."
                disabled={isStreaming || loadingContext || !targetMember || !mirrorContext}
                className="max-h-28 min-h-9 w-full resize-none rounded-lg border border-gray-200 px-2.5 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 sm:min-h-10 sm:max-h-32 sm:px-3"
              />
            </div>
            <VoiceInputButton
              onTranscript={appendVoiceTranscript}
              disabled={isStreaming || loadingContext || !targetMember || !mirrorContext}
              compact
              className="shrink-0 sm:hidden"
            />
            <button
              type="submit"
              disabled={!input.trim() || isStreaming || loadingContext || !targetMember || !mirrorContext}
              className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50 sm:h-10 sm:w-10"
              aria-label="发送"
            >
              {isStreaming ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
            </button>
          </form>
        </section>
      </div>
    </div>
  );
}
