import { aiRequest, request } from './shared';
import type {
  AgentDraftScene,
  AgentOrganizedDraft,
  AgentSaveToolPlan,
  AuthorizedMemoryRecallResult,
  ChatMessage,
  CreateFamilyMemoryRequest,
  MemoryEntry,
  MemoryVoteType,
  PageResult,
  RebuildMemoryIndexResult,
} from '@/types';

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
