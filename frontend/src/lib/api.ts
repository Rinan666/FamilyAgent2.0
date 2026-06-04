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
  Question, KnowledgePoint,
  AbilityProfile, TestRecord,
  ChatSession, GradeResult, PageResult,
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
    if (res.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      if (typeof window !== 'undefined') window.location.href = '/login';
    }
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
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      if (typeof window !== 'undefined') window.location.href = '/login';
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
  getQuestion: (id: number) => request<Question>(`/questions/${id}`),
  listQuestions: (params: { page?: number; size?: number; subject?: string; kpId?: number; difficulty?: number }) => {
    const sp = new URLSearchParams();
    Object.entries(params).forEach(([k, v]) => { if (v !== undefined) sp.set(k, String(v)); });
    return request<PageResult<Question>>(`/questions?${sp}`);
  },
  selectForTest: (params: { kpId?: number; subject?: string; difficulty?: number; type?: string; limit?: number }) =>
    request<Question[]>('/questions/select', { method: 'POST', body: JSON.stringify(params) }),
  adaptiveSelect: (userId: number, limit = 5) =>
    request<Question[]>(`/questions/adaptive-select?userId=${userId}&limit=${limit}`, { method: 'POST' }),
  getWrongQuestions: (userId: number, limit = 10) =>
    request<Question[]>(`/questions/wrong?userId=${userId}&limit=${limit}`),
};

// ============================================
// 评估
// ============================================
export const assessmentApi = {
  getProfiles: (userId: number) => request<AbilityProfile[]>(`/assessment/profiles/${userId}`),
  getZPD: (userId: number) => request<AbilityProfile[]>(`/assessment/zpd/${userId}`),
  getHistory: (userId: number, limit = 20) => request<TestRecord[]>(`/assessment/history/${userId}?limit=${limit}`),
};

// ============================================
// 会话
// ============================================
export const sessionApi = {
  getSession: (id: number) => request<ChatSession>(`/sessions/${id}`),
  getUserSessions: (userId: number, limit = 20) => request<ChatSession[]>(`/sessions/user/${userId}?limit=${limit}`),
  getActiveSessions: (userId: number) => request<ChatSession[]>(`/sessions/active/${userId}`),
};

// ============================================
// AI 家教（直连 Python AI 服务）
// ============================================
export const tutorApi = {
  explainStream: (
    body: { questionContent: string; answer: string; steps: string; studentMessage: string;
            history?: { role: string; content: string }[]; grade?: string; subject?: string;
            knowledgePoint?: string; masteryLevel?: string; },
    onChunk: (chunk: string) => void, onDone: () => void, onError: (error: string) => void,
  ) => sseRequest('/tutor/explain', {
    question_content: body.questionContent,
    answer: body.answer,
    steps: body.steps,
    student_message: body.studentMessage,
    history: body.history,
    grade: body.grade || '初中',
    subject: body.subject || '数学',
    knowledge_point: body.knowledgePoint || '',
    mastery_level: body.masteryLevel || '中',
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

  generateQuestions: (body: { subject: string; grade: string; knowledgePoint: string;
                               questionType?: string; difficulty?: number; count?: number; }) =>
    aiRequest<{ success: boolean; questions: Question[]; count: number }>('/tutor/generate', {
      subject: body.subject,
      grade: body.grade,
      knowledge_point: body.knowledgePoint,
      question_type: body.questionType || 'CALCULATION',
      difficulty: body.difficulty || 3,
      count: body.count || 5,
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
