import { request } from './shared';
import type { MirrorContextResponse } from '@/types';

export const mirrorApi = {
  getContext: (familyId: number, targetUserId: number, query?: string) => {
    const params = query?.trim() ? `?query=${encodeURIComponent(query.trim())}` : '';
    return request<MirrorContextResponse>(`/mirror/families/${familyId}/members/${targetUserId}/context${params}`);
  },
};

// ============================================
// Family agent calls sent to the Python AI service.
// ============================================
