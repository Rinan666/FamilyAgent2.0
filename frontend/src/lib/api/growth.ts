import { aiRequest, request } from './shared';
import type {
  CreateGrowthGuardRecordRequest,
  GrowthFollowUpStatus,
  GrowthGuardRecord,
  MemoryEntry,
  PageResult,
  WeeklyGrowthReport,
} from '@/types';

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
