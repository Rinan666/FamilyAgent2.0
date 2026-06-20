import type { ApiResult, PhotoClusterResult, PhotoItem, PhotoScope } from '@/types';
import { ApiError, request } from './shared';

function authHeaders(): HeadersInit {
  const token = typeof window !== 'undefined' ? localStorage.getItem('token') : null;
  return token ? { Authorization: token } : {};
}

function readBackendMessage(data: unknown, fallback: string) {
  if (data && typeof data === 'object') {
    const message = (data as { message?: unknown }).message;
    if (typeof message === 'string' && message.trim()) {
      return message;
    }
  }
  return fallback;
}

async function uploadPhotos(familyId: number, files: File[], scope: PhotoScope): Promise<PhotoItem[]> {
  const form = new FormData();
  form.append('familyId', String(familyId));
  form.append('scope', scope);
  files.forEach((file) => form.append('files', file));

  let res: Response;
  try {
    res = await fetch('/api/photos/upload', {
      method: 'POST',
      headers: authHeaders(),
      body: form,
    });
  } catch {
    throw new ApiError(503, 'Backend service unavailable. Please check the Java service and retry.');
  }

  const data = await res.json().catch(() => null) as ApiResult<PhotoItem[]> | null;
  if (!res.ok || data?.code !== 200 || !Array.isArray(data.data)) {
    throw new ApiError(data?.code || res.status, readBackendMessage(data, 'Photo upload failed.'));
  }
  return data.data;
}

export const photoApi = {
  uploadPhotos,
  listMyPhotos: (limit = 50) => request<PhotoItem[]>(`/photos/my?limit=${limit}`),
  listFamilyPhotos: (familyId: number, limit = 50) => request<PhotoItem[]>(`/photos/family/${familyId}?limit=${limit}`),
  deletePhoto: (photoId: number) => request<void>(`/photos/${photoId}`, { method: 'DELETE' }),
  saveClusterResult: (photoId: number, clusterResult: PhotoClusterResult) => request<void>(`/photos/${photoId}/cluster-result`, {
    method: 'PATCH',
    body: JSON.stringify(clusterResult),
  }),
};
