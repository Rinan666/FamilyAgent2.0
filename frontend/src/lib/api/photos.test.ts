import { afterEach, describe, expect, it, vi } from 'vitest';
import { photoApi } from './photos';
import { ApiError } from './shared';

describe('photoApi.clusterByUrls', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('normalizes skipped photo failures from the AI proxy response', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({
      success: true,
      groups: [{ group_id: 1, faces: [] }],
      total_faces: 2,
      silhouette_score: 0.5,
      failed_photos: [
        { photo_id: 171, file_index: 1, reason: 'HTTP_STATUS', status_code: 404 },
      ],
    }))));

    const result = await photoApi.clusterByUrls(['/api/photos/170/content'], [170]);

    expect(result.total_faces).toBe(2);
    expect(result.failed_photos).toEqual([
      { photo_id: 171, file_index: 1, reason: 'HTTP_STATUS', status_code: 404 },
    ]);
  });

  it('wraps network failures with a user-facing AI unavailable error', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => {
      throw new TypeError('Failed to fetch');
    }));

    await expect(photoApi.clusterByUrls(['/api/photos/170/content'], [170]))
      .rejects.toMatchObject({
        name: 'ApiError',
        code: 503,
        message: 'AI service unavailable. Please check the Python AI service and retry.',
      } satisfies Partial<ApiError>);
  });
});
