'use client';

import { useEffect, useState } from 'react';
import { Check, Loader2, X } from 'lucide-react';
import { normalizeVisibility } from '@/lib/savePlan';
import type {
  AgentMemorySavePlan,
  Family,
  MemoryContentType,
  MemoryLibraryKind,
  SaveMemoryVisibility,
} from '@/types';

interface SaveDraftCardProps {
  plan: AgentMemorySavePlan;
  isConfirming: boolean;
  onConfirm: (plan: AgentMemorySavePlan) => void;
  onCancel: () => void;
  families: Family[];
  activeFamilyId?: number | null;
}

const LIBRARY_OPTIONS: { value: MemoryLibraryKind; label: string }[] = [
  { value: 'PERSONAL', label: '个人记忆库' },
  { value: 'FAMILY', label: '家庭记忆库' },
];

const MEMORY_TYPE_OPTIONS: { value: MemoryContentType; label: string }[] = [
  { value: 'NOTE', label: '笔记' },
  { value: 'KNOWLEDGE', label: '新知' },
  { value: 'INSIGHT', label: '感悟' },
  { value: 'EXPERIENCE', label: '经历' },
  { value: 'OBSERVATION', label: '观察' },
  { value: 'PREFERENCE', label: '偏好' },
  { value: 'PLAN', label: '计划' },
];

const FAMILY_VISIBILITY_OPTIONS: { value: SaveMemoryVisibility; label: string }[] = [
  { value: 'PRIVATE', label: '仅自己可见' },
  { value: 'CARE_VISIBLE', label: '照护可见' },
  { value: 'FAMILY_VISIBLE', label: '家庭可见' },
];

const PERSONAL_VISIBILITY_OPTIONS: { value: SaveMemoryVisibility; label: string }[] = [
  { value: 'PRIVATE', label: '仅自己可见' },
  { value: 'ALL_FAMILIES_VISIBLE', label: '当前全部家族可见' },
  { value: 'SELECTED_FAMILIES_VISIBLE', label: '选择家族可见' },
  { value: 'CARE_VISIBLE', label: '仅照护可见' },
];

export default function SaveDraftCard({
  plan,
  isConfirming,
  onConfirm,
  onCancel,
  families,
  activeFamilyId,
}: SaveDraftCardProps) {
  const [title, setTitle] = useState(plan.title);
  const [content, setContent] = useState(plan.content);
  const [memoryLibrary, setMemoryLibrary] = useState(plan.memory_library);
  const [memoryType, setMemoryType] = useState(plan.memory_type);
  const [visibility, setVisibility] = useState<SaveMemoryVisibility>(
    normalizeVisibility(plan.memory_library, plan.visibility),
  );
  const [selectedFamilyIds, setSelectedFamilyIds] = useState<number[]>(
    initialSelectedFamilyIds(plan, activeFamilyId),
  );
  const [tags, setTags] = useState(plan.tags.join('，'));

  useEffect(() => {
    setTitle(plan.title);
    setContent(plan.content);
    setMemoryLibrary(plan.memory_library);
    setMemoryType(plan.memory_type);
    setVisibility(normalizeVisibility(plan.memory_library, plan.visibility));
    setSelectedFamilyIds(initialSelectedFamilyIds(plan, activeFamilyId));
    setTags(plan.tags.join('，'));
  }, [activeFamilyId, plan]);

  const needsSelectedFamilies =
    memoryLibrary === 'PERSONAL' && visibility === 'SELECTED_FAMILIES_VISIBLE';
  const canConfirm = Boolean(title.trim() && content.trim())
    && (!needsSelectedFamilies || selectedFamilyIds.length > 0)
    && !isConfirming;
  const visibilityOptions = memoryLibrary === 'PERSONAL'
    ? PERSONAL_VISIBILITY_OPTIONS
    : FAMILY_VISIBILITY_OPTIONS;

  return (
    <div
      data-testid="save-draft-card"
      className="mt-3 w-full max-w-2xl rounded-xl border border-sky-200 bg-sky-50/50 p-4 text-left text-stone-800 shadow-sm"
    >
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm font-semibold text-sky-900">保存草稿</p>
        <div className="flex gap-2">
          <select
            value={memoryLibrary}
            onChange={(event) => {
              const nextLibrary = event.target.value as MemoryLibraryKind;
              setMemoryLibrary(nextLibrary);
              setVisibility(normalizeVisibility(nextLibrary, visibility));
            }}
            disabled={isConfirming}
            className="rounded-md border border-sky-200 bg-white px-2 py-1.5 text-xs outline-none focus:border-sky-500"
            aria-label="记忆库"
          >
            {LIBRARY_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
          <select
            value={memoryType}
            onChange={(event) => setMemoryType(event.target.value as MemoryContentType)}
            disabled={isConfirming}
            className="rounded-md border border-sky-200 bg-white px-2 py-1.5 text-xs outline-none focus:border-sky-500"
            aria-label="记忆类型"
          >
            {MEMORY_TYPE_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
        </div>
      </div>

      <label className="mt-4 block text-xs font-medium text-stone-600">
        标题
        <input
          value={title}
          onChange={(event) => setTitle(event.target.value.slice(0, 24))}
          disabled={isConfirming}
          maxLength={24}
          className="mt-1 w-full rounded-md border border-stone-200 bg-white px-3 py-2 text-sm outline-none focus:border-sky-500"
        />
      </label>

      <label className="mt-3 block text-xs font-medium text-stone-600">
        保存内容
        <textarea
          value={content}
          onChange={(event) => setContent(event.target.value.slice(0, 1200))}
          disabled={isConfirming}
          maxLength={1200}
          rows={5}
          className="mt-1 w-full resize-y rounded-md border border-stone-200 bg-white px-3 py-2 text-sm leading-6 outline-none focus:border-sky-500"
        />
      </label>

      <div className="mt-3 grid gap-3 sm:grid-cols-2">
        <label className="block text-xs font-medium text-stone-600">
          可见范围
          <select
            value={visibility}
            onChange={(event) => setVisibility(event.target.value as SaveMemoryVisibility)}
            disabled={isConfirming}
            className="mt-1 w-full rounded-md border border-stone-200 bg-white px-3 py-2 text-sm outline-none focus:border-sky-500"
          >
            {visibilityOptions.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
        </label>

        <label className="block text-xs font-medium text-stone-600">
          标签
          <input
            value={tags}
            onChange={(event) => setTags(event.target.value)}
            disabled={isConfirming}
            placeholder="用逗号分隔"
            className="mt-1 w-full rounded-md border border-stone-200 bg-white px-3 py-2 text-sm outline-none focus:border-sky-500"
          />
        </label>
      </div>

      {needsSelectedFamilies && (
        <fieldset className="mt-3 rounded-lg border border-stone-200 bg-white p-3">
          <legend className="px-1 text-xs font-medium text-stone-600">选择可见家族</legend>
          <div className="grid gap-2 sm:grid-cols-2">
            {families.map((family) => (
              <label key={family.id} className="flex items-center gap-2 text-sm text-stone-700">
                <input
                  type="checkbox"
                  checked={selectedFamilyIds.includes(family.id)}
                  onChange={() => setSelectedFamilyIds(toggleFamilyId(selectedFamilyIds, family.id))}
                  disabled={isConfirming}
                  className="h-4 w-4 rounded border-stone-300 text-sky-700"
                />
                <span className="truncate">{family.name}</span>
              </label>
            ))}
          </div>
          {families.length === 0 && <p className="text-xs text-stone-500">当前没有可选择的家族。</p>}
        </fieldset>
      )}

      <div className="mt-4 flex justify-end gap-2">
        <button
          type="button"
          onClick={onCancel}
          disabled={isConfirming}
          className="inline-flex items-center gap-1 rounded-md border border-stone-200 bg-white px-3 py-2 text-sm text-stone-600 transition hover:bg-stone-50 disabled:opacity-50"
        >
          <X className="h-4 w-4" />
          取消
        </button>
        <button
          type="button"
          onClick={() => onConfirm(buildEditedPlan(
            plan,
            title,
            content,
            memoryLibrary,
            memoryType,
            visibility,
            tags,
            selectedFamilyIds,
          ))}
          disabled={!canConfirm}
          className="inline-flex items-center gap-1 rounded-md bg-sky-700 px-3 py-2 text-sm font-medium text-white transition hover:bg-sky-800 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {isConfirming ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />}
          确认保存
        </button>
      </div>
    </div>
  );
}

function buildEditedPlan(
  plan: AgentMemorySavePlan,
  title: string,
  content: string,
  memoryLibrary: MemoryLibraryKind,
  memoryType: MemoryContentType,
  visibility: SaveMemoryVisibility,
  tags: string,
  selectedFamilyIds: number[],
): AgentMemorySavePlan {
  const normalizedContent = content.trim();
  return {
    ...plan,
    should_save: true,
    memory_library: memoryLibrary,
    memory_type: memoryType,
    title: title.trim(),
    content: normalizedContent,
    summary: normalizedContent.slice(0, 80),
    visibility,
    selected_family_ids: memoryLibrary === 'PERSONAL' ? selectedFamilyIds : [],
    tags: tags
      .split(/[，,]/)
      .map((tag) => tag.trim())
      .filter(Boolean)
      .slice(0, 6),
    confirmation_message: '用户已确认保存草稿。',
  };
}

function initialSelectedFamilyIds(plan: AgentMemorySavePlan, activeFamilyId?: number | null) {
  const selected = Array.isArray(plan.selected_family_ids) ? plan.selected_family_ids : [];
  if (selected.length > 0) return selected;
  return activeFamilyId ? [activeFamilyId] : [];
}

function toggleFamilyId(selected: number[], familyId: number) {
  return selected.includes(familyId)
    ? selected.filter((id) => id !== familyId)
    : [...selected, familyId];
}
