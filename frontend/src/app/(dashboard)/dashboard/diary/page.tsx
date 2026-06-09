'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Link from 'next/link';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { BookHeart, CheckCircle, Lightbulb, Lock, RefreshCw, Save, Sparkles, Users } from 'lucide-react';
import { diaryApi, memoryApi } from '@/lib/api';
import { useAuthStore } from '@/stores/authStore';
import { useViewerRole } from '@/hooks/useViewerRole';
import VoiceInputButton from '@/components/voice/VoiceInputButton';
import GrowthPage from '@/app/(dashboard)/dashboard/growth/page';
import type { DiaryEntry, DiaryEntryType, DiaryVisibility } from '@/types';

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
  { value: 'CARE_VISIBLE', label: '照护授权可见', note: '本人、家族创建者和明确授权的照护成员可读' },
  { value: 'LEGACY_VISIBLE', label: '传承预留', note: '当前按仅自己可见处理，后续支持授权开放' },
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
    tags: '短期片段 日常',
    mood: '平静',
  },
  {
    id: 'choice',
    label: '一次选择',
    entryType: 'SELF_REFLECTION',
    visibility: 'PRIVATE',
    title: '一次选择的复盘',
    content: '我面对的选择是：\n\n我当时为什么这样选：\n\n结果如何：\n\n如果再来一次，我会提醒自己：',
    tags: '阶段复盘 人生选择',
  },
  {
    id: 'family-message',
    label: '给家人的话',
    entryType: 'MESSAGE_TO_FAMILY',
    visibility: 'FAMILY_VISIBLE',
    title: '想留给家人的一句话',
    content: '我想对家人说：\n\n这句话背后的原因是：\n\n希望以后你们看到时能记得：',
    tags: '长期留存 给家人的话',
    mood: '感激',
  },
  {
    id: 'care',
    label: '生活线索',
    entryType: 'IMPORTANT_EVENT',
    visibility: 'CARE_VISIBLE',
    title: '一次生活线索',
    content: '我注意到的生活线索是：\n\n可能相关的原因：\n\n这周可以轻轻尝试的行动：\n\n下次回看时间：',
    tags: '短期线索 照护',
  },
  {
    id: 'lesson',
    label: '经验教训',
    entryType: 'LESSON',
    visibility: 'FAMILY_VISIBLE',
    title: '一个想传下去的经验',
    content: '这件事发生在：\n\n我踩过的坑或学到的经验是：\n\n如果后辈遇到类似情况，我建议：',
    tags: '长期经验 家族教训',
    mood: '有收获',
  },
];

type DiaryTemplate = typeof diaryTemplates[number];
type WriteRecordTab = 'record' | 'growth';

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

function formatTags(raw: string) {
  return raw
    .split(/[,，\s]+/)
    .map((item) => item.trim())
    .filter(Boolean)
    .slice(0, 8);
}

function diaryTitle(entry: DiaryEntry) {
  return entry.structured?.title || entry.structured?.summary || entry.rawText.slice(0, 32) || '未命名记录';
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
      note: '这条记录有场景也有线索，后续回看和检索会更稳。',
      level: 'GOOD',
    };
  }
  if (length >= 20) {
    return {
      label: '可以保存',
      note: '再补一点原因、感受或结果，会更适合以后回看。',
      level: 'OK',
    };
  }
  return {
    label: '还很简短',
    note: '多写一个具体场景，AI 后续整理时会更可靠。',
    level: 'LIGHT',
  };
}

export default function DiaryPage() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const user = useAuthStore((state) => state.user);
  const { families, activeFamilyId, setActiveFamilyId, viewerRole, isLoading: loadingFamilies } = useViewerRole();

  const [selectedFamilyId, setSelectedFamilyId] = useState<number | null>(null);
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
  const [saveStatus, setSaveStatus] = useState('');
  const [saving, setSaving] = useState(false);
  const [organizingDraft, setOrganizingDraft] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const draftHydratedKeyRef = useRef('');
  const suppressDraftSaveRef = useRef(false);
  const queryTemplateAppliedRef = useRef('');
  const queryPrefillAppliedRef = useRef('');

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

  const requestedPrefill = useMemo(() => ({
    title: searchParams.get('prefillTitle')?.trim() || '',
    content: searchParams.get('prefillContent')?.trim() || '',
    tags: searchParams.get('prefillTags')?.trim() || '',
    visibility: searchParams.get('prefillVisibility')?.trim() as DiaryVisibility | '',
  }), [searchParams]);

  const requestedTab = useMemo(() => {
    const value = searchParams.get('tab');
    return value === 'growth' ? 'growth' : 'record';
  }, [searchParams]);

  const relatedContextLabel = requestedRelatedMemberName
    || (requestedRelatedUserId ? `用户 ${requestedRelatedUserId}` : '');

  const canViewGrowthTab = viewerRole !== 'STUDENT';
  const activeTab: WriteRecordTab = canViewGrowthTab ? requestedTab : 'record';

  const selectedFamily = useMemo(
    () => families.find((family) => family.id === selectedFamilyId) || null,
    [families, selectedFamilyId],
  );

  const quality = useMemo(
    () => contentQuality(content, title, tagText),
    [content, title, tagText],
  );

  const appendVoiceTranscript = useCallback((text: string) => {
    setContent((current) => (current.trim() ? `${current.trim()}\n${text}` : text));
    setContentTouched(true);
    setPendingTemplate(null);
  }, []);

  useEffect(() => {
    const queryFamily = requestedFamilyId && families.some((family) => family.id === requestedFamilyId)
      ? requestedFamilyId
      : null;
    const nextFamilyId = queryFamily
      || (activeFamilyId && families.some((family) => family.id === activeFamilyId) ? activeFamilyId : null)
      || families[0]?.id
      || null;
    setSelectedFamilyId(nextFamilyId);
    if (queryFamily && activeFamilyId !== queryFamily) {
      setActiveFamilyId(queryFamily);
    }
  }, [activeFamilyId, families, requestedFamilyId, setActiveFamilyId]);

  useEffect(() => {
    const key = draftKey(user?.id, selectedFamilyId, requestedRelatedUserId);
    draftHydratedKeyRef.current = key;
    suppressDraftSaveRef.current = true;
    setPendingTemplate(null);
    setDraftStatus('');

    if (!key || typeof window === 'undefined') return;

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
    if (!key || typeof window === 'undefined' || draftHydratedKeyRef.current !== key) return;

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

  const persistCurrentDraft = useCallback((status = '草稿已保存，内容不会丢失') => {
    const key = draftKey(user?.id, selectedFamilyId, requestedRelatedUserId);
    if (!key || typeof window === 'undefined') return;
    const hasDraftContent = Boolean(title.trim() || content.trim() || mood.trim() || tagText.trim());
    if (!hasDraftContent) return;

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
    setDraftStatus(status);
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

  const removeCurrentDraft = useCallback(() => {
    const key = draftKey(user?.id, selectedFamilyId, requestedRelatedUserId);
    if (key && typeof window !== 'undefined') {
      localStorage.removeItem(key);
    }
    setDraftStatus('');
  }, [requestedRelatedUserId, selectedFamilyId, user?.id]);

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
    setDraftStatus(`已套用“${template.label}”模板${relatedContextLabel ? `，并关联到${relatedContextLabel}` : ''}`);
  }, [applyTemplate, relatedContextLabel, requestedRelatedUserId, searchParams, selectedFamilyId]);

  useEffect(() => {
    if (!selectedFamilyId || (!requestedPrefill.title && !requestedPrefill.content && !requestedPrefill.tags)) return;

    const key = `${selectedFamilyId}:${requestedPrefill.title}:${requestedPrefill.content}:${requestedPrefill.tags}`;
    if (queryPrefillAppliedRef.current === key) return;

    queryPrefillAppliedRef.current = key;
    setEntryType('DAILY');
    setVisibility(requestedPrefill.visibility || 'FAMILY_VISIBLE');
    setTitle(requestedPrefill.title || '补充一条家族记录');
    setContent(requestedPrefill.content || '');
    setTagText(requestedPrefill.tags || '家族记忆 补充');
    setMood('');
    setTitleTouched(false);
    setContentTouched(false);
    setMoodTouched(false);
    setTagTextTouched(false);
    setPendingTemplate(null);
    setDraftStatus('已根据上下文生成草稿');
  }, [requestedPrefill, selectedFamilyId]);

  const clearDraft = () => {
    removeCurrentDraft();
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
  };

  const handleSave = async () => {
    if (!selectedFamilyId) {
      setError('请先选择一个家族空间，再保存记录。');
      return;
    }
    if (!content.trim()) {
      setError('正文为空，先写一点内容再保存。');
      return;
    }

    persistCurrentDraft('正在保存，已先保留本地草稿');
    setSaving(true);
    setError('');
    setSaveStatus('正在保存记录...');

    try {
      const saved = await diaryApi.create({
        familyId: selectedFamilyId,
        content,
        entryType,
        title: title || undefined,
        mood: mood || undefined,
        tags: formatTags(tagText),
        visibility,
        metadata: {
          source: 'DIARY_MANUAL',
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
      removeCurrentDraft();
      setSaveStatus('');

      const merged = Boolean(saved.metadata?.autoMerged);
      flashSuccess(merged ? `已合并到今天的记录：${diaryTitle(saved)}` : `记录已保存：${quality.label}`);
    } catch (err) {
      persistCurrentDraft('保存失败，草稿已保留');
      setError(err instanceof Error ? err.message : '保存失败，草稿已保留');
      setSaveStatus('');
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

  const handleTabChange = useCallback((tab: WriteRecordTab) => {
    const params = new URLSearchParams(searchParams.toString());
    if (tab === 'record') {
      params.delete('tab');
    } else {
      params.set('tab', 'growth');
    }
    const next = params.toString();
    router.replace(next ? `${pathname}?${next}` : pathname);
  }, [pathname, router, searchParams]);

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
        <p className="mt-2 text-sm text-gray-500">先创建或加入家族，再记录当下发生的片段和感受。</p>
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
          <h1 className="text-xl font-bold text-gray-900">写记录</h1>
          <p className="mt-1 text-sm text-gray-500">先把当下发生的事记下来，再决定哪些内容要继续跟进。</p>
        </div>
      </div>

      {canViewGrowthTab && (
        <div className="mb-4 rounded-xl border border-gray-200 bg-white p-2">
          <div className="grid grid-cols-2 gap-2">
            <button
              type="button"
              onClick={() => handleTabChange('record')}
              className={`rounded-lg px-4 py-3 text-left transition-colors ${
                activeTab === 'record' ? 'bg-blue-50 text-blue-700' : 'bg-transparent text-gray-500 hover:bg-gray-50 hover:text-gray-800'
              }`}
            >
              <p className="text-sm font-semibold">当下记录</p>
              <p className="mt-1 text-xs leading-5">保存经历、情绪、留言和当时的判断。</p>
            </button>
            <button
              type="button"
              onClick={() => handleTabChange('growth')}
              className={`rounded-lg px-4 py-3 text-left transition-colors ${
                activeTab === 'growth' ? 'bg-emerald-50 text-emerald-700' : 'bg-transparent text-gray-500 hover:bg-gray-50 hover:text-gray-800'
              }`}
            >
              <p className="text-sm font-semibold">守护观察</p>
              <p className="mt-1 text-xs leading-5">记录照护线索、复核时间和后续改善情况。</p>
            </button>
          </div>
        </div>
      )}

      {activeTab === 'growth' ? (
        <GrowthPage embedded />
      ) : (
        <>
          {error && <div className="mb-4 rounded-lg bg-red-50 px-4 py-2 text-sm text-red-600">{error}</div>}
          {success && (
            <div className="mb-4 flex items-center gap-2 rounded-lg bg-green-50 px-4 py-2 text-sm text-green-700">
              <CheckCircle className="h-4 w-4" />
              {success}
            </div>
          )}

          <div className="grid grid-cols-1 gap-4 lg:grid-cols-[0.9fr_1.1fr]">
            <section className="rounded-lg border border-gray-200 bg-white p-4 sm:p-5">
              <div className="mb-4 flex items-center gap-2">
                <BookHeart className="h-5 w-5 text-purple-600" />
                <h2 className="text-sm font-semibold text-gray-900">新增记录</h2>
              </div>

              {relatedContextLabel && (
                <div className="mb-4 rounded-lg border border-purple-100 bg-purple-50 p-3">
                  <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                    <div>
                      <p className="text-xs font-semibold text-purple-700">正在补充与 {relatedContextLabel} 相关的记录</p>
                      <p className="mt-1 text-xs leading-5 text-purple-600">
                        保存后会写入关联字段，后续会统一在家族记忆库里查看。
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
                  快速起草
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
                      当前内容已经编辑过。要套用“{pendingTemplate.label}”模板并覆盖标题、正文、心情和标签吗？
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
                <span>{saveStatus || draftStatus || '填写时会自动保存本地草稿'}</span>
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
                    name="entryType"
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
                    name="visibility"
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
                这条记录只会在当前可见范围内进入家族 Agent 与镜像 Agent 的参考上下文。
              </div>

              <label className="mb-3 block text-xs font-medium text-gray-500">
                标题
                <input
                  name="title"
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
                  name="content"
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
                    name="mood"
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
                    name="tagText"
                    value={tagText}
                    onChange={(event) => {
                      setTagText(event.target.value);
                      setTagTextTouched(true);
                      setPendingTemplate(null);
                    }}
                    placeholder="例如：短期片段 阶段复盘 长期留存"
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
                {saving ? '正在保存...' : '保存记录'}
              </button>
            </section>

            <section className="rounded-lg border border-gray-200 bg-white p-4 sm:p-5">
              <div className="mb-4 flex items-center gap-2">
                <BookHeart className="h-5 w-5 text-blue-600" />
                <h2 className="text-sm font-semibold text-gray-900">记录查看与管理</h2>
              </div>

              <div className="rounded-lg border border-blue-100 bg-blue-50 p-4">
                <p className="text-sm font-medium text-blue-900">记录列表和后续操作已统一收口到家族记忆库</p>
                <p className="mt-2 text-sm leading-6 text-blue-800">
                  家族成员查看和操作记录，家族创建者管理成员记录与归档，都统一在家族记忆库中完成。写记录页只保留高内聚的录入能力。
                </p>
                <div className="mt-4 flex flex-col gap-2 sm:flex-row">
                  <Link
                    href={`/dashboard/memory${selectedFamilyId ? `?familyId=${selectedFamilyId}` : ''}`}
                    className="inline-flex h-10 items-center justify-center rounded-lg bg-blue-600 px-4 text-sm font-medium text-white hover:bg-blue-700"
                  >
                    前往家族记忆库
                  </Link>
                  <Link
                    href={`/dashboard/memory${selectedFamilyId ? `?familyId=${selectedFamilyId}` : ''}`}
                    className="inline-flex h-10 items-center justify-center rounded-lg border border-blue-200 bg-white px-4 text-sm font-medium text-blue-700 hover:bg-blue-100"
                  >
                    查看全部记录
                  </Link>
                </div>
              </div>

              <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div className="rounded-lg border border-gray-100 bg-gray-50 p-4">
                  <p className="text-sm font-semibold text-gray-900">家族成员</p>
                  <p className="mt-2 text-sm leading-6 text-gray-600">
                    在记忆库中按成员、类型和可见范围集中查看记录，不再在写记录页翻分页列表。
                  </p>
                </div>
                <div className="rounded-lg border border-gray-100 bg-gray-50 p-4">
                  <p className="text-sm font-semibold text-gray-900">家族创建者</p>
                  <p className="mt-2 text-sm leading-6 text-gray-600">
                    在记忆库中统一管理成员记录，包括筛选、复核和归档，避免写入和管理混杂在同一页面。
                  </p>
                </div>
              </div>

              <div className="mt-4 rounded-lg border border-dashed border-gray-200 p-4 text-sm leading-6 text-gray-500">
                {selectedFamily
                  ? `当前写入目标：${selectedFamily.name}。保存后的记录会进入该家族的记忆库。`
                  : '保存后的记录会进入当前家族的记忆库。'}
              </div>
            </section>
          </div>
        </>
      )}
    </div>
  );
}
