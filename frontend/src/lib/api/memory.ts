import { request } from './shared';
import type {
  AgentDraftResult,
  AgentSaveMemoryPlanResult,
  AgentOrganizedDraft,
  AuthorizedMemoryRecallResult,
  ChatMessage,
  CreateFamilyMemoryRequest,
  CreatePersonalMemoryRequest,
  MemoryEntry,
  PersonalMemoryView,
  PersonalMemoryVisibility,
  SharedPersonalMemoryView,
  MemoryVoteType,
  PageResult,
  PersonaMaterialDraft,
  PersonaMaterialDraftProfile,
  RebuildMemoryIndexResult,
} from '@/types';

export const memoryApi = {
  listPersonalMemories: (limit = 50) =>
    request<PersonalMemoryView[]>(`/memories/personal?limit=${limit}`),
  listSharedPersonalMemories: (familyId: number, limit = 50) =>
    request<SharedPersonalMemoryView[]>(`/memories/personal/shared/${familyId}?limit=${limit}`),
  createPersonalMemory: (data: CreatePersonalMemoryRequest) =>
    request<PersonalMemoryView>('/memories/personal', { method: 'POST', body: JSON.stringify(data) }),
  updatePersonalMemoryVisibility: (
    memoryId: number,
    data: { visibility: PersonalMemoryVisibility; selectedFamilyIds?: number[] },
  ) => request<PersonalMemoryView>(`/memories/personal/${memoryId}/visibility`, {
    method: 'PUT',
    body: JSON.stringify(data),
  }),
  listMyMemories: (limit = 20) => request<PersonalMemoryView[]>(`/memories/me?limit=${limit}`),
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
  planMemorySave: (body: {
    familyId: number;
    message: string;
    familyContext?: string;
    conversationContext?: ChatMessage[];
    targetMemberName?: string;
    viewerRole?: string;
    source?: string;
    requestId?: string;
  }) =>
    request<AgentSaveMemoryPlanResult>('/agent/save-memory-plan', {
      method: 'POST',
      body: JSON.stringify({
        familyId: body.familyId,
        message: body.message,
        familyContext: body.familyContext || '',
        conversationContext: (body.conversationContext || []).map(({ role, content }) => ({ role, content })),
        targetMemberName: body.targetMemberName || '',
        viewerRole: body.viewerRole || '',
        source: body.source || 'FAMILY_AGENT_CHAT',
        requestId: body.requestId,
      }),
    }),
  organizeDraft: (body: {
    familyId: number;
    content: string;
    memoryLibrary: 'PERSONAL' | 'FAMILY';
    familyContext?: string;
    currentMemoryType?: string;
    currentVisibility?: string;
    target?: string;
    requestId?: string;
  }) =>
    request<AgentDraftResult<AgentOrganizedDraft>>('/agent/organize-draft', {
      method: 'POST',
      body: JSON.stringify({
        familyId: body.familyId,
        content: body.content,
        memoryLibrary: body.memoryLibrary,
        familyContext: body.familyContext || '',
        currentMemoryType: body.currentMemoryType || '',
        currentVisibility: body.currentVisibility || '',
        target: body.target || '',
        requestId: body.requestId,
      }),
    }),
  organizePersonaMaterialDraft: (body: {
    familyId: number;
    content: string;
    profile: Partial<PersonaMaterialDraftProfile>;
    familyContext?: string;
    requestId?: string;
  }) =>
    request<AgentDraftResult<PersonaMaterialDraft>>('/agent/persona-material-draft', {
      method: 'POST',
      body: JSON.stringify({
        familyId: body.familyId,
        content: body.content,
        profile: {
          name: body.profile.name || '',
          description: body.profile.description || '',
          eraIdentity: body.profile.eraIdentity || '',
          values: body.profile.values || '',
          speakingStyle: body.profile.speakingStyle || '',
          personality: body.profile.personality || '',
        },
        familyContext: body.familyContext || '',
        requestId: body.requestId,
      }),
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
