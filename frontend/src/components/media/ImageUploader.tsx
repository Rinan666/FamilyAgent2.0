'use client';

/* eslint-disable @next/next/no-img-element */

import { useCallback, useEffect, useState } from 'react';
import { Image as ImageIcon, RefreshCw, Trash2, Upload } from 'lucide-react';
import { mediaApi } from '@/lib/api/media';
import type { MediaAttachment, MediaRecordType } from '@/types';

const MAX_FILES = 10;
const MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;
const MAX_TOTAL_SIZE_BYTES = 40 * 1024 * 1024;

function getToken() {
  return typeof window !== 'undefined' ? localStorage.getItem('token') : null;
}

function authHeadersForMediaUrl(assetUrl: string): HeadersInit {
  const token = getToken();
  if (!token || typeof window === 'undefined') return {};

  try {
    const url = new URL(assetUrl, window.location.origin);
    const trustedMediaPath = url.origin === window.location.origin
      && /^\/api\/media\/\d+\/content$/.test(url.pathname);
    return trustedMediaPath ? { Authorization: token } : {};
  } catch {
    return {};
  }
}

function validateImageFiles(files: File[]) {
  if (files.length === 0) return '请选择图片。';
  if (files.length > MAX_FILES) return `一次最多可上传 ${MAX_FILES} 张图片。`;
  const nonImage = files.find((file) => !file.type.startsWith('image/'));
  if (nonImage) return `${nonImage.name} 不是图片文件。`;
  const tooLarge = files.find((file) => file.size > MAX_FILE_SIZE_BYTES);
  if (tooLarge) return `${tooLarge.name} 超过 10 MB。`;
  const totalBytes = files.reduce((sum, file) => sum + file.size, 0);
  return totalBytes > MAX_TOTAL_SIZE_BYTES ? '所选图片总大小超过 40 MB。' : null;
}

function AuthMediaImage({ attachment }: { attachment: MediaAttachment }) {
  const [src, setSrc] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    let objectUrl: string | null = null;

    async function loadImage() {
      setSrc(null);
      try {
        const res = await fetch(attachment.assetUrl, {
          headers: authHeadersForMediaUrl(attachment.assetUrl),
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
  }, [attachment.assetUrl]);

  if (!src) {
    return (
      <div className="flex h-full w-full items-center justify-center bg-gray-100 text-gray-400">
        <ImageIcon className="h-5 w-5" />
      </div>
    );
  }

  return <img src={src} alt={attachment.originalName || ''} className="h-full w-full object-cover" />;
}

export function ImageUploader({
  recordType,
  recordId,
  attachments,
  disabled,
  onChange,
}: {
  recordType: MediaRecordType;
  recordId: number;
  attachments: MediaAttachment[];
  disabled?: boolean;
  onChange: (attachments: MediaAttachment[]) => void;
}) {
  const [isUploading, setIsUploading] = useState(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);
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
      const uploaded = await mediaApi.uploadMedia(recordType, recordId, files);
      onChange([...uploaded, ...attachments]);
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : '上传失败，请重试。');
    } finally {
      setIsUploading(false);
    }
  }, [attachments, disabled, onChange, recordId, recordType]);

  const deleteAttachment = useCallback(async (attachmentId: number) => {
    setDeletingId(attachmentId);
    setError(null);
    try {
      await mediaApi.deleteMedia(attachmentId);
      onChange(attachments.filter((attachment) => attachment.id !== attachmentId));
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : '删除失败，请重试。');
    } finally {
      setDeletingId(null);
    }
  }, [attachments, onChange]);

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-3">
        <label className={`inline-flex h-9 items-center gap-2 rounded-md px-3 text-sm font-medium text-white ${disabled ? 'cursor-not-allowed bg-gray-300' : 'cursor-pointer bg-blue-600 hover:bg-blue-700'}`}>
          {isUploading ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />}
          {isUploading ? '上传中' : '上传图片'}
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

      {attachments.length > 0 && (
        <div className="grid grid-cols-3 gap-2 sm:grid-cols-4 lg:grid-cols-6">
          {attachments.map((attachment) => (
            <div key={attachment.id} className="group relative aspect-square overflow-hidden rounded-md border border-gray-200 bg-white">
              <AuthMediaImage attachment={attachment} />
              <button
                type="button"
                aria-label="删除图片"
                disabled={deletingId === attachment.id}
                onClick={() => {
                  void deleteAttachment(attachment.id);
                }}
                className="absolute right-1 top-1 flex h-7 w-7 items-center justify-center rounded-md bg-black/65 text-white opacity-0 transition hover:bg-red-600 group-hover:opacity-100 disabled:opacity-60"
              >
                {deletingId === attachment.id
                  ? <RefreshCw className="h-3.5 w-3.5 animate-spin" />
                  : <Trash2 className="h-3.5 w-3.5" />}
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
