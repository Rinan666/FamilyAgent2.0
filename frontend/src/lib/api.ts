/**
 * API 客户端
 *
 * - Java 业务 API 通过 Next.js 代理 (/api/* → backend)
 * - Python AI API 通过 Next.js 代理 (/ai-proxy/* → AI service)
 *
 * 环境变量：
 *   NEXT_PUBLIC_API_URL — 后端地址 (默认 /api)
 *   AI_SERVICE_URL — AI 服务地址 (默认 http://localhost:8000)
 */
import type {
  ApiResult,
  LoginRequest, LoginResponse, RegisterRequest, ChangePasswordRequest, UpdateProfileRequest,
  User, Family, FamilyMember, FamilyRelationship, CareAuthorization, DiaryEntry, CreateDiaryEntryRequest, UpdateDiaryEntryRequest,
  Question, KnowledgePoint, QuestionAnswer, QuestionContent,
  AbilityProfile, TestRecord, TestRecordDetail,
  ChatMessage, ChatSession, GradeResult, MemoryEntry, MemoryLibraryItem, MemoryLibraryItemType, MemoryMaintenanceSuggestion, PageResult, SubmitTestRequest, CreateQuestionRequest,
  TutorExtractResult, CreateFamilyMemoryRequest, FamilyMemoryCard, MemoryEntryType, MemoryVoteType,
  HeritageTask, CreateHeritageTaskRequest, HeritageTaskDraft,
  CreateGrowthGuardRecordRequest, GrowthGuardRecord, CreateGrowthGuardReportRequest, GrowthGuardReport, WeeklyGrowthReport,
  GrowthFollowUpStatus, MirrorContextResponse, MistakeReviewResult, DailyPracticeResult, ExamReviewResult, StudyPlanResult,
  AgentDraftScene, AgentOrganizedDraft, AgentSaveToolPlan, AuthorizedMemoryRecallResult, FamilyWeeklyDigest, RebuildMemoryIndexResult,
  DatabaseHealthResponse, MemoryRecallDiagnosticRequest, MemoryRecallDiagnosticResponse,
} from '@/types';
import type { ViewerRole } from '@/lib/roles';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || '/api';
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

function normalizeUser(raw: User): User {
  return {
    ...raw,
    metadata: parseJsonField<Record<string, unknown>>(raw.metadata, {}),
  };
}

function normalizeLoginResponse(raw: LoginResponse): LoginResponse {
  return {
    ...raw,
    metadata: parseJsonField<Record<string, unknown>>(raw.metadata, {}),
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

// 文件上传请求（同源代理到 Python AI 服务）
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

async function sseRequest(
  path: string, body: unknown,
  onChunk: (chunk: string) => void,
  onDone: () => void,
  onError: (error: string) => void,
  onMetadata?: (metadata: Record<string, unknown>) => void,
): Promise<void> {
  try {
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
      const detail = await readErrorDetail(res);
      onError(aiErrorMessage(res.status, detail, res.headers.get('Retry-After')));
      return;
    }

    const reader = res.body?.getReader();
    if (!reader) { onError('AI 服务没有返回可读取的响应，请稍后再试。'); return; }

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
            if (p.error) { onError(aiErrorMessage(500, p.error)); return; }
            if (p.metadata) onMetadata?.(p.metadata);
            if (p.content) onChunk(p.content);
          } catch { /* skip */ }
        }
      }
    }
    onDone();
  } catch (e) {
    onError(e instanceof Error ? e.message : 'AI 请求失败，请稍后再试。');
  }
}

// ============================================
// 用户
// ============================================
export const userApi = {
  register: (data: RegisterRequest) => request<User>('/users/register', { method: 'POST', body: JSON.stringify(data) }).then(normalizeUser),
  login: (data: LoginRequest) => request<LoginResponse>('/users/login', { method: 'POST', body: JSON.stringify(data) }).then(normalizeLoginResponse),
  getMe: () => request<User>('/users/me').then(normalizeUser),
  updateProfile: (data: UpdateProfileRequest) =>
    request<User>('/users/me/profile', { method: 'POST', body: JSON.stringify(data) }).then(normalizeUser),
  changePassword: (data: ChangePasswordRequest) =>
    request<void>('/users/change-password', { method: 'POST', body: JSON.stringify(data) }),
  getUser: (id: number) => request<User>(`/users/${id}`).then(normalizeUser),
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
  recall: (body: { query?: string; subject?: string; knowledgePointId?: number; limit?: number }) =>
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

export const mirrorApi = {
  getContext: (familyId: number, targetUserId: number, query?: string) => {
    const params = query?.trim() ? `?query=${encodeURIComponent(query.trim())}` : '';
    return request<MirrorContextResponse>(`/mirror/families/${familyId}/members/${targetUserId}/context${params}`);
  },
};

// ============================================
// 家族Agent（直连 Python AI 服务）
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
            viewerRole?: ViewerRole; targetRole?: ViewerRole | 'STUDENT';
            clientTimestamp?: string; clientTimezone?: string; },
    onChunk: (chunk: string) => void, onDone: () => void, onError: (error: string) => void,
    onMetadata?: (metadata: Record<string, unknown>) => void,
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
    client_timestamp: body.clientTimestamp || new Date().toISOString(),
    client_timezone: body.clientTimezone || Intl.DateTimeFormat().resolvedOptions().timeZone || '',
  }, onChunk, onDone, onError, onMetadata),

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
    return fetch(`/ai-proxy/tutor/math/verify?${params}`).then(r => r.json());
  },
};

export { ApiError };
