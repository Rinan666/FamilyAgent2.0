import { request } from './shared';
import type { MemoryLibraryItem, MemoryLibraryItemType, MemoryMaintenanceSuggestion, PageResult } from '@/types';

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
  classicalizeItem: (
    familyId: number,
    itemId: string,
    classicalText: string,
    plainSummary: string,
    styleNote: string,
  ) =>
    request<void>('/memory-library/classicalize', {
      method: 'POST',
      body: JSON.stringify({ familyId, itemId, classicalText, plainSummary, styleNote }),
    }),
  mergeItems: (familyId: number, primaryItemId: string, secondaryItemId: string) =>
    request<void>('/memory-library/merge', {
      method: 'POST',
      body: JSON.stringify({ familyId, primaryItemId, secondaryItemId }),
    }),
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
