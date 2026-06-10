import { request } from './shared';
import type { CreateDiaryEntryRequest, DiaryEntry, PageResult, UpdateDiaryEntryRequest } from '@/types';

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

