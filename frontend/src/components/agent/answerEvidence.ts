import type { RagRecallSource } from '@/types';

export function personalMemoryEvidenceLabel(source: RagRecallSource) {
  if (source.sourceType !== 'PERSONAL_MEMORY') return '';
  if (!source.author) return '个人记忆';
  return source.author.currentViewer ? '我的个人记忆' : '家人分享给我的';
}
