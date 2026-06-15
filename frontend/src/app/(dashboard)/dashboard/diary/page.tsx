'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { BookHeart, CheckCircle, ChevronDown, Lock, RefreshCw, Save, Sparkles, Users } from 'lucide-react';
import { familyApi, memoryApi, writeMemoryApi } from '@/lib/api';
import { useViewerRole } from '@/hooks/useViewerRole';
import { useAuthStore } from '@/stores/authStore';
import VoiceInputButton from '@/components/voice/VoiceInputButton';
import {
  WorkbenchEmptyState,
  WorkbenchHero,
  WorkbenchPage,
  WorkbenchSurface,
} from '@/components/layout/Workbench';
import type {
  DiaryEntryType,
  DiaryVisibility,
  FamilyMember,
  GrowthGuardCategory,
  MemoryEntryType,
  MemoryScope,
  WriteCategory,
} from '@/types';

interface WriteDraft {
  category: WriteCategory;
  content: string;
  title: string;
  tagText: string;
  visibility: DiaryVisibility;
  relatedUserId?: number;
  diaryEntryType: DiaryEntryType;
  memoryType: MemoryEntryType;
  growthCategory: GrowthGuardCategory;
  growthSeverity: number;
  updatedAt: string;
}

interface StarterTemplate {
  id: string;
  label: string;
  category: WriteCategory;
  content: string;
  tags?: string;
}

const DRAFT_VERSION = 'v2';
const visibilityOptions: { value: DiaryVisibility; label: string; note: string }[] = [
  { value: 'PRIVATE', label: '仅自己可见', note: '只自己回看和继续补充。' },
  { value: 'FAMILY_VISIBLE', label: '全家可见', note: '家族成员可查看和继续整理。' },
  { value: 'CARE_VISIBLE', label: '照护可见', note: '照护相关成员可查看和跟进。' },
];

const categoryOptions: {
  value: WriteCategory;
  label: string;
  description: string;
}[] = [
  { value: 'RECORD', label: '记录', description: '写下当下发生的事、感受和留言。' },
  { value: 'EXPERIENCE', label: '经验', description: '留下以后还会用到的经验和提醒。' },
  { value: 'OBSERVATION', label: '观察', description: '记录照护线索、变化和后续复核点。' },
];

const starterTemplates: StarterTemplate[] = [
  {
    id: 'record-daily',
    label: '今天发生了什么',
    category: 'RECORD',
    content: '今天发生了：\n\n我当时最在意的是：\n\n以后再回看，我想记住的是：',
    tags: '日常 片段',
  },
  {
    id: 'record-message',
    label: '留一句话',
    category: 'RECORD',
    content: '我想留给家人的一句话是：\n\n我为什么想说这句话：\n\n希望以后看到时能想起：',
    tags: '家人 留言',
  },
  {
    id: 'experience-lesson',
    label: '沉淀一个经验',
    category: 'EXPERIENCE',
    content: '这件事发生在：\n\n我踩过的坑或学到的经验是：\n\n如果以后再遇到类似情况，我建议：',
    tags: '经验 提醒',
  },
  {
    id: 'experience-health',
    label: '留一个提醒',
    category: 'EXPERIENCE',
    content: '我想留下的提醒是：\n\n这个提醒来自什么经历：\n\n以后最适合在什么场景用上：',
    tags: '提醒 家庭',
  },
  {
    id: 'observation-signal',
    label: '记录一条观察',
    category: 'OBSERVATION',
    content: '我观察到的具体情况是：\n\n我想继续留意的是：\n\n下次复核时想确认：',
    tags: '观察 跟进',
  },
  {
    id: 'observation-care',
    label: '照护跟进',
    category: 'OBSERVATION',
    content: '今天注意到的照护线索：\n\n可能相关的原因：\n\n这周可以轻量跟进的是：',
    tags: '照护 复核',
  },
];

const validMemoryTypes = new Set<MemoryEntryType>([
  'ELDER_ADVICE',
  'FAMILY_STORY',
  'HEALTH_REMINDER',
  'GROWTH_RISK',
  'VALUE',
]);

const validGrowthCategories = new Set<GrowthGuardCategory>([
  'POSTURE',
  'DENTAL',
  'VISION',
  'SLEEP',
  'EXERCISE',
  'SCREEN_TIME',
  'EMOTION',
  'COMMUNICATION',
  'OTHER',
]);

function draftKey(userId?: number, familyId?: number | null, relatedUserId?: number | null) {
  if (!userId || !familyId) return '';
  return `familyagent:write-memory-draft:${DRAFT_VERSION}:${userId}:${familyId}:${relatedUserId || 'self'}`;
}

function formatTags(raw: string) {
  return raw
    .split(/[,，\s]+/)
    .map((item) => item.trim())
    .filter(Boolean)
    .slice(0, 8);
}

function memberDisplayName(member?: FamilyMember | null) {
  if (!member) return '';
  return member.relationshipLabel?.trim()
    || member.nickname?.trim()
    || member.username?.trim()
    || `用户 ${member.userId}`;
}

function normalizeVisibility(value?: string): DiaryVisibility | null {
  if (value === 'PRIVATE' || value === 'FAMILY_VISIBLE' || value === 'CARE_VISIBLE' || value === 'LEGACY_VISIBLE') return value;
  return null;
}

function defaultVisibilityForCategory(category: WriteCategory): DiaryVisibility {
  return category === 'OBSERVATION' ? 'CARE_VISIBLE' : 'FAMILY_VISIBLE';
}

function defaultMemoryTypeForQuery(type?: string): MemoryEntryType {
  if (type && validMemoryTypes.has(type)) return type;
  return 'ELDER_ADVICE';
}

function defaultGrowthCategoryForQuery(category?: string): GrowthGuardCategory {
  if (category && validGrowthCategories.has(category as GrowthGuardCategory)) {
    return category as GrowthGuardCategory;
  }
  return 'OTHER';
}

function organizeScene(category: WriteCategory) {
  if (category === 'EXPERIENCE') return 'HERITAGE' as const;
  if (category === 'OBSERVATION') return 'GROWTH_GUARD' as const;
  return 'DIARY' as const;
}

function primaryActionLabel(category: WriteCategory) {
  if (category === 'EXPERIENCE') return '保存经验';
  if (category === 'OBSERVATION') return '保存观察';
  return '保存记录';
}

function successLabel(category: WriteCategory) {
  if (category === 'EXPERIENCE') return '经验已保存到记忆库';
  if (category === 'OBSERVATION') return '观察已保存到记忆库';
  return '记录已保存到记忆库';
}

export default function DiaryPage() {
  const searchParams = useSearchParams();
  const user = useAuthStore((state) => state.user);
  const {
    families,
    activeFamilyId,
    setActiveFamilyId,
    isLoading: loadingFamilies,
  } = useViewerRole();

  const [members, setMembers] = useState<FamilyMember[]>([]);
  const [selectedFamilyId, setSelectedFamilyId] = useState<number | null>(null);
  const [category, setCategory] = useState<WriteCategory>('RECORD');
  const [content, setContent] = useState('');
  const [title, setTitle] = useState('');
  const [tagText, setTagText] = useState('');
  const [visibility, setVisibility] = useState<DiaryVisibility>('FAMILY_VISIBLE');
  const [relatedUserId, setRelatedUserId] = useState<number | undefined>(undefined);
  const [diaryEntryType, setDiaryEntryType] = useState<DiaryEntryType>('DAILY');
  const [memoryType, setMemoryType] = useState<MemoryEntryType>('ELDER_ADVICE');
  const [growthCategory, setGrowthCategory] = useState<GrowthGuardCategory>('OTHER');
  const [growthSeverity, setGrowthSeverity] = useState(3);
  const [showTemplates, setShowTemplates] = useState(false);
  const [draftStatus, setDraftStatus] = useState('');
  const [saving, setSaving] = useState(false);
  const [organizing, setOrganizing] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const hydratedDraftKeyRef = useRef('');
  const suppressDraftSaveRef = useRef(false);
  const writeCategoryAppliedKeyRef = useRef('');
  const prefillAppliedKeyRef = useRef('');
  const successTimerRef = useRef<number | null>(null);

  const requestedFamilyId = useMemo(() => {
    const value = Number(searchParams.get('familyId'));
    return Number.isFinite(value) && value > 0 ? value : null;
  }, [searchParams]);

  const requestedTargetUserId = useMemo(() => {
    const raw = searchParams.get('targetUserId') || searchParams.get('relatedUserId');
    const value = Number(raw);
    return Number.isFinite(value) && value > 0 ? value : null;
  }, [searchParams]);

  const requestedTargetMemberName = useMemo(
    () => searchParams.get('relatedMemberName')?.trim() || '',
    [searchParams],
  );

  const requestedMemoryType = useMemo(
    () => searchParams.get('memoryType')?.trim() || searchParams.get('type')?.trim() || '',
    [searchParams],
  );

  const requestedWriteCategory = useMemo(
    () => searchParams.get('writeCategory')?.trim().toUpperCase() || '',
    [searchParams],
  );

  const requestedGrowthCategory = useMemo(
    () => searchParams.get('growthCategory')?.trim() || searchParams.get('category')?.trim() || '',
    [searchParams],
  );

  const requestedPrefill = useMemo(() => ({
    title: searchParams.get('prefillTitle')?.trim() || '',
    content: searchParams.get('prefillContent')?.trim() || '',
    tags: searchParams.get('prefillTags')?.trim() || '',
    visibility: normalizeVisibility(searchParams.get('prefillVisibility')?.trim() || ''),
  }), [searchParams]);

  const selectedFamily = useMemo(
    () => families.find((family) => family.id === selectedFamilyId) || null,
    [families, selectedFamilyId],
  );

  const selectedMember = useMemo(
    () => members.find((member) => member.userId === relatedUserId) || null,
    [members, relatedUserId],
  );

  const relatedMemberLabel = useMemo(() => {
    if (selectedMember) return memberDisplayName(selectedMember);
    if (requestedTargetMemberName) return requestedTargetMemberName;
    if (requestedTargetUserId) return `用户 ${requestedTargetUserId}`;
    return '';
  }, [requestedTargetMemberName, requestedTargetUserId, selectedMember]);

  const visibleTemplates = useMemo(
    () => starterTemplates.filter((item) => item.category === category),
    [category],
  );

  const hasDraftContent = Boolean(content.trim() || title.trim() || tagText.trim());

  const clearForm = useCallback((nextCategory?: WriteCategory) => {
    const categoryToUse = nextCategory || category;
    setContent('');
    setTitle('');
    setTagText('');
    setVisibility(defaultVisibilityForCategory(categoryToUse));
    setDiaryEntryType('DAILY');
    setMemoryType('ELDER_ADVICE');
    setGrowthCategory('OTHER');
    setGrowthSeverity(3);
    if (categoryToUse !== 'OBSERVATION') {
      setRelatedUserId(requestedTargetUserId || undefined);
    }
    setDraftStatus('');
  }, [category, requestedTargetUserId]);

  const flashSuccess = useCallback((message: string) => {
    if (successTimerRef.current !== null) window.clearTimeout(successTimerRef.current);
    setSuccess(message);
    successTimerRef.current = window.setTimeout(() => setSuccess(''), 2500);
  }, []);

  const persistDraftNow = useCallback((message?: string) => {
    const key = draftKey(user?.id, selectedFamilyId, requestedTargetUserId || relatedUserId || null);
    if (!key || typeof window === 'undefined') return;
    if (!hasDraftContent) {
      localStorage.removeItem(key);
      if (message) setDraftStatus(message);
      return;
    }
    const payload: WriteDraft = {
      category,
      content,
      title,
      tagText,
      visibility,
      relatedUserId,
      diaryEntryType,
      memoryType,
      growthCategory,
      growthSeverity,
      updatedAt: new Date().toISOString(),
    };
    localStorage.setItem(key, JSON.stringify(payload));
    if (message) setDraftStatus(message);
  }, [
    category,
    content,
    diaryEntryType,
    growthCategory,
    growthSeverity,
    hasDraftContent,
    memoryType,
    relatedUserId,
    requestedTargetUserId,
    selectedFamilyId,
    tagText,
    title,
    user?.id,
    visibility,
  ]);

  useEffect(() => {
    const nextFamilyId = (
      requestedFamilyId && families.some((family) => family.id === requestedFamilyId)
        ? requestedFamilyId
        : activeFamilyId && families.some((family) => family.id === activeFamilyId)
          ? activeFamilyId
          : families[0]?.id
    ) || null;
    setSelectedFamilyId(nextFamilyId);
    if (nextFamilyId && activeFamilyId !== nextFamilyId) {
      setActiveFamilyId(nextFamilyId);
    }
  }, [activeFamilyId, families, requestedFamilyId, setActiveFamilyId]);

  useEffect(() => {
    if (!selectedFamilyId) {
      setMembers([]);
      return;
    }
    let cancelled = false;
    familyApi.getMembers(selectedFamilyId)
      .then((result) => {
        if (!cancelled) setMembers(Array.isArray(result) ? result : []);
      })
      .catch(() => {
        if (!cancelled) setMembers([]);
      });
    return () => {
      cancelled = true;
    };
  }, [selectedFamilyId]);

  useEffect(() => {
    const initialCategory = requestedWriteCategory === 'OBSERVATION' || requestedGrowthCategory
      ? 'OBSERVATION'
      : requestedWriteCategory === 'EXPERIENCE' || requestedMemoryType
        ? 'EXPERIENCE'
        : 'RECORD';
    const key = `${requestedWriteCategory}:${requestedMemoryType}:${requestedGrowthCategory}:${requestedTargetUserId || ''}`;
    if (writeCategoryAppliedKeyRef.current === key) return;
    writeCategoryAppliedKeyRef.current = key;

    setCategory(initialCategory);
    setVisibility(defaultVisibilityForCategory(initialCategory));
    setMemoryType(defaultMemoryTypeForQuery(requestedMemoryType));
    setGrowthCategory(defaultGrowthCategoryForQuery(requestedGrowthCategory));
    if (requestedTargetUserId) setRelatedUserId(requestedTargetUserId);
  }, [requestedGrowthCategory, requestedMemoryType, requestedTargetUserId, requestedWriteCategory]);

  useEffect(() => {
    const key = draftKey(user?.id, selectedFamilyId, requestedTargetUserId || null);
    hydratedDraftKeyRef.current = key;
    suppressDraftSaveRef.current = true;
    setDraftStatus('');

    if (!key || typeof window === 'undefined') return;

    const raw = localStorage.getItem(key);
    if (!raw) {
      // No draft for this family/user — clear any content left from the previous family.
      setContent('');
      setTitle('');
      setTagText('');
      setVisibility(defaultVisibilityForCategory('RECORD'));
      setDiaryEntryType('DAILY');
      setMemoryType('ELDER_ADVICE');
      setGrowthCategory('OTHER');
      setGrowthSeverity(3);
      setRelatedUserId(requestedTargetUserId || undefined);
      suppressDraftSaveRef.current = false;
      return;
    }

    try {
      const saved = JSON.parse(raw) as Partial<WriteDraft>;
      setCategory(saved.category || 'RECORD');
      setContent(saved.content || '');
      setTitle(saved.title || '');
      setTagText(saved.tagText || '');
      setVisibility(normalizeVisibility(saved.visibility) || defaultVisibilityForCategory(saved.category || 'RECORD'));
      setRelatedUserId(saved.relatedUserId || requestedTargetUserId || undefined);
      setDiaryEntryType(saved.diaryEntryType || 'DAILY');
      setMemoryType(saved.memoryType && validMemoryTypes.has(saved.memoryType) ? saved.memoryType : 'ELDER_ADVICE');
      setGrowthCategory(
        saved.growthCategory && validGrowthCategories.has(saved.growthCategory)
          ? saved.growthCategory
          : defaultGrowthCategoryForQuery(requestedGrowthCategory),
      );
      setGrowthSeverity(saved.growthSeverity && saved.growthSeverity >= 1 && saved.growthSeverity <= 5 ? saved.growthSeverity : 3);
      setDraftStatus('已恢复上次未提交的草稿');
    } catch {
      localStorage.removeItem(key);
    } finally {
      suppressDraftSaveRef.current = false;
    }
  }, [requestedGrowthCategory, requestedTargetUserId, selectedFamilyId, user?.id]);

  useEffect(() => {
    if (!selectedFamilyId) return;
    if (!requestedPrefill.title && !requestedPrefill.content && !requestedPrefill.tags && !requestedPrefill.visibility) return;
    const key = `${selectedFamilyId}:${requestedPrefill.title}:${requestedPrefill.content}:${requestedPrefill.tags}:${requestedPrefill.visibility || ''}`;
    if (prefillAppliedKeyRef.current === `prefill:${key}`) return;
    prefillAppliedKeyRef.current = `prefill:${key}`;

    setCategory('RECORD');
    setContent(requestedPrefill.content);
    setTitle(requestedPrefill.title);
    setTagText(requestedPrefill.tags);
    setVisibility(requestedPrefill.visibility || 'FAMILY_VISIBLE');
    setDraftStatus('已根据上下文填入内容，可继续修改后保存');
  }, [requestedPrefill, selectedFamilyId]);

  useEffect(() => {
    const key = draftKey(user?.id, selectedFamilyId, requestedTargetUserId || relatedUserId || null);
    if (!key || typeof window === 'undefined' || hydratedDraftKeyRef.current !== key) return;
    if (suppressDraftSaveRef.current) {
      suppressDraftSaveRef.current = false;
      return;
    }

    const timer = window.setTimeout(() => {
      persistDraftNow(hasDraftContent ? '草稿已自动保存在本地' : '');
    }, 500);
    return () => window.clearTimeout(timer);
  }, [hasDraftContent, persistDraftNow, relatedUserId, requestedTargetUserId, selectedFamilyId, user?.id]);

  const applyTemplate = useCallback((template: StarterTemplate) => {
    setCategory(template.category);
    setContent(template.content);
    setTagText(template.tags || '');
    setVisibility(defaultVisibilityForCategory(template.category));
    setShowTemplates(false);
    setDraftStatus(`已填入“${template.label}”开头`);
  }, []);

  const clearDraft = useCallback(() => {
    const key = draftKey(user?.id, selectedFamilyId, requestedTargetUserId || relatedUserId || null);
    if (key && typeof window !== 'undefined') {
      localStorage.removeItem(key);
    }
    clearForm(category);
  }, [category, clearForm, relatedUserId, requestedTargetUserId, selectedFamilyId, user?.id]);

  const handleCategoryChange = useCallback((nextCategory: WriteCategory) => {
    setCategory(nextCategory);
    setVisibility((current) => {
      if (nextCategory === 'OBSERVATION') return current === 'PRIVATE' ? 'CARE_VISIBLE' : current;
      return current;
    });
    if (nextCategory !== 'OBSERVATION') {
      setRelatedUserId(requestedTargetUserId || undefined);
    }
  }, [requestedTargetUserId]);

  const handleOrganize = useCallback(async () => {
    if (!content.trim()) return;
    setOrganizing(true);
    setError('');
    try {
      const result = await memoryApi.organizeDraft({
        content,
        scene: organizeScene(category),
        familyContext: selectedFamily?.description || selectedFamily?.name || '',
        currentType: category === 'RECORD' ? diaryEntryType : category === 'EXPERIENCE' ? memoryType : growthCategory,
        currentVisibility: visibility,
        target: relatedMemberLabel,
      });
      const draft = result.data;
      setTitle(draft.title || title);
      setContent(draft.content || content);
      if (draft.tags?.length) setTagText(draft.tags.join(' '));
      if (category === 'RECORD') {
        setDiaryEntryType(
          draft.diary_entry_type
            && ['DAILY', 'IMPORTANT_EVENT', 'LESSON', 'EMOTION', 'MESSAGE_TO_FAMILY', 'SELF_REFLECTION'].includes(draft.diary_entry_type)
            ? draft.diary_entry_type as DiaryEntryType
            : diaryEntryType,
        );
        setVisibility(normalizeVisibility(draft.diary_visibility) || visibility);
      } else if (category === 'EXPERIENCE') {
        setMemoryType(
          draft.memory_type && validMemoryTypes.has(draft.memory_type)
            ? draft.memory_type
            : memoryType,
        );
        setVisibility(normalizeVisibility(draft.memory_scope) || visibility);
      } else {
        setGrowthCategory(
          draft.growth_category && validGrowthCategories.has(draft.growth_category as GrowthGuardCategory)
            ? draft.growth_category as GrowthGuardCategory
            : growthCategory,
        );
        setGrowthSeverity(draft.growth_severity && draft.growth_severity >= 1 && draft.growth_severity <= 5
          ? draft.growth_severity
          : growthSeverity);
        setVisibility(normalizeVisibility(draft.memory_scope) || visibility);
      }
      setDraftStatus(`已整理${draft.reason ? `：${draft.reason}` : ''}`);
      flashSuccess('已整理到可直接保存的版本');
    } catch (err) {
      setError(err instanceof Error ? err.message : '整理失败，请稍后再试');
    } finally {
      setOrganizing(false);
    }
  }, [
    category,
    content,
    diaryEntryType,
    flashSuccess,
    growthCategory,
    growthSeverity,
    memoryType,
    relatedMemberLabel,
    selectedFamily,
    title,
    visibility,
  ]);

  const handleSave = useCallback(async () => {
    if (!selectedFamilyId) {
      setError('请先选择一个家族空间。');
      return;
    }
    if (!content.trim()) {
      setError('先写一点内容再保存。');
      return;
    }
    if (category === 'OBSERVATION' && !relatedUserId) {
      setError('观察类内容需要先关联一位成员。');
      return;
    }

    persistDraftNow('正在保存，已先保留本地草稿');
    setSaving(true);
    setError('');

    try {
      const tags = formatTags(tagText);
      await writeMemoryApi.create({
        familyId: selectedFamilyId,
        writeCategory: category,
        content: content.trim(),
        title: title.trim() || undefined,
        tags,
        visibility: category === 'OBSERVATION' ? visibility as MemoryScope : visibility,
        relatedUserId: relatedUserId || undefined,
        diaryEntryType,
        memoryType,
        growthCategory,
        growthSeverity,
        metadata: {
          source: 'WRITE_MEMORY_SIMPLIFIED',
          authorName: user?.nickname || user?.username,
          relatedMemberName: relatedMemberLabel || undefined,
          observerPerspective: category === 'OBSERVATION' ? 'FAMILY_MEMBER' : undefined,
          evidenceType: category === 'OBSERVATION' ? 'OBSERVED_FACT' : undefined,
        },
      });

      const key = draftKey(user?.id, selectedFamilyId, requestedTargetUserId || relatedUserId || null);
      if (key && typeof window !== 'undefined') {
        localStorage.removeItem(key);
      }
      clearForm(category);
      flashSuccess(successLabel(category));
    } catch (err) {
      persistDraftNow('保存失败，草稿已保留');
      setError(err instanceof Error ? err.message : '保存失败，请稍后再试');
    } finally {
      setSaving(false);
    }
  }, [
    category,
    clearForm,
    content,
    diaryEntryType,
    flashSuccess,
    growthCategory,
    growthSeverity,
    memoryType,
    persistDraftNow,
    relatedMemberLabel,
    relatedUserId,
    requestedTargetUserId,
    selectedFamilyId,
    tagText,
    title,
    user?.id,
    user?.nickname,
    user?.username,
    visibility,
  ]);

  if (loadingFamilies) {
    return (
      <WorkbenchSurface className="flex h-60 items-center justify-center text-stone-500">
        <RefreshCw className="mr-2 h-5 w-5 animate-spin text-emerald-700" />
        正在加载...
      </WorkbenchSurface>
    );
  }

  if (families.length === 0) {
    return (
      <WorkbenchEmptyState
        icon={<Users className="h-6 w-6" />}
        title="先创建一个家族空间"
        description="创建或加入家族后，就可以从这里统一记录日记、经验和观察。"
        action={(
          <Link
            href="/dashboard/family"
            className="inline-flex h-10 items-center justify-center rounded-2xl bg-stone-950 px-4 text-sm font-medium text-white shadow-[0_16px_36px_rgba(24,39,32,0.14)] transition hover:bg-stone-800"
          >
            前往家族空间
          </Link>
        )}
      />
    );
  }

  return (
    <WorkbenchPage className="max-w-6xl">
      <WorkbenchHero
        badge={(
          <span className="inline-flex items-center gap-2 rounded-full bg-emerald-50 px-3 py-1 text-xs font-medium text-emerald-700">
            <BookHeart className="h-3.5 w-3.5" />
            日记
          </span>
        )}
        title="日记"
        description="先写下内容，再决定它是日常记录、经验沉淀，还是照护观察。"
        aside={(
          <label className="block text-xs font-medium text-stone-500">
            家族空间
            <select
              value={selectedFamilyId || ''}
              onChange={(event) => {
                const value = Number(event.target.value);
                const nextFamilyId = Number.isFinite(value) && value > 0 ? value : null;
                setSelectedFamilyId(nextFamilyId);
                if (nextFamilyId) setActiveFamilyId(nextFamilyId);
              }}
              className="mt-2 h-11 w-full rounded-2xl border border-stone-200/80 bg-white/90 px-4 text-sm font-medium text-stone-800 outline-none transition focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
            >
              {families.map((family) => (
                <option key={family.id} value={family.id}>{family.name}</option>
              ))}
            </select>
          </label>
        )}
      />

      {error && (
        <div className="rounded-2xl border border-red-100 bg-red-50/90 px-4 py-3 text-sm text-red-700 shadow-sm">
          {error}
        </div>
      )}

      {success && (
        <div className="flex items-center gap-2 rounded-2xl border border-emerald-100 bg-emerald-50/90 px-4 py-3 text-sm text-emerald-800 shadow-sm">
          <CheckCircle className="h-4 w-4 text-emerald-700" />
          {success}
        </div>
      )}

      <div>
        <WorkbenchSurface>
          <div className="mb-4 grid grid-cols-1 gap-2 sm:grid-cols-3">
            {categoryOptions.map((item) => (
              <button
                key={item.value}
                type="button"
                onClick={() => handleCategoryChange(item.value)}
                className={`rounded-2xl border px-4 py-3 text-left transition ${
                  category === item.value
                    ? 'border-emerald-200 bg-emerald-50/80 shadow-sm'
                    : 'border-stone-200/80 bg-white/78 hover:bg-stone-50'
                }`}
              >
                <p className="text-sm font-semibold text-stone-950">{item.label}</p>
                <p className="mt-1 text-xs leading-5 text-stone-500">{item.description}</p>
              </button>
            ))}
          </div>

          <div className="mb-3 flex flex-wrap items-center justify-between gap-2 rounded-2xl bg-stone-50/90 px-3 py-2 text-xs text-stone-500">
            <span>{draftStatus || '草稿会自动保存在本地'}</span>
            {hasDraftContent && (
              <button
                type="button"
                onClick={clearDraft}
                className="font-medium text-stone-500 transition hover:text-red-600"
              >
                清空草稿
              </button>
            )}
          </div>

          <div className="mb-4 flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={() => setShowTemplates((current) => !current)}
              className="inline-flex h-9 items-center gap-2 rounded-2xl border border-stone-200/80 bg-white/84 px-3 text-xs font-medium text-stone-600 transition hover:bg-stone-50"
            >
              不会开头
              <ChevronDown className={`h-3.5 w-3.5 transition-transform ${showTemplates ? 'rotate-180' : ''}`} />
            </button>
            <VoiceInputButton onTranscript={(text) => {
              setContent((current) => (current.trim() ? `${current.trim()}\n${text}` : text));
            }}
            disabled={saving || organizing}
            />
            <button
              type="button"
              onClick={() => void handleOrganize()}
              disabled={!content.trim() || saving || organizing}
              className="inline-flex h-9 items-center gap-2 rounded-2xl border border-emerald-100 bg-emerald-50 px-3 text-xs font-medium text-emerald-700 transition hover:bg-emerald-100 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {organizing ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <Sparkles className="h-3.5 w-3.5" />}
              帮我整理
            </button>
          </div>

          {showTemplates && (
            <div className="mb-4 rounded-2xl border border-emerald-100 bg-emerald-50/70 p-3">
              <p className="mb-2 text-xs font-medium text-emerald-800">从一句开头开始</p>
              <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                {visibleTemplates.map((template) => (
                  <button
                    key={template.id}
                    type="button"
                    onClick={() => applyTemplate(template)}
                    className="rounded-xl border border-emerald-100 bg-white/90 px-3 py-2 text-left text-xs font-medium text-emerald-800 transition hover:bg-emerald-100/80"
                  >
                    {template.label}
                  </button>
                ))}
              </div>
            </div>
          )}

          {relatedMemberLabel && (
            <div className="mb-4 rounded-2xl border border-emerald-100 bg-emerald-50/70 p-3 text-sm text-emerald-800">
              当前内容会关联到 {relatedMemberLabel}。
            </div>
          )}

          <label className="mb-4 block">
            <span className="mb-2 block text-sm font-medium text-stone-800">正文</span>
            <textarea
              value={content}
              onChange={(event) => setContent(event.target.value)}
              rows={12}
              placeholder={
                category === 'EXPERIENCE'
                  ? '把这次经历里值得以后再用上的经验写下来。'
                  : category === 'OBSERVATION'
                    ? '写下你观察到的情况、想继续留意的点和下次复核方向。'
                    : '直接写下此刻发生的事、感受、判断或想留给家人的一句话。'
              }
              className="min-h-[22rem] w-full resize-none rounded-2xl border border-stone-200/80 bg-white/90 px-4 py-3 text-sm leading-7 text-stone-800 outline-none transition placeholder:text-stone-400 focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
            />
          </label>

          {title && (
            <div className="mb-4 rounded-2xl border border-amber-100 bg-amber-50/80 px-3 py-2 text-xs text-amber-800">
              当前整理出的标题：{title}
            </div>
          )}

          <div className="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
            <label className="text-xs font-medium text-stone-500">
              可见范围
              <select
                value={visibility}
                onChange={(event) => setVisibility(event.target.value as DiaryVisibility)}
                className="mt-1 h-10 w-full rounded-2xl border border-stone-200/80 bg-white/90 px-3 text-sm text-stone-700 outline-none transition focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
              >
                {visibilityOptions.map((option) => (
                  <option key={option.value} value={option.value}>{option.label}</option>
                ))}
              </select>
              <span className="mt-1 block text-[11px] text-stone-400">
                {visibilityOptions.find((option) => option.value === visibility)?.note}
              </span>
            </label>

            <label className="text-xs font-medium text-stone-500">
              标签
              <input
                value={tagText}
                onChange={(event) => setTagText(event.target.value)}
                placeholder="例如：日常 经验 观察 照护"
                className="mt-1 h-10 w-full rounded-2xl border border-stone-200/80 bg-white/90 px-3 text-sm text-stone-700 outline-none transition placeholder:text-stone-400 focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
              />
            </label>
          </div>

          {category === 'OBSERVATION' && (
            <div className="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
              <label className="text-xs font-medium text-stone-500">
                关联成员
                <select
                  value={relatedUserId || ''}
                  onChange={(event) => {
                    const value = Number(event.target.value);
                    setRelatedUserId(Number.isFinite(value) && value > 0 ? value : undefined);
                  }}
                  className="mt-1 h-10 w-full rounded-2xl border border-stone-200/80 bg-white/90 px-3 text-sm text-stone-700 outline-none transition focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
                >
                  <option value="">请选择一位成员</option>
                  {members.map((member) => (
                    <option key={member.userId} value={member.userId}>{memberDisplayName(member)}</option>
                  ))}
                </select>
              </label>
              <div className="rounded-2xl border border-stone-200/80 bg-stone-50/90 px-3 py-3 text-xs leading-6 text-stone-500">
                观察类内容只保留最小字段，保存后再去记忆库继续筛选和整理。
              </div>
            </div>
          )}

          <div className="mb-4 rounded-2xl bg-stone-50/90 p-3 text-xs leading-6 text-stone-500">
            <div className="mb-1 flex items-center gap-1.5 font-medium text-stone-700">
              <Lock className="h-3.5 w-3.5" />
              AI 使用边界
            </div>
            这里只保留“帮我整理”一个辅助动作，不会自动整理、自动合并，也不会替你自动保存。
          </div>

          <button
            type="button"
            onClick={() => void handleSave()}
            disabled={!selectedFamilyId || !content.trim() || saving}
            className="inline-flex h-11 w-full items-center justify-center gap-2 rounded-2xl bg-stone-950 px-4 text-sm font-medium text-white shadow-[0_16px_36px_rgba(24,39,32,0.14)] transition hover:bg-stone-800 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {saving ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
            {saving ? '正在保存...' : primaryActionLabel(category)}
          </button>
        </WorkbenchSurface>
      </div>
    </WorkbenchPage>
  );
}
