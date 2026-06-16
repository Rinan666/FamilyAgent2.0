'use client';

/* eslint-disable @next/next/no-img-element */

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Image as ImageIcon, Images, RefreshCw, Trash2, Upload, UserRound, UsersRound, X } from 'lucide-react';
import { photoApi } from '@/lib/api/photos';
import { useViewerRole } from '@/hooks/useViewerRole';
import type { PhotoItem, PhotoScope } from '@/types';

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

type Stage = 'idle' | 'uploading' | 'clustering' | 'done';
type TabKey = 'personal' | 'family' | 'faces';

const MIN_CLUSTER_FILES = 2;
const MAX_FILES = 50;
const MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;
const MAX_TOTAL_SIZE_BYTES = 200 * 1024 * 1024;

function getToken() {
  return typeof window !== 'undefined' ? localStorage.getItem('token') : null;
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

async function clusterByUrls(urls: string[], photoIds: number[]): Promise<ClusterResult> {
  const token = getToken();

  const res = await fetch('/ai-proxy/dip/faces/cluster-by-urls', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: token } : {}),
    },
    body: JSON.stringify({ urls, photo_ids: photoIds }),
  });
  const data = await res.json().catch(() => null);
  if (!res.ok || !data?.success) {
    throw new Error(data?.detail || '人脸聚类失败。');
  }
  return data as ClusterResult;
}

function AuthImage({ photo, className }: { photo: PhotoItem; className?: string }) {
  const [src, setSrc] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    let objectUrl: string | null = null;

    async function loadImage() {
      const token = getToken();
      const res = await fetch(photo.assetUrl, {
        headers: token ? { Authorization: token } : {},
        cache: 'no-store',
      });
      if (!res.ok) return;
      const blob = await res.blob();
      if (cancelled) return;
      objectUrl = URL.createObjectURL(blob);
      setSrc(objectUrl);
    }

    void loadImage();
    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [photo.assetUrl]);

  if (!src) {
    return (
      <div className="flex h-full w-full items-center justify-center bg-gray-100 text-gray-400">
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
      <label className={`inline-flex h-10 items-center gap-2 rounded-md px-4 text-sm font-medium text-white ${disabled ? 'cursor-not-allowed bg-gray-300' : 'cursor-pointer bg-blue-600 hover:bg-blue-700'}`}>
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
}: {
  photos: PhotoItem[];
  emptyText: string;
  onDelete: (photoId: number) => Promise<void>;
}) {
  const [deletingId, setDeletingId] = useState<number | null>(null);

  if (photos.length === 0) {
    return (
      <div className="flex min-h-64 items-center justify-center rounded-md border border-dashed border-gray-300 bg-gray-50 text-sm text-gray-500">
        {emptyText}
      </div>
    );
  }

  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5 xl:grid-cols-6">
      {photos.map((photo) => (
        <div key={photo.id} className="group overflow-hidden rounded-md border border-gray-200 bg-white">
          <div className="relative aspect-square bg-gray-100">
            <AuthImage photo={photo} />
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
            <p className="truncate text-sm font-medium text-gray-800">{photo.originalName || `Photo ${photo.id}`}</p>
            <p className="text-xs text-gray-500">
              {[formatDate(photo.createdAt), formatBytes(photo.fileSize)].filter(Boolean).join(' · ')}
            </p>
          </div>
        </div>
      ))}
    </div>
  );
}

function FaceCrop({ face, src }: { face: FaceMeta; src?: string }) {
  if (!src) {
    return <div className="aspect-square rounded-md border border-gray-200 bg-gray-100" />;
  }

  const size = 80;
  const scale = size / Math.max(face.bbox.w, 1);

  return (
    <div
      style={{ width: size, height: size, overflow: 'hidden', position: 'relative' }}
      className="rounded-md border border-gray-200 bg-gray-100"
    >
      <img
        src={src}
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
}

export default function AlbumPage() {
  const { activeFamilyId, activeFamily } = useViewerRole();
  const [activeTab, setActiveTab] = useState<TabKey>('personal');
  const [personalPhotos, setPersonalPhotos] = useState<PhotoItem[]>([]);
  const [familyPhotos, setFamilyPhotos] = useState<PhotoItem[]>([]);
  const [isLoadingPhotos, setIsLoadingPhotos] = useState(false);
  const [listError, setListError] = useState<string | null>(null);

  const [clusterFiles, setClusterFiles] = useState<File[]>([]);
  const [clusterPreviews, setClusterPreviews] = useState<string[]>([]);
  const [stage, setStage] = useState<Stage>('idle');
  const [clusterResult, setClusterResult] = useState<ClusterResult | null>(null);
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

      const saveResults = await Promise.allSettled(
        photos.map((photo) => photoApi.saveClusterResult(photo.id, nextResult)),
      );
      const failedSaves = saveResults.filter((item) => item.status === 'rejected').length;
      if (failedSaves > 0) {
        setClusterWarning(`聚类已完成，但有 ${failedSaves} 张图片的结果未能保存。`);
      }
    } catch (nextError) {
      setClusterError(nextError instanceof Error ? nextError.message : '人脸聚类失败，请重试。');
    } finally {
      setStage('done');
    }
  }, [activeFamilyId, clusterFiles]);

  const validGroups = useMemo(
    () => clusterResult?.groups.filter((group) => group.group_id !== -1) ?? [],
    [clusterResult],
  );
  const noiseGroup = clusterResult?.groups.find((group) => group.group_id === -1);
  const isClusterLoading = stage === 'uploading' || stage === 'clustering';

  const tabs = [
    { key: 'personal' as const, label: '我的相册', icon: UserRound },
    { key: 'family' as const, label: activeFamily?.name || '家庭相册', icon: UsersRound },
    { key: 'faces' as const, label: '人脸分类', icon: Images },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Images className="h-6 w-6 text-blue-600" />
          <div>
            <h1 className="text-xl font-semibold text-gray-900">相册</h1>
            <p className="text-sm text-gray-500">{activeFamily?.name || '个人与家庭照片空间'}</p>
          </div>
        </div>
        {isLoadingPhotos && (
          <span className="inline-flex items-center gap-2 text-sm text-gray-500">
            <RefreshCw className="h-4 w-4 animate-spin" />
            加载中
          </span>
        )}
      </div>

      <div className="flex border-b border-gray-200">
        {tabs.map((tab) => {
          const Icon = tab.icon;
          const isActive = activeTab === tab.key;
          return (
            <button
              key={tab.key}
              type="button"
              onClick={() => setActiveTab(tab.key)}
              className={`flex h-11 items-center gap-2 border-b-2 px-4 text-sm font-medium transition ${isActive ? 'border-blue-600 text-blue-700' : 'border-transparent text-gray-500 hover:text-gray-800'}`}
            >
              <Icon className="h-4 w-4" />
              {tab.label}
            </button>
          );
        })}
      </div>

      {listError && <p className="rounded-md bg-red-50 px-4 py-3 text-sm text-red-600">{listError}</p>}

      {activeTab === 'personal' && (
        <div className="space-y-4">
          <UploadStrip
            label="上传到我的相册"
            disabled={!activeFamilyId}
            onUpload={(files) => uploadToScope('PERSONAL', files)}
          />
          <PhotoGrid photos={personalPhotos} emptyText="还没有个人照片" onDelete={deletePhoto} />
        </div>
      )}

      {activeTab === 'family' && (
        <div className="space-y-4">
          <UploadStrip
            label="上传到家庭相册"
            disabled={!activeFamilyId}
            onUpload={(files) => uploadToScope('FAMILY', files)}
          />
          <PhotoGrid photos={familyPhotos} emptyText="还没有家庭照片" onDelete={deletePhoto} />
        </div>
      )}

      {activeTab === 'faces' && (
        <div className="space-y-5">
          {!clusterResult && (
            <>
              <label className="flex cursor-pointer flex-col items-center justify-center gap-3 rounded-md border-2 border-dashed border-gray-300 bg-gray-50 py-12 transition hover:border-blue-400 hover:bg-blue-50">
                <Upload className="h-8 w-8 text-gray-400" />
                <span className="text-sm text-gray-500">至少 {MIN_CLUSTER_FILES} 张，单张 10 MB，总计 200 MB</span>
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
                    <div key={`${src}-${index}`} className="group relative aspect-square overflow-hidden rounded-md border border-gray-200 bg-gray-100">
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

              {clusterError && <p className="rounded-md bg-red-50 px-4 py-3 text-sm text-red-600">{clusterError}</p>}
              {clusterWarning && <p className="rounded-md bg-amber-50 px-4 py-3 text-sm text-amber-700">{clusterWarning}</p>}

              <button
                type="button"
                onClick={runCluster}
                disabled={isClusterLoading || clusterFiles.length < MIN_CLUSTER_FILES}
                className="inline-flex h-10 items-center gap-2 rounded-md bg-blue-600 px-5 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
              >
                {isClusterLoading ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Images className="h-4 w-4" />}
                {stage === 'uploading' ? '上传中' : stage === 'clustering' ? '聚类中' : `按人物分类（${clusterFiles.length}）`}
              </button>
            </>
          )}

          {clusterResult && (
            <div className="space-y-5">
              <div className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-gray-200 bg-white px-4 py-3 text-sm text-gray-600">
                <div className="flex flex-wrap gap-4">
                  <span>人脸 <strong className="text-gray-900">{clusterResult.total_faces}</strong></span>
                  <span>人物 <strong className="text-gray-900">{validGroups.length}</strong></span>
                  {clusterResult.silhouette_score !== null && (
                    <span>轮廓系数 <strong className="text-gray-900">{clusterResult.silhouette_score.toFixed(3)}</strong></span>
                  )}
                </div>
                <button type="button" onClick={resetCluster} className="inline-flex h-8 items-center gap-2 rounded-md border border-gray-200 px-3 text-sm text-gray-600 hover:bg-gray-50">
                  <X className="h-4 w-4" />
                  重置
                </button>
              </div>

              {clusterWarning && <p className="rounded-md bg-amber-50 px-4 py-3 text-sm text-amber-700">{clusterWarning}</p>}
              {clusterError && <p className="rounded-md bg-red-50 px-4 py-3 text-sm text-red-600">{clusterError}</p>}

              {validGroups.map((group, index) => (
                <div key={group.group_id} className="rounded-md border border-gray-200 bg-white p-4">
                  <div className="mb-3 flex items-center gap-2">
                    <UserRound className="h-4 w-4 text-gray-400" />
                    <span className="text-sm font-medium text-gray-700">人物 {index + 1}（{group.faces.length} 张）</span>
                  </div>
                  <div className="grid grid-cols-3 gap-2 sm:grid-cols-5 lg:grid-cols-8">
                    {group.faces.map((face, faceIndex) => (
                      <FaceCrop
                        key={`${group.group_id}-${faceIndex}`}
                        face={face}
                        src={clusterPreviews[face.file_index]}
                      />
                    ))}
                  </div>
                </div>
              ))}

              {noiseGroup && noiseGroup.faces.length > 0 && (
                <div className="rounded-md border border-gray-200 bg-gray-50 p-4">
                  <h2 className="mb-3 text-sm font-medium text-gray-500">未归类（{noiseGroup.faces.length}）</h2>
                  <div className="grid grid-cols-3 gap-2 sm:grid-cols-5 lg:grid-cols-8">
                    {noiseGroup.faces.map((face, faceIndex) => (
                      <div key={`noise-${faceIndex}`} className="aspect-square overflow-hidden rounded-md border border-gray-200 bg-gray-100">
                        <img src={clusterPreviews[face.file_index]} alt="" className="h-full w-full object-cover" />
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
