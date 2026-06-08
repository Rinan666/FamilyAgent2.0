import type {
  AgentSaveTool,
  AgentSaveToolPlan,
  CreateDiaryEntryRequest,
  CreateFamilyMemoryRequest,
  CreateGrowthGuardRecordRequest,
  DiaryEntryType,
  DiaryVisibility,
  GrowthGuardCategory,
  MemoryEntryType,
  MemoryScope,
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
  'PARENT_VISIBLE',
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

export type SavedRecordType = 'DIARY_ENTRY' | 'FAMILY_MEMORY' | 'GROWTH_GUARD' | 'NONE';

function choice<T extends string>(value: unknown, allowed: Set<T>, fallback: T): T {
  const normalized = String(value || '').trim().toUpperCase();
  return allowed.has(normalized as T) ? normalized as T : fallback;
}

function boundedInt(value: unknown, fallback: number) {
  const number = Number(value);
  if (!Number.isFinite(number)) return fallback;
  return Math.max(1, Math.min(5, Math.round(number)));
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
  if (tool === 'DIARY') return '每日记录';
  if (tool === 'FAMILY_MEMORY') return '经验沉淀';
  if (tool === 'GROWTH_GUARD') return '成长观察';
  return '未保存';
}

export function savedRecordType(tool: AgentSaveTool): SavedRecordType {
  if (tool === 'DIARY') return 'DIARY_ENTRY';
  if (tool === 'FAMILY_MEMORY') return 'FAMILY_MEMORY';
  if (tool === 'GROWTH_GUARD') return 'GROWTH_GUARD';
  return 'NONE';
}

export function savePlanDetail(plan: AgentSaveToolPlan, savedRecordId?: number) {
  const idPart = savedRecordId ? ` · #${savedRecordId}` : '';
  return `${toolLabel(plan.tool)} · ${plan.title} · ${plan.visibility || plan.scope}${idPart}`;
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

export function buildDiarySaveRequest(
  familyId: number,
  plan: AgentSaveToolPlan,
  metadata: Record<string, unknown>,
): CreateDiaryEntryRequest {
  return {
    familyId,
    content: plan.content,
    entryType: entryTypeFromPlan(plan),
    title: plan.title,
    tags: plan.tags,
    visibility: visibilityFromPlan(plan),
    metadata,
  };
}

export function buildFamilyMemorySaveRequest(
  familyId: number,
  plan: AgentSaveToolPlan,
  metadata: Record<string, unknown>,
): CreateFamilyMemoryRequest {
  return {
    familyId,
    content: plan.content,
    type: memoryTypeFromPlan(plan),
    scope: scopeFromPlan(plan),
    summary: plan.summary,
    importance: plan.importance,
    metadata,
  };
}

export function buildGrowthGuardSaveRequest(
  familyId: number,
  plan: AgentSaveToolPlan,
  observedAt: string,
  metadata: Record<string, unknown>,
  targetUserId?: number,
): CreateGrowthGuardRecordRequest {
  return {
    familyId,
    targetUserId,
    category: growthCategoryFromPlan(plan),
    content: plan.content,
    severity: plan.severity,
    observedAt,
    visibility: scopeFromPlan(plan),
    metadata,
  };
}

export function todayString() {
  return new Date().toISOString().slice(0, 10);
}

function defaultSaveTitle(tool: AgentSaveTool) {
  return {
    DIARY: '对话保存的每日记录',
    FAMILY_MEMORY: '对话沉淀的经验',
    GROWTH_GUARD: '对话记录的成长观察',
    NONE: '无需保存',
  }[tool];
}

function defaultSaveConfirmation(tool: AgentSaveTool) {
  return {
    DIARY: '已保存为每日记录。',
    FAMILY_MEMORY: '已保存为经验沉淀。',
    GROWTH_GUARD: '已保存为成长观察。',
    NONE: '这条消息不需要保存。',
  }[tool];
}
