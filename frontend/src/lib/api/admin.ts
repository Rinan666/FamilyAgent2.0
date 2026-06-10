import { normalizeFamilyMembers, request } from './shared';
import type {
  AdminUserSummary,
  DatabaseHealthResponse,
  FamilyDatabaseSummary,
  FamilyMember,
  MemoryRecallDiagnosticRequest,
  MemoryRecallDiagnosticResponse,
  PageResult,
} from '@/types';

export const adminApi = {
  getDatabaseHealth: () => request<DatabaseHealthResponse>('/admin/database/health'),
  listUsers: (params?: { keyword?: string; page?: number; pageSize?: number }) => {
    const sp = new URLSearchParams();
    if (params?.keyword?.trim()) sp.set('keyword', params.keyword.trim());
    if (params?.page) sp.set('page', String(params.page));
    if (params?.pageSize) sp.set('pageSize', String(params.pageSize));
    return request<PageResult<AdminUserSummary>>(`/admin/database/users?${sp}`);
  },
  listFamilies: (params?: { keyword?: string; page?: number; pageSize?: number }) => {
    const sp = new URLSearchParams();
    if (params?.keyword?.trim()) sp.set('keyword', params.keyword.trim());
    if (params?.page) sp.set('page', String(params.page));
    if (params?.pageSize) sp.set('pageSize', String(params.pageSize));
    return request<PageResult<FamilyDatabaseSummary>>(`/admin/database/families?${sp}`);
  },
  listFamilyMembers: (familyId: number) =>
    request<FamilyMember[]>(`/admin/database/families/${familyId}/members`).then(normalizeFamilyMembers),
  deleteUser: (userId: number) =>
    request<void>(`/admin/database/users/${userId}`, {
      method: 'DELETE',
    }),
  runMemoryRecallDiagnostic: (data: MemoryRecallDiagnosticRequest) =>
    request<MemoryRecallDiagnosticResponse>('/admin/database/memory-recall-diagnostic', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
};
