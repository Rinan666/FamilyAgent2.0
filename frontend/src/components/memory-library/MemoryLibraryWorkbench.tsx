'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import {
  Archive,
  BookHeart,
  Edit3,
  Loader2,
  MoreHorizontal,
  RotateCcw,
  Trash2,
} from 'lucide-react';
import { familyApi, memoryLibraryApi } from '@/lib/api';
import { useViewerRole } from '@/hooks/useViewerRole';
import MemoryRecordCard, { MemoryRecordHeader } from './MemoryRecordCard';
import type { MemoryLibraryItem, PageResult, FamilyMember } from '@/types';

type LibraryViewMode = 'ACTIVE' | 'ARCHIVED';

const pageSizeOptions = [6, 12, 24];
const familyVisibilityOptions = ['PRIVATE', 'CARE_VISIBLE', 'FAMILY_VISIBLE'];
const diaryVisibilityOptions = [...familyVisibilityOptions, 'LEGACY_VISIBLE'];

function emptyPage(pageSize: number): PageResult<MemoryLibraryItem> {
  return { items: [], page: 1, pageSize, total: 0, totalPages: 0 };
}

function resolveRequestedViewMode(view?: string | null): LibraryViewMode {
  return view?.toLowerCase() === 'archived' ? 'ARCHIVED' : 'ACTIVE';
}

function visibilityLabel(value?: string) {
  const text = String(value || '').toUpperCase();
  if (text === 'PRIVATE') return '仅自己可见';
  if (text === 'CARE_VISIBLE') return '照护可见';
  if (text === 'LEGACY_VISIBLE') return '传承可见';
  if (text === 'FAMILY_VISIBLE' || text === 'FAMILY') return '全家可见';
  return value || '按权限可见';
}

function isLegacyAiSummary(item: MemoryLibraryItem) {
  return item.sourceType === 'AI_SUMMARY';
}

function editVisibilityOptions(item: MemoryLibraryItem) {
  return item.sourceType === 'LIFE_RECORD' ? diaryVisibilityOptions : familyVisibilityOptions;
}

function familyMemoryTypeLabel(value?: string) {
  const labels: Record<string, string> = {
    DAILY: '日常',
    IMPORTANT_EVENT: '重要事件',
    LESSON: '经验',
    EMOTION: '情绪',
    MESSAGE_TO_FAMILY: '家书',
    SELF_REFLECTION: '反思',
    NOTE: '笔记',
    KNOWLEDGE: '新知',
    INSIGHT: '感悟',
    EXPERIENCE: '经历',
    OBSERVATION: '观察',
  };
  return labels[String(value || '').toUpperCase()] || value || '记忆';
}

function resolveMemberHeader(
  item: MemoryLibraryItem,
  members: FamilyMember[],
  viewerUserId?: number,
) {
  const memberId = item.memberUserId ?? item.authorUserId;
  const member = members.find((candidate) => candidate.userId === memberId);
  return {
    relationshipLabel:
      memberId === viewerUserId ? '我' : member?.relationshipLabel?.trim() || '家人',
    memberName:
      member?.nickname?.trim() || member?.username?.trim() || item.memberName || '家庭成员',
  };
}

interface MemoryLibraryWorkbenchProps {
  embedded?: boolean;
  simplified?: boolean;
  searchQuery?: string;
  libraryViewMode?: LibraryViewMode;
  refreshSignal?: number;
  onEditEntry?: (item: MemoryLibraryItem) => void;
}

export default function MemoryLibraryWorkbench({
  simplified = false,
  searchQuery,
  libraryViewMode,
  refreshSignal = 0,
  onEditEntry,
}: MemoryLibraryWorkbenchProps) {
  const searchParams = useSearchParams();
  const {
    families,
    activeFamilyId,
    activeMembership,
    viewerRole,
    setActiveFamilyId,
    isLoading: loadingFamilies,
  } = useViewerRole();

  const requestedFamilyId = useMemo(() => {
    const value = Number(searchParams.get('familyId'));
    return Number.isFinite(value) && value > 0 ? value : null;
  }, [searchParams]);

  const requestedViewMode = useMemo<LibraryViewMode>(() => {
    return libraryViewMode ?? resolveRequestedViewMode(searchParams.get('view'));
  }, [libraryViewMode, searchParams]);

  const latestLoadRequestId = useRef(0);
  const [pageData, setPageData] = useState<PageResult<MemoryLibraryItem>>(() =>
    emptyPage(pageSizeOptions[0]),
  );
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [query, setQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [viewMode, setViewMode] = useState<LibraryViewMode>(() => requestedViewMode);
  const [pageSize, setPageSize] = useState(pageSizeOptions[0]);
  const [currentPage, setCurrentPage] = useState(1);
  const [archivingItemId, setArchivingItemId] = useState('');
  const [restoringItemId, setRestoringItemId] = useState('');
  const [deletingItemId, setDeletingItemId] = useState('');
  const [savingItemId, setSavingItemId] = useState('');
  const [openItemMenuId, setOpenItemMenuId] = useState('');
  const [familyMembers, setFamilyMembers] = useState<FamilyMember[]>([]);

  const canManageLibrary = activeMembership?.role === 'OWNER' || viewerRole === 'ADMIN';
  const totalPages = Math.max(1, pageData.totalPages || 1);

  const loadData = useCallback(async () => {
    const requestId = latestLoadRequestId.current + 1;
    latestLoadRequestId.current = requestId;

    if (!activeFamilyId) {
      setPageData(emptyPage(pageSize));
      setIsLoading(false);
      setError('');
      return;
    }

    setIsLoading(true);
    setError('');
    try {
      const nextPage = await (
        viewMode === 'ARCHIVED' ? memoryLibraryApi.archived : memoryLibraryApi.search
      )({
        familyId: activeFamilyId,
        page: currentPage,
        pageSize,
        keyword: debouncedQuery,
        type: 'ALL',
      });
      if (latestLoadRequestId.current !== requestId) return;
      setPageData(nextPage);
    } catch (err) {
      if (latestLoadRequestId.current !== requestId) return;
      setError(err instanceof Error ? err.message : '记忆库加载失败');
      setPageData(emptyPage(pageSize));
    } finally {
      if (latestLoadRequestId.current === requestId) {
        setIsLoading(false);
      }
    }
  }, [activeFamilyId, currentPage, debouncedQuery, pageSize, viewMode]);

  useEffect(() => {
    const nextFamilyId =
      (requestedFamilyId && families.some((family) => family.id === requestedFamilyId)
        ? requestedFamilyId
        : activeFamilyId && families.some((family) => family.id === activeFamilyId)
          ? activeFamilyId
          : families[0]?.id) || null;
    if (nextFamilyId && activeFamilyId !== nextFamilyId) {
      setActiveFamilyId(nextFamilyId);
    }
  }, [activeFamilyId, families, requestedFamilyId, setActiveFamilyId]);

  useEffect(() => {
    setQuery(searchQuery ?? searchParams.get('q') ?? '');
  }, [searchParams, searchQuery]);

  useEffect(() => {
    if (viewMode === requestedViewMode) return;
    setViewMode(requestedViewMode);
    setCurrentPage(1);
  }, [requestedViewMode, viewMode]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedQuery(query.trim());
      setCurrentPage(1);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [query]);

  useEffect(() => {
    setCurrentPage(1);
  }, [viewMode, pageSize]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  useEffect(() => {
    if (!activeFamilyId) {
      setFamilyMembers([]);
      return;
    }
    familyApi
      .getMembers(activeFamilyId)
      .then(setFamilyMembers)
      .catch(() => setFamilyMembers([]));
  }, [activeFamilyId]);

  useEffect(() => {
    if (pageData.totalPages > 0 && currentPage > pageData.totalPages) {
      setCurrentPage(pageData.totalPages);
    }
  }, [currentPage, pageData.totalPages]);

  const refreshAll = useCallback(async () => {
    await loadData();
  }, [loadData]);

  useEffect(() => {
    if (refreshSignal <= 0) return;
    void refreshAll();
  }, [refreshAll, refreshSignal]);

  const handleArchiveItem = useCallback(
    async (item: MemoryLibraryItem) => {
      if (!activeFamilyId) return;
      if (!window.confirm('确认归档这条记忆吗？')) return;
      setArchivingItemId(item.id);
      setError('');
      try {
        await memoryLibraryApi.archiveItem(activeFamilyId, item.id);
        setSuccess('已归档');
        await refreshAll();
      } catch (err) {
        setError(err instanceof Error ? err.message : '归档失败');
      } finally {
        setArchivingItemId('');
      }
    },
    [activeFamilyId, refreshAll],
  );

  const handleRestoreItem = useCallback(
    async (item: MemoryLibraryItem) => {
      if (!activeFamilyId) return;
      if (!window.confirm('确认恢复这条记忆吗？')) return;
      setRestoringItemId(item.id);
      setError('');
      try {
        await memoryLibraryApi.restoreItem(activeFamilyId, item.id);
        setSuccess('已恢复');
        await refreshAll();
      } catch (err) {
        setError(err instanceof Error ? err.message : '恢复失败');
      } finally {
        setRestoringItemId('');
      }
    },
    [activeFamilyId, refreshAll],
  );

  const handleDeleteItem = useCallback(
    async (item: MemoryLibraryItem) => {
      if (!activeFamilyId) return;
      const deletingLegacyAiSummary = viewMode === 'ACTIVE' && isLegacyAiSummary(item);
      const confirmMessage = deletingLegacyAiSummary
        ? '确认永久删除这条历史 AI 摘要吗？删除后无法恢复。'
        : '确认永久删除这条已归档记忆吗？删除后无法恢复。';
      if (!window.confirm(confirmMessage)) return;
      setDeletingItemId(item.id);
      setError('');
      try {
        await memoryLibraryApi.deleteArchivedItem(activeFamilyId, item.id);
        setSuccess('已永久删除');
        await refreshAll();
      } catch (err) {
        setError(err instanceof Error ? err.message : '删除失败');
      } finally {
        setDeletingItemId('');
      }
    },
    [activeFamilyId, refreshAll, viewMode],
  );

  const handleUpdateVisibility = useCallback(
    async (item: MemoryLibraryItem, nextVisibility: string) => {
      if (!activeFamilyId || nextVisibility === item.visibility) return;
      const body = item.body?.trim() || item.title?.trim();
      if (!body) return;
      setSavingItemId(item.id);
      setError('');
      try {
        await memoryLibraryApi.updateItem({
          familyId: activeFamilyId,
          itemId: item.id,
          title: item.title || undefined,
          body,
          type: item.type || undefined,
          visibility: nextVisibility,
          tags: item.tags || [],
        });
        setSuccess('权限已更新');
        await refreshAll();
      } catch (err) {
        setError(err instanceof Error ? err.message : '权限更新失败');
      } finally {
        setSavingItemId('');
      }
    },
    [activeFamilyId, refreshAll],
  );

  const renderItemMenu = (item: MemoryLibraryItem) => {
    if (!canManageLibrary || openItemMenuId !== item.id) return null;

    return (
      <div
        className="absolute right-5 top-14 z-20 w-60 overflow-hidden rounded-lg border border-stone-200 bg-white py-2 text-left shadow-2xl sm:right-8"
        onClick={(event) => event.stopPropagation()}
      >
        {viewMode === 'ACTIVE' && !isLegacyAiSummary(item) && (
          <>
            {onEditEntry && (
              <button
                type="button"
                onClick={() => {
                  onEditEntry(item);
                  setOpenItemMenuId('');
                }}
                className="flex h-11 w-full items-center gap-3 px-4 text-sm text-stone-700 hover:bg-stone-50"
              >
                <Edit3 className="h-4 w-4" />
                编辑
              </button>
            )}
            <div className="border-t border-stone-100 px-4 py-3">
              <label className="block text-xs text-stone-400">
                权限
                <select
                  value={item.visibility || ''}
                  onChange={(event) => void handleUpdateVisibility(item, event.target.value)}
                  disabled={savingItemId === item.id}
                  className="mt-1 h-9 w-full rounded-md border border-stone-200 bg-white px-2 text-sm text-stone-700 outline-none transition focus:border-sky-400 disabled:opacity-60"
                >
                  {editVisibilityOptions(item).map((option) => (
                    <option key={option} value={option}>
                      {visibilityLabel(option)}
                    </option>
                  ))}
                </select>
              </label>
            </div>
            <button
              type="button"
              onClick={() => {
                setOpenItemMenuId('');
                void handleArchiveItem(item);
              }}
              disabled={archivingItemId === item.id}
              className="flex h-11 w-full items-center gap-3 px-4 text-sm text-stone-700 hover:bg-stone-50 disabled:opacity-60"
            >
              {archivingItemId === item.id ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Archive className="h-4 w-4" />
              )}
              归档
            </button>
          </>
        )}
        {viewMode === 'ARCHIVED' && (
          <>
            <button
              type="button"
              onClick={() => {
                setOpenItemMenuId('');
                void handleRestoreItem(item);
              }}
              disabled={restoringItemId === item.id}
              className="flex h-11 w-full items-center gap-3 px-4 text-sm text-stone-700 hover:bg-stone-50 disabled:opacity-60"
            >
              {restoringItemId === item.id ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <RotateCcw className="h-4 w-4" />
              )}
              恢复
            </button>
            <button
              type="button"
              onClick={() => {
                setOpenItemMenuId('');
                void handleDeleteItem(item);
              }}
              disabled={deletingItemId === item.id}
              className="flex h-11 w-full items-center gap-3 px-4 text-sm text-red-600 hover:bg-red-50 disabled:opacity-60"
            >
              {deletingItemId === item.id ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Trash2 className="h-4 w-4" />
              )}
              删除
            </button>
          </>
        )}
        {viewMode === 'ACTIVE' && isLegacyAiSummary(item) && (
          <button
            type="button"
            onClick={() => {
              setOpenItemMenuId('');
              void handleDeleteItem(item);
            }}
            disabled={deletingItemId === item.id}
            className="flex h-11 w-full items-center gap-3 px-4 text-sm text-red-600 hover:bg-red-50 disabled:opacity-60"
          >
            {deletingItemId === item.id ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <Trash2 className="h-4 w-4" />
            )}
            删除
          </button>
        )}
      </div>
    );
  };

  if (loadingFamilies) {
    return (
      <div className="flex h-60 items-center justify-center text-gray-400">
        <Loader2 className="mr-2 h-5 w-5 animate-spin" />
        正在加载...
      </div>
    );
  }

  if (families.length === 0) {
    return (
      <div className="mx-auto max-w-3xl rounded-xl border border-gray-200 bg-white p-10 text-center">
        <BookHeart className="mx-auto mb-3 h-10 w-10 text-gray-300" />
        <h1 className="text-lg font-semibold text-gray-900">先创建一个家族空间</h1>
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
    <div className={simplified ? 'w-full' : 'mx-auto w-full max-w-[1500px]'}>
      {error && (
        <div className="mb-4 rounded-lg border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">
          {error}
        </div>
      )}
      {success && (
        <div className="mb-4 rounded-lg border border-sky-100 bg-sky-50 px-4 py-3 text-sm text-sky-700">
          {success}
        </div>
      )}

      <section>
        {isLoading ? (
          <div className="flex h-64 items-center justify-center text-stone-400">
            <Loader2 className="mr-2 h-5 w-5 animate-spin" />
            正在整理记忆库...
          </div>
        ) : pageData.items.length === 0 ? (
          <div className="flex h-64 items-center justify-center rounded-lg bg-white text-center shadow-sm ring-1 ring-stone-100">
            <div className="text-stone-400">
              <BookHeart className="mx-auto mb-3 h-10 w-10 opacity-40" />
              <p className="text-sm">
                {viewMode === 'ARCHIVED' ? '归档箱里暂无内容。' : '还没有记忆记录。'}
              </p>
            </div>
          </div>
        ) : (
          <div className={simplified ? 'space-y-4' : 'space-y-6'}>
            {pageData.items.map((item) => {
              const tags = (item.tags || []).slice(0, 4);
              const memberHeader = resolveMemberHeader(
                item,
                familyMembers,
                activeMembership?.userId,
              );
              return (
                <MemoryRecordCard
                  key={item.id}
                  header={
                    <MemoryRecordHeader
                      relationshipLabel={memberHeader.relationshipLabel}
                      memberName={memberHeader.memberName}
                      typeLabel={familyMemoryTypeLabel(item.type)}
                      createdAt={item.createdAt}
                    />
                  }
                  badges={tags.map((tag) => (
                    <span
                      key={`${item.id}-tag-${tag}`}
                      className="rounded-md bg-blue-50 px-2 py-1 text-sm font-medium text-blue-600"
                    >
                      #{tag}
                    </span>
                  ))}
                  title={item.title || '未命名记忆'}
                  body={item.body || '暂无正文内容。'}
                  actions={
                    canManageLibrary ? (
                      <>
                        <button
                          type="button"
                          onClick={(event) => {
                            event.stopPropagation();
                            setOpenItemMenuId((current) => (current === item.id ? '' : item.id));
                          }}
                          className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-stone-400 transition hover:bg-stone-100 hover:text-stone-700"
                          aria-label="打开操作菜单"
                          aria-expanded={openItemMenuId === item.id}
                        >
                          <MoreHorizontal className="h-5 w-5" />
                        </button>
                        {renderItemMenu(item)}
                      </>
                    ) : null
                  }
                />
              );
            })}
          </div>
        )}

        {pageData.total > pageData.pageSize && (
          <div className="mt-5 flex flex-col gap-3 rounded-lg bg-white px-4 py-3 text-xs text-stone-500 shadow-sm ring-1 ring-stone-100 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-center gap-2">
              <span>每页</span>
              <select
                value={pageSize}
                onChange={(event) => setPageSize(Number(event.target.value))}
                className="h-8 rounded-md border border-stone-200 bg-white px-2 text-xs text-stone-700 outline-none"
              >
                {pageSizeOptions.map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
              </select>
              <span>条</span>
            </div>
            <div className="flex items-center gap-2">
              <span>
                第 {pageData.page} / {totalPages} 页
              </span>
              <button
                type="button"
                onClick={() => setCurrentPage((page) => Math.max(1, page - 1))}
                disabled={(pageData.page || 1) <= 1}
                className="h-8 rounded-md border border-stone-200 bg-white px-3 text-xs font-medium text-stone-600 hover:bg-stone-50 disabled:opacity-50"
              >
                上一页
              </button>
              <button
                type="button"
                onClick={() => setCurrentPage((page) => Math.min(totalPages, page + 1))}
                disabled={(pageData.page || 1) >= totalPages}
                className="h-8 rounded-md border border-stone-200 bg-white px-3 text-xs font-medium text-stone-600 hover:bg-stone-50 disabled:opacity-50"
              >
                下一页
              </button>
            </div>
          </div>
        )}
      </section>
    </div>
  );
}
