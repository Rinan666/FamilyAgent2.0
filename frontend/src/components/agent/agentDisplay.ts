import type {
  AgentMemorySavePlan,
  AgentSessionMetadata,
  ChatSessionSummary,
  DiaryEntry,
  FamilyMember,
  GrowthGuardRecord,
  MemoryEntry,
  MirrorContextResponse,
} from '@/types';

export type SaveFeedback = {
  status: 'saving' | 'draft' | 'confirming' | 'saved' | 'skipped' | 'error';
  detail: string;
  href?: string;
  skillRunId?: number;
  draft?: AgentMemorySavePlan;
};

export type ModeReadinessTone = 'gray' | 'green' | 'blue' | 'yellow';

export type ModeReadiness = {
  label: string;
  tone: ModeReadinessTone;
};

export function formatSessionTime(value?: string) {
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

export function getSessionTitle(session: Pick<ChatSessionSummary, 'title' | 'summary'>) {
  return (session.title || session.summary || '未命名会话').slice(0, 32);
}

export function parsePositiveNumber(value: unknown) {
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? number : null;
}

export function memberName(member?: FamilyMember | null) {
  if (!member) return '家庭成员';
  return member.relationshipLabel?.trim()
    || member.nickname?.trim()
    || member.username?.trim()
    || `用户 ${member.userId}`;
}

export function normalizeAgentSessionMetadata(metadata?: Record<string, unknown> | null): AgentSessionMetadata {
  const targetUserId = parsePositiveNumber(metadata?.targetUserId);
  const targetPersonaId = parsePositiveNumber(metadata?.targetPersonaId);
  const targetMemberName = typeof metadata?.targetMemberName === 'string' && metadata.targetMemberName.trim()
    ? metadata.targetMemberName.trim()
    : null;
  const targetPersonaName = typeof metadata?.targetPersonaName === 'string' && metadata.targetPersonaName.trim()
    ? metadata.targetPersonaName.trim()
    : null;
  const agentMode = metadata?.agentMode === 'mirror' || metadata?.agentMode === 'persona'
    ? metadata.agentMode
    : 'family';
  return {
    ...(metadata || {}),
    entry: typeof metadata?.entry === 'string' ? metadata.entry : 'agent',
    contextLabel: typeof metadata?.contextLabel === 'string' ? metadata.contextLabel : undefined,
    agentMode,
    targetUserId,
    targetPersonaId,
    targetMemberName,
    targetPersonaName,
    hasTargetSwitches: Boolean(metadata?.hasTargetSwitches),
  };
}

export function sessionBadge(metadata?: Record<string, unknown> | null) {
  const normalized = normalizeAgentSessionMetadata(metadata);
  if (normalized.hasTargetSwitches) {
    return {
      label: '切换过对象',
      className: 'bg-amber-100 text-amber-800',
    };
  }
  if (normalized.agentMode === 'mirror') {
    return {
      label: '镜像参考',
      className: 'bg-sky-100 text-sky-800',
    };
  }
  if (normalized.agentMode === 'persona') {
    return {
      label: '精神成员',
      className: 'bg-violet-100 text-violet-800',
    };
  }
  return {
    label: '家庭对话',
    className: 'bg-stone-200 text-stone-700',
  };
}

function hasMirrorProfile(context?: MirrorContextResponse | null) {
  return Boolean(context?.mirrorProfile && Object.keys(context.mirrorProfile).length > 0);
}

export function readinessLevel(context?: MirrorContextResponse | null): ModeReadiness {
  if (!context) return { label: '等待资料', tone: 'gray' };
  const diaryCount = context.diaries?.length || 0;
  const memoryCount = context.memories?.length || 0;
  const growthCount = context.growthRecords?.length || 0;
  const libraryCount = context.libraryItems?.length || 0;
  const profileBonus = hasMirrorProfile(context) ? 1 : 0;
  const score = Math.min(5, diaryCount + memoryCount + growthCount + libraryCount + profileBonus);
  if (score >= 5) return { label: '资料较充足', tone: 'green' };
  if (score >= 3) return { label: '可谨慎参考', tone: 'blue' };
  return { label: '资料偏少', tone: 'yellow' };
}

export function temporalLayerLabel(item: DiaryEntry | MemoryEntry | GrowthGuardRecord) {
  const label = item.metadata?.temporalLayerLabel;
  return typeof label === 'string' && label.trim() ? label.trim() : '未分层';
}

export function temporalLayerClass(item: DiaryEntry | MemoryEntry | GrowthGuardRecord) {
  switch (item.metadata?.temporalLayer) {
    case 'FRESH':
      return 'bg-sky-50 text-sky-700';
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

export function diaryTitle(entry: DiaryEntry) {
  return entry.structured?.title || entry.structured?.summary || entry.rawText.slice(0, 28) || '未命名记录';
}

export function isRelatedDiary(entry: DiaryEntry) {
  return entry.metadata?.mirrorSourceType === 'RELATED_BY_FAMILY' || Boolean(entry.metadata?.relatedUserId);
}

export function diarySourceCode(entry: DiaryEntry, index: number) {
  return `${isRelatedDiary(entry) ? 'R' : 'D'}${index + 1}`;
}

export function diarySourceLabel(entry: DiaryEntry) {
  return isRelatedDiary(entry) ? '家人补充' : '本人记录';
}

export function memoryTitle(memory: MemoryEntry) {
  if (memory.summary?.trim()) return memory.summary.trim();
  if (typeof memory.metadata?.scenario === 'string' && memory.metadata.scenario.trim()) {
    return `${memory.metadata.scenario.trim()}相关经验`;
  }
  return memory.content.slice(0, 28) || '未命名经验';
}

export function memorySourceLabel(memory: MemoryEntry) {
  return memory.metadata?.coreMemory === true ? '核心记忆' : '经验沉淀';
}
