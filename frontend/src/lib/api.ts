/**
 * API client helpers.
 *
 * - Java business APIs are proxied through Next.js (/api/* -> backend)
 * - Python AI APIs are proxied through Next.js (/ai-proxy/* -> AI service)
 *
 * Environment variables:
 *   NEXT_PUBLIC_API_URL - backend base URL (default /api)
 *   AI_SERVICE_URL - AI service base URL (default http://localhost:8090)
 */
import type {
  ApiResult,
  LoginRequest, LoginResponse, RegisterRequest, ChangePasswordRequest, UpdateProfileRequest,
  User, Family, FamilyMember, FamilyRelationship, CareAuthorization, DiaryEntry, CreateDiaryEntryRequest, UpdateDiaryEntryRequest,
  ChatMessage, ChatSessionArchiveDetail, ChatSessionArchiveSummary, ChatSessionDetail, ChatSessionMessageItem, ChatSessionMessagePage, ChatSessionSummary, MemoryEntry, MemoryLibraryItem, MemoryLibraryItemType, MemoryMaintenanceSuggestion, PageResult,
  CreateFamilyMemoryRequest, FamilyMemoryCard, HeritageClassicalDraft, MemoryEntryType, MemoryVoteType,
  HeritageTask, CreateHeritageTaskRequest, HeritageTaskDraft,
  CreateGrowthGuardRecordRequest, GrowthGuardRecord, CreateGrowthGuardReportRequest, GrowthGuardReport, WeeklyGrowthReport,
  GrowthFollowUpStatus, MirrorContextResponse,
  AgentDraftScene, AgentOrganizedDraft, AgentSaveToolPlan, HeritageSaveJudge, AuthorizedMemoryRecallResult, FamilyWeeklyDigest, RebuildMemoryIndexResult,
  DatabaseHealthResponse, MemoryRecallDiagnosticRequest, MemoryRecallDiagnosticResponse, AdminUserSummary, FamilyDatabaseSummary,
  SkillRun, CreateSkillRunRequest, UpdateSkillRunRequest,
} from '@/types';
import type { ViewerRole } from '@/lib/roles';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || '/api';
export type AIStreamHandle = {
  abort: () => void;
  completed: Promise<void>;
};
// ============================================
class ApiError extends Error {
  constructor(public code: number, message: string) {
    super(message);
    this.name = 'ApiError';
  }
}

function backendUnavailableError() {
  return new ApiError(503, 'Backend service unavailable. Please check the Java service and retry.');
}

function aiErrorMessage(status: number, detail?: unknown, retryAfter?: string | null) {
  const raw = typeof detail === 'string' ? detail.trim() : '';
  if (status === 400) {
    if (raw.includes('信息熵过低') || raw.includes('低俗暗语') || raw.includes('恶意复读') || raw.includes('刷 Token')) {
      return raw;
    }
    if (raw.includes('系统提示词') || raw.includes('开发者指令') || raw.includes('隐藏策略')) {
      return '这条请求触碰了系统安全边界，不能回溯或输出内部提示词。可以换一种方式描述你的真实问题。';
    }
    if (raw.includes('身份设定') || raw.includes('安全边界') || raw.includes('角色')) {
      return '这条请求试图改变 Agent 的身份边界，已被拦截。你可以要求语气更温和、更简洁，但不能要求它冒充或改成人设。';
    }
    return raw || '这条 AI 请求不符合安全规则，请换一种问法。';
  }
  if (status === 413) {
    return '这次发送的内容太长了，请拆成几段，或只保留最关键的记录再发送。';
  }
  if (status === 429) {
    const seconds = Number(retryAfter);
    return Number.isFinite(seconds) && seconds > 0
      ? `AI 请求太频繁了，请约 ${Math.ceil(seconds)} 秒后再试。`
      : 'AI 请求太频繁了，请稍后再试。';
  }
  if (status === 504) {
    return 'AI 思考时间过长，系统已自动中断。请把问题拆小一点再试。';
  }
  if (status === 401) {
    return 'AI 服务认证失败，请刷新页面或重新登录后再试。';
  }
  if (status >= 500) {
    return 'AI 服务暂时不可用，请稍后再试。';
  }
  return raw || `AI 服务返回异常（HTTP ${status}）`;
}

async function readErrorDetail(res: Response): Promise<unknown> {
  const data = await res.json().catch(() => null);
  if (data && typeof data === 'object') {
    const record = data as Record<string, unknown>;
    return record.detail || record.message || record.error;
  }
  return null;
}

async function request<T>(url: string, options: RequestInit = {}): Promise<T> {
  const token = typeof window !== 'undefined' ? localStorage.getItem('token') : null;
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = token;

  let res: Response;
  try {
    res = await fetch(`${API_BASE}${url}`, { ...options, headers });
  } catch {
    throw backendUnavailableError();
  }

  let data: ApiResult<T>;
  try {
    data = await res.json();
  } catch {
    throw res.ok
      ? new ApiError(502, 'Backend returned an invalid response.')
      : backendUnavailableError();
  }

  if (data.code !== 200) {
    if (data.code === 401) {
      if (typeof window !== 'undefined') {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        window.location.href = '/login';
      }
    }
    throw new ApiError(data.code, data.message);
  }
  return data.data as T;
}

function parseJsonField<T>(value: unknown, fallback: T): T {
  let current: unknown = value;

  for (let depth = 0; depth < 5; depth += 1) {
    if (current == null) return fallback;

    if (typeof current === 'string') {
      try {
        current = JSON.parse(current);
        continue;
      } catch {
        return fallback;
      }
    }

    if (typeof current === 'object') {
      const wrapped = current as { value?: unknown };
      if (
        Object.keys(current as Record<string, unknown>).length === 1
        && typeof wrapped.value === 'string'
      ) {
        current = wrapped.value;
        continue;
      }
      return current as T;
    }

    return current as T;
  }

  return fallback;
}

function toText(value: unknown): string {
  if (value == null) return '';
  if (typeof value === 'string') return value.trim();
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  if (typeof value === 'object' && !Array.isArray(value)) {
    const record = value as Record<string, unknown>;
    const nested = [
      record.value,
      record.text,
      record.stem,
      record.content,
      record.question,
      record.title,
      record.answer,
      record.final_answer,
      record.finalAnswer,
      record.standard_answer,
      record.standardAnswer,
      record.result,
    ].map(toText).find(Boolean);
    return nested || '';
  }
  return '';
}

function normalizeStringArray(value: unknown): string[] {
  const parsed = parseJsonField<unknown>(value, value);
  if (Array.isArray(parsed)) {
    return parsed.map(toText).filter(Boolean);
  }
  if (typeof parsed === 'string') {
    return parsed
      .split(/\r?\n/)
      .map((item) => item.trim())
      .filter(Boolean);
  }
  return [];
}

function normalizeSessionSummary(raw: ChatSessionSummary): ChatSessionSummary {
  return {
    ...raw,
    metadata: parseJsonField<Record<string, unknown>>(raw.metadata, {}),
  };
}

function normalizeSessionArchiveSummary(raw: ChatSessionArchiveSummary): ChatSessionArchiveSummary {
  return {
    ...raw,
    metadata: parseJsonField<Record<string, unknown>>(raw.metadata, {}),
  };
}

function normalizeSessionMessageItem(raw: ChatSessionMessageItem): ChatSessionMessageItem {
  return {
    ...raw,
    metadata: parseJsonField<Record<string, unknown>>(raw.metadata, {}),
  };
}

function normalizeSessionMessagePage(raw: ChatSessionMessagePage): ChatSessionMessagePage {
  return {
    ...raw,
    items: (raw.items || []).map(normalizeSessionMessageItem),
  };
}

function normalizeSessionArchiveDetail(raw: ChatSessionArchiveDetail): ChatSessionArchiveDetail {
  return {
    ...normalizeSessionArchiveSummary(raw),
    transcript: (raw.transcript || []).map(normalizeSessionMessageItem),
  };
}

function normalizeSessionDetail(raw: ChatSessionDetail): ChatSessionDetail {
  return {
    ...normalizeSessionSummary(raw),
    archiveMetadata: parseJsonField<Record<string, unknown>>(raw.archiveMetadata, {}),
    archives: (raw.archives || []).map(normalizeSessionArchiveSummary),
  };
}

function normalizeUser(raw: User): User {
  const metadata = parseJsonField<Record<string, unknown>>(raw.metadata, {});
  const birthDate = raw.birthDate
    || (typeof metadata.birthDate === 'string' ? metadata.birthDate.slice(0, 10) : '')
    || (typeof metadata.birthday === 'string' ? metadata.birthday.slice(0, 10) : '')
    || (typeof metadata.dateOfBirth === 'string' ? metadata.dateOfBirth.slice(0, 10) : '');
  const birthYear = raw.birthYear
    || (typeof metadata.birthYear === 'string' ? metadata.birthYear : '')
    || (typeof metadata.yearOfBirth === 'string' ? metadata.yearOfBirth : '')
    || (birthDate ? birthDate.slice(0, 4) : '');

  return {
    ...raw,
    birthDate: birthDate || undefined,
    birthYear: birthYear || undefined,
    metadata,
  };
}

function normalizeLoginResponse(raw: LoginResponse): LoginResponse {
  const metadata = parseJsonField<Record<string, unknown>>(raw.metadata, {});
  const birthDate = raw.birthDate
    || (typeof metadata.birthDate === 'string' ? metadata.birthDate.slice(0, 10) : '')
    || (typeof metadata.birthday === 'string' ? metadata.birthday.slice(0, 10) : '')
    || (typeof metadata.dateOfBirth === 'string' ? metadata.dateOfBirth.slice(0, 10) : '');
  const birthYear = raw.birthYear
    || (typeof metadata.birthYear === 'string' ? metadata.birthYear : '')
    || (typeof metadata.yearOfBirth === 'string' ? metadata.yearOfBirth : '')
    || (birthDate ? birthDate.slice(0, 4) : '');

  return {
    ...raw,
    birthDate: birthDate || undefined,
    birthYear: birthYear || undefined,
    metadata,
  };
}

function normalizeFamilyMember(raw: FamilyMember): FamilyMember {
  const metadata = parseJsonField<Record<string, unknown>>(raw.metadata, {});
  const birthDate = raw.birthDate
    || (typeof metadata.birthDate === 'string' ? metadata.birthDate.slice(0, 10) : '')
    || (typeof metadata.birthday === 'string' ? metadata.birthday.slice(0, 10) : '')
    || (typeof metadata.dateOfBirth === 'string' ? metadata.dateOfBirth.slice(0, 10) : '');
  const birthYear = raw.birthYear
    || (typeof metadata.birthYear === 'string' ? metadata.birthYear : '')
    || (typeof metadata.yearOfBirth === 'string' ? metadata.yearOfBirth : '')
    || (birthDate ? birthDate.slice(0, 4) : '');

  return {
    ...raw,
    birthDate: birthDate || undefined,
    birthYear: birthYear || undefined,
    metadata,
  };
}

function normalizeFamilyMembers(members: FamilyMember[] | undefined | null): FamilyMember[] {
  return (members || []).map(normalizeFamilyMember);
}

function normalizeSessionSummaries(sessions: ChatSessionSummary[] | undefined | null): ChatSessionSummary[] {
  return (sessions || []).map(normalizeSessionSummary);
}

function toSessionMessagePayload(message: ChatMessage) {
  return {
    id: message.id,
    role: message.role,
    content: message.content,
    timestamp: message.timestamp,
    metadata: message.metadata || {},
  };
}

export function sessionMessageItemToChatMessage(item: ChatSessionMessageItem): ChatMessage {
  return {
    id: item.id || `session-message-${item.seq || item.createdAt}`,
    role: (item.role === 'assistant' || item.role === 'system') ? item.role : 'user',
    content: item.content,
    timestamp: item.createdAt,
    metadata: item.metadata as ChatMessage['metadata'],
  };
}

function normalizeWeeklyGrowthReport(value: unknown): WeeklyGrowthReport {
  const parsed = parseJsonField<Record<string, unknown>>(value, {});
  return {
    title: toText(parsed.title) || '本周成长提醒',
    summary: toText(parsed.summary),
    affirmations: normalizeStringArray(parsed.affirmations),
    concerns: normalizeStringArray(parsed.concerns),
    signals: normalizeStringArray(parsed.signals),
    uncertainty_notes: normalizeStringArray(parsed.uncertainty_notes),
    family_experience_refs: normalizeStringArray(parsed.family_experience_refs),
    suggested_actions: normalizeStringArray(parsed.suggested_actions),
    follow_up_questions: normalizeStringArray(parsed.follow_up_questions),
    safety_note: toText(parsed.safety_note) || '此内容只作为家庭观察提醒，不构成医学诊断或治疗建议。',
  };
}

function normalizeGrowthGuardReport(raw: GrowthGuardReport): GrowthGuardReport {
  const report = normalizeWeeklyGrowthReport(raw.report);
  return {
    ...raw,
    title: raw.title || report.title,
    summary: raw.summary || report.summary,
    report,
    metadata: parseJsonField<Record<string, unknown>>(raw.metadata, {}),
  };
}

async function aiRequest<T>(path: string, body: unknown): Promise<T> {
  const token = typeof window !== 'undefined' ? localStorage.getItem('token') : null;
  const res = await fetch(`/ai-proxy${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json;charset=UTF-8',
      ...(token ? { Authorization: token } : {}),
    },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    // 401 from AI is likely a transient auth issue (backend unreachable) — don't kill session
    const detail = await readErrorDetail(res);
    throw new ApiError(res.status, aiErrorMessage(res.status, detail, res.headers.get('Retry-After')));
  }
  const data = await res.json();
  if (!data.success) throw new ApiError(500, aiErrorMessage(500, data.detail || 'AI error'));
  return data as T;
}

// File upload requests proxied to the Python AI service.
async function aiFileRequest<T>(path: string, file: File): Promise<T> {
  const token = typeof window !== 'undefined' ? localStorage.getItem('token') : null;
  const formData = new FormData();
  formData.append('file', file);

  const res = await fetch(`/ai-proxy${path}`, {
    method: 'POST',
    headers: {
      ...(token ? { Authorization: token } : {}),
    },
    body: formData,
  });

  const data = await res.json().catch(() => null);
  if (!res.ok) {
    const message = aiErrorMessage(res.status, data?.detail || data?.message, res.headers.get('Retry-After'));
    throw new ApiError(res.status, message);
  }

  if (!data.success) throw new ApiError(500, aiErrorMessage(500, data.detail || 'AI error'));
  return data as T;
}

function sseStreamRequest(
  path: string,
  body: unknown,
  onChunk: (chunk: string) => void,
  onDone: () => void,
  onError: (error: string) => void,
  onMetadata?: (metadata: Record<string, unknown>) => void,
  onAbort?: () => void,
): AIStreamHandle {
  const controller = new AbortController();

  const handleSseLine = (rawLine: string): boolean => {
    const line = rawLine.replace(/\r$/, '');
    if (!line || line.startsWith(':')) return false;
    if (!line.startsWith('data:')) return false;

    const data = line.slice(5).trimStart();
    if (!data) return false;

    try {
      const payload = JSON.parse(data);
      if (payload.done) {
        onDone();
        return true;
      }
      if (payload.error) {
        onError(aiErrorMessage(500, payload.error));
        return true;
      }
      if (payload.metadata) onMetadata?.(payload.metadata);
      if (payload.content) onChunk(payload.content);
    } catch {
      // skip malformed SSE payloads
    }
    return false;
  };

  const completed = (async () => {
    try {
      const token = typeof window !== 'undefined' ? localStorage.getItem('token') : null;
      const res = await fetch(`/ai-proxy${path}`, {
        method: 'POST',
        headers: {
          Accept: 'text/event-stream',
          'Content-Type': 'application/json;charset=UTF-8',
          ...(token ? { Authorization: token } : {}),
        },
        body: JSON.stringify(body),
        cache: 'no-store',
        signal: controller.signal,
      });
      if (!res.ok) {
        const detail = await readErrorDetail(res);
        onError(aiErrorMessage(res.status, detail, res.headers.get('Retry-After')));
        return;
      }

      const reader = res.body?.getReader();
      if (!reader) {
        onError('AI service returned no readable response body.');
        return;
      }

      const decoder = new TextDecoder();
      let buffer = '';
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          if (handleSseLine(line)) return;
        }
      }

      const tail = buffer + decoder.decode();
      if (tail) {
        const lines = tail.split('\n');
        for (const line of lines) {
          if (handleSseLine(line)) return;
        }
      }

      onDone();
    } catch (error) {
      if (controller.signal.aborted) {
        onAbort?.();
        return;
      }
      onError(error instanceof Error ? error.message : 'AI request failed, please retry later.');
    }
  })();

  return {
    abort: () => controller.abort(),
    completed,
  };
}

// ============================================
// Users
// ============================================
export const userApi = {
  register: (data: RegisterRequest) => request<User>('/users/register', { method: 'POST', body: JSON.stringify(data) }).then(normalizeUser),
  login: (data: LoginRequest) => request<LoginResponse>('/users/login', { method: 'POST', body: JSON.stringify(data) }).then(normalizeLoginResponse),
  getMe: () => request<User>('/users/me', { cache: 'no-store' }).then(normalizeUser),
  updateProfile: (data: UpdateProfileRequest) =>
    request<User>('/users/me/profile', { method: 'POST', body: JSON.stringify(data) }).then(normalizeUser),
  changePassword: (data: ChangePasswordRequest) =>
    request<void>('/users/change-password', { method: 'POST', body: JSON.stringify(data) }),
  getUser: (id: number) => request<User>(`/users/${id}`).then(normalizeUser),
};

// ============================================
// Families
// ============================================
export const familyApi = {
  create: (data: { name: string; description?: string }) =>
    request<Family>('/families', { method: 'POST', body: JSON.stringify(data) }),
  join: (inviteCode: string) =>
    request<FamilyMember>(`/families/join?inviteCode=${inviteCode}`, { method: 'POST' }),
  getMyFamilies: () => request<Family[]>('/families/my'),
  getFamily: (id: number) => request<Family>(`/families/${id}`),
  getMembers: (familyId: number) =>
    request<FamilyMember[]>(`/families/${familyId}/members`).then(normalizeFamilyMembers),
  getMyRelationshipLabels: (familyId: number) =>
    request<FamilyRelationship[]>(`/families/${familyId}/relationships/my-labels`),
  upsertRelationshipLabel: (
    familyId: number,
    targetUserId: number,
    data: { label: string; reverseLabel?: string; note?: string },
  ) =>
    request<FamilyRelationship>(`/families/${familyId}/members/${targetUserId}/relationship`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  getMyCareAuthorizations: (familyId: number) =>
    request<CareAuthorization[]>(`/families/${familyId}/care-authorizations/my`),
  upsertCareAuthorization: (
    familyId: number,
    subjectUserId: number,
    caregiverUserId: number,
    data: { scope?: 'ALL' | 'DIARY' | 'GROWTH_GUARD'; active?: boolean; expiresAt?: string },
  ) =>
    request<CareAuthorization>(`/families/${familyId}/members/${subjectUserId}/caregivers/${caregiverUserId}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  updateMemberRole: (familyId: number, userId: number, role: FamilyMember['role']) =>
    request<FamilyMember>(`/families/${familyId}/members/${userId}/role?role=${encodeURIComponent(role)}`, {
      method: 'PUT',
    }),
  transferOwner: (familyId: number, targetUserId: number) =>
    request<void>(`/families/${familyId}/owner/${targetUserId}`, {
      method: 'PUT',
    }),
};

export const diaryApi = {
  create: (data: CreateDiaryEntryRequest) =>
    request<DiaryEntry>('/diaries', { method: 'POST', body: JSON.stringify(data) }),
  updateEntry: (id: number, data: UpdateDiaryEntryRequest) =>
    request<DiaryEntry>(`/diaries/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  listFamilyEntries: (familyId: number, limit = 30) =>
    request<DiaryEntry[]>(`/diaries/family/${familyId}?limit=${limit}`),
  searchFamilyEntries: (params: {
    familyId: number;
    targetUserId?: number;
    keyword?: string;
    page?: number;
    pageSize?: number;
  }) => {
    const sp = new URLSearchParams();
    if (params.targetUserId) sp.set('targetUserId', String(params.targetUserId));
    if (params.keyword?.trim()) sp.set('keyword', params.keyword.trim());
    if (params.page) sp.set('page', String(params.page));
    if (params.pageSize) sp.set('pageSize', String(params.pageSize));
    return request<PageResult<DiaryEntry>>(`/diaries/family/${params.familyId}/search?${sp}`);
  },
  deleteEntry: (id: number) => request<void>(`/diaries/${id}`, { method: 'DELETE' }),
};

// ============================================
// Question bank
// ============================================

// ============================================
// Assessments
// ============================================
// ============================================
// Sessions
// ============================================
export const sessionApi = {
  createSession: (data: {
    familyId?: number; subject?: string;
    title?: string; summary?: string; messages?: ChatMessage[]; visibility?: string; source?: string; metadata?: Record<string, unknown>;
  }) => request<ChatSessionDetail>('/sessions', {
    method: 'POST',
    body: JSON.stringify({
      ...data,
      messages: (data.messages || []).map(toSessionMessagePayload),
    }),
  }).then(normalizeSessionDetail),
  getSession: (id: number) => request<ChatSessionDetail>(`/sessions/${id}`).then(normalizeSessionDetail),
  getUserSessions: (_userId?: number, limit = 20) =>
    request<ChatSessionSummary[]>(`/sessions/user/me?limit=${limit}`).then(normalizeSessionSummaries),
  getActiveSessions: (_userId?: number) => request<ChatSessionSummary[]>('/sessions/active/me').then(normalizeSessionSummaries),
  getSessionMessages: (id: number, beforeSeq?: number, limit = 40) => {
    const params = new URLSearchParams();
    if (beforeSeq != null) params.set('beforeSeq', String(beforeSeq));
    params.set('limit', String(limit));
    return request<ChatSessionMessagePage>(`/sessions/${id}/messages?${params}`).then(normalizeSessionMessagePage);
  },
  appendMessages: (id: number, messages: ChatMessage[]) =>
    request<ChatSessionDetail>(`/sessions/${id}/messages`, {
      method: 'POST',
      body: JSON.stringify({ messages: messages.map(toSessionMessagePayload) }),
    }).then(normalizeSessionDetail),
  updateMessages: (id: number, messages: ChatMessage[]) =>
    request<ChatSessionDetail>(`/sessions/${id}/messages`, {
      method: 'PUT',
      body: JSON.stringify({ messages: messages.map(toSessionMessagePayload) }),
    }).then(normalizeSessionDetail),
  endSession: (id: number, summary?: string) =>
    request<ChatSessionDetail>(`/sessions/${id}/end`, {
      method: 'POST',
      body: JSON.stringify({ summary }),
    }).then(normalizeSessionDetail),
  getSessionArchives: (id: number) =>
    request<ChatSessionArchiveSummary[]>(`/sessions/${id}/archives`).then((items) => (items || []).map(normalizeSessionArchiveSummary)),
  getSessionArchive: (id: number, archiveId: number) =>
    request<ChatSessionArchiveDetail>(`/sessions/${id}/archives/${archiveId}`).then(normalizeSessionArchiveDetail),
  deleteSession: (id: number) => request<void>(`/sessions/${id}`, { method: 'DELETE' }),
};

export const memoryApi = {
  listMyMemories: (limit = 20) => request<MemoryEntry[]>(`/memories/me?limit=${limit}`),
  listFamilyMemories: (familyId: number, limit = 30) =>
    request<MemoryEntry[]>(`/memories/family/${familyId}?limit=${limit}`),
  searchFamilyMemories: (params: {
    familyId: number;
    targetUserId?: number;
    keyword?: string;
    page?: number;
    pageSize?: number;
  }) => {
    const sp = new URLSearchParams();
    if (params.targetUserId) sp.set('targetUserId', String(params.targetUserId));
    if (params.keyword?.trim()) sp.set('keyword', params.keyword.trim());
    if (params.page) sp.set('page', String(params.page));
    if (params.pageSize) sp.set('pageSize', String(params.pageSize));
    return request<PageResult<MemoryEntry>>(`/memories/family/${params.familyId}/search?${sp}`);
  },
  createFamilyMemory: (data: CreateFamilyMemoryRequest) =>
    request<MemoryEntry>('/memories/family', { method: 'POST', body: JSON.stringify(data) }),
  voteFamilyMemory: (memoryId: number, voteType: MemoryVoteType) =>
    request<MemoryEntry>(`/memories/family/${memoryId}/vote`, {
      method: 'POST',
      body: JSON.stringify({ voteType }),
    }),
  createFamilyMemoryCard: (body: {
    content: string;
    memoryType?: MemoryEntryType;
    familyContext?: string;
    target?: string;
  }) =>
    aiRequest<{ success: boolean; data: FamilyMemoryCard }>('/memory/family-card', {
      content: body.content,
      memory_type: body.memoryType || 'ELDER_ADVICE',
      family_context: body.familyContext || '',
      target: body.target || '',
    }),
  createHeritageClassicalDraft: (body: {
    content: string;
    memoryType?: MemoryEntryType;
    scenario?: string;
    familyContext?: string;
  }) =>
    aiRequest<{ success: boolean; data: HeritageClassicalDraft }>('/memory/heritage-classical', {
      content: body.content,
      memory_type: body.memoryType || 'ELDER_ADVICE',
      scenario: body.scenario || '',
      family_context: body.familyContext || '',
    }),
  planSaveTool: (body: {
    message: string;
    familyContext?: string;
    conversationContext?: ChatMessage[];
    targetMemberName?: string;
    viewerRole?: string;
  }) =>
    aiRequest<{ success: boolean; data: AgentSaveToolPlan }>('/memory/save-plan', {
      message: body.message,
      family_context: body.familyContext || '',
      conversation_context: body.conversationContext || [],
      target_member_name: body.targetMemberName || '',
      viewer_role: body.viewerRole || '',
    }),
  organizeDraft: (body: {
    content: string;
    scene: AgentDraftScene;
    familyContext?: string;
    currentType?: string;
    currentVisibility?: string;
    target?: string;
  }) =>
    aiRequest<{ success: boolean; data: AgentOrganizedDraft }>('/memory/organize-draft', {
      content: body.content,
      scene: body.scene,
      family_context: body.familyContext || '',
      current_type: body.currentType || '',
      current_visibility: body.currentVisibility || '',
      target: body.target || '',
    }),
  judgeHeritageSave: (body: {
    content: string;
    memoryType?: string;
    scenario?: string;
    familyContext?: string;
    sourceMode?: string;
  }) =>
    aiRequest<{ success: boolean; data: HeritageSaveJudge }>('/memory/heritage-save-judge', {
      content: body.content,
      memory_type: body.memoryType || 'ELDER_ADVICE',
      scenario: body.scenario || '',
      family_context: body.familyContext || '',
      source_mode: body.sourceMode || '',
    }),
  familyWeeklyDigest: (body: {
    familyName?: string;
    diaries: DiaryEntry[];
    memories: MemoryEntry[];
    growthRecords: GrowthGuardRecord[];
    target?: string;
  }) =>
    aiRequest<{ success: boolean; data: FamilyWeeklyDigest }>('/memory/family-weekly-digest', {
      family_name: body.familyName || '',
      diaries: body.diaries,
      memories: body.memories,
      growth_records: body.growthRecords,
      target: body.target || '',
    }),
  heritageTaskDraft: (body: {
    content: string;
    summary?: string;
    memoryType?: string;
    scenario?: string;
    familyContext?: string;
    existingActions?: string[];
  }) =>
    aiRequest<{ success: boolean; data: HeritageTaskDraft }>('/memory/heritage-task-draft', {
      content: body.content,
      summary: body.summary || '',
      memory_type: body.memoryType || 'ELDER_ADVICE',
      scenario: body.scenario || '',
      family_context: body.familyContext || '',
      existing_actions: body.existingActions || [],
    }),
  recall: (body: { query?: string; subject?: string; limit?: number }) =>
    request<MemoryEntry[]>('/memories/recall', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  recallFamily: (familyId: number, body: { query?: string; scene?: string; limit?: number; diaryLimit?: number; memoryLimit?: number }) =>
    request<AuthorizedMemoryRecallResult>(`/memories/family/${familyId}/recall`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  rebuildFamilyIndexes: (familyId: number, limit = 500) =>
    request<RebuildMemoryIndexResult>(`/memories/family/${familyId}/indexes/rebuild?limit=${limit}`, {
      method: 'POST',
    }),
  rebuildFamilyEmbeddings: (familyId: number, limit = 200) =>
    request<RebuildMemoryIndexResult>(`/memories/family/${familyId}/embeddings/rebuild?limit=${limit}`, {
      method: 'POST',
    }),
  deleteMemory: (id: number) => request<void>(`/memories/${id}`, { method: 'DELETE' }),
};

export const memoryLibraryApi = {
  search: (params: {
    familyId: number;
    page?: number;
    pageSize?: number;
    keyword?: string;
    type?: MemoryLibraryItemType | 'ALL';
    memberUserId?: number;
    visibility?: string;
  }) => {
    const sp = new URLSearchParams();
    sp.set('familyId', String(params.familyId));
    if (params.page) sp.set('page', String(params.page));
    if (params.pageSize) sp.set('pageSize', String(params.pageSize));
    if (params.keyword?.trim()) sp.set('keyword', params.keyword.trim());
    if (params.type && params.type !== 'ALL') sp.set('type', params.type);
    if (params.memberUserId) sp.set('memberUserId', String(params.memberUserId));
    if (params.visibility && params.visibility !== 'ALL') sp.set('visibility', params.visibility);
    return request<PageResult<MemoryLibraryItem>>(`/memory-library/search?${sp}`);
  },
  maintenanceSuggestions: (familyId: number) =>
    request<MemoryMaintenanceSuggestion[]>(`/memory-library/maintenance-suggestions?familyId=${familyId}`),
  classicalizeItem: (
    familyId: number,
    itemId: string,
    classicalText: string,
    plainSummary: string,
    styleNote: string,
  ) =>
    request<void>('/memory-library/classicalize', {
      method: 'POST',
      body: JSON.stringify({ familyId, itemId, classicalText, plainSummary, styleNote }),
    }),
  mergeItems: (familyId: number, primaryItemId: string, secondaryItemId: string) =>
    request<void>('/memory-library/merge', {
      method: 'POST',
      body: JSON.stringify({ familyId, primaryItemId, secondaryItemId }),
    }),
  archived: (params: {
    familyId: number;
    page?: number;
    pageSize?: number;
    keyword?: string;
    type?: MemoryLibraryItemType | 'ALL';
    memberUserId?: number;
    visibility?: string;
  }) => {
    const sp = new URLSearchParams();
    sp.set('familyId', String(params.familyId));
    if (params.page) sp.set('page', String(params.page));
    if (params.pageSize) sp.set('pageSize', String(params.pageSize));
    if (params.keyword?.trim()) sp.set('keyword', params.keyword.trim());
    if (params.type && params.type !== 'ALL') sp.set('type', params.type);
    if (params.memberUserId) sp.set('memberUserId', String(params.memberUserId));
    if (params.visibility && params.visibility !== 'ALL') sp.set('visibility', params.visibility);
    return request<PageResult<MemoryLibraryItem>>(`/memory-library/archived?${sp}`);
  },
  archiveItem: (familyId: number, itemId: string) => {
    const sp = new URLSearchParams();
    sp.set('familyId', String(familyId));
    sp.set('itemId', itemId);
    return request<void>(`/memory-library/archive?${sp}`, { method: 'POST' });
  },
  restoreItem: (familyId: number, itemId: string) => {
    const sp = new URLSearchParams();
    sp.set('familyId', String(familyId));
    sp.set('itemId', itemId);
    return request<void>(`/memory-library/restore?${sp}`, { method: 'POST' });
  },
  deleteArchivedItem: (familyId: number, itemId: string) => {
    const sp = new URLSearchParams();
    sp.set('familyId', String(familyId));
    sp.set('itemId', itemId);
    return request<void>(`/memory-library/archived?${sp}`, { method: 'DELETE' });
  },
};

export const adminApi = {
  getDatabaseHealth: () => request<DatabaseHealthResponse>('/admin/database/health'),
  listUsers: (params?: { keyword?: string; page?: number; pageSize?: number }) => {
    const sp = new URLSearchParams();
    if (params?.keyword?.trim()) sp.set('keyword', params.keyword.trim());
    if (params?.page) sp.set('page', String(params.page));
    if (params?.pageSize) sp.set('pageSize', String(params.pageSize));
    return request<PageResult<AdminUserSummary>>(`/admin/database/users?${sp}`);
  },
  listFamilies: (params?: { keyword?: string; page?: number; pageSize?: number }) => {
    const sp = new URLSearchParams();
    if (params?.keyword?.trim()) sp.set('keyword', params.keyword.trim());
    if (params?.page) sp.set('page', String(params.page));
    if (params?.pageSize) sp.set('pageSize', String(params.pageSize));
    return request<PageResult<FamilyDatabaseSummary>>(`/admin/database/families?${sp}`);
  },
  listFamilyMembers: (familyId: number) =>
    request<FamilyMember[]>(`/admin/database/families/${familyId}/members`).then(normalizeFamilyMembers),
  deleteUser: (userId: number) =>
    request<void>(`/admin/database/users/${userId}`, {
      method: 'DELETE',
    }),
  runMemoryRecallDiagnostic: (data: MemoryRecallDiagnosticRequest) =>
    request<MemoryRecallDiagnosticResponse>('/admin/database/memory-recall-diagnostic', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
};

export const heritageTaskApi = {
  listFamilyTasks: (familyId: number, limit = 20) =>
    request<HeritageTask[]>(`/heritage-tasks/family/${familyId}?limit=${limit}`),
  create: (data: CreateHeritageTaskRequest) =>
    request<HeritageTask>('/heritage-tasks', { method: 'POST', body: JSON.stringify(data) }),
  complete: (id: number, completionNote: string) =>
    request<HeritageTask>(`/heritage-tasks/${id}/complete`, {
      method: 'PATCH',
      body: JSON.stringify({ completionNote }),
    }),
};

export const growthGuardApi = {
  listFamilyRecords: (familyId: number, limit = 30) =>
    request<GrowthGuardRecord[]>(`/growth-guards/family/${familyId}?limit=${limit}`),
  searchFamilyRecords: (params: {
    familyId: number;
    targetUserId?: number;
    keyword?: string;
    page?: number;
    pageSize?: number;
  }) => {
    const sp = new URLSearchParams();
    if (params.targetUserId) sp.set('targetUserId', String(params.targetUserId));
    if (params.keyword?.trim()) sp.set('keyword', params.keyword.trim());
    if (params.page) sp.set('page', String(params.page));
    if (params.pageSize) sp.set('pageSize', String(params.pageSize));
    return request<PageResult<GrowthGuardRecord>>(`/growth-guards/family/${params.familyId}/search?${sp}`);
  },
  createRecord: (data: CreateGrowthGuardRecordRequest) =>
    request<GrowthGuardRecord>('/growth-guards', { method: 'POST', body: JSON.stringify(data) }),
  updateFollowUpStatus: (id: number, followUpStatus: GrowthFollowUpStatus) =>
    request<GrowthGuardRecord>(`/growth-guards/${id}/follow-up-status`, {
      method: 'PATCH',
      body: JSON.stringify({ followUpStatus }),
    }),
  markStale: (id: number) =>
    request<GrowthGuardRecord>(`/growth-guards/${id}/stale`, { method: 'POST' }),
  deleteRecord: (id: number) => request<void>(`/growth-guards/${id}`, { method: 'DELETE' }),
  listFamilyReports: (familyId: number, limit = 5) =>
    request<GrowthGuardReport[]>(`/growth-guards/reports/family/${familyId}?limit=${limit}`)
      .then((items) => (items || []).map(normalizeGrowthGuardReport)),
  createReport: (data: CreateGrowthGuardReportRequest) =>
    request<GrowthGuardReport>('/growth-guards/reports', { method: 'POST', body: JSON.stringify(data) })
      .then(normalizeGrowthGuardReport),
  weeklyReport: (body: {
    familyName?: string;
    records: GrowthGuardRecord[];
    memories: MemoryEntry[];
    target?: string;
  }) =>
    aiRequest<{ success: boolean; data: WeeklyGrowthReport }>('/growth/weekly-report', {
      family_name: body.familyName || '',
      records: body.records,
      memories: body.memories,
      target: body.target || '',
    }),
};

export const skillRunApi = {
  create: (data: CreateSkillRunRequest) =>
    request<SkillRun>('/skill-runs', { method: 'POST', body: JSON.stringify(data) }),
  update: (id: number, data: UpdateSkillRunRequest) =>
    request<SkillRun>(`/skill-runs/${id}`, { method: 'PATCH', body: JSON.stringify(data) }),
  get: (id: number) => request<SkillRun>(`/skill-runs/${id}`),
  listFamilyRuns: (familyId: number, limit = 30) =>
    request<SkillRun[]>(`/skill-runs/family/${familyId}?limit=${limit}`),
};

export const mirrorApi = {
  getContext: (familyId: number, targetUserId: number, query?: string) => {
    const params = query?.trim() ? `?query=${encodeURIComponent(query.trim())}` : '';
    return request<MirrorContextResponse>(`/mirror/families/${familyId}/members/${targetUserId}/context${params}`);
  },
};

// ============================================
// Family agent calls sent to the Python AI service.
// ============================================
export const agentApi = {
  streamChat: (
    body: {
      message: string;
      history?: { role: string; content: string }[];
      subject?: string;
      contextLabel?: string;
      memoryContext?: string;
      viewerRole?: ViewerRole;
      targetRole?: ViewerRole;
      clientTimestamp?: string;
      clientTimezone?: string;
    },
    onChunk: (chunk: string) => void,
    onDone: () => void,
    onError: (error: string) => void,
    onMetadata?: (metadata: Record<string, unknown>) => void,
    onAbort?: () => void,
  ) => sseStreamRequest('/agent/chat/stream', {
    member_message: body.message,
    history: body.history || [],
    subject: body.subject || 'FamilyAgent',
    knowledge_point: body.contextLabel || '',
    memory_context: body.memoryContext || '',
    viewer_role: body.viewerRole || 'MEMBER',
    target_role: body.targetRole || 'MEMBER',
    client_timestamp: body.clientTimestamp || new Date().toISOString(),
    client_timezone: body.clientTimezone || Intl.DateTimeFormat().resolvedOptions().timeZone || '',
  }, onChunk, onDone, onError, onMetadata, onAbort),
};

export { ApiError };
