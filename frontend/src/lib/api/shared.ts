/**
 * Shared API client transport and response normalizers.
 */
import type {
  ApiResult,
  LoginResponse,
  User,
  FamilyMember,
  ChatMessage,
  ChatSessionArchiveDetail,
  ChatSessionArchiveMetadata,
  ChatSessionArchiveSummary,
  ChatSessionDetail,
  ChatSessionMessageItem,
  ChatSessionMessagePage,
  ChatSessionSummary,
  WeeklyGrowthReport,
} from '@/types';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || '/api';
export type AIStreamHandle = {
  abort: () => void;
  completed: Promise<void>;
};
// ============================================
export class ApiError extends Error {
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

export async function request<T>(url: string, options: RequestInit = {}): Promise<T> {
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

export function parseJsonField<T>(value: unknown, fallback: T): T {
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

export function toText(value: unknown): string {
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

export function normalizeStringArray(value: unknown): string[] {
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

export function normalizeSessionSummary(raw: ChatSessionSummary): ChatSessionSummary {
  return {
    ...raw,
    metadata: parseJsonField<Record<string, unknown>>(raw.metadata, {}),
  };
}

export function normalizeSessionArchiveSummary(raw: ChatSessionArchiveSummary): ChatSessionArchiveSummary {
  return {
    ...raw,
    metadata: parseJsonField<Record<string, unknown>>(raw.metadata, {}),
  };
}

export function normalizeSessionMessageItem(raw: ChatSessionMessageItem): ChatSessionMessageItem {
  return {
    ...raw,
    metadata: parseJsonField<Record<string, unknown>>(raw.metadata, {}),
  };
}

export function normalizeSessionMessagePage(raw: ChatSessionMessagePage): ChatSessionMessagePage {
  return {
    ...raw,
    items: (raw.items || []).map(normalizeSessionMessageItem),
  };
}

export function normalizeSessionArchiveDetail(raw: ChatSessionArchiveDetail): ChatSessionArchiveDetail {
  return {
    ...normalizeSessionArchiveSummary(raw),
    transcript: (raw.transcript || []).map(normalizeSessionMessageItem),
  };
}

export function normalizeSessionDetail(raw: ChatSessionDetail): ChatSessionDetail {
  return {
    ...normalizeSessionSummary(raw),
    archiveMetadata: parseJsonField<ChatSessionArchiveMetadata>(raw.archiveMetadata, { storageVersion: 0 }),
    archives: (raw.archives || []).map(normalizeSessionArchiveSummary),
  };
}

export function normalizeUser(raw: User): User {
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

export function normalizeLoginResponse(raw: LoginResponse): LoginResponse {
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

export function normalizeFamilyMember(raw: FamilyMember): FamilyMember {
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

export function normalizeFamilyMembers(members: FamilyMember[] | undefined | null): FamilyMember[] {
  return (members || []).map(normalizeFamilyMember);
}

export function normalizeSessionSummaries(sessions: ChatSessionSummary[] | undefined | null): ChatSessionSummary[] {
  return (sessions || []).map(normalizeSessionSummary);
}

export function toSessionMessagePayload(message: ChatMessage) {
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

export function normalizeWeeklyGrowthReport(value: unknown): WeeklyGrowthReport {
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
export async function aiRequest<T>(path: string, body: unknown): Promise<T> {
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
export async function aiFileRequest<T>(path: string, file: File): Promise<T> {
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

export function sseStreamRequest(
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

