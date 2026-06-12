'use client';

import { useCallback, useState } from 'react';
import { Images, RefreshCw, Upload, User, X } from 'lucide-react';
import { useViewerRole } from '@/hooks/useViewerRole';

interface PhotoUploadResponse {
  id: number;
  assetUrl: string;
}

interface FaceMeta {
  photo_id: number;
  file_index: number;
  face_index: number;
  bbox: { x: number; y: number; w: number; h: number };
}

interface Group {
  group_id: number;
  faces: FaceMeta[];
}

interface ClusterResult {
  groups: Group[];
  total_faces: number;
  silhouette_score: number | null;
}

interface ResultEnvelope<T> {
  code?: number;
  message?: string;
  data?: T;
}

const MIN_FILES = 2;
const MAX_FILES = 50;
const MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;
const MAX_TOTAL_SIZE_BYTES = 200 * 1024 * 1024;

function getBackendMessage(data: unknown, fallback: string) {
  if (data && typeof data === 'object') {
    const record = data as Record<string, unknown>;
    if (typeof record.message === 'string' && record.message.trim()) {
      return record.message;
    }
  }
  return fallback;
}

function getUploadFailureMessage(res: Response, rawText: string, fallback: string) {
  if (res.status === 401) {
    return 'Your session expired. Please sign in again and retry the upload.';
  }
  if (res.status === 413) {
    return 'The selected photos exceed the upload limit of 10 MB per image and 40 MB total.';
  }

  const trimmed = rawText.trim();
  if (trimmed && !trimmed.startsWith('<')) {
    return trimmed.length > 200 ? `${trimmed.slice(0, 200)}...` : trimmed;
  }

  return `${fallback} (HTTP ${res.status})`;
}

async function uploadPhotos(familyId: number, files: File[]): Promise<PhotoUploadResponse[]> {
  const token = typeof window !== 'undefined' ? localStorage.getItem('token') : null;
  const form = new FormData();
  form.append('familyId', String(familyId));
  for (const file of files) {
    form.append('files', file);
  }

  let res: Response;
  try {
    res = await fetch('/api/photos/upload', {
      method: 'POST',
      headers: token ? { Authorization: token } : {},
      body: form,
    });
  } catch {
    throw new Error('上传失败：无法连接到服务器，请检查网络后重试。');
  }
  const rawText = await res.text();
  let data: ResultEnvelope<PhotoUploadResponse[]> | null = null;
  if (rawText.trim()) {
    try {
      data = JSON.parse(rawText) as ResultEnvelope<PhotoUploadResponse[]>;
    } catch {
      // backend returned non-JSON (e.g. nginx 502 HTML page)
    }
  }
  if (!res.ok || data?.code !== 200 || !data.data) {
    throw new Error(
      data
        ? getBackendMessage(data, 'Upload failed. Please try again.')
        : getUploadFailureMessage(res, rawText, 'Upload failed. Please try again.'),
    );
  }
  return data.data;
}

async function clusterByUrls(urls: string[], photoIds: number[]): Promise<ClusterResult> {
  const token = typeof window !== 'undefined' ? localStorage.getItem('token') : null;
  const origin = typeof window !== 'undefined' ? window.location.origin : '';
  const absoluteUrls = urls.map((url) => (url.startsWith('http') ? url : `${origin}${url}`));
  const res = await fetch('/ai-proxy/dip/faces/cluster-by-urls', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: token } : {}),
    },
    body: JSON.stringify({ urls: absoluteUrls, photo_ids: photoIds }),
  });
  const data = await res.json();
  if (!res.ok || !data.success) {
    throw new Error(data.detail || 'Face clustering failed.');
  }
  return data as ClusterResult;
}

async function saveClusterResult(photoId: number, clusterResult: object): Promise<void> {
  const token = typeof window !== 'undefined' ? localStorage.getItem('token') : null;
  const res = await fetch(`/api/photos/${photoId}/cluster-result`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: token } : {}),
    },
    body: JSON.stringify(clusterResult),
  });
  const data = await res.json().catch(() => null) as ResultEnvelope<null> | null;
  if (!res.ok || data?.code !== 200) {
    throw new Error(getBackendMessage(data, 'Cluster results could not be saved.'));
  }
}

type Stage = 'idle' | 'uploading' | 'clustering' | 'done';

export default function AlbumPage() {
  const { activeFamilyId } = useViewerRole();

  const [files, setFiles] = useState<File[]>([]);
  const [previews, setPreviews] = useState<string[]>([]);
  const [stage, setStage] = useState<Stage>('idle');
  const [result, setResult] = useState<ClusterResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [warning, setWarning] = useState<string | null>(null);

  const validateFiles = useCallback((nextFiles: File[]) => {
    if (nextFiles.length > MAX_FILES) {
      return `You can upload up to ${MAX_FILES} photos at a time.`;
    }

    const tooLarge = nextFiles.find((file) => file.size > MAX_FILE_SIZE_BYTES);
    if (tooLarge) {
      return `${tooLarge.name} is larger than 10 MB.`;
    }

    const totalBytes = nextFiles.reduce((sum, file) => sum + file.size, 0);
    if (totalBytes > MAX_TOTAL_SIZE_BYTES) {
      return 'The selected photos exceed the 40 MB total upload limit.';
    }

    return null;
  }, []);

  const addFiles = useCallback((incoming: FileList | null) => {
    if (!incoming) return;

    const valid = Array.from(incoming).filter((file) => file.type.startsWith('image/'));
    if (valid.length === 0) {
      setError('Only image files can be added to the album.');
      return;
    }

    const nextFiles = [...files, ...valid];
    const validationError = validateFiles(nextFiles);
    if (validationError) {
      setError(validationError);
      return;
    }

    setError(null);
    setWarning(null);
    setFiles(nextFiles);
    setPreviews((current) => [
      ...current,
      ...valid.map((file) => URL.createObjectURL(file)),
    ]);
  }, [files, validateFiles]);

  const removeFile = useCallback((idx: number) => {
    const preview = previews[idx];
    if (preview) {
      URL.revokeObjectURL(preview);
    }

    setFiles((prev) => prev.filter((_, i) => i !== idx));
    setPreviews((prev) => prev.filter((_, i) => i !== idx));
    setError(null);
    setWarning(null);
  }, [previews]);

  const run = useCallback(async () => {
    const validationError = validateFiles(files);
    if (validationError) {
      setError(validationError);
      return;
    }
    if (files.length < MIN_FILES) {
      setError(`Upload at least ${MIN_FILES} photos to start clustering.`);
      return;
    }
    if (!activeFamilyId) {
      setError('Choose a family before uploading photos.');
      return;
    }

    setError(null);
    setWarning(null);
    setResult(null);

    setStage('uploading');
    let photos: PhotoUploadResponse[];
    try {
      photos = await uploadPhotos(Number(activeFamilyId), files);
    } catch (nextError: unknown) {
      setError(nextError instanceof Error ? nextError.message : 'Upload failed. Please try again.');
      setStage('idle');
      return;
    }

    setStage('clustering');
    try {
      const nextResult = await clusterByUrls(
        photos.map((photo) => photo.assetUrl),
        photos.map((photo) => photo.id),
      );
      setResult(nextResult);

      const saveResults = await Promise.allSettled(
        photos.map((photo) => saveClusterResult(photo.id, nextResult)),
      );
      const failedSaves = saveResults.filter((item) => item.status === 'rejected').length;
      if (failedSaves > 0) {
        setWarning(
          failedSaves === photos.length
            ? 'Clustering finished, but none of the results could be saved to the family album.'
            : `Clustering finished, but ${failedSaves} photo result(s) could not be saved.`,
        );
      }
    } catch (nextError: unknown) {
      setError(nextError instanceof Error ? nextError.message : 'Face clustering failed. Please try again.');
    } finally {
      setStage('done');
    }
  }, [activeFamilyId, files, validateFiles]);

  const reset = useCallback(() => {
    previews.forEach((preview) => URL.revokeObjectURL(preview));
    setFiles([]);
    setPreviews([]);
    setResult(null);
    setError(null);
    setWarning(null);
    setStage('idle');
  }, [previews]);

  const validGroups = result?.groups.filter((group) => group.group_id !== -1) ?? [];
  const noiseGroup = result?.groups.find((group) => group.group_id === -1);
  const isLoading = stage === 'uploading' || stage === 'clustering';
  const stageLabel = stage === 'uploading'
    ? 'Uploading...'
    : stage === 'clustering'
      ? 'Clustering faces...'
      : `Cluster by person (${files.length})`;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Images className="h-6 w-6 text-blue-600" />
          <div>
            <h1 className="text-xl font-semibold text-gray-900">Family Album</h1>
            <p className="text-sm text-gray-500">Experimental face clustering for family photo batches.</p>
          </div>
        </div>
        {(files.length > 0 || result || warning) && (
          <button
            type="button"
            onClick={reset}
            className="inline-flex h-9 items-center gap-2 rounded-lg border border-gray-200 px-3 text-sm text-gray-600 hover:bg-gray-50"
          >
            <X className="h-4 w-4" />
            Reset
          </button>
        )}
      </div>

      {!result && (
        <>
          <label className="flex cursor-pointer flex-col items-center justify-center gap-3 rounded-xl border-2 border-dashed border-gray-300 bg-gray-50 py-12 transition hover:border-blue-400 hover:bg-blue-50">
            <Upload className="h-8 w-8 text-gray-400" />
            <span className="text-sm text-gray-500">
              Add at least {MIN_FILES} photos. Up to {MAX_FILES} images, 10 MB each, 40 MB total.
            </span>
            <input
              type="file"
              accept="image/*"
              multiple
              className="sr-only"
              onChange={(event) => addFiles(event.target.files)}
            />
          </label>

          {previews.length > 0 && (
            <div className="grid grid-cols-3 gap-3 sm:grid-cols-4 lg:grid-cols-6">
              {previews.map((src, index) => (
                <div
                  key={`${src}-${index}`}
                  className="group relative aspect-square overflow-hidden rounded-lg border border-gray-200 bg-gray-100"
                >
                  <img src={src} alt="" className="h-full w-full object-cover" />
                  <button
                    type="button"
                    onClick={() => removeFile(index)}
                    className="absolute right-1 top-1 flex h-5 w-5 items-center justify-center rounded-full bg-black/60 text-white opacity-0 transition group-hover:opacity-100"
                    aria-label="Remove image"
                  >
                    <X className="h-3 w-3" />
                  </button>
                </div>
              ))}
            </div>
          )}

          {error && <p className="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-600">{error}</p>}
          {warning && <p className="rounded-lg bg-amber-50 px-4 py-3 text-sm text-amber-700">{warning}</p>}

          <button
            type="button"
            onClick={run}
            disabled={isLoading || files.length < MIN_FILES}
            className="inline-flex h-10 items-center gap-2 rounded-lg bg-blue-600 px-5 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {isLoading ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Images className="h-4 w-4" />}
            {stageLabel}
          </button>
        </>
      )}

      {result && (
        <div className="space-y-5">
          {warning && <p className="rounded-lg bg-amber-50 px-4 py-3 text-sm text-amber-700">{warning}</p>}

          <div className="flex flex-wrap items-center gap-4 rounded-lg border border-gray-200 bg-white px-4 py-3 text-sm text-gray-600">
            <span>
              Detected <strong className="text-gray-900">{result.total_faces}</strong> faces
            </span>
            <span>
              Found <strong className="text-gray-900">{validGroups.length}</strong> people
            </span>
            {result.silhouette_score !== null && (
              <span>
                Silhouette score <strong className="text-gray-900">{result.silhouette_score.toFixed(3)}</strong>
              </span>
            )}
          </div>

          {validGroups.length === 0 && (
            <p className="rounded-lg bg-yellow-50 px-4 py-3 text-sm text-yellow-700">
              No stable person groups were found yet. Try uploading more photos.
            </p>
          )}

          {validGroups.map((group, index) => (
            <div key={group.group_id} className="rounded-lg border border-gray-200 bg-white p-4">
              <div className="mb-3 flex items-center gap-2">
                <User className="h-4 w-4 text-gray-400" />
                <span className="text-sm font-medium text-gray-700">
                  Person {index + 1} ({group.faces.length} photos)
                </span>
              </div>
              <div className="grid grid-cols-3 gap-2 sm:grid-cols-5 lg:grid-cols-8">
                {group.faces.map((face, faceIndex) => {
                  const size = 80;
                  const scale = size / Math.max(face.bbox.w, 1);
                  const previewSrc = previews[face.file_index];

                  return (
                    <div
                      key={`${group.group_id}-${faceIndex}`}
                      style={{ width: size, height: size, overflow: 'hidden', position: 'relative' }}
                      className="rounded-lg border border-gray-200 bg-gray-100"
                    >
                      <img
                        src={previewSrc}
                        alt=""
                        style={{
                          position: 'absolute',
                          maxWidth: 'none',
                          left: -face.bbox.x * scale,
                          top: -face.bbox.y * scale,
                          width: 'auto',
                          height: 'auto',
                          transform: `scale(${scale})`,
                          transformOrigin: 'top left',
                        }}
                      />
                    </div>
                  );
                })}
              </div>
            </div>
          ))}

          {noiseGroup && noiseGroup.faces.length > 0 && (
            <div className="rounded-lg border border-gray-200 bg-gray-50 p-4">
              <h2 className="mb-3 text-sm font-medium text-gray-500">
                Unclustered ({noiseGroup.faces.length})
              </h2>
              <div className="grid grid-cols-3 gap-2 sm:grid-cols-5 lg:grid-cols-8">
                {noiseGroup.faces.map((face, faceIndex) => (
                  <div
                    key={`noise-${faceIndex}`}
                    className="aspect-square overflow-hidden rounded-lg border border-gray-200 bg-gray-100"
                  >
                    <img src={previews[face.file_index]} alt="" className="h-full w-full object-cover" />
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
