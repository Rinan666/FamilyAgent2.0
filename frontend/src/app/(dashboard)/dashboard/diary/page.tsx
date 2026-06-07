'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { BookHeart, CheckCircle, Edit3, Lightbulb, Lock, RefreshCw, Save, ScrollText, Sparkles, Trash2, Users, X } from 'lucide-react';
import { diaryApi, memoryApi } from '@/lib/api';
import { useAuthStore } from '@/stores/authStore';
import { useViewerRole } from '@/hooks/useViewerRole';
import VoiceInputButton from '@/components/voice/VoiceInputButton';
import type { DiaryEntry, DiaryEntryType, DiaryVisibility, MemoryEntryType, MemoryScope } from '@/types';

const entryTypeOptions: { value: DiaryEntryType; label: string }[] = [
  { value: 'DAILY', label: '日常记录' },
  { value: 'IMPORTANT_EVENT', label: '重要事件' },
  { value: 'LESSON', label: '经验教训' },
  { value: 'EMOTION', label: '情绪想法' },
  { value: 'MESSAGE_TO_FAMILY', label: '给家人的话' },
  { value: 'SELF_REFLECTION', label: '自我复盘' },
];

const visibilityOptions: { value: DiaryVisibility; label: string; note: string }[] = [
  { value: 'PRIVATE', label: '仅自己可见', note: '只作为个人长期记忆' },
  { value: 'FAMILY_VISIBLE', label: '全家可见', note: '家族成员可读' },
  { value: 'CARE_VISIBLE', label: '照护授权可见', note: '本人、家庭管理员和你明确授权的照护成员可读' },
  { value: 'LEGACY_VISIBLE', label: '传承预留', note: '暂按仅自己可见，未来支持授权后开放' },
];

const moodOptions = ['平静', '开心', '焦虑', '疲惫', '感激', '困惑', '难过', '有收获'];

const diaryTemplates: {
  id: string;
  label: string;
  entryType: DiaryEntryType;
  visibility: DiaryVisibility;
  title: string;
  content: string;
  tags: string;
  mood?: string;
}[] = [
  {
    id: 'daily',
    label: '日常片段',
    entryType: 'DAILY',
    visibility: 'FAMILY_VISIBLE',
    title: '今天的一个片段',
    content: '今天发生了：\n\n我当时的感受是：\n\n这件事以后回看，可能值得记住的是：',
    tags: '日常 家族记忆',
    mood: '平静',
  },
  {
    id: 'choice',
    label: '一次选择',
    entryType: 'SELF_REFLECTION',
    visibility: 'PRIVATE',
    title: '一次选择的复盘',
    content: '我面对的选择是：\n\n我当时为什么这样选：\n\n结果如何：\n\n如果再来一次，我会提醒自己：',
    tags: '选择 复盘',
  },
  {
    id: 'family-message',
    label: '给家人的话',
    entryType: 'MESSAGE_TO_FAMILY',
    visibility: 'FAMILY_VISIBLE',
    title: '想留给家人的一句话',
    content: '我想对家人说：\n\n这句话背后的原因是：\n\n希望以后你们看到时能记得：',
    tags: '给家人的话 传承',
    mood: '感激',
  },
  {
    id: 'care',
    label: '成长观察',
    entryType: 'IMPORTANT_EVENT',
    visibility: 'CARE_VISIBLE',
    title: '一次成长观察',
    content: '我观察到的现象是：\n\n可能相关的原因：\n\n这周可以轻轻尝试的行动：\n\n下次回看时间：',
    tags: '成长观察 照护',
  },
  {
    id: 'lesson',
    label: '经验教训',
    entryType: 'LESSON',
    visibility: 'FAMILY_VISIBLE',
    title: '一个想传下去的经验',
    content: '这件事发生在：\n\n我踩过的坑或学到的经验是：\n\n如果后辈遇到类似情况，我建议：',
    tags: '经验教训 长辈经验',
    mood: '有收获',
  },
];

type DiaryTemplate = typeof diaryTemplates[number];

interface DiaryDraft {
  entryType: DiaryEntryType;
  visibility: DiaryVisibility;
  title: string;
  content: string;
  mood: string;
  tagText: string;
  relatedUserId?: number;
  relatedMemberName?: string;
  updatedAt: string;
}

const DRAFT_VERSION = 'v1';

function draftKey(userId?: number, familyId?: number | null, relatedUserId?: number | null) {
  if (!userId || !familyId) return '';
  return `familyagent:diary-draft:${DRAFT_VERSION}:${userId}:${familyId}:${relatedUserId || 'self'}`;
}

function entryTypeLabel(value?: string) {
  return entryTypeOptions.find((option) => option.value === value)?.label || '日记';
}

function visibilityLabel(value?: string) {
  return visibilityOptions.find((option) => option.value === value)?.label || '仅自己可见';
}

function diaryTitle(entry: DiaryEntry) {
  return entry.structured?.title || entry.structured?.summary || entry.rawText.slice(0, 32) || '未命名记录';
}

function formatTags(raw: string) {
  return raw
    .split(/[,，\s]+/)
    .map((item) => item.trim())
    .filter(Boolean)
    .slice(0, 8);
}

function contentQuality(content: string, title: string, tagText: string) {
  const length = content.trim().length;
  const tagCount = formatTags(tagText).length;
  const signals = [
    length >= 80,
    Boolean(title.trim()),
    tagCount > 0,
    /感受|原因|结果|建议|提醒|下次|以后/.test(content),
  ].filter(Boolean).length;

  if (signals >= 3) {
    return {
      label: '适合进入长期记忆',
      note: '这条记录有场景、有线索，后续镜像参考会更稳。',
      level: 'GOOD',
    };
  }
  if (length >= 20) {
    return {
      label: '可以保存',
      note: '补一点原因、感受或结果，会更适合以后回看。',
      level: 'OK',
    };
  }
  return {
    label: '还很简短',
    note: '多写一个具体场景，AI 才不容易泛泛而谈。',
    level: 'LIGHT',
  };
}

function inferMemoryType(entry: DiaryEntry): MemoryEntryType {
  const text = `${entry.rawText} ${(entry.tags || []).join(' ')}`.toLowerCase();
  if (/牙|齿|视力|近视|坐姿|体态|睡眠|运动|屏幕|健康/.test(text)) {
    return 'HEALTH_REMINDER';
  }
  if (/风险|担心|提醒|注意|观察|复查|跟进/.test(text)) {
    return 'GROWTH_RISK';
  }
  switch ((entry.structured?.entryType || '').toUpperCase()) {
    case 'LESSON':
      return 'ELDER_ADVICE';
    case 'IMPORTANT_EVENT':
      return 'FAMILY_STORY';
    case 'MESSAGE_TO_FAMILY':
    case 'SELF_REFLECTION':
      return 'VALUE';
    default:
      return 'FAMILY_STORY';
  }
}

function memoryTypeName(type: MemoryEntryType) {
  switch (type) {
    case 'ELDER_ADVICE':
      return '长者建议';
    case 'FAMILY_STORY':
      return '家族故事';
    case 'HEALTH_REMINDER':
      return '健康提醒';
    case 'GROWTH_RISK':
      return '成长风险';
    case 'VALUE':
      return '价值观';
    default:
      return '家族经验';
  }
}

function scopeFromVisibility(visibility?: string): MemoryScope {
  if (visibility === 'CARE_VISIBLE' || visibility === 'PARENT_VISIBLE') return 'CARE_VISIBLE';
  if (visibility === 'PRIVATE' || visibility === 'LEGACY_VISIBLE') return 'PRIVATE';
  return 'FAMILY_VISIBLE';
}

function scenarioFromEntry(entry: DiaryEntry) {
  const tags = entry.tags || [];
  return tags[0] || entry.structured?.title || entryTypeLabel(entry.structured?.entryType);
}

function relatedUserIdFromEntry(entry: DiaryEntry) {
  const value = Number(entry.metadata?.relatedUserId);
  return Number.isFinite(value) && value > 0 ? value : null;
}

function relatedMemberNameFromEntry(entry: DiaryEntry) {
  const value = entry.metadata?.relatedMemberName;
  return typeof value === 'string' && value.trim() ? value.trim() : '';
}

function aiUsageNote(entry: DiaryEntry) {
  switch (entry.visibility) {
    case 'PRIVATE':
    case 'LEGACY_VISIBLE':
      return '仅作者本人可见。默认不会进入其他成员的镜像 Agent 上下文。';
    case 'CARE_VISIBLE':
    case 'PARENT_VISIBLE':
      return '会在本人、家庭管理员和明确授权照护者范围内，作为家庭陪伴 AI 与镜像 Agent 的参考资料。';
    case 'FAMILY_VISIBLE':
    case 'FAMILY':
      return '全家成员可见，会在权限允许时进入家庭陪伴 AI、家族经验沉淀和镜像 Agent 参考上下文。';
    default:
      return '会按照后端权限过滤结果进入 AI 参考上下文。';
  }
}

export default function DiaryPage() {
  const searchParams = useSearchParams();
  const user = useAuthStore((state) => state.user);
  const { families, activeFamilyId, setActiveFamilyId, isLoading: loadingFamilies } = useViewerRole();
  const [selectedFamilyId, setSelectedFamilyId] = useState<number | null>(null);
  const [entries, setEntries] = useState<DiaryEntry[]>([]);
  const [entryType, setEntryType] = useState<DiaryEntryType>('DAILY');
  const [visibility, setVisibility] = useState<DiaryVisibility>('PRIVATE');
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [mood, setMood] = useState('');
  const [tagText, setTagText] = useState('');
  const [titleTouched, setTitleTouched] = useState(false);
  const [contentTouched, setContentTouched] = useState(false);
  const [moodTouched, setMoodTouched] = useState(false);
  const [tagTextTouched, setTagTextTouched] = useState(false);
  const [pendingTemplate, setPendingTemplate] = useState<DiaryTemplate | null>(null);
  const [draftStatus, setDraftStatus] = useState('');
  const [loadingEntries, setLoadingEntries] = useState(false);
  const [saving, setSaving] = useState(false);
  const [organizingDraft, setOrganizingDraft] = useState(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [updatingId, setUpdatingId] = useState<number | null>(null);
  const [promotingId, setPromotingId] = useState<number | null>(null);
  const [promotedDiaryIds, setPromotedDiaryIds] = useState<Set<number>>(new Set());
  const [coreMemoryEntry, setCoreMemoryEntry] = useState<DiaryEntry | null>(null);
  const [coreMemoryReason, setCoreMemoryReason] = useState('');
  const [selectedEntryId, setSelectedEntryId] = useState<number | null>(null);
  const [editingEntryId, setEditingEntryId] = useState<number | null>(null);
  const [editEntryType, setEditEntryType] = useState<DiaryEntryType>('DAILY');
  const [editVisibility, setEditVisibility] = useState<DiaryVisibility>('PRIVATE');
  const [editTitle, setEditTitle] = useState('');
  const [editContent, setEditContent] = useState('');
  const [editMood, setEditMood] = useState('');
  const [editTagText, setEditTagText] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const draftHydratedKeyRef = useRef('');
  const suppressDraftSaveRef = useRef(false);
  const queryTemplateAppliedRef = useRef('');
  const requestedFamilyId = useMemo(() => {
    const value = Number(searchParams.get('familyId'));
    return Number.isFinite(value) && value > 0 ? value : null;
  }, [searchParams]);
  const requestedRelatedUserId = useMemo(() => {
    const value = Number(searchParams.get('relatedUserId'));
    return Number.isFinite(value) && value > 0 ? value : null;
  }, [searchParams]);
  const requestedRelatedMemberName = useMemo(
    () => searchParams.get('relatedMemberName')?.trim() || '',
    [searchParams],
  );
  const relatedContextLabel = requestedRelatedMemberName
    || (requestedRelatedUserId ? `用户 ${requestedRelatedUserId}` : '');

  const appendVoiceTranscript = useCallback((text: string) => {
    setContent((current) => (current.trim() ? `${current.trim()}\n${text}` : text));
    setContentTouched(true);
    setPendingTemplate(null);
  }, []);

  const selectedFamily = useMemo(
    () => families.find((family) => family.id === selectedFamilyId) || null,
    [families, selectedFamilyId],
  );
  const quality = useMemo(
    () => contentQuality(content, title, tagText),
    [content, title, tagText],
  );
  const selectedEntry = useMemo(
    () => entries.find((entry) => entry.id === selectedEntryId) || null,
    [entries, selectedEntryId],
  );
  const editQuality = useMemo(
    () => contentQuality(editContent, editTitle, editTagText),
    [editContent, editTagText, editTitle],
  );

  const loadEntries = useCallback(async (familyId: number) => {
    setLoadingEntries(true);
    setError('');
    try {
      const [data, memories] = await Promise.all([
        diaryApi.listFamilyEntries(familyId, 50),
        memoryApi.listFamilyMemories(familyId, 30),
      ]);
      setEntries(Array.isArray(data) ? data : []);
      setSelectedEntryId((current) => {
        if (current && Array.isArray(data) && data.some((entry) => entry.id === current)) return current;
        return Array.isArray(data) ? data[0]?.id ?? null : null;
      });
      const promotedIds = new Set<number>();
      (Array.isArray(memories) ? memories : []).forEach((memory) => {
        if (memory.metadata?.source !== 'DIARY_PROMOTION') return;
        const id = Number(memory.metadata?.sourceDiaryId);
        if (Number.isFinite(id)) {
          promotedIds.add(id);
        }
      });
      setPromotedDiaryIds(promotedIds);
    } catch (err) {
      setError(err instanceof Error ? err.message : '日记加载失败');
    } finally {
      setLoadingEntries(false);
    }
  }, []);

  useEffect(() => {
    const queryFamilyId = requestedFamilyId && families.some((family) => family.id === requestedFamilyId)
      ? requestedFamilyId
      : null;
    const nextFamilyId = queryFamilyId || (activeFamilyId && families.some((family) => family.id === activeFamilyId)
      ? activeFamilyId
      : families[0]?.id ?? null);
    setSelectedFamilyId(nextFamilyId);
    if (queryFamilyId && activeFamilyId !== queryFamilyId) {
      setActiveFamilyId(queryFamilyId);
    }
  }, [activeFamilyId, families, requestedFamilyId, setActiveFamilyId]);

  useEffect(() => {
    if (selectedFamilyId) {
      void loadEntries(selectedFamilyId);
    }
  }, [loadEntries, selectedFamilyId]);

  useEffect(() => {
    const key = draftKey(user?.id, selectedFamilyId, requestedRelatedUserId);
    draftHydratedKeyRef.current = key;
    suppressDraftSaveRef.current = true;
    setPendingTemplate(null);
    setDraftStatus('');

    if (!key || typeof window === 'undefined') {
      return;
    }

    const raw = localStorage.getItem(key);
    if (!raw) {
      setEntryType('DAILY');
      setVisibility('PRIVATE');
      setTitle('');
      setContent('');
      setMood('');
      setTagText('');
      setTitleTouched(false);
      setContentTouched(false);
      setMoodTouched(false);
      setTagTextTouched(false);
      return;
    }

    try {
      const draft = JSON.parse(raw) as Partial<DiaryDraft>;
      setEntryType(draft.entryType || 'DAILY');
      setVisibility(draft.visibility || 'PRIVATE');
      setTitle(draft.title || '');
      setContent(draft.content || '');
      setMood(draft.mood || '');
      setTagText(draft.tagText || '');
      setTitleTouched(Boolean(draft.title?.trim()));
      setContentTouched(Boolean(draft.content?.trim()));
      setMoodTouched(Boolean(draft.mood?.trim()));
      setTagTextTouched(Boolean(draft.tagText?.trim()));
      setDraftStatus('已恢复未完成草稿');
    } catch {
      localStorage.removeItem(key);
    }
  }, [requestedRelatedUserId, selectedFamilyId, user?.id]);

  useEffect(() => {
    const key = draftKey(user?.id, selectedFamilyId, requestedRelatedUserId);
    if (!key || typeof window === 'undefined' || draftHydratedKeyRef.current !== key) {
      return;
    }

    if (suppressDraftSaveRef.current) {
      suppressDraftSaveRef.current = false;
      return;
    }

    const hasDraftContent = Boolean(title.trim() || content.trim() || mood.trim() || tagText.trim());
    if (!hasDraftContent) {
      localStorage.removeItem(key);
      setDraftStatus('');
      return;
    }

    const timer = window.setTimeout(() => {
      const draft: DiaryDraft = {
        entryType,
        visibility,
        title,
        content,
        mood,
        tagText,
        relatedUserId: requestedRelatedUserId || undefined,
        relatedMemberName: relatedContextLabel || undefined,
        updatedAt: new Date().toISOString(),
      };
      localStorage.setItem(key, JSON.stringify(draft));
      setDraftStatus('草稿已自动保存');
    }, 700);

    return () => window.clearTimeout(timer);
  }, [
    content,
    entryType,
    mood,
    relatedContextLabel,
    requestedRelatedUserId,
    selectedFamilyId,
    tagText,
    title,
    user?.id,
    visibility,
  ]);

  const flashSuccess = (message: string) => {
    setSuccess(message);
    setTimeout(() => setSuccess(''), 3000);
  };

  const applyTemplate = useCallback((template: DiaryTemplate) => {
    setEntryType(template.entryType);
    setVisibility(template.visibility);
    const relatedPrefix = relatedContextLabel ? `关于${relatedContextLabel}的` : '';
    const nextTitle = relatedPrefix && template.id !== 'daily'
      ? `${relatedPrefix}${template.title}`
      : template.title;
    const nextContent = relatedContextLabel
      ? `这条记录关联的家族成员：${relatedContextLabel}\n\n${template.content}`
      : template.content;
    const nextTags = relatedContextLabel
      ? `${template.tags} ${relatedContextLabel}`
      : template.tags;
    setTitle(nextTitle);
    setContent(nextContent);
    setTagText(nextTags);
    setMood(template.mood || '');
    setTitleTouched(false);
    setContentTouched(false);
    setMoodTouched(false);
    setTagTextTouched(false);
    setPendingTemplate(null);
    setSuccess('');
  }, [relatedContextLabel]);

  const handleTemplateClick = (template: DiaryTemplate) => {
    const hasManualEdits = titleTouched || contentTouched || moodTouched || tagTextTouched;
    const hasContent = Boolean(title.trim() || content.trim() || mood.trim() || tagText.trim());
    if (hasManualEdits && hasContent) {
      setPendingTemplate(template);
      return;
    }
    applyTemplate(template);
  };

  useEffect(() => {
    const templateId = searchParams.get('template');
    if (!selectedFamilyId || !templateId) return;
    const key = `${selectedFamilyId}:${templateId}:${requestedRelatedUserId || 'self'}:${relatedContextLabel}`;
    if (queryTemplateAppliedRef.current === key) return;
    const template = diaryTemplates.find((item) => item.id === templateId);
    if (!template) return;
    queryTemplateAppliedRef.current = key;
    applyTemplate(template);
    setDraftStatus(`已套用“${template.label}”模板${relatedContextLabel ? `，关联到${relatedContextLabel}` : ''}`);
  }, [applyTemplate, relatedContextLabel, requestedRelatedUserId, searchParams, selectedFamilyId]);

  const clearDraft = () => {
    const key = draftKey(user?.id, selectedFamilyId, requestedRelatedUserId);
    if (key && typeof window !== 'undefined') {
      localStorage.removeItem(key);
    }
    setEntryType('DAILY');
    setVisibility('PRIVATE');
    setTitle('');
    setContent('');
    setMood('');
    setTagText('');
    setTitleTouched(false);
    setContentTouched(false);
    setMoodTouched(false);
    setTagTextTouched(false);
    setPendingTemplate(null);
    setDraftStatus('');
  };

  const handleSave = async () => {
    if (!selectedFamilyId || !content.trim()) return;
    setSaving(true);
    setError('');
    try {
      await diaryApi.create({
        familyId: selectedFamilyId,
        content,
        entryType,
        title: title || undefined,
        mood: mood || undefined,
        tags: formatTags(tagText),
        visibility,
        metadata: {
          authorName: user?.nickname || user?.username,
          qualityLevel: quality.level,
          qualityLabel: quality.label,
          sourceTemplate: entryType,
          relatedUserId: requestedRelatedUserId || undefined,
          relatedMemberName: relatedContextLabel || undefined,
          relationSource: requestedRelatedUserId ? 'MEMBER_MEMORY_PREFILL' : undefined,
        },
      });
      setTitle('');
      setContent('');
      setMood('');
      setTagText('');
      setTitleTouched(false);
      setContentTouched(false);
      setMoodTouched(false);
      setTagTextTouched(false);
      setPendingTemplate(null);
      const key = draftKey(user?.id, selectedFamilyId, requestedRelatedUserId);
      if (key && typeof window !== 'undefined') {
        localStorage.removeItem(key);
      }
      setDraftStatus('');
      flashSuccess(`人生记录已保存：${quality.label}`);
      await loadEntries(selectedFamilyId);
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存失败');
    } finally {
      setSaving(false);
    }
  };

  const handleOrganizeDraft = async () => {
    if (!content.trim()) return;
    setOrganizingDraft(true);
    setError('');
    try {
      const result = await memoryApi.organizeDraft({
        content,
        scene: 'DIARY',
        familyContext: selectedFamily?.description || selectedFamily?.name || '',
        currentType: entryType,
        currentVisibility: visibility,
        target: relatedContextLabel || '',
      });
      const draft = result.data;
      setTitle(draft.title || title);
      setContent(draft.content || content);
      if (entryTypeOptions.some((option) => option.value === draft.diary_entry_type)) {
        setEntryType(draft.diary_entry_type as DiaryEntryType);
      }
      if (visibilityOptions.some((option) => option.value === draft.diary_visibility)) {
        setVisibility(draft.diary_visibility as DiaryVisibility);
      }
      if (draft.tags?.length) {
        setTagText(draft.tags.join(' '));
      }
      setTitleTouched(true);
      setContentTouched(true);
      setTagTextTouched(true);
      setPendingTemplate(null);
      setDraftStatus(`AI 已整理草稿${draft.reason ? `：${draft.reason}` : ''}`);
      flashSuccess('草稿已整理，可继续修改后保存');
    } catch (err) {
      setError(err instanceof Error ? err.message : '草稿整理失败');
    } finally {
      setOrganizingDraft(false);
    }
  };

  const handleDelete = async (entryId: number) => {
    setDeletingId(entryId);
    setError('');
    try {
      await diaryApi.deleteEntry(entryId);
      setEntries((prev) => prev.filter((entry) => entry.id !== entryId));
      if (selectedEntryId === entryId) {
        setSelectedEntryId(null);
        setEditingEntryId(null);
      }
      flashSuccess('记录已归档');
    } catch (err) {
      setError(err instanceof Error ? err.message : '归档失败');
    } finally {
      setDeletingId(null);
    }
  };

  const startEditingEntry = (entry: DiaryEntry) => {
    setSelectedEntryId(entry.id);
    setEditingEntryId(entry.id);
    setEditEntryType((entry.structured?.entryType || 'DAILY') as DiaryEntryType);
    setEditVisibility((entry.visibility || 'PRIVATE') as DiaryVisibility);
    setEditTitle(entry.structured?.title || '');
    setEditContent(entry.rawText || '');
    setEditMood(entry.mood || '');
    setEditTagText((entry.tags || []).join(' '));
  };

  const cancelEditingEntry = () => {
    setEditingEntryId(null);
    setEditTitle('');
    setEditContent('');
    setEditMood('');
    setEditTagText('');
  };

  const handleUpdateEntry = async () => {
    if (!editingEntryId || !editContent.trim()) return;
    setUpdatingId(editingEntryId);
    setError('');
    try {
      const updated = await diaryApi.updateEntry(editingEntryId, {
        content: editContent,
        entryType: editEntryType,
        title: editTitle || undefined,
        mood: editMood || undefined,
        tags: formatTags(editTagText),
        visibility: editVisibility,
        metadata: {
          qualityLevel: editQuality.level,
          qualityLabel: editQuality.label,
          editedAt: new Date().toISOString(),
        },
      });
      setEntries((prev) => prev.map((entry) => (entry.id === updated.id ? updated : entry)));
      setSelectedEntryId(updated.id);
      cancelEditingEntry();
      flashSuccess('记录已更新，AI 参考索引会自动刷新');
    } catch (err) {
      setError(err instanceof Error ? err.message : '更新失败');
    } finally {
      setUpdatingId(null);
    }
  };

  const handlePromoteToMemory = async (entry: DiaryEntry, options?: { coreMemory?: boolean; coreReason?: string }) => {
    if (!selectedFamilyId) return;
    const memoryType = inferMemoryType(entry);
    const scope = scopeFromVisibility(entry.visibility);
    const isCoreMemory = Boolean(options?.coreMemory);
    setPromotingId(entry.id);
    setError('');
    try {
      await memoryApi.createFamilyMemory({
        familyId: selectedFamilyId,
        content: entry.rawText,
        type: memoryType,
        scope,
        summary: isCoreMemory
          ? `${diaryTitle(entry).slice(0, 90)}｜核心记忆：${(options?.coreReason || '').slice(0, 60)}`
          : diaryTitle(entry).slice(0, 120),
        importance: isCoreMemory ? 5 : memoryType === 'HEALTH_REMINDER' || memoryType === 'GROWTH_RISK' ? 4 : 3,
        metadata: {
          scenario: scenarioFromEntry(entry),
          target: scenarioFromEntry(entry),
          source: 'DIARY_PROMOTION',
          sourceDiaryId: entry.id,
          sourceVisibility: entry.visibility,
          relatedUserId: relatedUserIdFromEntry(entry) || undefined,
          relatedMemberName: relatedMemberNameFromEntry(entry) || undefined,
          coreMemory: isCoreMemory,
          coreReason: options?.coreReason?.trim() || undefined,
          promotedFromDiaryId: entry.id,
          promotedBy: user?.id,
          promotedByName: user?.nickname || user?.username,
          promotedAt: new Date().toISOString(),
        },
      });
      setPromotedDiaryIds((prev) => new Set(prev).add(entry.id));
      if (isCoreMemory) {
        setCoreMemoryEntry(null);
        setCoreMemoryReason('');
      }
      flashSuccess(isCoreMemory ? '已沉淀为核心记忆' : `已沉淀为${memoryTypeName(memoryType)}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : '沉淀为家族经验失败');
    } finally {
      setPromotingId(null);
    }
  };

  const openCoreMemoryDialog = (entry: DiaryEntry) => {
    setCoreMemoryEntry(entry);
    setCoreMemoryReason('');
    setError('');
  };

  const handlePromoteCoreMemory = async () => {
    if (!coreMemoryEntry || !coreMemoryReason.trim()) return;
    await handlePromoteToMemory(coreMemoryEntry, {
      coreMemory: true,
      coreReason: coreMemoryReason.trim(),
    });
  };

  if (loadingFamilies) {
    return (
      <div className="flex h-60 items-center justify-center text-gray-400">
        <RefreshCw className="mr-2 h-5 w-5 animate-spin" />
        加载中...
      </div>
    );
  }

  if (families.length === 0) {
    return (
      <div className="mx-auto max-w-3xl rounded-lg border border-gray-200 bg-white p-10 text-center">
        <Users className="mx-auto mb-3 h-10 w-10 text-gray-300" />
        <h1 className="text-lg font-semibold text-gray-900">还没有家族空间</h1>
        <p className="mt-2 text-sm text-gray-500">先创建或加入家族，再开始记录人生片段。</p>
        <Link
          href="/dashboard/family"
          className="mt-5 inline-flex h-10 items-center rounded-lg bg-blue-600 px-4 text-sm font-medium text-white hover:bg-blue-700"
        >
          前往家族空间
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto w-full max-w-6xl">
      <div className="mb-5 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-xl font-bold text-gray-900">家族日记</h1>
          <p className="mt-1 text-sm text-gray-500">记录人生片段、经验教训和给家人的话，按权限沉淀为家族长期记忆。</p>
        </div>
        <select
          value={selectedFamilyId ?? ''}
          onChange={(event) => {
            const familyId = Number(event.target.value);
            setSelectedFamilyId(familyId);
            setActiveFamilyId(familyId);
          }}
          className="h-10 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
        >
          {families.map((family) => (
            <option key={family.id} value={family.id}>{family.name}</option>
          ))}
        </select>
      </div>

      {error && <div className="mb-4 rounded-lg bg-red-50 px-4 py-2 text-sm text-red-600">{error}</div>}
      {success && (
        <div className="mb-4 flex items-center gap-2 rounded-lg bg-green-50 px-4 py-2 text-sm text-green-700">
          <CheckCircle className="h-4 w-4" />
          {success}
        </div>
      )}

      {coreMemoryEntry && (
        <div className="mb-4 rounded-lg border border-purple-100 bg-purple-50 p-4">
          <div className="mb-3 flex items-start justify-between gap-3">
            <div>
              <h2 className="text-sm font-semibold text-gray-900">沉淀为核心记忆</h2>
              <p className="mt-1 text-xs leading-5 text-purple-700">
                核心记忆会作为家族长期经验或价值观参考，不会因为时间久远而快速淡出。
              </p>
            </div>
            <button
              type="button"
              onClick={() => {
                setCoreMemoryEntry(null);
                setCoreMemoryReason('');
              }}
              className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-purple-600 hover:bg-white"
              aria-label="关闭核心记忆沉淀"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
          <div className="mb-3 rounded-lg bg-white/70 p-3">
            <p className="text-xs font-medium text-gray-700">{diaryTitle(coreMemoryEntry)}</p>
            <p className="mt-1 line-clamp-2 text-xs leading-5 text-gray-500">{coreMemoryEntry.rawText}</p>
          </div>
          <label className="block text-xs font-medium text-gray-600">
            为什么这条记录值得保留下来？
            <textarea
              value={coreMemoryReason}
              onChange={(event) => setCoreMemoryReason(event.target.value)}
              rows={3}
              maxLength={240}
              placeholder="例如：这是一次重要失败后的教训，后辈遇到类似选择时应该先想到它。"
              className="mt-1 w-full resize-none rounded-lg border border-purple-100 bg-white px-3 py-2 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-purple-400"
            />
          </label>
          <div className="mt-3 flex flex-col gap-2 sm:flex-row sm:justify-end">
            <button
              type="button"
              onClick={() => {
                setCoreMemoryEntry(null);
                setCoreMemoryReason('');
              }}
              className="inline-flex h-9 items-center justify-center rounded-lg border border-purple-100 bg-white px-3 text-xs font-medium text-purple-700 hover:bg-purple-100"
            >
              取消
            </button>
            <button
              type="button"
              onClick={handlePromoteCoreMemory}
              disabled={!coreMemoryReason.trim() || promotingId === coreMemoryEntry.id}
              className="inline-flex h-9 items-center justify-center gap-1 rounded-lg bg-purple-600 px-3 text-xs font-medium text-white hover:bg-purple-700 disabled:opacity-50"
            >
              {promotingId === coreMemoryEntry.id ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <ScrollText className="h-3.5 w-3.5" />}
              确认沉淀
            </button>
          </div>
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[0.9fr_1.1fr]">
        <section className="rounded-lg border border-gray-200 bg-white p-4 sm:p-5">
          <div className="mb-4 flex items-center gap-2">
            <BookHeart className="h-5 w-5 text-purple-600" />
            <h2 className="text-sm font-semibold text-gray-900">新增人生记录</h2>
          </div>

          {relatedContextLabel && (
            <div className="mb-4 rounded-lg border border-purple-100 bg-purple-50 p-3">
              <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <p className="text-xs font-semibold text-purple-700">正在补充与 {relatedContextLabel} 相关的记录</p>
                  <p className="mt-1 text-xs leading-5 text-purple-600">
                    保存后会写入关联字段，成员记忆页和镜像资料完整度可据此识别“他人为 TA 补充的记录”。
                  </p>
                </div>
                <Link
                  href={`/dashboard/diary${selectedFamilyId ? `?familyId=${selectedFamilyId}` : ''}`}
                  className="inline-flex h-8 shrink-0 items-center justify-center rounded-lg bg-white px-3 text-xs font-medium text-purple-700 hover:bg-purple-100"
                >
                  取消关联
                </Link>
              </div>
            </div>
          )}

          <div className="mb-4 rounded-lg border border-blue-100 bg-blue-50 p-3">
            <div className="mb-2 flex items-center gap-1.5 text-xs font-medium text-blue-700">
              <Sparkles className="h-3.5 w-3.5" />
              快速起稿
            </div>
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
              {diaryTemplates.map((template) => (
                <button
                  key={template.id}
                  type="button"
                  onClick={() => handleTemplateClick(template)}
                  className="min-h-9 rounded-lg border border-blue-100 bg-white px-2 py-1.5 text-xs font-medium text-blue-700 transition-colors hover:border-blue-200 hover:bg-blue-100"
                >
                  {template.label}
                </button>
              ))}
            </div>
            {pendingTemplate && (
              <div className="mt-3 rounded-lg border border-amber-100 bg-amber-50 p-3">
                <p className="text-xs leading-5 text-amber-700">
                  当前内容已经编辑过。要套用“{pendingTemplate.label}”模板并覆盖标题、内容、心情和标签吗？
                </p>
                <div className="mt-2 flex flex-wrap gap-2">
                  <button
                    type="button"
                    onClick={() => applyTemplate(pendingTemplate)}
                    className="rounded-lg bg-amber-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-amber-700"
                  >
                    套用新模板
                  </button>
                  <button
                    type="button"
                    onClick={() => setPendingTemplate(null)}
                    className="rounded-lg border border-amber-200 bg-white px-3 py-1.5 text-xs font-medium text-amber-700 hover:bg-amber-100"
                  >
                    保留当前内容
                  </button>
                </div>
              </div>
            )}
          </div>

          <div className="mb-3 flex flex-wrap items-center justify-between gap-2 rounded-lg bg-gray-50 px-3 py-2 text-xs text-gray-500">
            <span>{draftStatus || '填写时会自动保存本地草稿'}</span>
            {(title || content || mood || tagText || draftStatus) && (
              <button
                type="button"
                onClick={clearDraft}
                className="font-medium text-gray-500 hover:text-red-600"
              >
                清空草稿
              </button>
            )}
          </div>

          <div className="mb-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
            <label className="text-xs font-medium text-gray-500">
              记录类型
              <select
                value={entryType}
                onChange={(event) => setEntryType(event.target.value as DiaryEntryType)}
                className="mt-1 h-10 w-full rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
              >
                {entryTypeOptions.map((option) => (
                  <option key={option.value} value={option.value}>{option.label}</option>
                ))}
              </select>
            </label>
            <label className="text-xs font-medium text-gray-500">
              可见范围
              <select
                value={visibility}
                onChange={(event) => setVisibility(event.target.value as DiaryVisibility)}
                className="mt-1 h-10 w-full rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
              >
                {visibilityOptions.map((option) => (
                  <option key={option.value} value={option.value}>{option.label}</option>
                ))}
              </select>
              <span className="mt-1 block text-[11px] text-gray-400">
                {visibilityOptions.find((option) => option.value === visibility)?.note}
              </span>
            </label>
          </div>

          <div className="mb-3 rounded-lg bg-gray-50 p-3 text-xs leading-5 text-gray-500">
            <div className="mb-1 flex items-center gap-1.5 font-medium text-gray-700">
              <Lock className="h-3.5 w-3.5" />
              AI 使用边界
            </div>
            这条记录只会在当前可见范围内进入家庭陪伴 AI 和镜像 Agent 的参考上下文。选择“照护授权可见”时，只有本人、家庭管理员和你在家族空间明确授权的照护成员可见。
          </div>

          <label className="mb-3 block text-xs font-medium text-gray-500">
            标题
            <input
              value={title}
              onChange={(event) => {
                setTitle(event.target.value);
                setTitleTouched(true);
                setPendingTemplate(null);
              }}
              placeholder="例如：第一次离家读书、爷爷的一句话"
              className="mt-1 h-10 w-full rounded-lg border border-gray-200 px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
            />
          </label>

          <div className="mb-3">
            <div className="mb-2 flex items-center justify-between gap-3">
              <span className="text-xs font-medium text-gray-500">正文</span>
              <div className="flex flex-wrap justify-end gap-2">
                <VoiceInputButton onTranscript={appendVoiceTranscript} disabled={saving || organizingDraft} />
                <button
                  type="button"
                  onClick={handleOrganizeDraft}
                  disabled={!content.trim() || saving || organizingDraft}
                  className="inline-flex h-9 items-center gap-2 rounded-lg border border-purple-100 bg-purple-50 px-3 text-xs font-medium text-purple-700 hover:bg-purple-100 disabled:opacity-50"
                >
                  {organizingDraft ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <Sparkles className="h-3.5 w-3.5" />}
                  整理草稿
                </button>
              </div>
            </div>
            <textarea
              value={content}
              onChange={(event) => {
                setContent(event.target.value);
                setContentTouched(true);
                setPendingTemplate(null);
              }}
              rows={9}
              placeholder="写下今天发生的事、一次选择、一个教训、一个情绪，或以后想留给家人的话。"
              className="w-full resize-none rounded-lg border border-gray-200 px-4 py-3 text-sm text-gray-800 outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div className="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
            <label className="text-xs font-medium text-gray-500">
              心情
              <select
                value={mood}
                onChange={(event) => {
                  setMood(event.target.value);
                  setMoodTouched(true);
                  setPendingTemplate(null);
                }}
                className="mt-1 h-10 w-full rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="">不记录</option>
                {moodOptions.map((option) => (
                  <option key={option} value={option}>{option}</option>
                ))}
              </select>
            </label>
            <label className="text-xs font-medium text-gray-500">
              标签
              <input
                value={tagText}
                onChange={(event) => {
                  setTagText(event.target.value);
                  setTagTextTouched(true);
                  setPendingTemplate(null);
                }}
                placeholder="用空格或逗号分隔，例如：工作 选择 后悔"
                className="mt-1 h-10 w-full rounded-lg border border-gray-200 px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
              />
            </label>
          </div>

          <div className="mb-4 rounded-lg border border-gray-200 p-3">
            <div className="flex items-center gap-2">
              <Lightbulb className="h-4 w-4 text-amber-500" />
              <span className="text-xs font-semibold text-gray-800">{quality.label}</span>
            </div>
            <p className="mt-1 text-xs leading-5 text-gray-500">{quality.note}</p>
          </div>

          <button
            type="button"
            onClick={handleSave}
            disabled={!selectedFamilyId || !content.trim() || saving}
            className="inline-flex h-10 w-full items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {saving ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
            保存记录
          </button>
        </section>

        <section className="rounded-lg border border-gray-200 bg-white p-4 sm:p-5">
          <div className="mb-4 flex items-center justify-between gap-3">
            <div>
              <h2 className="text-sm font-semibold text-gray-900">{selectedFamily?.name || '家族'}的记忆流</h2>
              <p className="mt-1 text-xs text-gray-500">只显示你有权限查看的记录。</p>
            </div>
            <button
              type="button"
              onClick={() => selectedFamilyId && loadEntries(selectedFamilyId)}
              className="inline-flex h-8 w-8 items-center justify-center rounded-lg text-gray-500 hover:bg-gray-50"
              aria-label="刷新日记"
            >
              <RefreshCw className={`h-4 w-4 ${loadingEntries ? 'animate-spin' : ''}`} />
            </button>
          </div>

          {selectedEntry && (
            <div className="mb-4 rounded-lg border border-blue-100 bg-blue-50 p-4">
              <div className="mb-3 flex items-start justify-between gap-3">
                <div>
                  <h3 className="text-sm font-semibold text-gray-900">{diaryTitle(selectedEntry)}</h3>
                  <p className="mt-1 text-xs text-gray-500">
                    {new Date(selectedEntry.createdAt).toLocaleString('zh-CN')}
                    {selectedEntry.updatedAt ? ` · 更新于 ${new Date(selectedEntry.updatedAt).toLocaleString('zh-CN')}` : ''}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => {
                    setSelectedEntryId(null);
                    setEditingEntryId(null);
                  }}
                  className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-gray-500 hover:bg-white"
                  aria-label="关闭记录详情"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>

              {editingEntryId === selectedEntry.id ? (
                <div className="space-y-3">
                  <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                    <label className="text-xs font-medium text-gray-500">
                      记录类型
                      <select
                        value={editEntryType}
                        onChange={(event) => setEditEntryType(event.target.value as DiaryEntryType)}
                        className="mt-1 h-10 w-full rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
                      >
                        {entryTypeOptions.map((option) => (
                          <option key={option.value} value={option.value}>{option.label}</option>
                        ))}
                      </select>
                    </label>
                    <label className="text-xs font-medium text-gray-500">
                      可见范围
                      <select
                        value={editVisibility}
                        onChange={(event) => setEditVisibility(event.target.value as DiaryVisibility)}
                        className="mt-1 h-10 w-full rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
                      >
                        {visibilityOptions.map((option) => (
                          <option key={option.value} value={option.value}>{option.label}</option>
                        ))}
                      </select>
                    </label>
                  </div>
                  <label className="block text-xs font-medium text-gray-500">
                    标题
                    <input
                      value={editTitle}
                      onChange={(event) => setEditTitle(event.target.value)}
                      className="mt-1 h-10 w-full rounded-lg border border-gray-200 px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </label>
                  <textarea
                    value={editContent}
                    onChange={(event) => setEditContent(event.target.value)}
                    rows={7}
                    className="w-full resize-none rounded-lg border border-gray-200 bg-white px-4 py-3 text-sm text-gray-800 outline-none focus:ring-2 focus:ring-blue-500"
                  />
                  <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                    <label className="text-xs font-medium text-gray-500">
                      心情
                      <select
                        value={editMood}
                        onChange={(event) => setEditMood(event.target.value)}
                        className="mt-1 h-10 w-full rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
                      >
                        <option value="">不记录</option>
                        {moodOptions.map((option) => (
                          <option key={option} value={option}>{option}</option>
                        ))}
                      </select>
                    </label>
                    <label className="text-xs font-medium text-gray-500">
                      标签
                      <input
                        value={editTagText}
                        onChange={(event) => setEditTagText(event.target.value)}
                        className="mt-1 h-10 w-full rounded-lg border border-gray-200 px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </label>
                  </div>
                  <div className="rounded-lg border border-blue-100 bg-white p-3">
                    <div className="flex items-center gap-2">
                      <Lightbulb className="h-4 w-4 text-amber-500" />
                      <span className="text-xs font-semibold text-gray-800">{editQuality.label}</span>
                    </div>
                    <p className="mt-1 text-xs leading-5 text-gray-500">{editQuality.note}</p>
                  </div>
                  <div className="flex flex-col gap-2 sm:flex-row">
                    <button
                      type="button"
                      onClick={handleUpdateEntry}
                      disabled={!editContent.trim() || updatingId === selectedEntry.id}
                      className="inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
                    >
                      {updatingId === selectedEntry.id ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
                      保存修改
                    </button>
                    <button
                      type="button"
                      onClick={cancelEditingEntry}
                      className="inline-flex h-10 items-center justify-center rounded-lg border border-gray-200 bg-white px-4 text-sm font-medium text-gray-600 hover:bg-gray-50"
                    >
                      取消编辑
                    </button>
                  </div>
                </div>
              ) : (
                <>
                  <div className="mb-3 flex flex-wrap items-center gap-2">
                    <span className="rounded-full bg-purple-50 px-2 py-0.5 text-[11px] font-medium text-purple-700">
                      {entryTypeLabel(selectedEntry.structured?.entryType)}
                    </span>
                    <span className="inline-flex items-center gap-1 rounded-full bg-white px-2 py-0.5 text-[11px] font-medium text-gray-600">
                      <Lock className="h-3 w-3" />
                      {visibilityLabel(selectedEntry.visibility)}
                    </span>
                    {selectedEntry.mood && (
                      <span className="rounded-full bg-blue-50 px-2 py-0.5 text-[11px] font-medium text-blue-700">
                        {selectedEntry.mood}
                      </span>
                    )}
                    {relatedUserIdFromEntry(selectedEntry) && (
                      <span className="rounded-full bg-purple-50 px-2 py-0.5 text-[11px] font-medium text-purple-700">
                        关联：{relatedMemberNameFromEntry(selectedEntry) || `用户 ${relatedUserIdFromEntry(selectedEntry)}`}
                      </span>
                    )}
                    {selectedEntry.tags?.map((tag) => (
                      <span key={tag} className="rounded bg-white px-2 py-0.5 text-[11px] text-gray-500">#{tag}</span>
                    ))}
                  </div>
                  <p className="whitespace-pre-wrap rounded-lg bg-white p-3 text-sm leading-6 text-gray-700">
                    {selectedEntry.rawText}
                  </p>
                  <div className="mt-3 rounded-lg border border-blue-100 bg-white p-3">
                    <div className="mb-1 flex items-center gap-1.5 text-xs font-medium text-gray-700">
                      <Lock className="h-3.5 w-3.5" />
                      AI 使用说明
                    </div>
                    <p className="text-xs leading-5 text-gray-500">{aiUsageNote(selectedEntry)}</p>
                  </div>
                  <div className="mt-3 flex flex-wrap gap-2">
                    {selectedEntry.userId === user?.id && (
                      <button
                        type="button"
                        onClick={() => startEditingEntry(selectedEntry)}
                        className="inline-flex h-9 items-center gap-1 rounded-lg bg-blue-600 px-3 text-xs font-medium text-white hover:bg-blue-700"
                      >
                        <Edit3 className="h-3.5 w-3.5" />
                        继续编辑
                      </button>
                    )}
                    <button
                      type="button"
                      onClick={() => handlePromoteToMemory(selectedEntry)}
                      disabled={promotedDiaryIds.has(selectedEntry.id) || promotingId === selectedEntry.id}
                      className="inline-flex h-9 items-center gap-1 rounded-lg border border-purple-200 bg-white px-3 text-xs font-medium text-purple-700 hover:bg-purple-50 disabled:text-green-600 disabled:opacity-80"
                    >
                      {promotingId === selectedEntry.id ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <ScrollText className="h-3.5 w-3.5" />}
                      {promotedDiaryIds.has(selectedEntry.id) ? '已沉淀为经验' : '沉淀为经验'}
                    </button>
                    <button
                      type="button"
                      onClick={() => openCoreMemoryDialog(selectedEntry)}
                      disabled={promotingId === selectedEntry.id}
                      className="inline-flex h-9 items-center gap-1 rounded-lg border border-amber-200 bg-white px-3 text-xs font-medium text-amber-700 hover:bg-amber-50 disabled:opacity-50"
                    >
                      <ScrollText className="h-3.5 w-3.5" />
                      核心记忆
                    </button>
                  </div>
                </>
              )}
            </div>
          )}

          {loadingEntries ? (
            <div className="flex h-48 items-center justify-center text-gray-400">
              <RefreshCw className="mr-2 h-5 w-5 animate-spin" />
              加载记录...
            </div>
          ) : entries.length === 0 ? (
            <div className="rounded-lg border border-dashed border-gray-200 p-8 text-center">
              <BookHeart className="mx-auto mb-2 h-8 w-8 text-gray-300" />
              <p className="text-sm text-gray-500">这个家族还没有可见的人生记录。</p>
            </div>
          ) : (
            <div className="space-y-3">
              {entries.map((entry) => (
                <article
                  key={entry.id}
                  className={`rounded-lg border p-4 transition-colors ${
                    selectedEntryId === entry.id ? 'border-blue-200 bg-blue-50/40' : 'border-gray-200'
                  }`}
                >
                  {(() => {
                    const isPromoted = promotedDiaryIds.has(entry.id);
                    const relatedId = relatedUserIdFromEntry(entry);
                    const relatedName = relatedMemberNameFromEntry(entry);
                    return (
                      <>
                        <div className="mb-2 flex flex-wrap items-center gap-2">
                          <span className="text-sm font-semibold text-gray-900">{diaryTitle(entry)}</span>
                          <span className="rounded-full bg-purple-50 px-2 py-0.5 text-[11px] font-medium text-purple-700">
                            {entryTypeLabel(entry.structured?.entryType)}
                          </span>
                          <span className="inline-flex items-center gap-1 rounded-full bg-gray-100 px-2 py-0.5 text-[11px] font-medium text-gray-600">
                            <Lock className="h-3 w-3" />
                            {visibilityLabel(entry.visibility)}
                          </span>
                          {isPromoted && (
                            <span className="inline-flex items-center gap-1 rounded-full bg-green-50 px-2 py-0.5 text-[11px] font-medium text-green-700">
                              <CheckCircle className="h-3 w-3" />
                              已沉淀
                            </span>
                          )}
                          {entry.mood && (
                            <span className="rounded-full bg-blue-50 px-2 py-0.5 text-[11px] font-medium text-blue-700">
                              {entry.mood}
                            </span>
                          )}
                          {relatedId && (
                            <span className="rounded-full bg-purple-50 px-2 py-0.5 text-[11px] font-medium text-purple-700">
                              关联：{relatedName || `用户 ${relatedId}`}
                            </span>
                          )}
                        </div>
                        <p className="whitespace-pre-wrap text-sm leading-6 text-gray-700">{entry.rawText}</p>
                        <div className="mt-3 flex flex-wrap items-center gap-2 text-xs text-gray-400">
                          <span>{new Date(entry.createdAt).toLocaleString('zh-CN')}</span>
                          {entry.tags?.map((tag) => (
                            <span key={tag} className="rounded bg-gray-50 px-2 py-0.5">#{tag}</span>
                          ))}
                          <button
                            type="button"
                            onClick={() => {
                              setSelectedEntryId(entry.id);
                              setEditingEntryId(null);
                            }}
                            className="inline-flex items-center gap-1 text-gray-400 hover:text-blue-600 sm:ml-auto"
                          >
                            <BookHeart className="h-3.5 w-3.5" />
                            查看详情
                          </button>
                          <button
                            type="button"
                            onClick={() => handlePromoteToMemory(entry)}
                            disabled={isPromoted || promotingId === entry.id}
                            className="inline-flex items-center gap-1 text-gray-400 hover:text-purple-600 disabled:text-green-600 disabled:opacity-80"
                          >
                            {promotingId === entry.id ? (
                              <RefreshCw className="h-3.5 w-3.5 animate-spin" />
                            ) : isPromoted ? (
                              <CheckCircle className="h-3.5 w-3.5" />
                            ) : (
                              <ScrollText className="h-3.5 w-3.5" />
                            )}
                            {isPromoted ? '已沉淀' : '沉淀为经验'}
                          </button>
                          <button
                            type="button"
                            onClick={() => openCoreMemoryDialog(entry)}
                            disabled={promotingId === entry.id}
                            className="inline-flex items-center gap-1 text-gray-400 hover:text-amber-600 disabled:opacity-50"
                          >
                            <ScrollText className="h-3.5 w-3.5" />
                            核心记忆
                          </button>
                          {entry.userId === user?.id && (
                            <button
                              type="button"
                              onClick={() => handleDelete(entry.id)}
                              disabled={deletingId === entry.id}
                              className="inline-flex items-center gap-1 text-gray-400 hover:text-red-600 disabled:opacity-50"
                            >
                              <Trash2 className="h-3.5 w-3.5" />
                              归档
                            </button>
                          )}
                        </div>
                      </>
                    );
                  })()}
                </article>
              ))}
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
