'use client';

import { useMemo, useState } from 'react';
import { Loader2 } from 'lucide-react';
import { memoryApi } from '@/lib/api';
import type { Family, PersonalMemoryType, PersonalMemoryVisibility } from '@/types';

interface PersonalMemoryComposerProps {
  families: Family[];
  activeFamilyId?: number | null;
  onSaved: () => void;
}

const TYPE_OPTIONS: { value: PersonalMemoryType; label: string }[] = [
  { value: 'NOTE', label: '笔记' },
  { value: 'KNOWLEDGE', label: '新知' },
  { value: 'INSIGHT', label: '感悟' },
  { value: 'EXPERIENCE', label: '经历' },
  { value: 'OBSERVATION', label: '观察' },
  { value: 'PREFERENCE', label: '偏好' },
  { value: 'PLAN', label: '计划' },
];

export default function PersonalMemoryComposer({
  families,
  activeFamilyId,
  onSaved,
}: PersonalMemoryComposerProps) {
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [type, setType] = useState<PersonalMemoryType>('NOTE');
  const [visibility, setVisibility] = useState<PersonalMemoryVisibility>('PRIVATE');
  const [selectedFamilyIds, setSelectedFamilyIds] = useState<number[]>(activeFamilyId ? [activeFamilyId] : []);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const needsFamilies = visibility === 'SELECTED_FAMILIES_VISIBLE';
  const canSave = useMemo(
    () => content.trim() && (!needsFamilies || selectedFamilyIds.length > 0) && !saving,
    [content, needsFamilies, saving, selectedFamilyIds.length],
  );

  const save = async () => {
    if (!canSave) return;
    setSaving(true);
    setError('');
    try {
      await memoryApi.createPersonalMemory({
        content: content.trim(),
        summary: title.trim() || content.trim().slice(0, 80),
        type,
        visibility,
        selectedFamilyIds: needsFamilies ? selectedFamilyIds : [],
        importance: 3,
      });
      onSaved();
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存个人记忆失败');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      {error && <div className="rounded-lg border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
      <label className="block text-sm font-medium text-stone-700">
        标题
        <input
          value={title}
          onChange={(event) => setTitle(event.target.value.slice(0, 80))}
          className="mt-1 w-full rounded-lg border border-stone-200 bg-white px-3 py-2.5 outline-none focus:border-sky-500"
          placeholder="可选"
        />
      </label>
      <label className="block text-sm font-medium text-stone-700">
        内容
        <textarea
          value={content}
          onChange={(event) => setContent(event.target.value.slice(0, 8000))}
          rows={10}
          className="mt-1 w-full resize-y rounded-lg border border-stone-200 bg-white px-3 py-2.5 leading-7 outline-none focus:border-sky-500"
          placeholder="写下想长期保留的知识、观点、感悟、偏好或计划"
        />
      </label>
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="text-sm font-medium text-stone-700">
          类型
          <select value={type} onChange={(event) => setType(event.target.value as PersonalMemoryType)} className="mt-1 w-full rounded-lg border border-stone-200 bg-white px-3 py-2.5">
            {TYPE_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
          </select>
        </label>
        <label className="text-sm font-medium text-stone-700">
          可见范围
          <select value={visibility} onChange={(event) => setVisibility(event.target.value as PersonalMemoryVisibility)} className="mt-1 w-full rounded-lg border border-stone-200 bg-white px-3 py-2.5">
            <option value="PRIVATE">仅自己可见</option>
            <option value="ALL_FAMILIES_VISIBLE">当前全部家族可见</option>
            <option value="SELECTED_FAMILIES_VISIBLE">选择家族可见</option>
            <option value="CARE_VISIBLE">仅照护可见</option>
          </select>
        </label>
      </div>
      {needsFamilies && (
        <fieldset className="rounded-lg border border-stone-200 bg-white p-4">
          <legend className="px-1 text-sm font-medium text-stone-700">选择家族</legend>
          <div className="grid gap-2 sm:grid-cols-2">
            {families.map((family) => (
              <label key={family.id} className="flex items-center gap-2 text-sm text-stone-700">
                <input
                  type="checkbox"
                  checked={selectedFamilyIds.includes(family.id)}
                  onChange={() => setSelectedFamilyIds(toggleId(selectedFamilyIds, family.id))}
                />
                {family.name}
              </label>
            ))}
          </div>
        </fieldset>
      )}
      <div className="flex justify-end">
        <button type="button" onClick={() => void save()} disabled={!canSave} className="inline-flex items-center gap-2 rounded-lg bg-sky-700 px-5 py-2.5 text-sm font-medium text-white disabled:opacity-50">
          {saving && <Loader2 className="h-4 w-4 animate-spin" />}
          确认保存
        </button>
      </div>
    </div>
  );
}

function toggleId(values: number[], id: number) {
  return values.includes(id) ? values.filter((value) => value !== id) : [...values, id];
}
