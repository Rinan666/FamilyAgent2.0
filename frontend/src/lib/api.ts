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
  LoginRequest, LoginResponse, RegisterRequest,
  User, Family, FamilyMember,
  Question, KnowledgePoint, QuestionAnswer, QuestionContent,
  AbilityProfile, TestRecord,
  ChatMessage, ChatSession, GradeResult, PageResult, SubmitTestRequest, CreateQuestionRequest,
} from '@/types';

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

function normalizeQuestion(raw: Question): Question {
  const content = parseJsonField<QuestionContent>(raw.content, { stem: '' });
  const answer = parseJsonField<QuestionAnswer>(raw.answer, { value: '', steps: [] });
  const tags = normalizeTags((raw as Question & { tags?: unknown }).tags);

  return {
    ...raw,
    content: {
      stem: content.stem || String(raw.content ?? ''),
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

async function aiRequest<T>(path: string, body: unknown): Promise<T> {
  const token = typeof window !== 'undefined' ? localStorage.getItem('token') : null;
  const res = await fetch(`${AI_BASE}/ai${path}`, {
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
    page?: number; size?: number; subject?: string; kpId?: number;
    difficulty?: number; type?: string; tag?: string;
  }) => {
    const sp = new URLSearchParams();
    Object.entries(params).forEach(([k, v]) => { if (v !== undefined) sp.set(k, String(v)); });
    const page = await request<PageResult<Question>>(`/questions?${sp}`);
    return { ...page, items: normalizeQuestions(page.items) };
  },
  createQuestion: (data: CreateQuestionRequest) =>
    request<Question>('/questions', { method: 'POST', body: JSON.stringify(data) }).then(normalizeQuestion),
  batchCreateQuestions: (data: CreateQuestionRequest[]) =>
    request<Question[]>('/questions/batch', { method: 'POST', body: JSON.stringify(data) }).then(normalizeQuestions),
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
  getZPD: (_userId?: number) => request<AbilityProfile[]>('/assessment/zpd/me'),
  getHistory: (_userId?: number, limit = 20) => request<TestRecord[]>(`/assessment/history/me?limit=${limit}`),
  submitTest: (data: SubmitTestRequest) =>
    request<TestRecord>('/assessment/tests', { method: 'POST', body: JSON.stringify(data) }),
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

// ============================================
// AI 家教（直连 Python AI 服务）
// ============================================
export const tutorApi = {
  explainStream: (
    body: { questionContent: string; answer: string; steps: string; studentMessage: string;
            history?: { role: string; content: string }[]; grade?: string; subject?: string;
            knowledgePoint?: string; masteryLevel?: string; teachingStyle?: 'guided' | 'direct';
            mode?: 'explain' | 'chat'; memoryContext?: string; },
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
                               questionType?: string; difficulty?: number; count?: number; }) =>
    aiRequest<{ success: boolean; questions: Question[]; count: number }>('/tutor/generate', {
      subject: body.subject,
      grade: body.grade,
      knowledge_point: body.knowledgePoint,
      question_type: body.questionType || 'CALCULATION',
      difficulty: body.difficulty || 3,
      count: body.count || 5,
    }).then((result) => ({ ...result, questions: normalizeQuestions(result.questions) })),

  mathVerify: (expression?: string, expected?: string, studentAnswer?: string) => {
    const params = new URLSearchParams();
    if (expression) params.set('expression', expression);
    if (expected) params.set('expected', expected);
    if (studentAnswer) params.set('student_answer', studentAnswer);
    return fetch(`${AI_BASE}/ai/tutor/math/verify?${params}`).then(r => r.json());
  },
};

export { ApiError };
