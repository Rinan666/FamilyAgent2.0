'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { Loader2, RefreshCw, Sparkles, Users } from 'lucide-react';
import FamilyMembersPanel from '@/components/family/FamilyMembersPanel';
import SearchPaginationControls from '@/components/family/SearchPaginationControls';
import {
  WorkbenchEmptyState,
  WorkbenchHero,
  WorkbenchPage,
  WorkbenchSectionTitle,
  WorkbenchSurface,
} from '@/components/layout/Workbench';
import { diaryApi, familyApi, growthGuardApi, memoryApi } from '@/lib/api';
import { usePaginatedSearch } from '@/hooks/usePaginatedSearch';
import { useViewerRole } from '@/hooks/useViewerRole';
import type {
  DiaryEntry,
  FamilyMember,
  FamilyTab,
  GrowthGuardRecord,
  MemoryEntry,
} from '@/types';

const tabs: { value: FamilyTab; label: string }[] = [
  { value: 'members', label: '成员列表' },
  { value: 'stream', label: '记忆流' },
];

const legacyTabRedirects: Record<string, FamilyTab> = {
  overview: 'members',
  heritage: 'stream',
  library: 'stream',
  growth: 'stream',
};

type StreamFilter = 'all' | 'diary' | 'memory' | 'growth';

type StreamItem = {
  id: string;
  kind: Exclude<StreamFilter, 'all'>;
  title: string;
  summary: string;
  createdAt: string;
  href: string;
  sourceLabel: string;
  accentClass: string;
};

const STREAM_FETCH_LIMIT = 40;
const PAGE_SIZE = 8;

function parseFamilyTab(value: string | null): FamilyTab {
  if (tabs.some((tab) => tab.value === value)) return value as FamilyTab;
  if (value && legacyTabRedirects[value]) return legacyTabRedirects[value];
  return 'members';
}

function shortDate(value?: string) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' });
}

function streamItemHref(familyId: number, item: { kind: 'diary' | 'memory' | 'growth'; id: number | string }) {
  if (item.kind === 'diary') {
    return `/dashboard/diary?familyId=${familyId}`;
  }
  return `/dashboard/memory?familyId=${familyId}&itemId=${item.kind}-${item.id}`;
}

function diaryTitle(entry: DiaryEntry) {
  return entry.structured?.title?.trim()
    || entry.structured?.summary?.trim()
    || entry.rawText.trim().slice(0, 36)
    || '未命名记录';
}

function diarySummary(entry: DiaryEntry) {
  return entry.structured?.summary?.trim() || entry.rawText.trim() || '暂无摘要';
}

function memoryTitle(memory: MemoryEntry) {
  if (memory.summary?.trim()) return memory.summary.trim();
  if (typeof memory.metadata?.scenario === 'string' && memory.metadata.scenario.trim()) {
    return memory.metadata.scenario.trim();
  }
  return memory.content.trim().slice(0, 36) || '未命名经验';
}

function memorySummary(memory: MemoryEntry) {
  return memory.content.trim() || memory.summary?.trim() || '暂无内容';
}

function growthTitle(record: GrowthGuardRecord) {
  const category = String(record.category || '').toUpperCase();
  if (category === 'POSTURE') return '体态观察';
  if (category === 'DENTAL') return '牙齿观察';
  if (category === 'VISION') return '视力观察';
  if (category === 'SLEEP') return '睡眠观察';
  if (category === 'EXERCISE') return '运动观察';
  if (category === 'SCREEN_TIME') return '屏幕时间观察';
  if (category === 'EMOTION') return '情绪观察';
  if (category === 'COMMUNICATION') return '沟通观察';
  return '成长观察';
}

function growthSummary(record: GrowthGuardRecord) {
  return record.content.trim() || '暂无观察内容';
}

export default function FamilyPage() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const {
    families,
    activeFamilyId,
    setActiveFamilyId,
    viewerRole,
    isLoading: loadingFamilies,
  } = useViewerRole();

  const [members, setMembers] = useState<FamilyMember[]>([]);
  const [diaries, setDiaries] = useState<DiaryEntry[]>([]);
  const [memories, setMemories] = useState<MemoryEntry[]>([]);
  const [growthRecords, setGrowthRecords] = useState<GrowthGuardRecord[]>([]);
  const [loadingData, setLoadingData] = useState(false);
  const [error, setError] = useState('');

  const [streamFilter, setStreamFilter] = useState<StreamFilter>('all');
  const [streamQuery, setStreamQuery] = useState('');
  const [streamPage, setStreamPage] = useState(1);

  const requestedTab = searchParams.get('tab');
  const currentTab = useMemo(() => parseFamilyTab(requestedTab), [requestedTab]);

  const requestedFamilyId = useMemo(() => {
    const value = Number(searchParams.get('familyId'));
    return Number.isFinite(value) && value > 0 ? value : null;
  }, [searchParams]);

  const selectedFamilyId = useMemo(() => {
    const fromQuery = requestedFamilyId && families.some((family) => family.id === requestedFamilyId)
      ? requestedFamilyId
      : null;
    return fromQuery
      || (activeFamilyId && families.some((family) => family.id === activeFamilyId) ? activeFamilyId : null)
      || families[0]?.id
      || null;
  }, [activeFamilyId, families, requestedFamilyId]);

  const selectedFamily = useMemo(
    () => families.find((family) => family.id === selectedFamilyId) || null,
    [families, selectedFamilyId],
  );

  const updateUrl = useCallback((nextTab: FamilyTab, nextFamilyId?: number | null) => {
    const params = new URLSearchParams(searchParams.toString());
    params.set('tab', nextTab);
    if (nextFamilyId) {
      params.set('familyId', String(nextFamilyId));
    } else {
      params.delete('familyId');
    }
    router.push(`${pathname}?${params.toString()}`, { scroll: false });
  }, [pathname, router, searchParams]);

  useEffect(() => {
    if (!requestedTab) return;
    const normalizedTab = parseFamilyTab(requestedTab);
    if (requestedTab === normalizedTab) return;

    const params = new URLSearchParams(searchParams.toString());
    params.set('tab', normalizedTab);
    router.replace(`${pathname}?${params.toString()}`, { scroll: false });
  }, [pathname, requestedTab, router, searchParams]);

  useEffect(() => {
    if (selectedFamilyId && selectedFamilyId !== activeFamilyId) {
      setActiveFamilyId(selectedFamilyId);
    }
  }, [activeFamilyId, selectedFamilyId, setActiveFamilyId]);

  const loadFamilySpaceData = useCallback(async () => {
    if (!selectedFamilyId) {
      setMembers([]);
      setDiaries([]);
      setMemories([]);
      setGrowthRecords([]);
      return;
    }

    setLoadingData(true);
    setError('');
    try {
      const [memberList, diaryList, memoryList, growthList] = await Promise.all([
        familyApi.getMembers(selectedFamilyId).catch(() => [] as FamilyMember[]),
        diaryApi.listFamilyEntries(selectedFamilyId, STREAM_FETCH_LIMIT).catch(() => [] as DiaryEntry[]),
        memoryApi.listFamilyMemories(selectedFamilyId, STREAM_FETCH_LIMIT).catch(() => [] as MemoryEntry[]),
        growthGuardApi.listFamilyRecords(selectedFamilyId, STREAM_FETCH_LIMIT).catch(() => [] as GrowthGuardRecord[]),
      ]);

      setMembers(Array.isArray(memberList) ? memberList : []);
      setDiaries(Array.isArray(diaryList) ? diaryList : []);
      setMemories(Array.isArray(memoryList) ? memoryList : []);
      setGrowthRecords(Array.isArray(growthList) ? growthList : []);
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载家族空间失败');
    } finally {
      setLoadingData(false);
    }
  }, [selectedFamilyId]);

  useEffect(() => {
    if (currentTab !== 'stream') {
      setError('');
      if (!selectedFamilyId) setMembers([]);
      return;
    }
    void loadFamilySpaceData();
  }, [currentTab, loadFamilySpaceData, selectedFamilyId]);

  const streamItems = useMemo(() => {
    if (!selectedFamilyId) return [] as StreamItem[];

    const nextItems: StreamItem[] = [
      ...diaries.map((entry) => ({
        id: `diary-${entry.id}`,
        kind: 'diary' as const,
        title: diaryTitle(entry),
        summary: diarySummary(entry),
        createdAt: entry.createdAt,
        href: streamItemHref(selectedFamilyId, { kind: 'diary', id: entry.id }),
        sourceLabel: '记录',
        accentClass: 'bg-rose-50 text-rose-700',
      })),
      ...memories.map((memory) => ({
        id: `memory-${memory.id}`,
        kind: 'memory' as const,
        title: memoryTitle(memory),
        summary: memorySummary(memory),
        createdAt: memory.createdAt,
        href: streamItemHref(selectedFamilyId, { kind: 'memory', id: memory.id }),
        sourceLabel: '经验',
        accentClass: 'bg-amber-50 text-amber-700',
      })),
      ...growthRecords.map((record) => ({
        id: `growth-${record.id}`,
        kind: 'growth' as const,
        title: growthTitle(record),
        summary: growthSummary(record),
        createdAt: record.createdAt,
        href: streamItemHref(selectedFamilyId, { kind: 'growth', id: record.id }),
        sourceLabel: '守护',
        accentClass: 'bg-emerald-50 text-emerald-700',
      })),
    ];

    return nextItems.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
  }, [diaries, growthRecords, memories, selectedFamilyId]);

  const visibleStreamItems = useMemo(() => {
    if (streamFilter === 'all') return streamItems;
    return streamItems.filter((item) => item.kind === streamFilter);
  }, [streamFilter, streamItems]);

  useEffect(() => {
    setStreamPage(1);
  }, [selectedFamilyId, streamFilter, streamQuery]);

  const {
    items: pagedStreamItems,
    total: filteredStreamTotal,
    pageCount: streamPageCount,
    currentPage: currentStreamPage,
    startIndex: streamStartIndex,
    endIndex: streamEndIndex,
  } = usePaginatedSearch({
    items: visibleStreamItems,
    query: streamQuery,
    page: streamPage,
    pageSize: PAGE_SIZE,
    getSearchText: (item) => [item.title, item.summary, item.sourceLabel, item.kind].join(' '),
  });

  if (loadingFamilies) {
    return (
      <div className="flex h-60 items-center justify-center text-gray-400">
        <Loader2 className="mr-2 h-5 w-5 animate-spin" />
        正在加载家族空间...
      </div>
    );
  }

  return (
    <WorkbenchPage>
      <WorkbenchHero
        badge={(
          <span className="inline-flex items-center gap-2 rounded-full bg-emerald-50 px-3 py-1 text-xs font-medium text-emerald-700">
            <Sparkles className="h-3.5 w-3.5" />
            家族空间
          </span>
        )}
        title="成员与家族记忆"
        description="这里只保留成员列表、成员记忆视图入口和家族记忆流，其他工作页内容已移出。"
        aside={(
          <div>
            <p className="text-xs font-medium uppercase tracking-[0.2em] text-stone-400">当前家族</p>
            <div className="mt-3 space-y-2">
              {selectedFamily ? (
                <>
                  <p className="text-base font-semibold text-stone-950">{selectedFamily.name}</p>
                  <p className="text-sm leading-6 text-stone-500">
                    从成员列表进入成员记忆视图，从记忆流查看当前家族近期记录、经验与成长观察。
                  </p>
                </>
              ) : (
                <p className="text-sm text-stone-500">先创建或加入一个家族空间。</p>
              )}
            </div>
          </div>
        )}
      />

      <WorkbenchSurface className="grid grid-cols-2 gap-2">
        {tabs.map((tab) => (
          <button
            key={tab.value}
            type="button"
            onClick={() => updateUrl(tab.value, selectedFamilyId)}
            className={`rounded-2xl px-4 py-3 text-left text-sm font-medium transition ${
              currentTab === tab.value
                ? 'bg-stone-950 text-white shadow-[0_16px_36px_rgba(24,39,32,0.14)]'
                : 'bg-stone-50 text-stone-600 hover:bg-stone-100 hover:text-stone-900'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </WorkbenchSurface>

      {error && (
        <div className="rounded-[24px] border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">
          {error}
        </div>
      )}

      {families.length === 0 ? (
        <WorkbenchEmptyState
          icon={<Users className="h-6 w-6" />}
          title="还没有家族空间"
          description="创建或加入家族后，就可以在这里查看成员列表、进入成员记忆视图，以及浏览家族记忆流。"
          action={(
            <div className="w-full max-w-4xl">
              <FamilyMembersPanel viewerRole={viewerRole} families={families} focusedFamilyId={selectedFamilyId} />
            </div>
          )}
        />
      ) : (
        <>
          {currentTab === 'members' && (
            <WorkbenchSurface className="space-y-4">
              <WorkbenchSectionTitle
                title="成员列表"
                description="在成员列表中查看当前家族成员，并通过“成员记忆”按钮进入对应成员的记忆视图。"
              />
              <FamilyMembersPanel
                viewerRole={viewerRole}
                families={families}
                focusedFamilyId={selectedFamilyId}
              />
            </WorkbenchSurface>
          )}

          {currentTab === 'stream' && (
            <WorkbenchSurface>
              <div className="mb-4 flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                <WorkbenchSectionTitle
                  title="家族记忆流"
                  description="汇总当前家族最近的记录、经验和成长观察。"
                />
                <div className="flex flex-wrap gap-2">
                  {([
                    { value: 'all', label: '全部' },
                    { value: 'diary', label: '记录' },
                    { value: 'memory', label: '经验' },
                    { value: 'growth', label: '守护' },
                  ] as const).map((filter) => (
                    <button
                      key={filter.value}
                      type="button"
                      onClick={() => setStreamFilter(filter.value)}
                      className={`rounded-full px-3 py-1.5 text-xs font-medium transition ${
                        streamFilter === filter.value
                          ? 'bg-stone-950 text-white'
                          : 'bg-stone-100 text-stone-600 hover:bg-stone-200'
                      }`}
                    >
                      {filter.label}
                    </button>
                  ))}
                  <Link
                    href={`/dashboard/diary${selectedFamilyId ? `?familyId=${selectedFamilyId}` : ''}`}
                    className="inline-flex h-8 items-center rounded-full bg-stone-950 px-3 text-xs font-medium text-white hover:bg-stone-800"
                  >
                    去写记录
                  </Link>
                </div>
              </div>

              <SearchPaginationControls
                className="mb-4"
                searchValue={streamQuery}
                onSearchChange={setStreamQuery}
                searchPlaceholder="搜索标题、摘要或来源"
                itemLabel="项"
                currentPage={currentStreamPage}
                pageCount={streamPageCount}
                onPageChange={setStreamPage}
                startIndex={streamStartIndex}
                endIndex={streamEndIndex}
                filteredTotal={filteredStreamTotal}
                total={visibleStreamItems.length}
              />

              {loadingData ? (
                <div className="flex h-48 items-center justify-center text-stone-400">
                  <Loader2 className="mr-2 h-5 w-5 animate-spin" />
                  正在整理记忆流...
                </div>
              ) : filteredStreamTotal === 0 ? (
                <div className="rounded-2xl border border-dashed border-stone-200 px-4 py-10 text-center text-sm text-stone-400">
                  当前筛选下还没有内容。
                </div>
              ) : (
                <div className="space-y-3">
                  {pagedStreamItems.map((item) => (
                    <article key={item.id} className="rounded-2xl border border-stone-200 bg-stone-50 p-4">
                      <div className="mb-2 flex flex-wrap items-center gap-2">
                        <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${item.accentClass}`}>
                          {item.sourceLabel}
                        </span>
                        <span className="text-[11px] text-stone-400">{shortDate(item.createdAt)}</span>
                      </div>
                      <h3 className="text-sm font-semibold text-stone-900">{item.title}</h3>
                      <p className="mt-1 whitespace-pre-wrap text-sm leading-6 text-stone-600">{item.summary}</p>
                      <div className="mt-3">
                        <Link
                          href={item.href}
                          className="inline-flex h-8 items-center rounded-full border border-stone-200 bg-white px-3 text-xs font-medium text-stone-600 hover:bg-stone-100"
                        >
                          查看来源
                        </Link>
                      </div>
                    </article>
                  ))}
                </div>
              )}
            </WorkbenchSurface>
          )}
        </>
      )}

      {selectedFamilyId && currentTab === 'stream' && (
        <div className="mt-4 flex justify-end">
          <button
            type="button"
            onClick={() => { void loadFamilySpaceData(); }}
            disabled={loadingData}
            className="inline-flex h-9 items-center gap-2 rounded-full border border-stone-200 bg-white px-3 text-sm font-medium text-stone-600 hover:bg-stone-50 disabled:opacity-60"
          >
            <RefreshCw className={`h-4 w-4 ${loadingData ? 'animate-spin' : ''}`} />
            刷新记忆流
          </button>
        </div>
      )}
    </WorkbenchPage>
  );
}
