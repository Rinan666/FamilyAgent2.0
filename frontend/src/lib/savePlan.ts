import type {
  AgentSaveTool,
  AgentSaveToolPlan,
  DiaryEntryType,
  DiaryVisibility,
  GrowthGuardCategory,
  MemoryEntryType,
  MemoryScope,
  WriteCategory,
  WriteMemoryRequest,
} from '../types';

const SAVE_TOOLS = new Set<AgentSaveTool>(['NONE', 'DIARY', 'FAMILY_MEMORY', 'GROWTH_GUARD']);
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

const EXPLICIT_SAVE_COMMAND_PATTERN = /^(?:请|麻烦)?(?:你)?(?:帮我|替我|给我)?(?:把)?(?:刚才|上面|上文|前面|这段|这条|这个|这些|那段|那条|那件事)?(?:的)?(?:内容|对话|记录|记忆|经历|事情|话)?(?:保存|保存成记忆|存起来|记一下|记下来|记录下来|留个记录|留作记录|沉淀下来|加入记忆库|放到记忆库|收进记忆库)(?:一下|起来|吧|为记忆|到记忆库)?[。.!！?？]*$/;

export type SavedRecordType = 'DIARY_ENTRY' | 'FAMILY_MEMORY' | 'GROWTH_GUARD' | 'NONE';

export type SavePlanPersistenceDecision = {
  plan: AgentSaveToolPlan;
  shouldPersist: boolean;
  skippedDetail: string;
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
  const normalized = value.trim().replace(/\s+/g, '');
  if (!normalized || normalized.length > 40) return false;
  return EXPLICIT_SAVE_COMMAND_PATTERN.test(normalized);
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
    scope,
    category: growthCategoryFromPlan(plan),
    severity: boundedInt(plan.severity, 3),
    importance: boundedInt(plan.importance, 3),
    tags: Array.isArray(plan.tags)
      ? plan.tags.map((tag) => String(tag).trim()).filter(Boolean).slice(0, 6)
      : [],
    reason: String(plan.reason || '').trim().slice(0, 120),
    confirmation_message: String(plan.confirmation_message || defaultSaveConfirmation(tool)).trim().slice(0, 120),
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

export function scopeFromPlan(plan: Pick<AgentSaveToolPlan, 'scope' | 'visibility' | 'tool'>): MemoryScope {
  const tool = choice(plan.tool, SAVE_TOOLS, 'NONE');
  if (tool === 'GROWTH_GUARD') return 'CARE_VISIBLE';
  const fallback = plan.visibility === 'FAMILY_VISIBLE' ? 'FAMILY_VISIBLE' : 'PRIVATE';
  return choice(plan.scope || plan.visibility, MEMORY_SCOPES, fallback);
}

export function visibilityFromPlan(plan: Pick<AgentSaveToolPlan, 'visibility' | 'scope' | 'tool'>): DiaryVisibility {
  const tool = choice(plan.tool, SAVE_TOOLS, 'NONE');
  if (tool === 'GROWTH_GUARD') return 'CARE_VISIBLE';
  if (tool === 'FAMILY_MEMORY') return plan.scope === 'CARE_VISIBLE' ? 'CARE_VISIBLE' : 'FAMILY_VISIBLE';
  return choice(plan.visibility || plan.scope, DIARY_VISIBILITIES, 'PRIVATE');
}

export function entryTypeFromPlan(plan: Pick<AgentSaveToolPlan, 'entry_type'>): DiaryEntryType {
  return choice(plan.entry_type, DIARY_ENTRY_TYPES, 'DAILY');
}

export function memoryTypeFromPlan(plan: Pick<AgentSaveToolPlan, 'memory_type'>): MemoryEntryType {
  return choice(plan.memory_type, MEMORY_TYPES, 'ELDER_ADVICE');
}

export function growthCategoryFromPlan(plan: Pick<AgentSaveToolPlan, 'category'>): GrowthGuardCategory {
  return choice(plan.category, GROWTH_CATEGORIES, 'OTHER');
}

export function toolLabel(tool: AgentSaveTool) {
  if (tool === 'DIARY') return '日记';
  if (tool === 'FAMILY_MEMORY') return '家庭记忆';
  if (tool === 'GROWTH_GUARD') return '成长观察';
  return '未保存';
}

export function savedRecordType(tool: AgentSaveTool): SavedRecordType {
  if (tool === 'DIARY') return 'DIARY_ENTRY';
  if (tool === 'FAMILY_MEMORY') return 'FAMILY_MEMORY';
  if (tool === 'GROWTH_GUARD') return 'GROWTH_GUARD';
  return 'NONE';
}

export function writeCategoryFromTool(tool: AgentSaveTool): WriteCategory {
  if (tool === 'FAMILY_MEMORY') return 'EXPERIENCE';
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
    confirmationPolicy: 'USER_CONFIRMATION_OR_EXPLICIT_SAVE_COMMAND',
    savedAt: savedAt || '',
  };
}

export function buildWriteMemorySaveRequest(
  familyId: number,
  plan: AgentSaveToolPlan,
  metadata: Record<string, unknown>,
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
    growthCategory: growthCategoryFromPlan(plan),
    growthSeverity: plan.severity,
    metadata,
  };
}

export function todayString() {
  return new Date().toISOString().slice(0, 10);
}

function defaultSaveTitle(tool: AgentSaveTool) {
  return {
    DIARY: '聊天保存日记',
    FAMILY_MEMORY: '聊天保存家庭记忆',
    GROWTH_GUARD: '聊天保存成长观察',
    NONE: '无需保存',
  }[tool];
}

function defaultSaveConfirmation(tool: AgentSaveTool) {
  return {
    DIARY: '已保存为日记。',
    FAMILY_MEMORY: '已保存为家庭记忆。',
    GROWTH_GUARD: '已保存为成长观察。',
    NONE: '这条消息无需保存。',
  }[tool];
}

function visibilityLabel(value?: string) {
  const normalized = String(value || '').trim().toUpperCase();
  if (normalized === 'PRIVATE') return '仅自己可见';
  if (normalized === 'FAMILY_VISIBLE') return '家庭可见';
  if (normalized === 'CARE_VISIBLE') return '照护可见';
  if (normalized === 'LEGACY_VISIBLE') return '传承可见';
  return value || '未设置';
}
