import type { ApiResult, MediaAttachment, MediaRecordType } from '@/types';
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

async function uploadMedia(
  recordType: MediaRecordType,
  recordId: number,
  files: File[],
): Promise<MediaAttachment[]> {
  const form = new FormData();
  form.append('recordType', recordType);
  form.append('recordId', String(recordId));
  files.forEach((file) => form.append('files', file));

  let res: Response;
  try {
    res = await fetch('/api/media/upload', {
      method: 'POST',
      headers: authHeaders(),
      body: form,
    });
  } catch {
    throw new ApiError(503, 'Backend service unavailable. Please check the Java service and retry.');
  }

  const data = await res.json().catch(() => null) as ApiResult<MediaAttachment[]> | null;
  if (!res.ok || data?.code !== 200 || !Array.isArray(data.data)) {
    throw new ApiError(data?.code || res.status, readBackendMessage(data, 'Media upload failed.'));
  }
  return data.data;
}

export const mediaApi = {
  uploadMedia,
  listMedia: (recordType: MediaRecordType, recordId: number) =>
    request<MediaAttachment[]>(`/media?recordType=${recordType}&recordId=${recordId}`),
  deleteMedia: (attachmentId: number) => request<void>(`/media/${attachmentId}`, { method: 'DELETE' }),
};
