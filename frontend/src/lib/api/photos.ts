import type { ApiResult, PhotoClusterResult, PhotoItem, PhotoScope } from '@/types';
import { ApiError, request } from './shared';

type ClusterByUrlsResponse = {
  success?: boolean;
  detail?: string;
  message?: string;
  groups?: unknown;
  total_faces?: unknown;
  silhouette_score?: unknown;
  failed_photos?: unknown;
};

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

function normalizeClusterResult(data: ClusterByUrlsResponse): PhotoClusterResult {
  return {
    groups: Array.isArray(data.groups) ? data.groups as PhotoClusterResult['groups'] : [],
    total_faces: Number(data.total_faces) || 0,
    silhouette_score: typeof data.silhouette_score === 'number' ? data.silhouette_score : null,
    failed_photos: Array.isArray(data.failed_photos)
      ? data.failed_photos
        .map((item: { photo_id?: unknown; file_index?: unknown; reason?: unknown; status_code?: unknown }) => ({
          photo_id: Number(item.photo_id),
          file_index: Number(item.file_index),
          reason: typeof item.reason === 'string' ? item.reason : 'UNKNOWN',
          status_code: typeof item.status_code === 'number' ? item.status_code : null,
        }))
        .filter((item: { photo_id: number; file_index: number }) => (
          Number.isFinite(item.photo_id) && Number.isFinite(item.file_index)
        ))
      : [],
  };
}

async function clusterByUrls(urls: string[], photoIds: number[]): Promise<PhotoClusterResult> {
  let res: Response;
  try {
    res = await fetch('/ai-proxy/dip/faces/cluster-by-urls', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...authHeaders(),
      },
      body: JSON.stringify({ urls, photo_ids: photoIds }),
    });
  } catch {
    throw new ApiError(503, 'AI service unavailable. Please check the Python AI service and retry.');
  }

  const responseText = await res.text();
  let data: ClusterByUrlsResponse | null = null;
  try {
    data = responseText ? JSON.parse(responseText) : null;
  } catch {
    data = null;
  }

  if (!res.ok || !data?.success) {
    const detail = data?.detail || data?.message || responseText.trim();
    throw new ApiError(
      res.status,
      detail ? `Face clustering failed (HTTP ${res.status}): ${detail}` : `Face clustering failed (HTTP ${res.status}).`,
    );
  }
  return normalizeClusterResult(data);
}

export const photoApi = {
  uploadPhotos,
  clusterByUrls,
  listMyPhotos: (limit = 50) => request<PhotoItem[]>(`/photos/my?limit=${limit}`),
  listFamilyPhotos: (familyId: number, limit = 50) => request<PhotoItem[]>(`/photos/family/${familyId}?limit=${limit}`),
  deletePhoto: (photoId: number) => request<void>(`/photos/${photoId}`, { method: 'DELETE' }),
  saveClusterResult: (photoId: number, clusterResult: PhotoClusterResult) => request<void>(`/photos/${photoId}/cluster-result`, {
    method: 'PATCH',
    body: JSON.stringify(clusterResult),
  }),
};
