import {
  normalizeSessionArchiveDetail,
  normalizeSessionArchiveSummary,
  normalizeSessionDetail,
  normalizeSessionMessagePage,
  normalizeSessionSummaries,
  request,
  toSessionMessagePayload,
} from './shared';
import type {
  ChatMessage,
  ChatSessionArchiveDetail,
  ChatSessionArchiveSummary,
  ChatSessionDetail,
  ChatSessionMessagePage,
  ChatSessionSummary,
} from '@/types';

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
  patchSession: (id: number, data: { metadata?: Record<string, unknown> }) =>
    request<ChatSessionDetail>(`/sessions/${id}`, {
      method: 'PATCH',
      body: JSON.stringify(data),
    }).then(normalizeSessionDetail),
  deleteSession: (id: number) => request<void>(`/sessions/${id}`, { method: 'DELETE' }),
};
