'use client';

import { useCallback, useEffect, useState } from 'react';
import { Archive, Loader2, UserRound } from 'lucide-react';
import { memoryApi } from '@/lib/api';
import { useViewerRole } from '@/hooks/useViewerRole';
import type { PersonalMemoryView, PersonalMemoryVisibility, SharedPersonalMemoryView } from '@/types';

export default function PersonalMemoryWorkbench({ refreshSignal = 0 }: { refreshSignal?: number }) {
  const { families, activeFamilyId } = useViewerRole();
  const [items, setItems] = useState<PersonalMemoryView[]>([]);
  const [sharedItems, setSharedItems] = useState<SharedPersonalMemoryView[]>([]);
  const [loading, setLoading] = useState(true);
  const [savingId, setSavingId] = useState<number | null>(null);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    const [mine, shared] = await Promise.allSettled([
        memoryApi.listPersonalMemories(100),
        activeFamilyId ? memoryApi.listSharedPersonalMemories(activeFamilyId, 100) : Promise.resolve([]),
    ]);
    if (mine.status === 'rejected') {
      setError(mine.reason instanceof Error ? mine.reason.message : '个人记忆加载失败');
    } else {
      setItems(mine.value);
    }
    if (shared.status === 'rejected') {
      setSharedItems([]);
      setError((current) => current || (shared.reason instanceof Error ? shared.reason.message : '家人分享加载失败'));
    } else {
      setSharedItems(shared.value);
    }
    setLoading(false);
  }, [activeFamilyId]);

  useEffect(() => { void load(); }, [load, refreshSignal]);

  const updateVisibility = async (item: PersonalMemoryView, visibility: PersonalMemoryVisibility, familyIds?: number[]) => {
    setSavingId(item.id);
    setError('');
    try {
      const selectedFamilyIds = visibility === 'SELECTED_FAMILIES_VISIBLE'
        ? familyIds?.length ? familyIds : item.selectedFamilyIds.length ? item.selectedFamilyIds : activeFamilyId ? [activeFamilyId] : []
        : [];
      const updated = await memoryApi.updatePersonalMemoryVisibility(item.id, { visibility, selectedFamilyIds });
      setItems((current) => current.map((memory) => memory.id === item.id ? updated : memory));
    } catch (err) {
      setError(err instanceof Error ? err.message : '可见范围更新失败');
    } finally {
      setSavingId(null);
    }
  };

  const archive = async (item: PersonalMemoryView) => {
    if (!window.confirm('确认归档这条个人记忆吗？')) return;
    setSavingId(item.id);
    try {
      await memoryApi.deleteMemory(item.id);
      setItems((current) => current.filter((memory) => memory.id !== item.id));
    } catch (err) {
      setError(err instanceof Error ? err.message : '归档失败');
    } finally {
      setSavingId(null);
    }
  };

  if (loading) return <div className="flex h-48 items-center justify-center text-stone-400"><Loader2 className="mr-2 h-5 w-5 animate-spin" />正在加载个人记忆...</div>;

  return (
    <div className="mx-auto w-full max-w-6xl">
      {error && <div className="mb-4 rounded-lg border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
      <h2 className="mb-3 text-lg font-semibold text-stone-900">我的个人记忆</h2>
      {items.length === 0 ? (
        <div className="rounded-xl border border-dashed border-stone-300 bg-white p-12 text-center">
          <UserRound className="mx-auto h-10 w-10 text-stone-300" />
          <h2 className="mt-3 font-semibold text-stone-800">还没有个人记忆</h2>
          <p className="mt-1 text-sm text-stone-500">可以手动新建，也可以在对话中让 AI 整理草稿后保存。</p>
        </div>
      ) : (
        <div className="grid gap-4 md:grid-cols-2">
          {items.map((item) => (
            <article key={item.id} className="rounded-xl border border-stone-200 bg-white p-5 shadow-sm">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <span className="rounded-full bg-violet-50 px-2 py-1 text-[11px] font-medium text-violet-700">{typeLabel(item.type)}</span>
                  <h3 className="mt-3 line-clamp-2 font-semibold text-stone-900">{item.summary || item.content.slice(0, 60)}</h3>
                </div>
                <button type="button" onClick={() => void archive(item)} disabled={savingId === item.id} className="rounded-lg p-2 text-stone-400 hover:bg-stone-100 hover:text-stone-700" aria-label="归档个人记忆"><Archive className="h-4 w-4" /></button>
              </div>
              <p className="mt-3 line-clamp-4 whitespace-pre-wrap text-sm leading-6 text-stone-600">{item.content}</p>
              <div className="mt-4 border-t border-stone-100 pt-4">
                <select value={item.visibility} onChange={(event) => void updateVisibility(item, event.target.value as PersonalMemoryVisibility)} disabled={savingId === item.id} className="w-full rounded-lg border border-stone-200 bg-stone-50 px-3 py-2 text-sm">
                  <option value="PRIVATE">仅自己可见</option>
                  <option value="ALL_FAMILIES_VISIBLE">当前全部家族可见</option>
                  <option value="SELECTED_FAMILIES_VISIBLE">选择家族可见</option>
                  <option value="CARE_VISIBLE">仅照护可见</option>
                </select>
                {item.visibility === 'SELECTED_FAMILIES_VISIBLE' && (
                  <div className="mt-3 flex flex-wrap gap-2">
                    {families.map((family) => {
                      const selected = item.selectedFamilyIds.includes(family.id);
                      return (
                        <button key={family.id} type="button" onClick={() => void updateVisibility(item, 'SELECTED_FAMILIES_VISIBLE', toggleId(item.selectedFamilyIds, family.id))} className={selected ? 'rounded-full bg-emerald-100 px-3 py-1 text-xs text-emerald-800' : 'rounded-full bg-stone-100 px-3 py-1 text-xs text-stone-500'}>{family.name}</button>
                      );
                    })}
                  </div>
                )}
              </div>
            </article>
          ))}
        </div>
      )}

      <section className="mt-8 border-t border-stone-200 pt-6">
        <h2 className="text-lg font-semibold text-stone-900">家人分享给我的</h2>
        <p className="mt-1 text-sm text-stone-500">只显示当前家族中明确共享或授权照护查看的个人记忆。</p>
        {sharedItems.length === 0 ? (
          <p className="mt-4 rounded-xl border border-dashed border-stone-200 bg-white px-4 py-8 text-center text-sm text-stone-400">当前没有家人分享的个人记忆。</p>
        ) : (
          <div className="mt-4 grid gap-4 md:grid-cols-2">
            {sharedItems.map((memory) => (
              <article key={memory.id} className="rounded-xl border border-emerald-100 bg-emerald-50/30 p-5">
                <div className="flex flex-wrap items-center gap-2 text-xs text-emerald-800">
                  <span className="rounded-full bg-emerald-100 px-2 py-1">{memory.relationshipToViewer || memory.ownerName}</span>
                  {memory.relationshipToViewer !== memory.ownerName && <span>{memory.ownerName}</span>}
                  <span>·</span>
                  <span>{typeLabel(memory.type)}</span>
                </div>
                <h3 className="mt-3 font-semibold text-stone-900">{memory.summary || memory.content.slice(0, 60)}</h3>
                <p className="mt-2 line-clamp-5 whitespace-pre-wrap text-sm leading-6 text-stone-600">{memory.content}</p>
              </article>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

function typeLabel(value: string) {
  return ({ NOTE: '笔记', KNOWLEDGE: '新知', INSIGHT: '感悟', EXPERIENCE: '经历', PREFERENCE: '偏好', PLAN: '计划' } as Record<string, string>)[value] || value;
}

function toggleId(values: number[], id: number) {
  return values.includes(id) ? values.filter((value) => value !== id) : [...values, id];
}
