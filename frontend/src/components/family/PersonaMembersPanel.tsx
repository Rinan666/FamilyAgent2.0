'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { BookOpen, Loader2, Pencil, Plus, Trash2, UserCheck, X } from 'lucide-react';
import { familyApi } from '@/lib/api';
import type { CreatePersonaMemberRequest, PersonaMember, UpdatePersonaMemberRequest } from '@/types';

interface PersonaMembersPanelProps {
  familyId?: number | null;
  isOwner?: boolean;
}

const PERSONA_FIELDS: { key: keyof CreatePersonaMemberRequest; label: string; rows?: number }[] = [
  { key: 'name', label: '姓名' },
  { key: 'description', label: '简介', rows: 2 },
  { key: 'eraIdentity', label: '时代 / 身份' },
  { key: 'values', label: '价值观', rows: 2 },
  { key: 'speakingStyle', label: '说话风格', rows: 2 },
  { key: 'personality', label: '性格气质', rows: 2 },
];

const EMPTY_FORM: CreatePersonaMemberRequest = {
  name: '',
  description: '',
  eraIdentity: '',
  values: '',
  speakingStyle: '',
  personality: '',
};

function PersonaCard({
  persona,
  isOwner,
  onEdit,
  onDelete,
}: {
  persona: PersonaMember;
  isOwner: boolean;
  onEdit: (persona: PersonaMember) => void;
  onDelete: (persona: PersonaMember) => void;
}) {
  return (
    <article className="rounded-2xl border border-gray-200 bg-white p-5">
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-start gap-3 min-w-0">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-violet-100 text-base font-bold text-violet-700">
            {persona.name.charAt(0)}
          </div>
          <div className="min-w-0">
            <h3 className="text-sm font-semibold text-gray-900">{persona.name}</h3>
            {persona.eraIdentity && (
              <p className="mt-0.5 text-xs text-gray-500">{persona.eraIdentity}</p>
            )}
            {persona.description && (
              <p className="mt-1 line-clamp-2 text-xs leading-5 text-gray-600">{persona.description}</p>
            )}
            <div className="mt-2 flex flex-wrap gap-1.5">
              {persona.hasMaterial ? (
                <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 text-[11px] text-emerald-700">
                  <BookOpen className="h-3 w-3" />
                  已有材料
                </span>
              ) : (
                <span className="rounded-full bg-gray-100 px-2 py-0.5 text-[11px] text-gray-500">暂无材料</span>
              )}
            </div>
          </div>
        </div>
        {isOwner && (
          <div className="flex shrink-0 gap-1">
            <button
              type="button"
              onClick={() => onEdit(persona)}
              className="rounded-lg p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-700"
              title="编辑"
            >
              <Pencil className="h-3.5 w-3.5" />
            </button>
            <button
              type="button"
              onClick={() => onDelete(persona)}
              className="rounded-lg p-1.5 text-gray-400 hover:bg-red-50 hover:text-red-600"
              title="删除"
            >
              <Trash2 className="h-3.5 w-3.5" />
            </button>
          </div>
        )}
      </div>
    </article>
  );
}

function PersonaFormModal({
  title,
  initialValues,
  onSubmit,
  onClose,
  submitting,
}: {
  title: string;
  initialValues: CreatePersonaMemberRequest;
  onSubmit: (data: CreatePersonaMemberRequest) => void;
  onClose: () => void;
  submitting: boolean;
}) {
  const [form, setForm] = useState<CreatePersonaMemberRequest>(initialValues);

  function set(key: keyof CreatePersonaMemberRequest, value: string) {
    setForm((prev) => ({ ...prev, [key]: value }));
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    onSubmit(form);
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="w-full max-w-lg rounded-2xl bg-white shadow-xl">
        <div className="flex items-center justify-between border-b border-gray-100 px-5 py-4">
          <h2 className="text-base font-semibold text-gray-900">{title}</h2>
          <button type="button" onClick={onClose} className="rounded-lg p-1 text-gray-400 hover:bg-gray-100">
            <X className="h-4 w-4" />
          </button>
        </div>
        <form onSubmit={handleSubmit} className="max-h-[70vh] overflow-y-auto px-5 py-4">
          <div className="space-y-4">
            {PERSONA_FIELDS.map(({ key, label, rows }) => (
              <div key={key}>
                <label className="mb-1 block text-xs font-medium text-gray-700">
                  {label}
                  {key === 'name' && <span className="ml-1 text-red-500">*</span>}
                </label>
                {rows ? (
                  <textarea
                    rows={rows}
                    value={form[key] ?? ''}
                    onChange={(e) => set(key, e.target.value)}
                    required={key === 'name'}
                    className="w-full resize-none rounded-xl border border-gray-200 px-3 py-2 text-sm focus:border-violet-400 focus:outline-none focus:ring-1 focus:ring-violet-400"
                  />
                ) : (
                  <input
                    type="text"
                    value={form[key] ?? ''}
                    onChange={(e) => set(key, e.target.value)}
                    required={key === 'name'}
                    className="w-full rounded-xl border border-gray-200 px-3 py-2 text-sm focus:border-violet-400 focus:outline-none focus:ring-1 focus:ring-violet-400"
                  />
                )}
              </div>
            ))}
          </div>
          <div className="mt-5 flex justify-end gap-2">
            <button
              type="button"
              onClick={onClose}
              className="rounded-xl border border-gray-200 px-4 py-2 text-sm text-gray-600 hover:bg-gray-50"
            >
              取消
            </button>
            <button
              type="submit"
              disabled={submitting || !form.name?.trim()}
              className="inline-flex items-center gap-1.5 rounded-xl bg-violet-600 px-4 py-2 text-sm font-medium text-white hover:bg-violet-700 disabled:opacity-50"
            >
              {submitting && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
              保存
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

function DeleteConfirmModal({
  persona,
  onConfirm,
  onClose,
  submitting,
}: {
  persona: PersonaMember;
  onConfirm: (word: string) => void;
  onClose: () => void;
  submitting: boolean;
}) {
  const [word, setWord] = useState('');
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="w-full max-w-sm rounded-2xl bg-white p-6 shadow-xl">
        <h2 className="text-base font-semibold text-gray-900">删除精神成员</h2>
        <p className="mt-2 text-sm text-gray-600">
          即将删除 <span className="font-medium text-gray-900">「{persona.name}」</span>，此操作不可撤销。
          请输入 <span className="font-medium text-red-600">确认删除</span> 以继续。
        </p>
        <input
          ref={inputRef}
          type="text"
          value={word}
          onChange={(e) => setWord(e.target.value)}
          placeholder="确认删除"
          className="mt-3 w-full rounded-xl border border-gray-200 px-3 py-2 text-sm focus:border-red-400 focus:outline-none focus:ring-1 focus:ring-red-400"
        />
        <div className="mt-4 flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className="rounded-xl border border-gray-200 px-4 py-2 text-sm text-gray-600 hover:bg-gray-50"
          >
            取消
          </button>
          <button
            type="button"
            disabled={word !== '确认删除' || submitting}
            onClick={() => onConfirm(word)}
            className="inline-flex items-center gap-1.5 rounded-xl bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700 disabled:opacity-50"
          >
            {submitting && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
            删除
          </button>
        </div>
      </div>
    </div>
  );
}

export default function PersonaMembersPanel({ familyId, isOwner = false }: PersonaMembersPanelProps) {
  const [personas, setPersonas] = useState<PersonaMember[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [editTarget, setEditTarget] = useState<PersonaMember | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<PersonaMember | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState('');

  const load = useCallback(() => {
    if (!familyId) return;
    setLoading(true);
    setError('');
    familyApi.listPersonaMembers(familyId)
      .then(setPersonas)
      .catch((err) => setError(err instanceof Error ? err.message : '加载精神成员失败'))
      .finally(() => setLoading(false));
  }, [familyId]);

  useEffect(() => {
    load();
  }, [load]);

  async function handleCreate(data: CreatePersonaMemberRequest) {
    if (!familyId) return;
    setSubmitting(true);
    setFormError('');
    try {
      await familyApi.createPersonaMember(familyId, data);
      setShowCreate(false);
      load();
    } catch (err) {
      setFormError(err instanceof Error ? err.message : '创建失败');
    } finally {
      setSubmitting(false);
    }
  }

  async function handleUpdate(data: UpdatePersonaMemberRequest) {
    if (!familyId || !editTarget) return;
    setSubmitting(true);
    setFormError('');
    try {
      await familyApi.updatePersonaMember(familyId, editTarget.id, data);
      setEditTarget(null);
      load();
    } catch (err) {
      setFormError(err instanceof Error ? err.message : '更新失败');
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete(confirmationWord: string) {
    if (!familyId || !deleteTarget) return;
    setSubmitting(true);
    try {
      await familyApi.deletePersonaMember(familyId, deleteTarget.id, confirmationWord);
      setDeleteTarget(null);
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : '删除失败');
      setDeleteTarget(null);
    } finally {
      setSubmitting(false);
    }
  }

  if (!familyId) {
    return (
      <div className="rounded-2xl border border-dashed border-gray-200 px-4 py-10 text-center text-sm text-gray-400">
        请先选择一个家族空间
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {error && (
        <div className="rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">{error}</div>
      )}

      {loading ? (
        <div className="flex h-32 items-center justify-center text-gray-400">
          <Loader2 className="mr-2 h-4 w-4 animate-spin" />
          正在加载精神成员...
        </div>
      ) : (
        <>
          {personas.length === 0 ? (
            <div className="rounded-2xl border border-dashed border-gray-200 px-4 py-10 text-center">
              <UserCheck className="mx-auto mb-2 h-8 w-8 text-gray-300" />
              <p className="text-sm text-gray-500">还没有精神成员</p>
              {isOwner && (
                <p className="mt-1 text-xs text-gray-400">点击下方按钮添加第一个精神顾问</p>
              )}
            </div>
          ) : (
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {personas.map((persona) => (
                <PersonaCard
                  key={persona.id}
                  persona={persona}
                  isOwner={isOwner}
                  onEdit={setEditTarget}
                  onDelete={setDeleteTarget}
                />
              ))}
            </div>
          )}

          {isOwner && personas.length < 3 && (
            <button
              type="button"
              onClick={() => { setFormError(''); setShowCreate(true); }}
              className="flex w-full items-center justify-center gap-2 rounded-2xl border border-dashed border-violet-300 py-3 text-sm font-medium text-violet-600 hover:border-violet-400 hover:bg-violet-50"
            >
              <Plus className="h-4 w-4" />
              新增精神成员
            </button>
          )}

          {isOwner && personas.length >= 3 && (
            <p className="text-center text-xs text-gray-400">已达到 3 个精神成员上限，请先删除一个再添加。</p>
          )}
        </>
      )}

      {showCreate && (
        <PersonaFormModal
          title="新增精神成员"
          initialValues={EMPTY_FORM}
          onSubmit={handleCreate}
          onClose={() => setShowCreate(false)}
          submitting={submitting}
        />
      )}

      {editTarget && (
        <PersonaFormModal
          title="编辑精神成员"
          initialValues={{
            name: editTarget.name,
            description: editTarget.description ?? '',
            eraIdentity: editTarget.eraIdentity ?? '',
            values: editTarget.values ?? '',
            speakingStyle: editTarget.speakingStyle ?? '',
            personality: editTarget.personality ?? '',
          }}
          onSubmit={handleUpdate}
          onClose={() => setEditTarget(null)}
          submitting={submitting}
        />
      )}

      {deleteTarget && (
        <DeleteConfirmModal
          persona={deleteTarget}
          onConfirm={handleDelete}
          onClose={() => setDeleteTarget(null)}
          submitting={submitting}
        />
      )}

      {formError && (
        <div className="rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">{formError}</div>
      )}
    </div>
  );
}
