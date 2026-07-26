import type {
  AgentSaveTool,
  AgentSaveMemoryMetadata,
  AgentSaveMemoryToolRequest,
  AgentSaveToolPlan,
  AgentMode,
  ChatMessage,
  DiaryEntryType,
  DiaryVisibility,
  GrowthGuardCategory,
  MemoryEntryType,
  MemoryScope,
  PersonalMemoryType,
  PersonalMemoryVisibility,
  SaveMemoryVisibility,
  WriteCategory,
  WriteMemoryMetadata,
  WriteMemoryRequest,
} from '../types';

const SAVE_TOOLS = new Set<AgentSaveTool>([
  'NONE',
  'DIARY',
  'PERSONAL_MEMORY',
  'FAMILY_MEMORY',
  'GROWTH_GUARD',
]);
const DIARY_ENTRY_TYPES = new Set<DiaryEntryType>([
  'DAILY',
  'IMPORTANT_EVENT',
  'LESSON',
  'EMOTION',
  'MESSAGE_TO_FAMILY',
  'SELF_REFLECTION',
]);
const DIARY_VISIBILITIES = new Set<DiaryVisibility>([
  'PRIVATE',
  'FAMILY_VISIBLE',
  'CARE_VISIBLE',
  'LEGACY_VISIBLE',
]);
const MEMORY_TYPES = new Set<MemoryEntryType>([
  'FAMILY_STORY',
  'ELDER_ADVICE',
  'HEALTH_REMINDER',
  'GROWTH_RISK',
  'VALUE',
  'PLAN',
]);
const MEMORY_SCOPES = new Set<MemoryScope>([
  'PRIVATE',
  'CARE_VISIBLE',
  'FAMILY_VISIBLE',
]);
const PERSONAL_MEMORY_VISIBILITIES = new Set<PersonalMemoryVisibility>([
  'PRIVATE',
  'ALL_FAMILIES_VISIBLE',
  'SELECTED_FAMILIES_VISIBLE',
  'CARE_VISIBLE',
]);
const PERSONAL_MEMORY_TYPES = new Set<PersonalMemoryType>([
  'NOTE',
  'KNOWLEDGE',
  'INSIGHT',
  'EXPERIENCE',
  'PREFERENCE',
  'PLAN',
]);
const GROWTH_CATEGORIES = new Set<GrowthGuardCategory>([
  'POSTURE',
  'DENTAL',
  'VISION',
  'SLEEP',
  'EXERCISE',
  'SCREEN_TIME',
  'EMOTION',
  'COMMUNICATION',
  'OTHER',
]);

const EXPLICIT_SAVE_COMMAND_PATTERN = /^(?:请|麻烦)?(?:你)?(?:帮我|替我|给我)?(?:把)?(?:刚才|上面|上文|前面|这段|这条|这个|这些|那段|那条|那件事)?(?:的)?(?:内容|对话|记录|记忆|经历|事情|话)?(?:保存|保存成记忆|存起来|记一下|记下来|记录下来|留个记录|留作记录|沉淀下来|加入记忆库|放到记忆库|收进记忆库)(?:一下|起来|为记忆|到记忆库)?(?:吧)?[。.!！?？]*$/;
const INLINE_SAVE_PREFIX_PATTERN = /^(?:请|麻烦)?(?:你)?(?:帮我|替我|给我)?(?:把)?(?:以下|下面|这条|这段|这份|这句话)?(?:内容|记录|记忆|经历|事情|材料)?(?:保存(?:到(?:家庭)?记忆库)?|加入(?:家庭)?记忆库|放到(?:家庭)?记忆库|收进(?:家庭)?记忆库|记下来|记录下来|存起来)(?:里)?(?:一下)?(?:吧)?[：:\s]+/;
const INLINE_SAVE_ACTION_FIRST_PREFIX_PATTERN = /^(?:请|麻烦)?(?:你)?(?:帮我|替我|给我)?(?:保存|记下|记录)(?:以下|下面|这条|这段|这份|这句话|这些内容)?(?:一下)?(?:吧)?[：:\s]+/;
const INLINE_SAVE_SUFFIX_PATTERN = /[，,。；;\n]\s*(?:请|麻烦)?(?:你)?(?:帮我|替我|给我)?(?:把它|将它)?(?:保存(?:到(?:家庭)?记忆库)?|加入(?:家庭)?记忆库|放到(?:家庭)?记忆库|收进(?:家庭)?记忆库|记下来|记录下来|存起来)(?:里)?(?:一下)?(?:吧)?[。.!！?？]*$/;
const MAX_EXPLICIT_SAVE_COMMAND_LENGTH = 5000;

export type SavedRecordType = 'DIARY_ENTRY' | 'PERSONAL_MEMORY' | 'FAMILY_MEMORY' | 'GROWTH_GUARD' | 'NONE';

export type SavePlanPersistenceDecision = {
  plan: AgentSaveToolPlan;
  shouldPersist: boolean;
  skippedDetail: string;
};

export type AgentSubmissionRoute = {
  content: string;
  kind: 'chat' | 'explicit_save';
  conversationContext: ChatMessage[];
};

export type AgentSaveMemoryToolRequestContext = {
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
  if (normalized.length <= 40 && EXPLICIT_SAVE_COMMAND_PATTERN.test(normalized)) {
    return true;
  }

  const prefixPayload = trimmed
    .replace(INLINE_SAVE_PREFIX_PATTERN, '')
    .replace(INLINE_SAVE_ACTION_FIRST_PREFIX_PATTERN, '')
    .trim();
  if (prefixPayload !== trimmed && prefixPayload.replace(/\s+/g, '').length >= 8) {
    return true;
  }

  const suffixPayload = trimmed.replace(INLINE_SAVE_SUFFIX_PATTERN, '').trim();
  return suffixPayload !== trimmed && suffixPayload.replace(/\s+/g, '').length >= 8;
}

export function routeAgentSubmission(
  value: string,
  messages: readonly ChatMessage[],
): AgentSubmissionRoute {
  const content = value.trim();
  const isExplicitSave = isExplicitSaveMemoryCommand(content);
  return {
    content,
    kind: isExplicitSave ? 'explicit_save' : 'chat',
    conversationContext: isExplicitSave
      ? messages.filter((message) => message.role !== 'system').slice(-10)
      : [],
  };
}

export function buildFallbackSaveToolPlan(
  message: string,
  conversationContext: readonly ChatMessage[],
): AgentSaveToolPlan | null {
  const directContent = stripInlineSaveCommand(message);
  const content = directContent || [...conversationContext]
    .reverse()
    .find((item) => item.role !== 'system' && item.content.trim())
    ?.content.trim() || '';
  if (!content) return null;

  return normalizeSaveToolPlan({
    should_save: true,
    tool: 'DIARY',
    content: content.slice(0, 1200),
    title: '待确认的保存草稿',
    summary: content.slice(0, 80),
    visibility: 'PRIVATE',
    entry_type: 'DAILY',
    memory_type: 'ELDER_ADVICE',
    personal_memory_type: 'NOTE',
    selected_family_ids: [],
    scope: 'PRIVATE',
    category: 'OTHER',
    severity: 1,
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

export function normalizeSaveToolPlan(plan: AgentSaveToolPlan): AgentSaveToolPlan {
  const tool = choice(plan.tool, SAVE_TOOLS, 'NONE');
  const content = String(plan.content || '').trim();
  const shouldSave = Boolean(plan.should_save) && tool !== 'NONE' && content.length > 0;
  const visibility = visibilityFromPlan({ ...plan, tool });
  const scope = scopeFromPlan({ ...plan, tool, visibility });

  return {
    ...plan,
    should_save: shouldSave,
    tool: shouldSave ? tool : 'NONE',
    content,
    title: String(plan.title || '').trim().slice(0, 24) || defaultSaveTitle(tool),
    summary: String(plan.summary || content).trim().slice(0, 80),
    visibility,
    entry_type: entryTypeFromPlan(plan),
    memory_type: memoryTypeFromPlan(plan),
    personal_memory_type: personalMemoryTypeFromPlan(plan),
    selected_family_ids: normalizeFamilyIds(plan.selected_family_ids),
    scope,
    category: growthCategoryFromPlan(plan),
    severity: boundedInt(plan.severity, 3),
    importance: boundedInt(plan.importance, 3),
    tags: Array.isArray(plan.tags)
      ? plan.tags.map((tag) => String(tag).trim()).filter(Boolean).slice(0, 6)
      : [],
    reason: String(plan.reason || '').trim().slice(0, 120),
    confirmation_message: defaultSaveConfirmation(shouldSave ? tool : 'NONE'),
  };
}

export function savePlanPersistenceDecision(plan: AgentSaveToolPlan): SavePlanPersistenceDecision {
  const normalized = normalizeSaveToolPlan(plan);
  const shouldPersist = normalized.should_save && normalized.tool !== 'NONE' && normalized.content.trim().length > 0;
  return {
    plan: normalized,
    shouldPersist,
    skippedDetail: shouldPersist ? '' : skippedSavePlanDetail(normalized),
  };
}

export function skippedSavePlanDetail(plan: AgentSaveToolPlan) {
  const reason = String(plan.reason || '').trim();
  if (reason.includes('保存规划暂时不可用')) return '暂未保存，可稍后重试。';
  return reason ? `这段对话暂时没有可沉淀的内容：${reason}` : '这段对话暂时没有可沉淀的内容。';
}

export function scopeFromPlan(
  plan: Pick<AgentSaveToolPlan, 'scope' | 'visibility' | 'tool'>,
): SaveMemoryVisibility {
  const tool = choice(plan.tool, SAVE_TOOLS, 'NONE');
  if (tool === 'GROWTH_GUARD') return 'CARE_VISIBLE';
  if (tool === 'PERSONAL_MEMORY') {
    return choice(plan.scope || plan.visibility, PERSONAL_MEMORY_VISIBILITIES, 'PRIVATE');
  }
  const fallback = plan.visibility === 'FAMILY_VISIBLE' ? 'FAMILY_VISIBLE' : 'PRIVATE';
  return choice(plan.scope || plan.visibility, MEMORY_SCOPES, fallback);
}

export function visibilityFromPlan(
  plan: Pick<AgentSaveToolPlan, 'visibility' | 'scope' | 'tool'>,
): SaveMemoryVisibility {
  const tool = choice(plan.tool, SAVE_TOOLS, 'NONE');
  if (tool === 'GROWTH_GUARD') return 'CARE_VISIBLE';
  if (tool === 'FAMILY_MEMORY') return plan.scope === 'CARE_VISIBLE' ? 'CARE_VISIBLE' : 'FAMILY_VISIBLE';
  if (tool === 'PERSONAL_MEMORY') {
    return choice(plan.visibility || plan.scope, PERSONAL_MEMORY_VISIBILITIES, 'PRIVATE');
  }
  return choice(plan.visibility || plan.scope, DIARY_VISIBILITIES, 'PRIVATE');
}

export function entryTypeFromPlan(plan: Pick<AgentSaveToolPlan, 'entry_type'>): DiaryEntryType {
  return choice(plan.entry_type, DIARY_ENTRY_TYPES, 'DAILY');
}

export function memoryTypeFromPlan(plan: Pick<AgentSaveToolPlan, 'memory_type'>): MemoryEntryType {
  return choice(plan.memory_type, MEMORY_TYPES, 'ELDER_ADVICE');
}

export function personalMemoryTypeFromPlan(
  plan: Pick<AgentSaveToolPlan, 'personal_memory_type'>,
): PersonalMemoryType {
  return choice(plan.personal_memory_type, PERSONAL_MEMORY_TYPES, 'NOTE');
}

export function growthCategoryFromPlan(plan: Pick<AgentSaveToolPlan, 'category'>): GrowthGuardCategory {
  return choice(plan.category, GROWTH_CATEGORIES, 'OTHER');
}

export function toolLabel(tool: AgentSaveTool) {
  if (tool === 'DIARY') return '日记';
  if (tool === 'PERSONAL_MEMORY') return '个人记忆';
  if (tool === 'FAMILY_MEMORY') return '家庭记忆';
  if (tool === 'GROWTH_GUARD') return '成长观察';
  return '未保存';
}

export function savedRecordType(tool: AgentSaveTool): SavedRecordType {
  if (tool === 'DIARY') return 'DIARY_ENTRY';
  if (tool === 'PERSONAL_MEMORY') return 'PERSONAL_MEMORY';
  if (tool === 'FAMILY_MEMORY') return 'FAMILY_MEMORY';
  if (tool === 'GROWTH_GUARD') return 'GROWTH_GUARD';
  return 'NONE';
}

export function writeCategoryFromTool(tool: AgentSaveTool): WriteCategory {
  if (tool === 'PERSONAL_MEMORY' || tool === 'FAMILY_MEMORY') return 'EXPERIENCE';
  if (tool === 'GROWTH_GUARD') return 'OBSERVATION';
  return 'RECORD';
}

export function savePlanDetail(plan: AgentSaveToolPlan, savedRecordId?: number) {
  const idPart = savedRecordId ? ` · #${savedRecordId}` : '';
  return `${toolLabel(plan.tool)} · ${plan.title} · ${visibilityLabel(plan.visibility || plan.scope)}${idPart}`;
}

export function truncateAuditText(value: string, maxLength = 500) {
  const text = value.trim().replace(/\s+/g, ' ');
  return text.length <= maxLength ? text : `${text.slice(0, maxLength)}...`;
}

export function saveMemorySkillMetadata(plan: AgentSaveToolPlan, savedAt?: string) {
  return {
    skillName: 'save_memory',
    plannedTool: plan.tool,
    plannedTitle: plan.title,
    plannedReason: plan.reason,
    visibility: plan.visibility || '',
    scope: plan.scope || '',
    confirmationPolicy: 'USER_APPROVED_EDITABLE_DRAFT',
    savedAt: savedAt || '',
  };
}

export function buildAgentSaveMemoryMetadata(
  plan: AgentSaveToolPlan,
  context: AgentSaveMemoryToolRequestContext,
): AgentSaveMemoryMetadata {
  const savedAt = context.savedAt || new Date().toISOString();
  const common = {
    familyName: context.familyName || '',
    viewerRole: context.viewerRole || '',
    savedFromMessageRole: context.savedFromMessageRole || '',
    ...saveMemorySkillMetadata(plan, savedAt),
  };

  const metadata: AgentSaveMemoryMetadata = context.agentMode === 'mirror'
    ? {
        source: 'MIRROR_AGENT_TOOL',
        relationSource: 'MIRROR_AGENT_TOOL',
        relatedUserId: context.targetUserId ?? null,
        relatedMemberName: context.targetMemberName || '',
        ...common,
      }
    : context.agentMode === 'persona'
      ? {
          source: 'PERSONA_MEMBER_TOOL',
          relationSource: 'PERSONA_MEMBER_TOOL',
          relatedPersonaId: context.targetPersonaId ?? null,
          relatedPersonaName: context.targetPersonaName || '',
          ...common,
        }
      : {
          source: 'FAMILY_COMPANION_TOOL',
          relationSource: 'FAMILY_AGENT_TOOL',
          ...common,
        };

  if (plan.tool === 'FAMILY_MEMORY' && context.agentMode === 'mirror') {
    return {
      ...metadata,
      sourceType: 'FAMILY_EXPERIENCE',
      scenario: '镜像对话保存',
      target: context.targetMemberName || '',
    };
  }
  if (plan.tool === 'FAMILY_MEMORY' && context.agentMode === 'persona') {
    return {
      ...metadata,
      sourceType: 'FAMILY_EXPERIENCE',
      scenario: '精神成员对话保存',
      target: context.targetPersonaName || '',
    };
  }
  if (plan.tool === 'GROWTH_GUARD' && context.agentMode === 'mirror') {
    return {
      ...metadata,
      sourceType: 'GROWTH_OBSERVATION',
      followUpStatus: 'PENDING',
    };
  }
  return metadata;
}

export function buildAgentSaveMemoryToolRequest(
  familyId: number,
  plan: AgentSaveToolPlan,
  context: AgentSaveMemoryToolRequestContext,
): AgentSaveMemoryToolRequest {
  const metadata = buildAgentSaveMemoryMetadata(plan, context);
  const toolRequest = buildWriteMemorySaveRequest(
    familyId,
    plan,
    metadata,
    context.targetUserId || undefined,
  );

  return {
    ...toolRequest,
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
  plan: AgentSaveToolPlan,
  metadata: WriteMemoryMetadata,
  targetUserId?: number,
): WriteMemoryRequest {
  return {
    familyId,
    writeCategory: writeCategoryFromTool(plan.tool),
    content: plan.content,
    title: plan.title,
    tags: plan.tags,
    visibility: plan.tool === 'GROWTH_GUARD' ? scopeFromPlan(plan) : visibilityFromPlan(plan),
    relatedUserId: targetUserId,
    diaryEntryType: entryTypeFromPlan(plan),
    memoryType: memoryTypeFromPlan(plan),
    personalMemoryType: personalMemoryTypeFromPlan(plan),
    memoryLibrary: plan.tool === 'PERSONAL_MEMORY' ? 'PERSONAL' : 'FAMILY',
    selectedFamilyIds: plan.tool === 'PERSONAL_MEMORY'
      ? normalizeFamilyIds(plan.selected_family_ids)
      : [],
    growthCategory: growthCategoryFromPlan(plan),
    growthSeverity: plan.severity,
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

function defaultSaveTitle(tool: AgentSaveTool) {
  return {
    DIARY: '聊天保存日记',
    PERSONAL_MEMORY: '聊天保存个人记忆',
    FAMILY_MEMORY: '聊天保存家庭记忆',
    GROWTH_GUARD: '聊天保存成长观察',
    NONE: '无需保存',
  }[tool];
}

function defaultSaveConfirmation(tool: AgentSaveTool) {
  return {
    DIARY: '日记草稿已准备，请修改或确认后保存。',
    PERSONAL_MEMORY: '个人记忆草稿已准备，请修改或确认后保存。',
    FAMILY_MEMORY: '家庭记忆草稿已准备，请修改或确认后保存。',
    GROWTH_GUARD: '成长观察草稿已准备，请修改或确认后保存。',
    NONE: '没有找到可保存的内容。',
  }[tool];
}

function stripInlineSaveCommand(value: string) {
  const trimmed = value.trim();
  const prefixPayload = trimmed
    .replace(INLINE_SAVE_PREFIX_PATTERN, '')
    .replace(INLINE_SAVE_ACTION_FIRST_PREFIX_PATTERN, '')
    .trim();
  if (prefixPayload !== trimmed) return prefixPayload;
  const suffixPayload = trimmed.replace(INLINE_SAVE_SUFFIX_PATTERN, '').trim();
  if (suffixPayload !== trimmed) return suffixPayload;
  return EXPLICIT_SAVE_COMMAND_PATTERN.test(trimmed.replace(/\s+/g, '')) ? '' : trimmed;
}

function visibilityLabel(value?: string) {
  const normalized = String(value || '').trim().toUpperCase();
  if (normalized === 'PRIVATE') return '仅自己可见';
  if (normalized === 'FAMILY_VISIBLE') return '家庭可见';
  if (normalized === 'ALL_FAMILIES_VISIBLE') return '当前全部家族可见';
  if (normalized === 'SELECTED_FAMILIES_VISIBLE') return '选择家族可见';
  if (normalized === 'CARE_VISIBLE') return '照护可见';
  if (normalized === 'LEGACY_VISIBLE') return '传承可见';
  return value || '未设置';
}

function normalizeFamilyIds(value?: number[]) {
  if (!Array.isArray(value)) return [];
  return Array.from(new Set(value.filter((id) => Number.isInteger(id) && id > 0))).slice(0, 20);
}
