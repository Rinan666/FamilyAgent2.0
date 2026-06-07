/**
 * API 客户端
 *
 * - Java 业务 API 通过 Next.js 代理 (/api/* → backend)
 * - Python AI API 直连 AI 服务（避免 Next.js 代理 POST body 丢失）
 *
 * 环境变量：
 *   NEXT_PUBLIC_API_URL — 后端地址 (默认 /api)
 *   NEXT_PUBLIC_AI_SERVICE_URL — AI 服务地址 (默认 http://localhost:8000)
 */
import type {
  ApiResult,
  LoginRequest, LoginResponse, RegisterRequest, ChangePasswordRequest,
  User, Family, FamilyMember, FamilyRelationship, CareAuthorization, DiaryEntry, CreateDiaryEntryRequest, UpdateDiaryEntryRequest,
  Question, KnowledgePoint, QuestionAnswer, QuestionContent,
  AbilityProfile, TestRecord, TestRecordDetail,
  ChatMessage, ChatSession, GradeResult, MemoryEntry, PageResult, SubmitTestRequest, CreateQuestionRequest,
  TutorExtractResult, CreateFamilyMemoryRequest, FamilyMemoryCard, MemoryEntryType,
  CreateGrowthGuardRecordRequest, GrowthGuardRecord, CreateGrowthGuardReportRequest, GrowthGuardReport, WeeklyGrowthReport,
  GrowthFollowUpStatus, MirrorContextResponse, MistakeReviewResult, DailyPracticeResult, ExamReviewResult, StudyPlanResult,
  AgentDraftScene, AgentOrganizedDraft, AgentSaveToolPlan,
} from '@/types';
import type { ViewerRole } from '@/lib/roles';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || '/api';
const AI_BASE = process.env.NEXT_PUBLIC_AI_SERVICE_URL || 'http://localhost:8000';

// ============================================
class ApiError extends Error {
  constructor(public code: number, message: string) {
    super(message);
    this.name = 'ApiError';
  }
}

async function request<T>(url: string, options: RequestInit = {}): Promise<T> {
  const token = typeof window !== 'undefined' ? localStorage.getItem('token') : null;
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = token;

  const res = await fetch(`${API_BASE}${url}`, { ...options, headers });
  const data: ApiResult<T> = await res.json();

  if (data.code !== 200) {
    if (data.code === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      if (typeof window !== 'undefined') window.location.href = '/login';
    }
    throw new ApiError(data.code, data.message);
  }
  return data.data as T;
}

function parseJsonField<T>(value: unknown, fallback: T): T {
  if (value == null) return fallback;
  if (typeof value === 'string') {
    try {
      return JSON.parse(value) as T;
    } catch {
      return fallback;
    }
  }
  if (typeof value === 'object') {
    const wrapped = value as { value?: unknown };
    if (typeof wrapped.value === 'string') {
      try {
        return JSON.parse(wrapped.value) as T;
      } catch {
        return fallback;
      }
    }
    return value as T;
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

function normalizeQuestionAnswer(rawAnswer: unknown, rawQuestion: Record<string, unknown>): QuestionAnswer {
  const parsed = parseJsonField<unknown>(rawAnswer, rawAnswer);
  const answerObject = parsed && typeof parsed === 'object' && !Array.isArray(parsed)
    ? parsed as Record<string, unknown>
    : {};

  const value = [
    answerObject.value,
    answerObject.answer,
    answerObject.final_answer,
    answerObject.finalAnswer,
    answerObject.standard_answer,
    answerObject.standardAnswer,
    answerObject.result,
    rawQuestion.answer_value,
    rawQuestion.answerValue,
    rawQuestion.final_answer,
    rawQuestion.finalAnswer,
    rawQuestion.standard_answer,
    rawQuestion.standardAnswer,
    rawQuestion.result,
    typeof parsed === 'string' ? parsed : undefined,
  ].map(toText).find(Boolean) || '';

  const steps = normalizeStringArray(answerObject.steps).length > 0
    ? normalizeStringArray(answerObject.steps)
    : normalizeStringArray(answerObject.solution_steps ?? rawQuestion.steps ?? rawQuestion.solution_steps ?? rawQuestion.solutionSteps);

  const explanation = [
    answerObject.explanation,
    answerObject.analysis,
    answerObject.solution,
    rawQuestion.explanation,
    rawQuestion.analysis,
    rawQuestion.solution,
  ].map(toText).find(Boolean);

  return { value, steps, explanation };
}

function normalizeQuestion(raw: Question): Question {
  const rawRecord = raw as unknown as Record<string, unknown>;
  const content = parseJsonField<QuestionContent>(raw.content, { stem: '' });
  const answer = normalizeQuestionAnswer(raw.answer, rawRecord);
  const tags = normalizeTags((raw as Question & { tags?: unknown }).tags);
  const kpId = Number(raw.kpId ?? rawRecord.kp_id ?? rawRecord.kpId);
  const stem = [
    content.stem,
    rawRecord.stem,
    rawRecord.question,
    rawRecord.title,
    raw.content,
  ].map(toText).find(Boolean) || '';

  return {
    ...raw,
    kpId: Number.isFinite(kpId) ? kpId : raw.kpId,
    content: {
      stem,
      options: Array.isArray(content.options) ? content.options : undefined,
      figures: Array.isArray(content.figures) ? content.figures : undefined,
    },
    answer: {
      value: answer.value || '',
      steps: Array.isArray(answer.steps) ? answer.steps : [],
      explanation: answer.explanation,
    },
    tags,
  };
}

function normalizeTags(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value.map(String).map((tag) => tag.trim()).filter(Boolean);
  }
  if (typeof value === 'string') {
    return value
      .replace(/^\{|\}$/g, '')
      .split(',')
      .map((tag) => tag.trim().replace(/^"|"$/g, ''))
      .filter(Boolean);
  }
  return [];
}

function normalizeQuestions(questions: Question[] | undefined | null): Question[] {
  return (questions || []).map(normalizeQuestion);
}

function normalizeSession(raw: ChatSession): ChatSession {
  return {
    ...raw,
    messages: parseJsonField<ChatMessage[]>(raw.messages, []),
  };
}

function normalizeSessions(sessions: ChatSession[] | undefined | null): ChatSession[] {
  return (sessions || []).map(normalizeSession);
}

function normalizeNumberArray(value: unknown): number[] {
  const parsed = parseJsonField<unknown>(value, []);
  if (!Array.isArray(parsed)) return [];
  return parsed
    .map((item) => Number(item))
    .filter((item) => Number.isFinite(item));
}

function normalizeStringRecord(value: unknown): Record<string, string> {
  const parsed = parseJsonField<unknown>(value, {});
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {};
  return Object.fromEntries(
    Object.entries(parsed as Record<string, unknown>).map(([key, item]) => [key, item == null ? '' : String(item)]),
  );
}

function normalizeNumberRecord(value: unknown): Record<string, number> {
  const parsed = parseJsonField<unknown>(value, {});
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {};
  return Object.fromEntries(
    Object.entries(parsed as Record<string, unknown>).map(([key, item]) => {
      const score = Number(item);
      return [key, Number.isFinite(score) ? score : 0];
    }),
  );
}

function normalizeTestRecord(raw: TestRecord): TestRecord {
  const totalScore = Number(raw.totalScore);
  const totalTime = raw.totalTime == null ? undefined : Number(raw.totalTime);

  return {
    ...raw,
    questionIds: normalizeNumberArray((raw as TestRecord & { questionIds?: unknown }).questionIds),
    answers: normalizeStringRecord((raw as TestRecord & { answers?: unknown }).answers),
    scores: normalizeNumberRecord((raw as TestRecord & { scores?: unknown }).scores),
    timeSpent: normalizeNumberArray((raw as TestRecord & { timeSpent?: unknown }).timeSpent),
    totalScore: Number.isFinite(totalScore) ? totalScore : 0,
    totalTime: Number.isFinite(totalTime) ? totalTime : undefined,
  };
}

function normalizeTestRecords(records: TestRecord[] | undefined | null): TestRecord[] {
  return (records || []).map(normalizeTestRecord);
}

function normalizeTestRecordDetail(raw: TestRecordDetail): TestRecordDetail {
  return {
    ...raw,
    record: normalizeTestRecord(raw.record),
    items: (raw.items || []).map((item) => ({
      ...item,
      question: item.question ? normalizeQuestion(item.question) : undefined,
      studentAnswer: item.studentAnswer == null ? '' : String(item.studentAnswer),
      score: safeNumber(item.score),
      correct: Boolean(item.correct),
      wrong: Boolean(item.wrong),
      errorType: item.errorType == null ? undefined : String(item.errorType),
      feedback: item.feedback == null ? undefined : String(item.feedback),
      parentExplanation: item.parentExplanation == null ? undefined : String(item.parentExplanation),
      nextSuggestion: item.nextSuggestion == null ? undefined : String(item.nextSuggestion),
    })),
  };
}

function safeNumber(value: unknown): number {
  const number = Number(value);
  return Number.isFinite(number) ? number : 0;
}

function normalizeWeeklyGrowthReport(value: unknown): WeeklyGrowthReport {
  const parsed = parseJsonField<Record<string, unknown>>(value, {});
  return {
    title: toText(parsed.title) || '本周成长提醒',
    summary: toText(parsed.summary),
    affirmations: normalizeStringArray(parsed.affirmations),
    concerns: normalizeStringArray(parsed.concerns),
    signals: normalizeStringArray(parsed.signals),
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
    throw new ApiError(res.status, `AI service error ${res.status}`);
  }
  const data = await res.json();
  if (!data.success) throw new ApiError(500, data.detail || 'AI error');
  return data as T;
}

// SSE 流式请求（直连 Python）
async function aiFileRequest<T>(path: string, file: File): Promise<T> {
  const token = typeof window !== 'undefined' ? localStorage.getItem('token') : null;
  const formData = new FormData();
  formData.append('file', file);

  const res = await fetch(`${AI_BASE}/ai${path}`, {
    method: 'POST',
    headers: {
      ...(token ? { Authorization: token } : {}),
    },
    body: formData,
  });

  const data = await res.json().catch(() => null);
  if (!res.ok) {
    const message = data?.detail || data?.message || `AI service error ${res.status}`;
    throw new ApiError(res.status, message);
  }

  if (!data.success) throw new ApiError(500, data.detail || 'AI error');
  return data as T;
}

async function sseRequest(
  path: string, body: unknown,
  onChunk: (chunk: string) => void,
  onDone: () => void,
  onError: (error: string) => void,
): Promise<void> {
  try {
    const token = typeof window !== 'undefined' ? localStorage.getItem('token') : null;
    const res = await fetch(`${AI_BASE}/ai${path}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json;charset=UTF-8',
        ...(token ? { Authorization: token } : {}),
      },
      body: JSON.stringify(body),
    });
    if (res.status === 401) {
      onError('AI服务认证失败，请尝试刷新页面后重试');
      return;
    }
    if (!res.ok) { onError(`HTTP ${res.status}`); return; }

    const reader = res.body?.getReader();
    if (!reader) { onError('No stream'); return; }

    const decoder = new TextDecoder();
    let buffer = '';
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';
      for (const line of lines) {
        if (line.startsWith('data: ')) {
          try {
            const p = JSON.parse(line.slice(6));
            if (p.done) { onDone(); return; }
            if (p.error) { onError(p.error); return; }
            if (p.content) onChunk(p.content);
          } catch { /* skip */ }
        }
      }
    }
    onDone();
  } catch (e) {
    onError(`${e}`);
  }
}

// ============================================
// 用户
// ============================================
export const userApi = {
  register: (data: RegisterRequest) => request<User>('/users/register', { method: 'POST', body: JSON.stringify(data) }),
  login: (data: LoginRequest) => request<LoginResponse>('/users/login', { method: 'POST', body: JSON.stringify(data) }),
  getMe: () => request<User>('/users/me'),
  changePassword: (data: ChangePasswordRequest) =>
    request<void>('/users/change-password', { method: 'POST', body: JSON.stringify(data) }),
  getUser: (id: number) => request<User>(`/users/${id}`),
};

// ============================================
// 家族
// ============================================
export const familyApi = {
  create: (data: { name: string; description?: string }) =>
    request<Family>('/families', { method: 'POST', body: JSON.stringify(data) }),
  join: (inviteCode: string) =>
    request<FamilyMember>(`/families/join?inviteCode=${inviteCode}`, { method: 'POST' }),
  getMyFamilies: () => request<Family[]>('/families/my'),
  getFamily: (id: number) => request<Family>(`/families/${id}`),
  getMembers: (familyId: number) => request<FamilyMember[]>(`/families/${familyId}/members`),
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
};

export const diaryApi = {
  create: (data: CreateDiaryEntryRequest) =>
    request<DiaryEntry>('/diaries', { method: 'POST', body: JSON.stringify(data) }),
  updateEntry: (id: number, data: UpdateDiaryEntryRequest) =>
    request<DiaryEntry>(`/diaries/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  listFamilyEntries: (familyId: number, limit = 30) =>
    request<DiaryEntry[]>(`/diaries/family/${familyId}?limit=${limit}`),
  deleteEntry: (id: number) => request<void>(`/diaries/${id}`, { method: 'DELETE' }),
};

// ============================================
// 题库
// ============================================
export const questionApi = {
  getKnowledgeTree: () => request<KnowledgePoint[]>('/questions/knowledge-points/tree'),
  getChildren: (parentId: number) => request<KnowledgePoint[]>(`/questions/knowledge-points/${parentId}/children`),
  getKnowledgePoint: (id: number) => request<KnowledgePoint>(`/questions/knowledge-points/${id}`),
  getQuestion: (id: number) => request<Question>(`/questions/${id}`).then(normalizeQuestion),
  listQuestions: async (params: {
    page?: number; size?: number; subject?: string; grade?: string; kpId?: number; kpIds?: number[];
    difficulty?: number; type?: string; tag?: string;
  }) => {
    const sp = new URLSearchParams();
    Object.entries(params).forEach(([k, v]) => {
      if (v === undefined) return;
      if (Array.isArray(v)) {
        v.forEach((item) => sp.append(k, String(item)));
        return;
      }
      sp.set(k, String(v));
    });
    const page = await request<PageResult<Question>>(`/questions?${sp}`);
    return { ...page, items: normalizeQuestions(page.items) };
  },
  createQuestion: (data: CreateQuestionRequest) =>
    request<Question>('/questions', { method: 'POST', body: JSON.stringify(data) }).then(normalizeQuestion),
  batchCreateQuestions: (data: CreateQuestionRequest[]) =>
    request<Question[]>('/questions/batch', { method: 'POST', body: JSON.stringify(data) }).then(normalizeQuestions),
  deleteQuestion: (id: number) => request<void>(`/questions/${id}`, { method: 'DELETE' }),
  selectForTest: (params: { kpId?: number; subject?: string; difficulty?: number; type?: string; limit?: number }) => {
    const sp = new URLSearchParams();
    Object.entries(params).forEach(([k, v]) => { if (v !== undefined) sp.set(k, String(v)); });
    return request<Question[]>(`/questions/select?${sp}`, { method: 'POST' }).then(normalizeQuestions);
  },
  adaptiveSelect: (_userId?: number, limit = 5) =>
    request<Question[]>(`/questions/adaptive-select/me?limit=${limit}`, { method: 'POST' }).then(normalizeQuestions),
  getWrongQuestions: (_userId?: number, limit = 10) =>
    request<Question[]>(`/questions/wrong/me?limit=${limit}`).then(normalizeQuestions),
};

// ============================================
// 评估
// ============================================
export const assessmentApi = {
  getProfiles: (_userId?: number) => request<AbilityProfile[]>('/assessment/profiles/me'),
  getProfilesForUser: (userId: number) => request<AbilityProfile[]>(`/assessment/profiles/${userId}`),
  getZPD: (_userId?: number) => request<AbilityProfile[]>('/assessment/zpd/me'),
  getZPDForUser: (userId: number) => request<AbilityProfile[]>(`/assessment/zpd/${userId}`),
  getHistory: (_userId?: number, limit = 20) =>
    request<TestRecord[]>(`/assessment/history/me?limit=${limit}`).then(normalizeTestRecords),
  getHistoryForUser: (userId: number, limit = 20) =>
    request<TestRecord[]>(`/assessment/history/${userId}?limit=${limit}`).then(normalizeTestRecords),
  getTestDetail: (id: number) =>
    request<TestRecordDetail>(`/assessment/tests/${id}/detail`).then(normalizeTestRecordDetail),
  getTestDetailForUser: (userId: number, id: number) =>
    request<TestRecordDetail>(`/assessment/users/${userId}/tests/${id}/detail`).then(normalizeTestRecordDetail),
  submitTest: (data: SubmitTestRequest) =>
    request<TestRecord>('/assessment/tests', { method: 'POST', body: JSON.stringify(data) }).then(normalizeTestRecord),
};

// ============================================
// 会话
// ============================================
export const sessionApi = {
  createSession: (data: {
    familyId?: number; questionId?: number; subject?: string; knowledgePointId?: number;
    messages?: ChatMessage[]; visibility?: string; source?: string; metadata?: Record<string, unknown>;
  }) => request<ChatSession>('/sessions', { method: 'POST', body: JSON.stringify(data) }).then(normalizeSession),
  getSession: (id: number) => request<ChatSession>(`/sessions/${id}`).then(normalizeSession),
  getUserSessions: (_userId?: number, limit = 20) =>
    request<ChatSession[]>(`/sessions/user/me?limit=${limit}`).then(normalizeSessions),
  getActiveSessions: (_userId?: number) => request<ChatSession[]>('/sessions/active/me').then(normalizeSessions),
  updateMessages: (id: number, messages: ChatMessage[]) =>
    request<ChatSession>(`/sessions/${id}/messages`, {
      method: 'PUT',
      body: JSON.stringify({ messages }),
    }).then(normalizeSession),
  endSession: (id: number, summary?: string) =>
    request<ChatSession>(`/sessions/${id}/end`, {
      method: 'POST',
      body: JSON.stringify({ summary }),
    }).then(normalizeSession),
  deleteSession: (id: number) => request<void>(`/sessions/${id}`, { method: 'DELETE' }),
};

export const memoryApi = {
  listMyMemories: (limit = 20) => request<MemoryEntry[]>(`/memories/me?limit=${limit}`),
  listFamilyMemories: (familyId: number, limit = 30) =>
    request<MemoryEntry[]>(`/memories/family/${familyId}?limit=${limit}`),
  createFamilyMemory: (data: CreateFamilyMemoryRequest) =>
    request<MemoryEntry>('/memories/family', { method: 'POST', body: JSON.stringify(data) }),
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
  planSaveTool: (body: {
    message: string;
    familyContext?: string;
    targetMemberName?: string;
    viewerRole?: string;
  }) =>
    aiRequest<{ success: boolean; data: AgentSaveToolPlan }>('/memory/save-plan', {
      message: body.message,
      family_context: body.familyContext || '',
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
  recall: (body: { query?: string; subject?: string; knowledgePointId?: number; limit?: number }) =>
    request<MemoryEntry[]>('/memories/recall', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  deleteMemory: (id: number) => request<void>(`/memories/${id}`, { method: 'DELETE' }),
};

export const growthGuardApi = {
  listFamilyRecords: (familyId: number, limit = 30) =>
    request<GrowthGuardRecord[]>(`/growth-guards/family/${familyId}?limit=${limit}`),
  createRecord: (data: CreateGrowthGuardRecordRequest) =>
    request<GrowthGuardRecord>('/growth-guards', { method: 'POST', body: JSON.stringify(data) }),
  updateFollowUpStatus: (id: number, followUpStatus: GrowthFollowUpStatus) =>
    request<GrowthGuardRecord>(`/growth-guards/${id}/follow-up-status`, {
      method: 'PATCH',
      body: JSON.stringify({ followUpStatus }),
    }),
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

export const mirrorApi = {
  getContext: (familyId: number, targetUserId: number, query?: string) => {
    const params = query?.trim() ? `?query=${encodeURIComponent(query.trim())}` : '';
    return request<MirrorContextResponse>(`/mirror/families/${familyId}/members/${targetUserId}/context${params}`);
  },
};

// ============================================
// 家庭陪伴 AI（直连 Python AI 服务）
// ============================================
export const tutorApi = {
  extractContent: (file: File) =>
    aiFileRequest<{
      success: boolean;
      data: {
        filename: string;
        source_type: string;
        content_type: string;
        text: string;
        structured_text?: string;
        detected_questions?: string[];
        detected_answers?: string[];
        detected_steps?: string[];
        supported: boolean;
        message: string;
      };
    }>('/tutor/extract', file).then((result) => ({
      success: result.success,
      data: {
        filename: result.data.filename,
        sourceType: result.data.source_type,
        contentType: result.data.content_type,
        text: result.data.text,
        structuredText: result.data.structured_text || result.data.text,
        detectedQuestions: Array.isArray(result.data.detected_questions) ? result.data.detected_questions : [],
        detectedAnswers: Array.isArray(result.data.detected_answers) ? result.data.detected_answers : [],
        detectedSteps: Array.isArray(result.data.detected_steps) ? result.data.detected_steps : [],
        supported: result.data.supported,
        message: result.data.message,
      } satisfies TutorExtractResult,
    })),

  explainStream: (
    body: { questionContent: string; answer: string; steps: string; studentMessage: string;
            history?: { role: string; content: string }[]; grade?: string; subject?: string;
            knowledgePoint?: string; masteryLevel?: string; teachingStyle?: 'guided' | 'direct';
            mode?: 'explain' | 'chat'; memoryContext?: string;
            viewerRole?: ViewerRole; targetRole?: ViewerRole | 'STUDENT'; },
    onChunk: (chunk: string) => void, onDone: () => void, onError: (error: string) => void,
  ) => sseRequest('/tutor/explain', {
    question_content: body.questionContent,
    answer: body.answer,
    steps: body.steps,
    student_message: body.studentMessage,
    history: body.history,
    grade: body.grade || '',
    subject: body.subject || '数学',
    knowledge_point: body.knowledgePoint || '',
    mastery_level: body.masteryLevel || '中',
    teaching_style: body.teachingStyle || 'guided',
    mode: body.mode || 'explain',
    memory_context: body.memoryContext || '',
    viewer_role: body.viewerRole || 'STUDENT',
    target_role: body.targetRole || 'STUDENT',
  }, onChunk, onDone, onError),

  grade: (body: { questionContent: string; answer: string; steps: string;
                   studentAnswer: string; subject?: string; grade?: string; }) =>
    aiRequest<{ success: boolean; data: GradeResult }>('/tutor/grade', {
      question_content: body.questionContent,
      answer: body.answer,
      steps: body.steps,
      student_answer: body.studentAnswer,
      subject: body.subject || '数学',
      grade: body.grade || '初中',
    }),

  quickGrade: (body: { questionContent: string; answer: string; steps?: string;
                        studentAnswer: string; subject?: string; grade?: string; }) =>
    aiRequest<{ success: boolean; data: GradeResult }>('/tutor/grade/quick', {
      question_content: body.questionContent,
      answer: body.answer,
      steps: body.steps || '',
      student_answer: body.studentAnswer,
      subject: body.subject || '数学',
      grade: body.grade || '',
    }),

  generateQuestions: (body: { subject: string; grade: string; knowledgePoint: string;
                               questionType?: string; difficulty?: number; count?: number;
                               additionalRequirements?: string; }) =>
    aiRequest<{ success: boolean; questions: Question[]; count: number }>('/tutor/generate', {
      subject: body.subject,
      grade: body.grade,
      knowledge_point: body.knowledgePoint,
      question_type: body.questionType || 'CALCULATION',
      difficulty: body.difficulty || 3,
      count: body.count || 5,
      additional_requirements: body.additionalRequirements || '',
    }).then((result) => ({ ...result, questions: normalizeQuestions(result.questions) })),

  mistakeReview: (body: {
    questionContent: string; answer: string; studentAnswer: string; steps?: string;
    gradeResult?: Record<string, unknown>; grade?: string; subject?: string;
    knowledgePoint?: string; weakPoints?: string[];
  }) =>
    aiRequest<{ success: boolean; skill: 'mistake_review'; data: MistakeReviewResult }>('/tutor/skills/mistake-review', {
      question_content: body.questionContent,
      answer: body.answer,
      student_answer: body.studentAnswer,
      steps: body.steps || '',
      grade_result: body.gradeResult || null,
      grade: body.grade || '初中',
      subject: body.subject || '数学',
      knowledge_point: body.knowledgePoint || '未知',
      weak_points: body.weakPoints || [],
    }),

  dailyPractice: (body: {
    knowledgePoint: string; grade?: string; subject?: string; masteryLevel?: string;
    availableMinutes?: number; difficulty?: string; questionCount?: number;
    weakPoints?: string[]; scenario?: string;
  }) =>
    aiRequest<{ success: boolean; skill: 'daily_practice'; data: DailyPracticeResult }>('/tutor/skills/daily-practice', {
      knowledge_point: body.knowledgePoint,
      grade: body.grade || '初中',
      subject: body.subject || '数学',
      mastery_level: body.masteryLevel || '中',
      available_minutes: body.availableMinutes || 15,
      difficulty: body.difficulty || '标准',
      question_count: body.questionCount || 5,
      weak_points: body.weakPoints || [],
      scenario: body.scenario || '学生自练',
    }),

  examReview: (body: {
    examGoal?: string; scoreSummary: string; grade?: string; subject?: string;
    profiles?: Record<string, unknown>; weakPoints?: string[];
    recentMistakes?: Record<string, unknown>[]; availableMinutes?: number; reviewDays?: number;
  }) =>
    aiRequest<{ success: boolean; skill: 'exam_review'; data: ExamReviewResult }>('/tutor/skills/exam-review', {
      exam_goal: body.examGoal || '阶段测评提升',
      score_summary: body.scoreSummary,
      grade: body.grade || '初中',
      subject: body.subject || '数学',
      profiles: body.profiles || {},
      weak_points: body.weakPoints || [],
      recent_mistakes: body.recentMistakes || [],
      available_minutes: body.availableMinutes || 30,
      review_days: body.reviewDays || 7,
    }),

  studyPlan: (body: {
    learningGoal: string; grade?: string; subject?: string;
    profiles?: Record<string, unknown>; weakPoints?: string[];
    availableMinutes?: number; planDays?: number; constraints?: string;
  }) =>
    aiRequest<{ success: boolean; skill: 'study_plan'; data: StudyPlanResult }>('/tutor/skills/study-plan', {
      learning_goal: body.learningGoal,
      grade: body.grade || '初中',
      subject: body.subject || '数学',
      profiles: body.profiles || {},
      weak_points: body.weakPoints || [],
      available_minutes: body.availableMinutes || 30,
      plan_days: body.planDays || 7,
      constraints: body.constraints || '无',
    }),

  mathVerify: (expression?: string, expected?: string, studentAnswer?: string) => {
    const params = new URLSearchParams();
    if (expression) params.set('expression', expression);
    if (expected) params.set('expected', expected);
    if (studentAnswer) params.set('student_answer', studentAnswer);
    return fetch(`${AI_BASE}/ai/tutor/math/verify?${params}`).then(r => r.json());
  },
};

export { ApiError };
