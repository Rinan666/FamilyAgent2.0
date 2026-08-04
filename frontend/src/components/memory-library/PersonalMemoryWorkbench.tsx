'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { Archive, Loader2, MoreHorizontal, UserRound } from 'lucide-react';
import { memoryApi } from '@/lib/api';
import { useViewerRole } from '@/hooks/useViewerRole';
import { useAuthStore } from '@/stores/authStore';
import MemoryRecordCard, { MemoryRecordHeader } from './MemoryRecordCard';
import type {
  PersonalMemoryView,
  PersonalMemoryVisibility,
  SharedPersonalMemoryView,
} from '@/types';

interface PersonalMemoryWorkbenchProps {
  refreshSignal?: number;
  searchQuery?: string;
}

export default function PersonalMemoryWorkbench({
  refreshSignal = 0,
  searchQuery = '',
}: PersonalMemoryWorkbenchProps) {
  const { families, activeFamilyId } = useViewerRole();
  const user = useAuthStore((state) => state.user);
  const [items, setItems] = useState<PersonalMemoryView[]>([]);
  const [sharedItems, setSharedItems] = useState<SharedPersonalMemoryView[]>([]);
  const [loading, setLoading] = useState(true);
  const [savingId, setSavingId] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [openItemMenuId, setOpenItemMenuId] = useState<number | null>(null);
  const normalizedQuery = searchQuery.trim().toLocaleLowerCase('zh-CN');
  const visibleItems = useMemo(
    () =>
      items.filter((item) =>
        matchesQuery(normalizedQuery, [
          item.summary,
          item.content,
          typeLabel(item.type),
          visibilityLabel(item.visibility),
        ]),
      ),
    [items, normalizedQuery],
  );
  const visibleSharedItems = useMemo(
    () =>
      sharedItems.filter((item) =>
        matchesQuery(normalizedQuery, [
          item.relationshipToViewer,
          item.ownerName,
          item.summary,
          item.content,
          typeLabel(item.type),
          visibilityLabel(item.visibility),
        ]),
      ),
    [normalizedQuery, sharedItems],
  );

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    const [mine, shared] = await Promise.allSettled([
      memoryApi.listPersonalMemories(100),
      activeFamilyId
        ? memoryApi.listSharedPersonalMemories(activeFamilyId, 100)
        : Promise.resolve([]),
    ]);
    if (mine.status === 'rejected')
      setError(mine.reason instanceof Error ? mine.reason.message : '个人记忆加载失败');
    else setItems(mine.value);
    if (shared.status === 'rejected') {
      setError(
        (current) =>
          current || (shared.reason instanceof Error ? shared.reason.message : '家人分享加载失败'),
      );
      setSharedItems([]);
    } else setSharedItems(shared.value);
    setLoading(false);
  }, [activeFamilyId]);

  useEffect(() => {
    void load();
  }, [load, refreshSignal]);

  const updateVisibility = async (
    item: PersonalMemoryView,
    visibility: PersonalMemoryVisibility,
    familyIds?: number[],
  ) => {
    setSavingId(item.id);
    setError('');
    try {
      const selectedFamilyIds =
        visibility === 'SELECTED_FAMILIES_VISIBLE'
          ? familyIds?.length
            ? familyIds
            : item.selectedFamilyIds.length
              ? item.selectedFamilyIds
              : activeFamilyId
                ? [activeFamilyId]
                : []
          : [];
      const updated = await memoryApi.updatePersonalMemoryVisibility(item.id, {
        visibility,
        selectedFamilyIds,
      });
      setItems((current) => current.map((memory) => (memory.id === item.id ? updated : memory)));
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

  if (loading) {
    return (
      <div className="flex h-48 items-center justify-center text-stone-400">
        <Loader2 className="mr-2 h-5 w-5 animate-spin" />
        正在加载个人记忆...
      </div>
    );
  }

  const personalMemberName = user?.nickname?.trim() || user?.username?.trim() || '我';

  return (
    <div className="mx-auto w-full max-w-6xl">
      {error && (
        <div className="mb-4 rounded-2xl border border-red-100/80 bg-red-50/75 px-4 py-3 text-sm text-red-700 backdrop-blur">
          {error}
        </div>
      )}
      <div className="mb-4 flex items-end justify-between gap-3 px-1">
        <h2 className="text-lg font-semibold text-stone-900">我的个人记忆</h2>
        <span className="rounded-full bg-white/65 px-3 py-1 text-xs text-stone-500 ring-1 ring-white/80">
          {visibleItems.length} 条记录
        </span>
      </div>

      {visibleItems.length === 0 ? (
        <div className="glass-panel rounded-[24px] border-dashed border-stone-300/80 p-12 text-center">
          <UserRound className="mx-auto h-10 w-10 text-stone-300" />
          <h2 className="mt-3 font-semibold text-stone-800">
            {normalizedQuery ? '没有匹配的个人记忆' : '还没有个人记忆'}
          </h2>
        </div>
      ) : (
        <div className="space-y-5">
          {visibleItems.map((item) => (
            <MemoryRecordCard
              key={item.id}
              header={
                <MemoryRecordHeader
                  relationshipLabel="我"
                  memberName={personalMemberName}
                  typeLabel={typeLabel(item.type)}
                  createdAt={item.createdAt}
                />
              }
              badges={
                <span className="rounded-md bg-blue-50 px-2 py-1 text-sm font-medium text-blue-600">
                  #{visibilityLabel(item.visibility)}
                </span>
              }
              title={item.summary || item.content.slice(0, 60)}
              body={item.content}
              actions={
                <>
                  <button
                    type="button"
                    onClick={() =>
                      setOpenItemMenuId((current) => (current === item.id ? null : item.id))
                    }
                    className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-stone-400 transition hover:bg-stone-100 hover:text-stone-700"
                    aria-label="打开操作菜单"
                    aria-expanded={openItemMenuId === item.id}
                  >
                    <MoreHorizontal className="h-5 w-5" />
                  </button>
                  {openItemMenuId === item.id && (
                    <div className="absolute right-5 top-14 z-20 w-64 overflow-hidden rounded-lg border border-stone-200 bg-white py-2 text-left shadow-2xl sm:right-8">
                      <div className="px-4 py-3">
                        <label className="block text-xs text-stone-400">
                          可见范围
                          <select
                            value={item.visibility}
                            onChange={(event) =>
                              void updateVisibility(
                                item,
                                event.target.value as PersonalMemoryVisibility,
                              )
                            }
                            disabled={savingId === item.id}
                            className="mt-1 h-9 w-full rounded-md border border-stone-200 bg-white px-2 text-sm text-stone-700 outline-none transition focus:border-sky-400 disabled:opacity-60"
                          >
                            <option value="PRIVATE">仅自己可见</option>
                            <option value="ALL_FAMILIES_VISIBLE">全部家庭可见</option>
                            <option value="SELECTED_FAMILIES_VISIBLE">指定家庭可见</option>
                            <option value="CARE_VISIBLE">照护可见</option>
                          </select>
                        </label>
                        {item.visibility === 'SELECTED_FAMILIES_VISIBLE' && (
                          <div className="mt-3 flex flex-wrap gap-2">
                            {families.map((family) => {
                              const selected = item.selectedFamilyIds.includes(family.id);
                              return (
                                <button
                                  key={family.id}
                                  type="button"
                                  onClick={() =>
                                    void updateVisibility(
                                      item,
                                      'SELECTED_FAMILIES_VISIBLE',
                                      toggleId(item.selectedFamilyIds, family.id),
                                    )
                                  }
                                  className={
                                    selected
                                      ? 'rounded-full bg-sky-100 px-3 py-1 text-xs text-sky-800'
                                      : 'rounded-full bg-stone-100 px-3 py-1 text-xs text-stone-500'
                                  }
                                >
                                  {family.name}
                                </button>
                              );
                            })}
                          </div>
                        )}
                      </div>
                      <button
                        type="button"
                        onClick={() => {
                          setOpenItemMenuId(null);
                          void archive(item);
                        }}
                        disabled={savingId === item.id}
                        className="flex h-11 w-full items-center gap-3 border-t border-stone-100 px-4 text-sm text-stone-700 hover:bg-stone-50 disabled:opacity-60"
                      >
                        <Archive className="h-4 w-4" />
                        归档
                      </button>
                    </div>
                  )}
                </>
              }
            />
          ))}
        </div>
      )}

      <section className="mt-10 border-t border-white/70 pt-7">
        <h2 className="text-lg font-semibold text-stone-900">家人分享给我的</h2>
        {visibleSharedItems.length === 0 ? (
          <p className="glass-panel mt-4 rounded-[24px] border-dashed border-stone-200/80 px-4 py-8 text-center text-sm text-stone-400">
            {normalizedQuery ? '没有匹配的分享记忆。' : '当前没有家人分享的个人记忆。'}
          </p>
        ) : (
          <div className="mt-4 space-y-4">
            {visibleSharedItems.map((memory) => (
              <MemoryRecordCard
                key={memory.id}
                header={
                  <MemoryRecordHeader
                    relationshipLabel={memory.relationshipToViewer || memory.ownerName}
                    memberName={memory.ownerName}
                    typeLabel={typeLabel(memory.type)}
                    createdAt={memory.createdAt}
                  />
                }
                title={memory.summary || memory.content.slice(0, 60)}
                body={memory.content}
              />
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

function typeLabel(value: string) {
  return (
    (
      {
        NOTE: '笔记',
        KNOWLEDGE: '新知',
        INSIGHT: '感悟',
        EXPERIENCE: '经历',
        OBSERVATION: '观察',
        PREFERENCE: '偏好',
        PLAN: '计划',
      } as Record<string, string>
    )[value] || value
  );
}

function visibilityLabel(value: string) {
  return (
    (
      {
        PRIVATE: '仅自己可见',
        ALL_FAMILIES_VISIBLE: '全部家庭可见',
        SELECTED_FAMILIES_VISIBLE: '指定家庭可见',
        CARE_VISIBLE: '照护可见',
      } as Record<string, string>
    )[value] || value
  );
}

function toggleId(values: number[], id: number) {
  return values.includes(id) ? values.filter((value) => value !== id) : [...values, id];
}

function matchesQuery(query: string, values: Array<string | undefined>) {
  if (!query) return true;
  return values.some((value) => value?.toLocaleLowerCase('zh-CN').includes(query));
}
