import type {
  AgentMemorySavePlan,
  AgentMode,
  AgentSaveMemoryMetadata,
  AgentSaveMemoryRequest,
  ChatMessage,
  MemoryContentType,
  MemoryLibraryKind,
  PersonalMemoryVisibility,
  SaveMemoryVisibility,
  WriteMemoryMetadata,
  WriteMemoryRequest,
} from '../types';

const MEMORY_LIBRARIES = new Set<MemoryLibraryKind>(['PERSONAL', 'FAMILY']);
const MEMORY_TYPES = new Set<MemoryContentType>([
  'NOTE',
  'KNOWLEDGE',
  'INSIGHT',
  'EXPERIENCE',
  'OBSERVATION',
  'PREFERENCE',
  'PLAN',
]);
const PERSONAL_VISIBILITIES = new Set<PersonalMemoryVisibility>([
  'PRIVATE',
  'ALL_FAMILIES_VISIBLE',
  'SELECTED_FAMILIES_VISIBLE',
  'CARE_VISIBLE',
]);
const FAMILY_VISIBILITIES = new Set<SaveMemoryVisibility>([
  'PRIVATE',
  'FAMILY_VISIBLE',
  'CARE_VISIBLE',
]);

const EXPLICIT_SAVE_COMMAND_PATTERN = /^(?:请|麻烦)?(?:你)?(?:帮我|替我|给我)?(?:把)?(?:刚才|上面|上文|前面|这段|这条|这个|这些|那段|那条|那件事)?(?:的)?(?:内容|对话|记录|记忆|经历|事情|话)?(?:保存|保存成记忆|存起来|记一下|记下来|记录下来|留个记录|留作记录|沉淀下来|加入记忆库|放到记忆库|收进记忆库)(?:一下|起来|为记忆|到记忆库)?(?:吧)?[。.!！?？]*$/;
const INLINE_SAVE_PREFIX_PATTERN = /^(?:请|麻烦)?(?:你)?(?:帮我|替我|给我)?(?:把)?(?:以下|下面|这条|这段|这份|这句话)?(?:内容|记录|记忆|经历|事情|材料)?(?:保存(?:到(?:个人|家庭|家族)?记忆库)?|加入(?:个人|家庭|家族)?记忆库|放到(?:个人|家庭|家族)?记忆库|收进(?:个人|家庭|家族)?记忆库|记下来|记录下来|存起来)(?:里)?(?:一下)?(?:吧)?[：:\s]+/;
const INLINE_SAVE_ACTION_FIRST_PREFIX_PATTERN = /^(?:请|麻烦)?(?:你)?(?:帮我|替我|给我)?(?:保存|记下|记录)(?:以下|下面|这条|这段|这份|这句话|这些内容)?(?:一下)?(?:吧)?[：:\s]+/;
const INLINE_SAVE_SUFFIX_PATTERN = /[，,。；;\n]\s*(?:请|麻烦)?(?:你)?(?:帮我|替我|给我)?(?:把它|将它)?(?:保存(?:到(?:个人|家庭|家族)?记忆库)?|加入(?:个人|家庭|家族)?记忆库|放到(?:个人|家庭|家族)?记忆库|收进(?:个人|家庭|家族)?记忆库|记下来|记录下来|存起来)(?:里)?(?:一下)?(?:吧)?[。.!！?？]*$/;
const MEMORY_TYPE_WORDS = '笔记|新知|知识|感悟|洞见|教训|经历|经验|故事|观察|偏好|计划|提醒';
const SAVE_DESTINATION_WORDS = `(?:个人|家庭|家族)(?:记忆库|记忆)?(?:${MEMORY_TYPE_WORDS})?|${MEMORY_TYPE_WORDS}`;
const INLINE_TYPED_SAVE_PREFIX_PATTERN = new RegExp(
  `^(?:请|麻烦)?(?:你)?(?:帮我|替我|给我)?(?:把)?(?:以下|下面|这条|这段|这份|这句话|这些内容)?(?:内容|记录|记忆|经历|事情|材料)?(?:存为|保存为|记录为|归档为)(?:一条)?(?:${SAVE_DESTINATION_WORDS})(?:里)?(?:一下)?(?:吧)?[：:\\s]+`,
);
const INLINE_TYPED_SAVE_SUFFIX_PATTERN = new RegExp(
  `[，,。；;\\n]\\s*(?:请|麻烦)?(?:你)?(?:帮我|替我|给我)?(?:把它|将它)?(?:存为|保存为|记录为|归档为)(?:一条)?(?:${SAVE_DESTINATION_WORDS})(?:里)?(?:一下)?(?:吧)?[。.!！?？]*$`,
);
const TYPED_SAVE_COMMAND_PATTERN = new RegExp(
  `^(?:请|麻烦)?(?:你)?(?:帮我|替我|给我)?(?:把)?(?:刚才|上面|上文|前面|这条|这段|这份|这句话|这些内容)?(?:的)?(?:内容|记录|记忆|经历|事情|材料)?(?:存为|保存为|记录为|归档为)(?:一条)?(?:${SAVE_DESTINATION_WORDS})(?:里)?(?:一下)?(?:吧)?[。.!！?？]*$`,
);
const MAX_EXPLICIT_SAVE_COMMAND_LENGTH = 5000;

export type RequestedMemorySave = {
  memoryLibrary?: MemoryLibraryKind;
  memoryType?: MemoryContentType;
};

export type SavePlanPersistenceDecision = {
  plan: AgentMemorySavePlan;
  shouldPersist: boolean;
  skippedDetail: string;
};

export type AgentSubmissionRoute = {
  content: string;
  kind: 'chat' | 'explicit_save';
  conversationContext: ChatMessage[];
  requestedSave?: RequestedMemorySave;
  saveContent?: string;
};

export type AgentSaveMemoryRequestContext = {
  requestId?: string;
  sessionId?: number | null;
  agentMode: AgentMode;
  familyName?: string;
  viewerRole?: string;
  savedFromMessageRole?: string;
  targetUserId?: number | null;
  targetMemberName?: string | null;
  targetPersonaId?: number | null;
  targetPersonaName?: string | null;
  contextLabel?: string;
  savedAt?: string;
};

function choice<T extends string>(value: unknown, allowed: Set<T>, fallback: T): T {
  const normalized = String(value || '').trim().toUpperCase();
  return allowed.has(normalized as T) ? normalized as T : fallback;
}

function boundedInt(value: unknown, fallback: number) {
  const number = Number(value);
  if (!Number.isFinite(number)) return fallback;
  return Math.max(1, Math.min(5, Math.round(number)));
}

export function isExplicitSaveMemoryCommand(value: string) {
  const trimmed = value.trim();
  if (!trimmed || trimmed.length > MAX_EXPLICIT_SAVE_COMMAND_LENGTH) return false;

  const normalized = trimmed.replace(/\s+/g, '');
  if (TYPED_SAVE_COMMAND_PATTERN.test(trimmed)) return true;
  if (INLINE_TYPED_SAVE_PREFIX_PATTERN.test(trimmed)) return true;
  if (INLINE_TYPED_SAVE_SUFFIX_PATTERN.test(trimmed)) return true;
  if (normalized.length <= 40 && EXPLICIT_SAVE_COMMAND_PATTERN.test(normalized)) return true;

  const prefixPayload = trimmed
    .replace(INLINE_SAVE_PREFIX_PATTERN, '')
    .replace(INLINE_SAVE_ACTION_FIRST_PREFIX_PATTERN, '')
    .trim();
  if (prefixPayload !== trimmed && prefixPayload.replace(/\s+/g, '').length >= 8) return true;

  const suffixPayload = trimmed.replace(INLINE_SAVE_SUFFIX_PATTERN, '').trim();
  return suffixPayload !== trimmed && suffixPayload.replace(/\s+/g, '').length >= 8;
}

export function routeAgentSubmission(
  value: string,
  messages: readonly ChatMessage[],
): AgentSubmissionRoute {
  const content = value.trim();
  const isExplicitSave = isExplicitSaveMemoryCommand(content);
  const saveContent = isExplicitSave ? stripInlineSaveCommand(content) : '';
  return {
    content,
    kind: isExplicitSave ? 'explicit_save' : 'chat',
    conversationContext: isExplicitSave
      ? messages.filter((message) => message.role !== 'system').slice(-10)
      : [],
    requestedSave: isExplicitSave ? requestedMemorySave(content) : undefined,
    saveContent: saveContent || undefined,
  };
}

export function requestedMemorySave(value: string): RequestedMemorySave | undefined {
  const normalized = value.replace(/\s+/g, '');
  const memoryLibrary = normalized.includes('个人')
    ? 'PERSONAL'
    : normalized.includes('家庭') || normalized.includes('家族')
      ? 'FAMILY'
      : undefined;
  const memoryType = requestedMemoryType(normalized);
  return memoryLibrary || memoryType ? { memoryLibrary, memoryType } : undefined;
}

function requestedMemoryType(value: string): MemoryContentType | undefined {
  if (value.includes('观察')) return 'OBSERVATION';
  if (value.includes('偏好')) return 'PREFERENCE';
  if (value.includes('计划') || value.includes('提醒')) return 'PLAN';
  if (value.includes('新知') || value.includes('知识')) return 'KNOWLEDGE';
  if (value.includes('感悟') || value.includes('洞见') || value.includes('教训')) return 'INSIGHT';
  if (value.includes('经历') || value.includes('经验') || value.includes('故事')) return 'EXPERIENCE';
  if (value.includes('笔记')) return 'NOTE';
  return undefined;
}

export function applyRequestedMemorySave(
  plan: AgentMemorySavePlan,
  requested?: RequestedMemorySave,
  explicitContent?: string,
) {
  const normalized = normalizeMemorySavePlan(plan);
  if (!requested) return normalized;
  const memoryLibrary = requested.memoryLibrary || normalized.memory_library;
  const visibility = requested.memoryLibrary
    ? defaultVisibility(memoryLibrary)
    : normalized.visibility;
  const content = explicitContent?.trim() || normalized.content;
  return normalizeMemorySavePlan({
    ...normalized,
    should_save: true,
    memory_library: memoryLibrary,
    memory_type: requested.memoryType || normalized.memory_type,
    content,
    summary: explicitContent?.trim().slice(0, 80) || normalized.summary,
    visibility,
    selected_family_ids: memoryLibrary === 'PERSONAL' ? normalized.selected_family_ids : [],
  });
}

export function buildFallbackSavePlan(
  message: string,
  conversationContext: readonly ChatMessage[],
): AgentMemorySavePlan | null {
  const directContent = stripInlineSaveCommand(message);
  const content = directContent || [...conversationContext]
    .reverse()
    .find((item) => item.role !== 'system' && item.content.trim())
    ?.content.trim() || '';
  if (!content) return null;

  return normalizeMemorySavePlan({
    should_save: true,
    memory_library: 'PERSONAL',
    memory_type: 'NOTE',
    content: content.slice(0, 1200),
    title: '待确认的保存草稿',
    summary: content.slice(0, 80),
    visibility: 'PRIVATE',
    selected_family_ids: [],
    importance: 1,
    tags: [],
    reason: 'AI 整理暂时不可用，已保留用户选择的原文草稿。',
    confirmation_message: '原文草稿已准备，请修改或确认后保存。',
  });
}

export function buildRelevantSaveContext(message: ChatMessage, messages: readonly ChatMessage[]) {
  const visibleMessages = messages.filter((item) => item.role !== 'system');
  const messageIndex = visibleMessages.findIndex((item) => item.id === message.id);
  if (messageIndex < 0) return visibleMessages.slice(-4);
  const contextStart = message.role === 'assistant'
    ? Math.max(0, messageIndex - 1)
    : Math.max(0, messageIndex - 2);
  return visibleMessages.slice(contextStart, messageIndex + 1);
}

export function normalizeMemorySavePlan(plan: AgentMemorySavePlan): AgentMemorySavePlan {
  const memoryLibrary = choice(plan.memory_library, MEMORY_LIBRARIES, 'PERSONAL');
  const memoryType = choice(plan.memory_type, MEMORY_TYPES, 'NOTE');
  const content = String(plan.content || '').trim();
  const shouldSave = Boolean(plan.should_save) && content.length > 0;
  return {
    ...plan,
    should_save: shouldSave,
    memory_library: memoryLibrary,
    memory_type: memoryType,
    content,
    title: String(plan.title || '').trim().slice(0, 24) || defaultSaveTitle(memoryLibrary),
    summary: String(plan.summary || content).trim().slice(0, 80),
    visibility: normalizeVisibility(memoryLibrary, plan.visibility),
    selected_family_ids: normalizeFamilyIds(plan.selected_family_ids),
    importance: boundedInt(plan.importance, 3),
    tags: Array.isArray(plan.tags)
      ? plan.tags.map((tag) => String(tag).trim()).filter(Boolean).slice(0, 6)
      : [],
    reason: String(plan.reason || '').trim().slice(0, 120),
    confirmation_message: shouldSave
      ? defaultSaveConfirmation(memoryLibrary)
      : '没有找到可保存的内容。',
  };
}

export function savePlanPersistenceDecision(plan: AgentMemorySavePlan): SavePlanPersistenceDecision {
  const normalized = normalizeMemorySavePlan(plan);
  const shouldPersist = normalized.should_save && normalized.content.length > 0;
  return {
    plan: normalized,
    shouldPersist,
    skippedDetail: shouldPersist ? '' : skippedSavePlanDetail(normalized),
  };
}

export function skippedSavePlanDetail(plan: AgentMemorySavePlan) {
  const reason = String(plan.reason || '').trim();
  if (reason.includes('保存规划暂时不可用')) return '暂未保存，可稍后重试。';
  return reason ? `未生成保存草稿：${reason}` : '未生成保存草稿。';
}

export function normalizeVisibility(
  memoryLibrary: MemoryLibraryKind,
  value: unknown,
): SaveMemoryVisibility {
  if (memoryLibrary === 'PERSONAL') {
    return choice(value, PERSONAL_VISIBILITIES, 'PRIVATE');
  }
  return choice(value, FAMILY_VISIBILITIES, 'FAMILY_VISIBLE');
}

export function memoryLibraryLabel(memoryLibrary: MemoryLibraryKind) {
  return memoryLibrary === 'PERSONAL' ? '个人记忆库' : '家庭记忆库';
}

export function memoryTypeLabel(memoryType: MemoryContentType) {
  return {
    NOTE: '笔记',
    KNOWLEDGE: '新知',
    INSIGHT: '感悟',
    EXPERIENCE: '经历',
    OBSERVATION: '观察',
    PREFERENCE: '偏好',
    PLAN: '计划',
  }[memoryType];
}

export function savePlanDetail(plan: AgentMemorySavePlan, memoryId?: number) {
  const idPart = memoryId ? ` · #${memoryId}` : '';
  return `${memoryLibraryLabel(plan.memory_library)} · ${memoryTypeLabel(plan.memory_type)} · ${plan.title} · ${visibilityLabel(plan.visibility)}${idPart}`;
}

export function truncateAuditText(value: string, maxLength = 500) {
  const text = value.trim().replace(/\s+/g, ' ');
  return text.length <= maxLength ? text : `${text.slice(0, maxLength)}...`;
}

export function saveMemorySkillMetadata(plan: AgentMemorySavePlan, savedAt?: string) {
  return {
    skillName: 'save_memory',
    memoryLibrary: plan.memory_library,
    memoryType: plan.memory_type,
    plannedTitle: plan.title,
    plannedReason: plan.reason,
    visibility: plan.visibility || '',
    confirmationPolicy: 'USER_APPROVED_EDITABLE_DRAFT',
    savedAt: savedAt || '',
  };
}

export function buildAgentSaveMemoryMetadata(
  plan: AgentMemorySavePlan,
  context: AgentSaveMemoryRequestContext,
): AgentSaveMemoryMetadata {
  const savedAt = context.savedAt || new Date().toISOString();
  const common = {
    familyName: context.familyName || '',
    viewerRole: context.viewerRole || '',
    savedFromMessageRole: context.savedFromMessageRole || '',
    ...saveMemorySkillMetadata(plan, savedAt),
  };
  if (context.agentMode === 'mirror') {
    return {
      source: 'MIRROR_AGENT_TOOL',
      relationSource: 'MIRROR_AGENT_TOOL',
      relatedUserId: context.targetUserId ?? null,
      relatedMemberName: context.targetMemberName || '',
      scenario: '镜像对话保存',
      target: context.targetMemberName || '',
      ...common,
    };
  }
  if (context.agentMode === 'persona') {
    return {
      source: 'PERSONA_MEMBER_TOOL',
      relationSource: 'PERSONA_MEMBER_TOOL',
      relatedPersonaId: context.targetPersonaId ?? null,
      relatedPersonaName: context.targetPersonaName || '',
      scenario: '精神成员对话保存',
      target: context.targetPersonaName || '',
      ...common,
    };
  }
  return {
    source: 'FAMILY_COMPANION_TOOL',
    relationSource: 'FAMILY_AGENT_TOOL',
    ...common,
  };
}

export function buildAgentSaveMemoryRequest(
  familyId: number,
  plan: AgentMemorySavePlan,
  context: AgentSaveMemoryRequestContext,
): AgentSaveMemoryRequest {
  const metadata = buildAgentSaveMemoryMetadata(plan, context);
  return {
    ...buildWriteMemorySaveRequest(familyId, plan, metadata, context.targetUserId || undefined),
    requestId: context.requestId,
    sessionId: context.sessionId,
    agentMode: context.agentMode,
    subject: subjectFromAgentMode(context.agentMode),
    contextLabel: context.contextLabel || 'save_memory',
    metadata,
  };
}

export function buildWriteMemorySaveRequest(
  familyId: number,
  plan: AgentMemorySavePlan,
  metadata: WriteMemoryMetadata,
  targetUserId?: number,
): WriteMemoryRequest {
  return {
    familyId,
    memoryLibrary: plan.memory_library,
    memoryType: plan.memory_type,
    content: plan.content,
    title: plan.title,
    tags: plan.tags,
    visibility: normalizeVisibility(plan.memory_library, plan.visibility),
    relatedUserId: targetUserId,
    selectedFamilyIds: plan.memory_library === 'PERSONAL'
      ? normalizeFamilyIds(plan.selected_family_ids)
      : [],
    metadata,
  };
}

export function todayString() {
  return new Date().toISOString().slice(0, 10);
}

function subjectFromAgentMode(mode: AgentMode) {
  if (mode === 'mirror') return 'MirrorAgent';
  if (mode === 'persona') return 'PersonaMemberAgent';
  return 'FamilyAgent';
}

function defaultVisibility(memoryLibrary: MemoryLibraryKind): SaveMemoryVisibility {
  return memoryLibrary === 'PERSONAL' ? 'PRIVATE' : 'FAMILY_VISIBLE';
}

function defaultSaveTitle(memoryLibrary: MemoryLibraryKind) {
  return memoryLibrary === 'PERSONAL' ? '聊天保存个人记忆' : '聊天保存家庭记忆';
}

function defaultSaveConfirmation(memoryLibrary: MemoryLibraryKind) {
  return `${memoryLibraryLabel(memoryLibrary)}草稿已准备，请修改或确认后保存。`;
}

function stripInlineSaveCommand(value: string) {
  const trimmed = value.trim();
  const prefixPayload = trimmed
    .replace(INLINE_TYPED_SAVE_PREFIX_PATTERN, '')
    .replace(INLINE_SAVE_PREFIX_PATTERN, '')
    .replace(INLINE_SAVE_ACTION_FIRST_PREFIX_PATTERN, '')
    .trim();
  if (prefixPayload !== trimmed) return prefixPayload;
  const suffixPayload = trimmed
    .replace(INLINE_TYPED_SAVE_SUFFIX_PATTERN, '')
    .replace(INLINE_SAVE_SUFFIX_PATTERN, '')
    .trim();
  if (suffixPayload !== trimmed) return suffixPayload;
  const normalized = trimmed.replace(/\s+/g, '');
  return EXPLICIT_SAVE_COMMAND_PATTERN.test(normalized) || TYPED_SAVE_COMMAND_PATTERN.test(trimmed)
    ? ''
    : trimmed;
}

function visibilityLabel(value?: string) {
  const normalized = String(value || '').trim().toUpperCase();
  if (normalized === 'PRIVATE') return '仅自己可见';
  if (normalized === 'FAMILY_VISIBLE') return '家庭可见';
  if (normalized === 'ALL_FAMILIES_VISIBLE') return '当前全部家族可见';
  if (normalized === 'SELECTED_FAMILIES_VISIBLE') return '选择家族可见';
  if (normalized === 'CARE_VISIBLE') return '照护可见';
  return value || '未设置';
}

function normalizeFamilyIds(value?: number[]) {
  if (!Array.isArray(value)) return [];
  return [...new Set(value.map(Number).filter((id) => Number.isInteger(id) && id > 0))];
}
