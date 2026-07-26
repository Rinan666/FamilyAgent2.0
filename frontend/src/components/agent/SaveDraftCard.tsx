'use client';

import { useEffect, useState } from 'react';
import { Check, Loader2, X } from 'lucide-react';
import type { AgentSaveTool, AgentSaveToolPlan, MemoryScope } from '@/types';

interface SaveDraftCardProps {
  plan: AgentSaveToolPlan;
  isConfirming: boolean;
  onConfirm: (plan: AgentSaveToolPlan) => void;
  onCancel: () => void;
}

const TOOL_OPTIONS: { value: AgentSaveTool; label: string }[] = [
  { value: 'DIARY', label: '日记' },
  { value: 'FAMILY_MEMORY', label: '家庭记忆' },
  { value: 'GROWTH_GUARD', label: '成长观察' },
];

const VISIBILITY_OPTIONS: { value: MemoryScope; label: string }[] = [
  { value: 'PRIVATE', label: '仅自己可见' },
  { value: 'CARE_VISIBLE', label: '照护可见' },
  { value: 'FAMILY_VISIBLE', label: '家庭可见' },
];

export default function SaveDraftCard({
  plan,
  isConfirming,
  onConfirm,
  onCancel,
}: SaveDraftCardProps) {
  const [title, setTitle] = useState(plan.title);
  const [content, setContent] = useState(plan.content);
  const [tool, setTool] = useState<AgentSaveTool>(plan.tool === 'NONE' ? 'DIARY' : plan.tool);
  const [visibility, setVisibility] = useState<MemoryScope>(draftVisibility(plan));
  const [tags, setTags] = useState(plan.tags.join('，'));

  useEffect(() => {
    setTitle(plan.title);
    setContent(plan.content);
    setTool(plan.tool === 'NONE' ? 'DIARY' : plan.tool);
    setVisibility(draftVisibility(plan));
    setTags(plan.tags.join('，'));
  }, [plan]);

  const effectiveVisibility = tool === 'GROWTH_GUARD' ? 'CARE_VISIBLE' : visibility;
  const canConfirm = Boolean(title.trim() && content.trim()) && !isConfirming;

  return (
    <div className="mt-3 w-full max-w-2xl rounded-xl border border-emerald-200 bg-emerald-50/50 p-4 text-left text-stone-800 shadow-sm">
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-emerald-900">保存草稿</p>
          <p className="mt-0.5 text-xs text-emerald-700">尚未保存，可直接修改后确认。</p>
        </div>
        <select
          value={tool}
          onChange={(event) => setTool(event.target.value as AgentSaveTool)}
          disabled={isConfirming}
          className="rounded-md border border-emerald-200 bg-white px-2 py-1.5 text-xs outline-none focus:border-emerald-500"
          aria-label="保存类型"
        >
          {TOOL_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>{option.label}</option>
          ))}
        </select>
      </div>

      <label className="mt-4 block text-xs font-medium text-stone-600">
        标题
        <input
          value={title}
          onChange={(event) => setTitle(event.target.value.slice(0, 24))}
          disabled={isConfirming}
          maxLength={24}
          className="mt-1 w-full rounded-md border border-stone-200 bg-white px-3 py-2 text-sm outline-none focus:border-emerald-500"
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
          className="mt-1 w-full resize-y rounded-md border border-stone-200 bg-white px-3 py-2 text-sm leading-6 outline-none focus:border-emerald-500"
        />
      </label>

      <div className="mt-3 grid gap-3 sm:grid-cols-2">
        <label className="block text-xs font-medium text-stone-600">
          可见范围
          <select
            value={effectiveVisibility}
            onChange={(event) => setVisibility(event.target.value as MemoryScope)}
            disabled={isConfirming || tool === 'GROWTH_GUARD'}
            className="mt-1 w-full rounded-md border border-stone-200 bg-white px-3 py-2 text-sm outline-none focus:border-emerald-500 disabled:bg-stone-100"
          >
            {VISIBILITY_OPTIONS.map((option) => (
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
            className="mt-1 w-full rounded-md border border-stone-200 bg-white px-3 py-2 text-sm outline-none focus:border-emerald-500"
          />
        </label>
      </div>

      {plan.reason && (
        <p className="mt-3 text-xs leading-5 text-stone-500">AI 整理说明：{plan.reason}</p>
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
          onClick={() => onConfirm(buildEditedPlan(plan, title, content, tool, effectiveVisibility, tags))}
          disabled={!canConfirm}
          className="inline-flex items-center gap-1 rounded-md bg-emerald-700 px-3 py-2 text-sm font-medium text-white transition hover:bg-emerald-800 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {isConfirming ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />}
          确认保存
        </button>
      </div>
    </div>
  );
}

function draftVisibility(plan: AgentSaveToolPlan): MemoryScope {
  const value = String(plan.scope || plan.visibility || '').toUpperCase();
  if (value === 'CARE_VISIBLE' || value === 'FAMILY_VISIBLE') return value;
  return 'PRIVATE';
}

function buildEditedPlan(
  plan: AgentSaveToolPlan,
  title: string,
  content: string,
  tool: AgentSaveTool,
  visibility: MemoryScope,
  tags: string,
): AgentSaveToolPlan {
  const normalizedContent = content.trim();
  return {
    ...plan,
    should_save: true,
    tool,
    title: title.trim(),
    content: normalizedContent,
    summary: normalizedContent.slice(0, 80),
    visibility,
    scope: visibility,
    tags: tags
      .split(/[，,]/)
      .map((tag) => tag.trim())
      .filter(Boolean)
      .slice(0, 6),
    confirmation_message: '用户已确认保存草稿。',
  };
}
