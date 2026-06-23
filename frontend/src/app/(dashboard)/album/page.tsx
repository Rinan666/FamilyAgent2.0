'use client';

/* eslint-disable @next/next/no-img-element */

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Image as ImageIcon, Images, RefreshCw, Trash2, Upload, UserRound, UsersRound, X } from 'lucide-react';
import { photoApi } from '@/lib/api/photos';
import { useViewerRole } from '@/hooks/useViewerRole';
import {
  WorkbenchAlert,
  WorkbenchButton,
  WorkbenchPage,
  WorkbenchSurface,
} from '@/components/layout/Workbench';
import type { PhotoClusterResult, PhotoFaceMeta, PhotoItem, PhotoScope } from '@/types';

type Stage = 'idle' | 'uploading' | 'clustering' | 'done';
type TabKey = 'personal' | 'family' | 'faces';

const MIN_CLUSTER_FILES = 2;
const MAX_FILES = 50;
const MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;
const MAX_TOTAL_SIZE_BYTES = 200 * 1024 * 1024;

function getToken() {
  return typeof window !== 'undefined' ? localStorage.getItem('token') : null;
}

function authHeadersForPhotoUrl(assetUrl: string): HeadersInit {
  const token = getToken();
  if (!token || typeof window === 'undefined') return {};

  try {
    const url = new URL(assetUrl, window.location.origin);
    const trustedPhotoPath = url.origin === window.location.origin
      && /^\/api\/photos\/\d+\/content$/.test(url.pathname);
    return trustedPhotoPath ? { Authorization: token } : {};
  } catch {
    return {};
  }
}

function formatBytes(bytes?: number) {
  if (!bytes) return '';
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function formatDate(value?: string) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' });
}

function useAuthenticatedObjectUrl(assetUrl?: string) {
  const [src, setSrc] = useState<string | null>(null);

  useEffect(() => {
    if (!assetUrl) {
      setSrc(null);
      return;
    }
    const targetUrl = assetUrl;

    let cancelled = false;
    let objectUrl: string | null = null;

    async function loadImage() {
      setSrc(null);
      try {
        const res = await fetch(targetUrl, {
          headers: authHeadersForPhotoUrl(targetUrl),
          cache: 'no-store',
        });
        if (!res.ok) return;
        const blob = await res.blob();
        if (cancelled) return;
        objectUrl = URL.createObjectURL(blob);
        setSrc(objectUrl);
      } catch {
        setSrc(null);
      }
    }

    void loadImage();
    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [assetUrl]);

  return src;
}

function validateImageFiles(files: File[], minFiles = 1) {
  if (files.length < minFiles) {
    return `至少选择 ${minFiles} 张图片。`;
  }
  if (files.length > MAX_FILES) {
    return `一次最多可上传 ${MAX_FILES} 张图片。`;
  }
  const nonImage = files.find((file) => !file.type.startsWith('image/'));
  if (nonImage) {
    return `${nonImage.name} 不是图片文件。`;
  }
  const tooLarge = files.find((file) => file.size > MAX_FILE_SIZE_BYTES);
  if (tooLarge) {
    return `${tooLarge.name} 超过 10 MB。`;
  }
  const totalBytes = files.reduce((sum, file) => sum + file.size, 0);
  if (totalBytes > MAX_TOTAL_SIZE_BYTES) {
    return '所选图片总大小超过 200 MB。';
  }
  return null;
}

async function clusterByUrls(urls: string[], photoIds: number[]): Promise<PhotoClusterResult> {
  const token = getToken();

  const res = await fetch('/ai-proxy/dip/faces/cluster-by-urls', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: token } : {}),
    },
    body: JSON.stringify({ urls, photo_ids: photoIds }),
  });
  const responseText = await res.text();
  let data: {
    success?: boolean;
    detail?: string;
    message?: string;
    groups?: unknown;
    total_faces?: unknown;
    silhouette_score?: unknown;
    failed_photos?: unknown;
  } | null = null;
  try {
    data = responseText ? JSON.parse(responseText) : null;
  } catch {
    data = null;
  }
  if (!res.ok || !data?.success) {
    const detail = data?.detail || data?.message || responseText.trim();
    throw new Error(detail ? `人脸聚类失败（HTTP ${res.status}）：${detail}` : `人脸聚类失败（HTTP ${res.status}）。`);
  }
  return {
    groups: Array.isArray(data.groups) ? data.groups : [],
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

function summarizeClusterFailures(failures: PhotoClusterResult['failed_photos']): string {
  if (!failures?.length) return '';
  const reasons = Array.from(new Set(failures.map((item) => (
    item.status_code ? `HTTP ${item.status_code}` : item.reason
  ))));
  return `聚类已完成，但有 ${failures.length} 张图片无法读取，已跳过（${reasons.slice(0, 3).join('、')}）。`;
}

function AuthImage({ photo, className }: { photo: PhotoItem; className?: string }) {
  const src = useAuthenticatedObjectUrl(photo.assetUrl);

  if (!src) {
    return (
      <div className="flex h-full w-full items-center justify-center bg-stone-100 text-stone-400">
        <ImageIcon className="h-6 w-6" />
      </div>
    );
  }

  return <img src={src} alt={photo.originalName || ''} className={className || 'h-full w-full object-cover'} />;
}

function UploadStrip({
  label,
  disabled,
  onUpload,
}: {
  label: string;
  disabled?: boolean;
  onUpload: (files: File[]) => Promise<void>;
}) {
  const [isUploading, setIsUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleFiles = useCallback(async (fileList: FileList | null) => {
    if (!fileList || disabled) return;
    const files = Array.from(fileList);
    const validationError = validateImageFiles(files);
    if (validationError) {
      setError(validationError);
      return;
    }
    setError(null);
    setIsUploading(true);
    try {
      await onUpload(files);
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : '上传失败，请重试。');
    } finally {
      setIsUploading(false);
    }
  }, [disabled, onUpload]);

  return (
    <div className="space-y-2">
      <label className={`inline-flex h-10 items-center gap-2 rounded-md px-4 text-sm font-medium text-white transition ${disabled ? 'cursor-not-allowed bg-stone-300' : 'cursor-pointer bg-stone-950 hover:bg-stone-800'}`}>
        {isUploading ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />}
        {isUploading ? '上传中' : label}
        <input
          type="file"
          accept="image/*"
          multiple
          disabled={disabled || isUploading}
          className="sr-only"
          onChange={(event) => {
            void handleFiles(event.target.files);
            event.target.value = '';
          }}
        />
      </label>
      {error && <p className="text-sm text-red-600">{error}</p>}
    </div>
  );
}

function PhotoGrid({
  photos,
  emptyText,
  onDelete,
  onSelect,
}: {
  photos: PhotoItem[];
  emptyText: string;
  onDelete: (photoId: number) => Promise<void>;
  onSelect: (photo: PhotoItem) => void;
}) {
  const [deletingId, setDeletingId] = useState<number | null>(null);

  if (photos.length === 0) {
    return (
      <div className="flex min-h-64 items-center justify-center rounded-md border border-dashed border-stone-300 bg-stone-50 text-sm text-stone-500">
        {emptyText}
      </div>
    );
  }

  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5 xl:grid-cols-6">
      {photos.map((photo) => (
        <div key={photo.id} className="group overflow-hidden rounded-md border border-stone-200 bg-white transition hover:border-stone-300 hover:shadow-sm">
          <div className="relative aspect-square bg-stone-100">
            <button
              type="button"
              onClick={() => onSelect(photo)}
              className="block h-full w-full text-left"
              aria-label={`查看 ${photo.originalName || `Photo ${photo.id}`}`}
            >
              <AuthImage photo={photo} />
            </button>
            <button
              type="button"
              aria-label="删除图片"
              disabled={deletingId === photo.id}
              onClick={async () => {
                setDeletingId(photo.id);
                try {
                  await onDelete(photo.id);
                } finally {
                  setDeletingId(null);
                }
              }}
              className="absolute right-2 top-2 flex h-8 w-8 items-center justify-center rounded-md bg-black/65 text-white opacity-0 transition hover:bg-red-600 group-hover:opacity-100 disabled:opacity-60"
            >
              {deletingId === photo.id ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
            </button>
          </div>
          <div className="space-y-1 px-3 py-2">
            <p className="truncate text-sm font-medium text-stone-800">{photo.originalName || `Photo ${photo.id}`}</p>
            <p className="text-xs text-stone-500">
              {[formatDate(photo.createdAt), formatBytes(photo.fileSize)].filter(Boolean).join(' · ')}
            </p>
          </div>
        </div>
      ))}
    </div>
  );
}

function PhotoPreviewDialog({
  photo,
  onClose,
  onDelete,
}: {
  photo: PhotoItem | null;
  onClose: () => void;
  onDelete: (photoId: number) => Promise<void>;
}) {
  const src = useAuthenticatedObjectUrl(photo?.assetUrl);
  const [isDeleting, setIsDeleting] = useState(false);

  useEffect(() => {
    setIsDeleting(false);
  }, [photo?.id]);

  if (!photo) return null;

  const details = [
    { label: '文件名', value: photo.originalName || `Photo ${photo.id}` },
    { label: '上传时间', value: formatDate(photo.createdAt) || '-' },
    { label: '大小', value: formatBytes(photo.fileSize) || '-' },
    { label: '范围', value: photo.scope === 'PERSONAL' ? '我的相册' : '家庭相册' },
  ];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4">
      <div className="flex max-h-[90vh] w-full max-w-5xl flex-col overflow-hidden rounded-md bg-white shadow-xl">
        <div className="flex items-center justify-between border-b border-stone-200 px-4 py-3">
          <div className="min-w-0">
            <h2 className="truncate text-sm font-semibold text-stone-900">{photo.originalName || `Photo ${photo.id}`}</h2>
            <p className="text-xs text-stone-500">{[formatDate(photo.createdAt), formatBytes(photo.fileSize)].filter(Boolean).join(' · ')}</p>
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={async () => {
                setIsDeleting(true);
                try {
                  await onDelete(photo.id);
                  onClose();
                } finally {
                  setIsDeleting(false);
                }
              }}
              disabled={isDeleting}
              className="inline-flex h-9 items-center gap-2 rounded-md border border-red-200 px-3 text-sm text-red-600 hover:bg-red-50 disabled:opacity-60"
            >
              {isDeleting ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
              删除
            </button>
            <button
              type="button"
              onClick={onClose}
              className="inline-flex h-9 w-9 items-center justify-center rounded-md border border-stone-200 text-stone-500 hover:bg-stone-50"
              aria-label="关闭预览"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        </div>

        <div className="grid min-h-0 flex-1 grid-cols-1 md:grid-cols-[1fr_240px]">
          <div className="flex min-h-[320px] items-center justify-center bg-stone-950 p-4">
            {src ? (
              <img src={src} alt={photo.originalName || ''} className="max-h-[70vh] max-w-full object-contain" />
            ) : (
              <div className="flex h-40 w-40 items-center justify-center rounded-md bg-stone-900 text-stone-500">
                <ImageIcon className="h-8 w-8" />
              </div>
            )}
          </div>
          <dl className="space-y-4 border-t border-stone-200 p-4 md:border-l md:border-t-0">
            {details.map((detail) => (
              <div key={detail.label}>
                <dt className="text-xs font-medium text-stone-500">{detail.label}</dt>
                <dd className="mt-1 break-words text-sm text-stone-900">{detail.value}</dd>
              </div>
            ))}
            {photo.description && (
              <div>
                <dt className="text-xs font-medium text-stone-500">描述</dt>
                <dd className="mt-1 whitespace-pre-wrap break-words text-sm text-stone-900">{photo.description}</dd>
              </div>
            )}
          </dl>
        </div>
      </div>
    </div>
  );
}

function FaceCrop({ face, src }: { face: PhotoFaceMeta; src?: string }) {
  const [naturalSize, setNaturalSize] = useState<{ width: number; height: number } | null>(null);

  useEffect(() => {
    setNaturalSize(null);
  }, [src]);

  if (!src) {
    return <div className="aspect-square rounded-md border border-stone-200 bg-stone-100" />;
  }

  const size = 80;
  const scale = naturalSize ? size / Math.max(face.bbox.w, 1) : 1;

  return (
    <div
      style={{ width: size, height: size, overflow: 'hidden', position: 'relative' }}
      className="rounded-md border border-stone-200 bg-stone-100"
    >
      <img
        src={src}
        alt=""
        onLoad={(event) => {
          const image = event.currentTarget;
          setNaturalSize({ width: image.naturalWidth, height: image.naturalHeight });
        }}
        style={{
          position: 'absolute',
          maxWidth: 'none',
          left: naturalSize ? -face.bbox.x * scale : 0,
          top: naturalSize ? -face.bbox.y * scale : 0,
          width: naturalSize ? naturalSize.width * scale : '100%',
          height: naturalSize ? naturalSize.height * scale : '100%',
          objectFit: naturalSize ? undefined : 'cover',
        }}
      />
    </div>
  );
}

function SavedFaceCrop({ face, photosById }: { face: PhotoFaceMeta; photosById: Map<number, PhotoItem> }) {
  const photo = photosById.get(face.photo_id);
  const src = useAuthenticatedObjectUrl(photo?.assetUrl);

  return <FaceCrop face={face} src={src ?? undefined} />;
}

function ClusterResultSummary({
  clusterResult,
  photosById,
  previewUrls,
  title,
}: {
  clusterResult: PhotoClusterResult;
  photosById?: Map<number, PhotoItem>;
  previewUrls?: string[];
  title?: string;
}) {
  const validGroups = clusterResult.groups.filter((group) => group.group_id !== -1);
  const noiseGroup = clusterResult.groups.find((group) => group.group_id === -1);
  const failedPhotoCount = clusterResult.failed_photos?.length ?? 0;

  const renderFace = (face: PhotoFaceMeta, key: string) => (
    photosById
      ? <SavedFaceCrop key={key} face={face} photosById={photosById} />
      : <FaceCrop key={key} face={face} src={previewUrls?.[face.file_index]} />
  );

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-stone-200 bg-white px-4 py-3 text-sm text-stone-600">
        <div className="flex flex-wrap gap-4">
          {title && <span className="font-medium text-stone-800">{title}</span>}
          <span>人脸 <strong className="text-stone-900">{clusterResult.total_faces}</strong></span>
          <span>人物 <strong className="text-stone-900">{validGroups.length}</strong></span>
          {clusterResult.silhouette_score !== null && (
            <span>轮廓系数 <strong className="text-stone-900">{clusterResult.silhouette_score.toFixed(3)}</strong></span>
          )}
          {failedPhotoCount > 0 && (
            <span>跳过图片 <strong className="text-stone-900">{failedPhotoCount}</strong></span>
          )}
        </div>
      </div>

      {validGroups.map((group, index) => (
        <div key={group.group_id} className="rounded-md border border-stone-200 bg-white p-4">
          <div className="mb-3 flex items-center gap-2">
            <UserRound className="h-4 w-4 text-emerald-700" />
            <span className="text-sm font-medium text-stone-700">人物 {index + 1}（{group.faces.length} 张）</span>
          </div>
          <div className="grid grid-cols-3 gap-2 sm:grid-cols-5 lg:grid-cols-8">
            {group.faces.map((face, faceIndex) => renderFace(face, `${group.group_id}-${faceIndex}`))}
          </div>
        </div>
      ))}

      {noiseGroup && noiseGroup.faces.length > 0 && (
        <div className="rounded-md border border-stone-200 bg-stone-50 p-4">
          <h2 className="mb-3 text-sm font-medium text-stone-500">未归类（{noiseGroup.faces.length}）</h2>
          <div className="grid grid-cols-3 gap-2 sm:grid-cols-5 lg:grid-cols-8">
            {noiseGroup.faces.map((face, faceIndex) => renderFace(face, `noise-${faceIndex}`))}
          </div>
        </div>
      )}
    </div>
  );
}

export default function AlbumPage() {
  const { activeFamilyId, activeFamily } = useViewerRole();
  const [activeTab, setActiveTab] = useState<TabKey>('personal');
  const [personalPhotos, setPersonalPhotos] = useState<PhotoItem[]>([]);
  const [familyPhotos, setFamilyPhotos] = useState<PhotoItem[]>([]);
  const [isLoadingPhotos, setIsLoadingPhotos] = useState(false);
  const [listError, setListError] = useState<string | null>(null);
  const [previewPhoto, setPreviewPhoto] = useState<PhotoItem | null>(null);

  const [clusterFiles, setClusterFiles] = useState<File[]>([]);
  const [clusterPreviews, setClusterPreviews] = useState<string[]>([]);
  const [stage, setStage] = useState<Stage>('idle');
  const [clusterResult, setClusterResult] = useState<PhotoClusterResult | null>(null);
  const [clusterError, setClusterError] = useState<string | null>(null);
  const [clusterWarning, setClusterWarning] = useState<string | null>(null);
  const clusterPreviewsRef = useRef<string[]>([]);

  const loadPhotos = useCallback(async () => {
    setIsLoadingPhotos(true);
    setListError(null);
    try {
      const [personal, family] = await Promise.all([
        photoApi.listMyPhotos(100),
        activeFamilyId ? photoApi.listFamilyPhotos(Number(activeFamilyId), 100) : Promise.resolve([]),
      ]);
      setPersonalPhotos(personal);
      setFamilyPhotos(family);
    } catch (nextError) {
      setListError(nextError instanceof Error ? nextError.message : '相册加载失败。');
    } finally {
      setIsLoadingPhotos(false);
    }
  }, [activeFamilyId]);

  useEffect(() => {
    void loadPhotos();
  }, [loadPhotos]);

  useEffect(() => {
    clusterPreviewsRef.current = clusterPreviews;
  }, [clusterPreviews]);

  useEffect(() => () => {
    clusterPreviewsRef.current.forEach((preview) => URL.revokeObjectURL(preview));
  }, []);

  const uploadToScope = useCallback(async (scope: PhotoScope, files: File[]) => {
    if (!activeFamilyId) {
      throw new Error('请先选择一个家庭。');
    }
    const uploaded = await photoApi.uploadPhotos(Number(activeFamilyId), files, scope);
    if (scope === 'PERSONAL') {
      setPersonalPhotos((current) => [...uploaded, ...current]);
    } else {
      setFamilyPhotos((current) => [...uploaded, ...current]);
    }
  }, [activeFamilyId]);

  const deletePhoto = useCallback(async (photoId: number) => {
    await photoApi.deletePhoto(photoId);
    setPersonalPhotos((current) => current.filter((photo) => photo.id !== photoId));
    setFamilyPhotos((current) => current.filter((photo) => photo.id !== photoId));
    setPreviewPhoto((current) => (current?.id === photoId ? null : current));
  }, []);

  const addClusterFiles = useCallback((incoming: FileList | null) => {
    if (!incoming) return;
    const files = Array.from(incoming);
    const nextFiles = [...clusterFiles, ...files];
    const validationError = validateImageFiles(nextFiles);
    if (validationError) {
      setClusterError(validationError);
      return;
    }
    setClusterError(null);
    setClusterWarning(null);
    setClusterFiles(nextFiles);
    setClusterPreviews((current) => [
      ...current,
      ...files.map((file) => URL.createObjectURL(file)),
    ]);
  }, [clusterFiles]);

  const removeClusterFile = useCallback((index: number) => {
    const preview = clusterPreviews[index];
    if (preview) URL.revokeObjectURL(preview);
    setClusterFiles((current) => current.filter((_, idx) => idx !== index));
    setClusterPreviews((current) => current.filter((_, idx) => idx !== index));
    setClusterError(null);
    setClusterWarning(null);
  }, [clusterPreviews]);

  const resetCluster = useCallback(() => {
    clusterPreviews.forEach((preview) => URL.revokeObjectURL(preview));
    setClusterFiles([]);
    setClusterPreviews([]);
    setClusterResult(null);
    setClusterError(null);
    setClusterWarning(null);
    setStage('idle');
  }, [clusterPreviews]);

  const runCluster = useCallback(async () => {
    const validationError = validateImageFiles(clusterFiles, MIN_CLUSTER_FILES);
    if (validationError) {
      setClusterError(validationError);
      return;
    }
    if (!activeFamilyId) {
      setClusterError('请先选择一个家庭。');
      return;
    }

    setClusterError(null);
    setClusterWarning(null);
    setClusterResult(null);
    setStage('uploading');

    let photos: PhotoItem[];
    try {
      photos = await photoApi.uploadPhotos(Number(activeFamilyId), clusterFiles, 'FAMILY');
      setFamilyPhotos((current) => [...photos, ...current]);
    } catch (nextError) {
      setClusterError(nextError instanceof Error ? nextError.message : '上传失败，请重试。');
      setStage('idle');
      return;
    }

    setStage('clustering');
    try {
      const nextResult = await clusterByUrls(
        photos.map((photo) => photo.assetUrl),
        photos.map((photo) => photo.id),
      );
      setClusterResult(nextResult);

      const warnings: string[] = [];
      const clusterFailureSummary = summarizeClusterFailures(nextResult.failed_photos);
      if (clusterFailureSummary) {
        warnings.push(clusterFailureSummary);
      }

      const saveResults = await Promise.allSettled(
        photos.map((photo) => photoApi.saveClusterResult(photo.id, nextResult)),
      );
      const failedSaves = saveResults.filter((item) => item.status === 'rejected').length;
      if (failedSaves > 0) {
        warnings.push(`有 ${failedSaves} 张图片的结果未能保存。`);
      }
      if (warnings.length > 0) {
        setClusterWarning(warnings.join(' '));
      }
    } catch (nextError) {
      setClusterError(nextError instanceof Error ? nextError.message : '人脸聚类失败，请重试。');
    } finally {
      setStage('done');
    }
  }, [activeFamilyId, clusterFiles]);

  const savedClusterResult = useMemo(
    () => familyPhotos.find((photo) => photo.metadata?.groups?.length)?.metadata ?? null,
    [familyPhotos],
  );
  const familyPhotosById = useMemo(
    () => new Map(familyPhotos.map((photo) => [photo.id, photo])),
    [familyPhotos],
  );
  const isClusterLoading = stage === 'uploading' || stage === 'clustering';

  const tabs = [
    { value: 'personal' as TabKey, label: '我的相册', icon: <UserRound className="h-4 w-4" /> },
    { value: 'family' as TabKey, label: activeFamily?.name || '家庭相册', icon: <UsersRound className="h-4 w-4" /> },
    { value: 'faces' as TabKey, label: '人脸分类', icon: <Images className="h-4 w-4" /> },
  ];

  return (
    <WorkbenchPage>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <nav className="flex min-w-0 gap-1 overflow-x-auto rounded-md bg-stone-100 p-1">
          {tabs.map((tab) => {
            const active = activeTab === tab.value;
            return (
              <button
                key={tab.value}
                type="button"
                onClick={() => setActiveTab(tab.value)}
                className={`inline-flex h-9 shrink-0 items-center gap-2 rounded-md px-3 text-sm font-medium transition ${
                  active
                    ? 'bg-stone-950 text-white shadow-sm'
                    : 'text-stone-600 hover:bg-white hover:text-stone-950'
                }`}
              >
                {tab.icon}
                {tab.label}
              </button>
            );
          })}
        </nav>
        {isLoadingPhotos && (
          <span className="inline-flex h-10 items-center justify-center gap-2 rounded-md bg-stone-100 px-3 text-sm text-stone-500 sm:shrink-0">
            <RefreshCw className="h-4 w-4 animate-spin" />
            加载中
          </span>
        )}
      </div>

      {listError && <WorkbenchAlert tone="danger">{listError}</WorkbenchAlert>}

      {activeTab === 'personal' && (
        <WorkbenchSurface className="space-y-4">
          <UploadStrip
            label="上传到我的相册"
            disabled={!activeFamilyId}
            onUpload={(files) => uploadToScope('PERSONAL', files)}
          />
          <PhotoGrid photos={personalPhotos} emptyText="还没有个人照片" onDelete={deletePhoto} onSelect={setPreviewPhoto} />
        </WorkbenchSurface>
      )}

      {activeTab === 'family' && (
        <WorkbenchSurface className="space-y-4">
          <UploadStrip
            label="上传到家庭相册"
            disabled={!activeFamilyId}
            onUpload={(files) => uploadToScope('FAMILY', files)}
          />
          <PhotoGrid photos={familyPhotos} emptyText="还没有家庭照片" onDelete={deletePhoto} onSelect={setPreviewPhoto} />
        </WorkbenchSurface>
      )}

      {activeTab === 'faces' && (
        <WorkbenchSurface className="space-y-5">
          {!clusterResult && (
            <>
              <label className="flex cursor-pointer flex-col items-center justify-center gap-3 rounded-md border-2 border-dashed border-stone-300 bg-stone-50 py-12 transition hover:border-emerald-300 hover:bg-emerald-50">
                <Upload className="h-8 w-8 text-stone-400" />
                <span className="text-sm text-stone-500">至少 {MIN_CLUSTER_FILES} 张，单张 10 MB，总计 200 MB</span>
                <input
                  type="file"
                  accept="image/*"
                  multiple
                  className="sr-only"
                  onChange={(event) => {
                    addClusterFiles(event.target.files);
                    event.target.value = '';
                  }}
                />
              </label>

              {clusterPreviews.length > 0 && (
                <div className="grid grid-cols-3 gap-3 sm:grid-cols-4 lg:grid-cols-6">
                  {clusterPreviews.map((src, index) => (
                    <div key={`${src}-${index}`} className="group relative aspect-square overflow-hidden rounded-md border border-stone-200 bg-stone-100">
                      <img src={src} alt="" className="h-full w-full object-cover" />
                      <button
                        type="button"
                        onClick={() => removeClusterFile(index)}
                        className="absolute right-1 top-1 flex h-6 w-6 items-center justify-center rounded-md bg-black/60 text-white opacity-0 transition group-hover:opacity-100"
                        aria-label="移除图片"
                      >
                        <X className="h-3 w-3" />
                      </button>
                    </div>
                  ))}
                </div>
              )}

              {clusterError && <WorkbenchAlert tone="danger">{clusterError}</WorkbenchAlert>}
              {clusterWarning && <WorkbenchAlert tone="warning">{clusterWarning}</WorkbenchAlert>}

              <WorkbenchButton
                type="button"
                onClick={runCluster}
                disabled={isClusterLoading || clusterFiles.length < MIN_CLUSTER_FILES}
              >
                {isClusterLoading ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Images className="h-4 w-4" />}
                {stage === 'uploading' ? '上传中' : stage === 'clustering' ? '聚类中' : `按人物分类（${clusterFiles.length}）`}
              </WorkbenchButton>

              {savedClusterResult && (
                <ClusterResultSummary
                  clusterResult={savedClusterResult}
                  photosById={familyPhotosById}
                  title="最近一次已保存分类"
                />
              )}
            </>
          )}

          {clusterResult && (
            <div className="space-y-5">
              <div className="flex justify-end">
                <WorkbenchButton type="button" onClick={resetCluster} variant="secondary" size="sm">
                  <X className="h-4 w-4" />
                  重置
                </WorkbenchButton>
              </div>

              {clusterWarning && <WorkbenchAlert tone="warning">{clusterWarning}</WorkbenchAlert>}
              {clusterError && <WorkbenchAlert tone="danger">{clusterError}</WorkbenchAlert>}
              <ClusterResultSummary clusterResult={clusterResult} previewUrls={clusterPreviews} title="本次分类结果" />
            </div>
          )}
        </WorkbenchSurface>
      )}
      <PhotoPreviewDialog photo={previewPhoto} onClose={() => setPreviewPhoto(null)} onDelete={deletePhoto} />
    </WorkbenchPage>
  );
}
