'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import * as DropdownMenu from '@radix-ui/react-dropdown-menu';
import { CheckCircle, ChevronDown, RefreshCw, Save, Sparkles, Users } from 'lucide-react';
import { familyApi, memoryApi, memoryLibraryApi, writeMemoryApi } from '@/lib/api';
import { useViewerRole } from '@/hooks/useViewerRole';
import { useAuthStore } from '@/stores/authStore';
import VoiceInputButton from '@/components/voice/VoiceInputButton';
import { WorkbenchEmptyState, WorkbenchPage, WorkbenchSurface } from '@/components/layout/Workbench';
import { submitFormOnEnter } from '@/lib/formKeyboard';
import type {
  FamilyMember,
  MemoryContentType,
  MemoryLibraryItem,
  MemoryScope,
} from '@/types';

interface UnifiedMemoryDraft {
  content: string;
  title: string;
  tagText: string;
  visibility: MemoryScope;
  relatedUserId?: number;
  memoryType: MemoryContentType;
  updatedAt: string;
}

interface StarterTemplate {
  id: string;
  label: string;
  memoryType: MemoryContentType;
  content: string;
  tags?: string;
}

interface DiaryComposerProps {
  editItem?: MemoryLibraryItem | null;
  onSaved?: () => void;
}

const DRAFT_VERSION = 'v3';
const MEMORY_TYPES: { value: MemoryContentType; label: string }[] = [
  { value: 'NOTE', label: '笔记' },
  { value: 'KNOWLEDGE', label: '新知' },
  { value: 'INSIGHT', label: '感悟' },
  { value: 'EXPERIENCE', label: '经历' },
  { value: 'OBSERVATION', label: '观察' },
  { value: 'PREFERENCE', label: '偏好' },
  { value: 'PLAN', label: '计划' },
];
const MEMORY_TYPE_VALUES = new Set(MEMORY_TYPES.map((item) => item.value));
const VISIBILITY_OPTIONS: { value: MemoryScope; label: string }[] = [
  { value: 'PRIVATE', label: '仅自己可见' },
  { value: 'FAMILY_VISIBLE', label: '全家可见' },
  { value: 'CARE_VISIBLE', label: '照护可见' },
];
const STARTER_TEMPLATES: StarterTemplate[] = [
  {
    id: 'note-daily',
    label: '今天发生了什么',
    memoryType: 'NOTE',
    content: '今天发生了：\n\n我当时最在意的是：\n\n以后再回看，我想记住的是：',
    tags: '日常 片段',
  },
  {
    id: 'knowledge-learning',
    label: '记下一条新知',
    memoryType: 'KNOWLEDGE',
    content: '我今天学到的是：\n\n它适合用在：\n\n下次我会这样实践：',
    tags: '新知 学习',
  },
  {
    id: 'insight-lesson',
    label: '留下一次感悟',
    memoryType: 'INSIGHT',
    content: '这件事让我意识到：\n\n我过去忽略了：\n\n以后我想提醒自己：',
    tags: '感悟 复盘',
  },
  {
    id: 'experience-story',
    label: '记录一段经历',
    memoryType: 'EXPERIENCE',
    content: '这件事发生在：\n\n事情的经过是：\n\n我想保留下来的细节是：',
    tags: '经历 故事',
  },
  {
    id: 'observation-follow-up',
    label: '记录一条观察',
    memoryType: 'OBSERVATION',
    content: '我观察到的具体情况是：\n\n我想继续留意的是：\n\n下次复核时想确认：',
    tags: '观察 跟进',
  },
  {
    id: 'plan-reminder',
    label: '制定一个计划',
    memoryType: 'PLAN',
    content: '我准备做的是：\n\n开始时间或触发条件：\n\n完成后我想复盘：',
    tags: '计划 提醒',
  },
];

export default function DiaryComposer({ editItem, onSaved }: DiaryComposerProps) {
  const searchParams = useSearchParams();
  const user = useAuthStore((state) => state.user);
  const { families, activeFamilyId, setActiveFamilyId, isLoading } = useViewerRole();
  const [members, setMembers] = useState<FamilyMember[]>([]);
  const [selectedFamilyId, setSelectedFamilyId] = useState<number | null>(null);
  const [content, setContent] = useState('');
  const [title, setTitle] = useState('');
  const [tagText, setTagText] = useState('');
  const [visibility, setVisibility] = useState<MemoryScope>('FAMILY_VISIBLE');
  const [relatedUserId, setRelatedUserId] = useState<number | undefined>();
  const [memoryType, setMemoryType] = useState<MemoryContentType>('NOTE');
  const [showTemplates, setShowTemplates] = useState(false);
  const [draftStatus, setDraftStatus] = useState('');
  const [saving, setSaving] = useState(false);
  const [organizing, setOrganizing] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const hydratedDraftKeyRef = useRef('');
  const successTimerRef = useRef<number | null>(null);

  const requestedFamilyId = positiveNumber(searchParams.get('familyId'));
  const requestedTargetUserId = positiveNumber(
    searchParams.get('targetUserId') || searchParams.get('relatedUserId'),
  );
  const requestedMemoryType = normalizeMemoryType(
    searchParams.get('memoryType') || searchParams.get('type'),
  );
  const selectedFamily = useMemo(
    () => families.find((family) => family.id === selectedFamilyId) || null,
    [families, selectedFamilyId],
  );
  const relatedMember = useMemo(
    () => members.find((member) => member.userId === relatedUserId) || null,
    [members, relatedUserId],
  );
  const visibleTemplates = useMemo(
    () => STARTER_TEMPLATES.filter((item) => item.memoryType === memoryType),
    [memoryType],
  );
  const draftStorageKey = draftKey(user?.id, selectedFamilyId, relatedUserId);

  useEffect(() => {
    if (families.length === 0) return;
    const nextFamilyId = requestedFamilyId || activeFamilyId || families[0].id;
    setSelectedFamilyId(nextFamilyId);
    if (activeFamilyId !== nextFamilyId) setActiveFamilyId(nextFamilyId);
  }, [activeFamilyId, families, requestedFamilyId, setActiveFamilyId]);

  useEffect(() => {
    if (!selectedFamilyId) {
      setMembers([]);
      return;
    }
    let active = true;
    familyApi.getMembers(selectedFamilyId)
      .then((items) => active && setMembers(items))
      .catch(() => active && setMembers([]));
    return () => { active = false; };
  }, [selectedFamilyId]);

  useEffect(() => {
    if (editItem) {
      setSelectedFamilyId(editItem.familyId);
      setTitle(editItem.title || '');
      setContent(editItem.body || '');
      setTagText((editItem.tags || []).join(' '));
      setVisibility(normalizeVisibility(editItem.visibility) || 'FAMILY_VISIBLE');
      setMemoryType(normalizeMemoryType(editItem.type) || 'NOTE');
      setRelatedUserId(editItem.memberUserId);
      return;
    }
    if (requestedMemoryType) setMemoryType(requestedMemoryType);
    if (requestedTargetUserId) setRelatedUserId(requestedTargetUserId);
    const prefillTitle = searchParams.get('prefillTitle')?.trim();
    const prefillContent = searchParams.get('prefillContent')?.trim();
    const prefillTags = searchParams.get('prefillTags')?.trim();
    const prefillVisibility = normalizeVisibility(searchParams.get('prefillVisibility') || '');
    if (prefillTitle) setTitle(prefillTitle);
    if (prefillContent) setContent(prefillContent);
    if (prefillTags) setTagText(prefillTags);
    if (prefillVisibility) setVisibility(prefillVisibility);
  }, [editItem, requestedMemoryType, requestedTargetUserId, searchParams]);

  useEffect(() => {
    if (editItem || !draftStorageKey || hydratedDraftKeyRef.current === draftStorageKey) return;
    hydratedDraftKeyRef.current = draftStorageKey;
    const stored = readDraft(draftStorageKey);
    if (!stored) return;
    setContent(stored.content);
    setTitle(stored.title);
    setTagText(stored.tagText);
    setVisibility(stored.visibility);
    setRelatedUserId(stored.relatedUserId);
    setMemoryType(stored.memoryType);
    setDraftStatus('已恢复本地草稿');
  }, [draftStorageKey, editItem]);

  useEffect(() => {
    if (editItem || !draftStorageKey || !content.trim()) return;
    const timer = window.setTimeout(() => {
      const draft: UnifiedMemoryDraft = {
        content,
        title,
        tagText,
        visibility,
        relatedUserId,
        memoryType,
        updatedAt: new Date().toISOString(),
      };
      localStorage.setItem(draftStorageKey, JSON.stringify(draft));
      setDraftStatus('草稿已自动保存');
    }, 500);
    return () => window.clearTimeout(timer);
  }, [content, draftStorageKey, editItem, memoryType, relatedUserId, tagText, title, visibility]);

  useEffect(() => () => {
    if (successTimerRef.current) window.clearTimeout(successTimerRef.current);
  }, []);

  const flashSuccess = useCallback((message: string) => {
    setSuccess(message);
    if (successTimerRef.current) window.clearTimeout(successTimerRef.current);
    successTimerRef.current = window.setTimeout(() => setSuccess(''), 2600);
  }, []);

  const handleMemoryTypeChange = useCallback((nextType: MemoryContentType) => {
    setMemoryType(nextType);
    if (nextType === 'OBSERVATION' && visibility === 'FAMILY_VISIBLE') {
      setVisibility('CARE_VISIBLE');
    }
    if (nextType !== 'OBSERVATION') setRelatedUserId(undefined);
  }, [visibility]);

  const applyTemplate = useCallback((template: StarterTemplate) => {
    setMemoryType(template.memoryType);
    setContent(template.content);
    setTagText(template.tags || '');
    if (template.memoryType === 'OBSERVATION') setVisibility('CARE_VISIBLE');
    setShowTemplates(false);
  }, []);

  const handleOrganize = useCallback(async () => {
    if (!selectedFamilyId || !content.trim()) return;
    setOrganizing(true);
    setError('');
    try {
      const result = await memoryApi.organizeDraft({
        familyId: selectedFamilyId,
        content: content.trim(),
        memoryLibrary: 'FAMILY',
        familyContext: selectedFamily?.name || '',
        currentMemoryType: memoryType,
        currentVisibility: visibility,
        target: relatedMember ? memberDisplayName(relatedMember) : '',
        requestId: `organize-memory-${Date.now()}`,
      });
      setTitle(result.data.title || title);
      setContent(result.data.content || content);
      setTagText((result.data.tags || []).join(' '));
      setMemoryType(result.data.memoryType);
      setVisibility(normalizeVisibility(result.data.visibility) || visibility);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '整理失败，请稍后重试。');
    } finally {
      setOrganizing(false);
    }
  }, [content, memoryType, relatedMember, selectedFamily, selectedFamilyId, title, visibility]);

  const handleSave = useCallback(async () => {
    if (!selectedFamilyId || !content.trim()) return;
    setSaving(true);
    setError('');
    try {
      const resolvedTitle = title.trim() || titleFromFirstLine(content);
      if (editItem) {
        await memoryLibraryApi.updateItem({
          familyId: editItem.familyId,
          itemId: editItem.id,
          title: resolvedTitle,
          body: content.trim(),
          type: memoryType,
          visibility,
          tags: formatTags(tagText),
        });
        flashSuccess('记忆已更新');
      } else {
        await writeMemoryApi.create({
          familyId: selectedFamilyId,
          memoryLibrary: 'FAMILY',
          memoryType,
          content: content.trim(),
          title: resolvedTitle,
          tags: formatTags(tagText),
          visibility,
          relatedUserId: memoryType === 'OBSERVATION' ? relatedUserId : undefined,
          metadata: {
            source: 'MEMORY_LIBRARY_COMPOSER',
            authorName: user?.nickname || user?.username || '',
            relatedMemberName: relatedMember ? memberDisplayName(relatedMember) : null,
          },
        });
        if (draftStorageKey) localStorage.removeItem(draftStorageKey);
        setContent('');
        setTitle('');
        setTagText('');
        setRelatedUserId(undefined);
        setMemoryType('NOTE');
        setVisibility('FAMILY_VISIBLE');
        setDraftStatus('');
        flashSuccess('记忆已保存到家庭记忆库');
      }
      onSaved?.();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '保存失败，请稍后重试。');
    } finally {
      setSaving(false);
    }
  }, [content, draftStorageKey, editItem, flashSuccess, memoryType, onSaved, relatedMember, relatedUserId, selectedFamilyId, tagText, title, user, visibility]);

  const clearDraft = useCallback(() => {
    if (draftStorageKey) localStorage.removeItem(draftStorageKey);
    setContent('');
    setTitle('');
    setTagText('');
    setRelatedUserId(undefined);
    setMemoryType('NOTE');
    setVisibility('FAMILY_VISIBLE');
    setDraftStatus('');
  }, [draftStorageKey]);

  if (isLoading) {
    return (
      <WorkbenchSurface className="flex h-60 items-center justify-center text-stone-500">
        <RefreshCw className="mr-2 h-5 w-5 animate-spin text-sky-700" />
        正在加载...
      </WorkbenchSurface>
    );
  }

  if (families.length === 0) {
    return (
      <WorkbenchEmptyState
        icon={<Users className="h-6 w-6" />}
        title="先创建一个家族空间"
        action={(
          <Link
            href="/dashboard/family"
            className="inline-flex h-10 items-center justify-center rounded-2xl bg-stone-950 px-4 text-sm font-medium text-white"
          >
            前往家族空间
          </Link>
        )}
      />
    );
  }

  return (
    <WorkbenchPage className="max-w-[1500px]">
      {error && <div className="rounded-md border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
      {success && (
        <div className="flex items-center gap-2 rounded-md border border-sky-100 bg-sky-50 px-4 py-3 text-sm text-sky-800">
          <CheckCircle className="h-4 w-4 text-sky-700" />
          {success}
        </div>
      )}

      <form
        onSubmit={(event) => { event.preventDefault(); void handleSave(); }}
        onKeyDown={submitFormOnEnter}
        className="space-y-3"
      >
        <div className="overflow-hidden rounded-[1.35rem] border border-sky-400 bg-white shadow-sm focus-within:ring-2 focus-within:ring-sky-100">
          <input
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            maxLength={120}
            placeholder="给这条记忆写一个标题"
            aria-label="记忆标题"
            className="h-14 w-full border-0 border-b border-stone-100 px-5 text-lg font-semibold outline-none placeholder:text-stone-300 sm:px-7"
          />
          <textarea
            value={content}
            onChange={(event) => setContent(event.target.value)}
            rows={14}
            placeholder="直接写下想长期保留的内容。"
            className="min-h-[28rem] w-full resize-none border-0 px-5 py-5 text-base leading-8 text-stone-800 outline-none placeholder:text-stone-400 sm:px-7 sm:py-6"
          />
          <div className="flex flex-col gap-3 px-3 pb-3 sm:flex-row sm:items-center sm:justify-between sm:px-4">
            <div className="flex flex-wrap items-center gap-2">
              <DropdownMenu.Root open={showTemplates} onOpenChange={setShowTemplates}>
                <DropdownMenu.Trigger asChild>
                  <button type="button" className="inline-flex h-9 items-center gap-2 rounded-md px-3 text-sm text-stone-500 hover:bg-stone-100">
                    不会开头 <ChevronDown className="h-3.5 w-3.5" />
                  </button>
                </DropdownMenu.Trigger>
                <DropdownMenu.Portal>
                  <DropdownMenu.Content className="z-[70] w-72 rounded-md border border-sky-100 bg-sky-50 p-3 shadow-xl" side="top" sideOffset={8}>
                    <div className="grid gap-2">
                      {(visibleTemplates.length > 0 ? visibleTemplates : STARTER_TEMPLATES).map((template) => (
                        <DropdownMenu.Item key={template.id} asChild>
                          <button type="button" onClick={() => applyTemplate(template)} className="rounded-md bg-white px-3 py-2 text-left text-xs text-sky-800 hover:bg-sky-100">
                            {template.label}
                          </button>
                        </DropdownMenu.Item>
                      ))}
                    </div>
                  </DropdownMenu.Content>
                </DropdownMenu.Portal>
              </DropdownMenu.Root>
              <VoiceInputButton
                onTranscript={(text) => setContent((current) => current.trim() ? `${current.trim()}\n${text}` : text)}
                disabled={saving || organizing}
              />
              <button
                type="button"
                onClick={() => void handleOrganize()}
                disabled={!content.trim() || saving || organizing}
                className="inline-flex h-9 items-center gap-2 rounded-md px-3 text-sm text-sky-700 hover:bg-sky-50 disabled:opacity-50"
              >
                {organizing ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Sparkles className="h-4 w-4" />}
                帮我整理
              </button>
            </div>
            <div className="flex items-center gap-3">
              <span className="text-xs text-stone-400">{draftStatus || '草稿会自动保存在本地'}</span>
              {!editItem && content.trim() && (
                <button type="button" onClick={clearDraft} className="text-xs text-stone-400 hover:text-red-600">清空草稿</button>
              )}
              <button
                type="submit"
                disabled={!selectedFamilyId || !content.trim() || saving}
                className="inline-flex h-10 items-center gap-2 rounded-md bg-stone-950 px-4 text-sm text-white disabled:bg-stone-300"
              >
                {saving ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
                {editItem ? '保存修改' : '保存记忆'}
              </button>
            </div>
          </div>
        </div>

        {relatedMember && (
          <div className="rounded-md border border-sky-100 bg-sky-50 px-3 py-2 text-sm text-sky-800">
            当前内容会关联到 {memberDisplayName(relatedMember)}。
          </div>
        )}

        <div className="grid gap-3 rounded-md bg-stone-50 p-3 md:grid-cols-2 xl:grid-cols-4">
          <SelectField label="家族空间" value={selectedFamilyId || ''} onChange={(value) => {
            const nextFamilyId = positiveNumber(value);
            setSelectedFamilyId(nextFamilyId);
            if (nextFamilyId) setActiveFamilyId(nextFamilyId);
          }} options={families.map((family) => ({ value: family.id, label: family.name }))} />
          <SelectField label="类型" value={memoryType} onChange={(value) => handleMemoryTypeChange(value as MemoryContentType)} options={MEMORY_TYPES} />
          <SelectField label="可见范围" value={visibility} onChange={(value) => setVisibility(value as MemoryScope)} options={VISIBILITY_OPTIONS} />
          <label className="text-xs font-medium text-stone-500">
            标签
            <input value={tagText} onChange={(event) => setTagText(event.target.value)} placeholder="例如：日常 学习 照护" className="mt-1 h-9 w-full rounded-md border border-stone-200 bg-white px-3 text-sm outline-none" />
          </label>
          {memoryType === 'OBSERVATION' && (
            <SelectField
              label="关联成员（可选）"
              value={relatedUserId || ''}
              onChange={(value) => setRelatedUserId(positiveNumber(value) || undefined)}
              options={[{ value: '', label: '不关联具体成员' }, ...members.map((member) => ({ value: member.userId, label: memberDisplayName(member) }))]}
            />
          )}
        </div>
      </form>
    </WorkbenchPage>
  );
}

function SelectField({
  label,
  value,
  onChange,
  options,
}: {
  label: string;
  value: string | number;
  onChange: (value: string) => void;
  options: { value: string | number; label: string }[];
}) {
  return (
    <label className="text-xs font-medium text-stone-500">
      {label}
      <select value={value} onChange={(event) => onChange(event.target.value)} className="mt-1 h-9 w-full rounded-md border border-stone-200 bg-white px-3 text-sm outline-none">
        {options.map((option) => <option key={String(option.value)} value={option.value}>{option.label}</option>)}
      </select>
    </label>
  );
}

function normalizeMemoryType(value?: string | null): MemoryContentType | null {
  const normalized = value?.trim().toUpperCase() as MemoryContentType | undefined;
  return normalized && MEMORY_TYPE_VALUES.has(normalized) ? normalized : null;
}

function normalizeVisibility(value?: string | null): MemoryScope | null {
  return value === 'PRIVATE' || value === 'FAMILY_VISIBLE' || value === 'CARE_VISIBLE' ? value : null;
}

function positiveNumber(value?: string | null) {
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? number : null;
}

function draftKey(userId?: number, familyId?: number | null, relatedUserId?: number | null) {
  if (!userId || !familyId) return '';
  return `familyagent:write-memory-draft:${DRAFT_VERSION}:${userId}:${familyId}:${relatedUserId || 'self'}`;
}

function readDraft(key: string): UnifiedMemoryDraft | null {
  try {
    const parsed = JSON.parse(localStorage.getItem(key) || '') as UnifiedMemoryDraft;
    const memoryType = normalizeMemoryType(parsed.memoryType);
    const visibility = normalizeVisibility(parsed.visibility);
    if (!parsed.content || !memoryType || !visibility) return null;
    return { ...parsed, memoryType, visibility };
  } catch {
    return null;
  }
}

function formatTags(raw: string) {
  return raw.split(/[,，\s]+/).map((item) => item.trim()).filter(Boolean).slice(0, 8);
}

function memberDisplayName(member?: FamilyMember | null) {
  return member?.relationshipLabel?.trim()
    || member?.nickname?.trim()
    || member?.username?.trim()
    || (member ? `用户 ${member.userId}` : '');
}

function titleFromFirstLine(value: string) {
  return value.split(/\r?\n/).map((line) => line.trim()).find(Boolean)?.slice(0, 120) || '未命名记忆';
}
