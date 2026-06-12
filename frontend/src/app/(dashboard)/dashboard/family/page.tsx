'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import {
  BookHeart,
  Bot,
  ChevronRight,
  HeartPulse,
  Loader2,
  RefreshCw,
  ScrollText,
  Shield,
  Sparkles,
  Users,
} from 'lucide-react';
import FamilyMembersPanel from '@/components/family/FamilyMembersPanel';
import SearchPaginationControls from '@/components/family/SearchPaginationControls';
import { diaryApi, familyApi, growthGuardApi, heritageTaskApi, memoryApi } from '@/lib/api';
import { usePaginatedSearch } from '@/hooks/usePaginatedSearch';
import { useViewerRole } from '@/hooks/useViewerRole';
import type {
  DiaryEntry,
  FamilyMember,
  FamilyTab,
  GrowthGuardRecord,
  HeritageTask,
  MemoryEntry,
} from '@/types';
import HeritagePage from '../heritage/page';
import MemoryLibraryPage from '../memory/page';

const tabs: { value: FamilyTab; label: string; description: string }[] = [
  { value: 'overview', label: '总览', description: '家族概况、快捷入口与近期动态' },
  { value: 'stream', label: '记忆流', description: '查看最近的记录、经验与成长观察' },
  { value: 'heritage', label: '经验沉淀', description: '整理家族经验与后续任务' },
  { value: 'library', label: '记忆库', description: '统一搜索、筛选与维护家族资产' },
  { value: 'members', label: '成员', description: '查看成员、称呼关系与授权信息' },
];

type StreamFilter = 'all' | 'diary' | 'memory' | 'growth';

type StreamItem = {
  id: string;
  kind: StreamFilter;
  title: string;
  summary: string;
  createdAt: string;
  href: string;
  sourceLabel: string;
  accentClass: string;
  actionHref?: string;
  actionLabel?: string;
};

const FETCH_LIMIT = 100;
const PAGE_SIZE = 8;

function parseFamilyTab(value: string | null): FamilyTab {
  return tabs.some((tab) => tab.value === value) ? (value as FamilyTab) : 'overview';
}

function shortDate(value?: string) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' });
}

function familyLibraryItemHref(familyId: number, itemId: string) {
  const params = new URLSearchParams();
  params.set('tab', 'library');
  params.set('familyId', String(familyId));
  params.set('itemId', itemId);
  return `/dashboard/family?${params.toString()}`;
}

function memberName(member?: FamilyMember | null) {
  if (!member) return '家族成员';
  return member.relationshipLabel?.trim() || member.nickname?.trim() || member.username?.trim() || `用户 ${member.userId}`;
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

function taskStatusLabel(task: HeritageTask) {
  return task.status === 'DONE' ? '已完成' : '待实践';
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
  const [tasks, setTasks] = useState<HeritageTask[]>([]);
  const [loadingData, setLoadingData] = useState(false);
  const [error, setError] = useState('');
  const [pendingFamilyId, setPendingFamilyId] = useState<number | null>(null);

  const [streamFilter, setStreamFilter] = useState<StreamFilter>('all');
  const [streamQuery, setStreamQuery] = useState('');
  const [streamPage, setStreamPage] = useState(1);

  const currentTab = useMemo(() => parseFamilyTab(searchParams.get('tab')), [searchParams]);

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

  const displayFamilyId = pendingFamilyId ?? selectedFamilyId;

  const selectedFamily = useMemo(
    () => families.find((family) => family.id === displayFamilyId) || null,
    [displayFamilyId, families],
  );

  const updateUrl = useCallback((nextTab: FamilyTab, nextFamilyId?: number | null) => {
    const params = new URLSearchParams(searchParams.toString());
    params.set('tab', nextTab);
    if (nextFamilyId) {
      params.set('familyId', String(nextFamilyId));
    } else {
      params.delete('familyId');
    }
    const nextUrl = `${pathname}?${params.toString()}`;
    router.push(nextUrl, { scroll: false });
  }, [pathname, router, searchParams]);

  useEffect(() => {
    if (displayFamilyId && displayFamilyId !== activeFamilyId) {
      setActiveFamilyId(displayFamilyId);
    }
  }, [activeFamilyId, displayFamilyId, setActiveFamilyId]);

  useEffect(() => {
    if (pendingFamilyId && pendingFamilyId === selectedFamilyId) {
      setPendingFamilyId(null);
    }
  }, [pendingFamilyId, selectedFamilyId]);

  const loadFamilySpaceData = useCallback(async () => {
    if (!displayFamilyId) {
      setMembers([]);
      setDiaries([]);
      setMemories([]);
      setGrowthRecords([]);
      setTasks([]);
      return;
    }

    setLoadingData(true);
    setError('');
    try {
      const [memberList, diaryList, memoryList, growthList, taskList] = await Promise.all([
        familyApi.getMembers(displayFamilyId).catch(() => [] as FamilyMember[]),
        diaryApi.listFamilyEntries(displayFamilyId, FETCH_LIMIT).catch(() => [] as DiaryEntry[]),
        memoryApi.listFamilyMemories(displayFamilyId, FETCH_LIMIT).catch(() => [] as MemoryEntry[]),
        growthGuardApi.listFamilyRecords(displayFamilyId, FETCH_LIMIT).catch(() => [] as GrowthGuardRecord[]),
        heritageTaskApi.listFamilyTasks(displayFamilyId, 8).catch(() => [] as HeritageTask[]),
      ]);

      setMembers(Array.isArray(memberList) ? memberList : []);
      setDiaries(Array.isArray(diaryList) ? diaryList : []);
      setMemories(Array.isArray(memoryList) ? memoryList : []);
      setGrowthRecords(Array.isArray(growthList) ? growthList : []);
      setTasks(Array.isArray(taskList) ? taskList : []);
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载家族空间失败');
    } finally {
      setLoadingData(false);
    }
  }, [displayFamilyId]);

  useEffect(() => {
    void loadFamilySpaceData();
  }, [loadFamilySpaceData]);

  const streamItems = useMemo(() => {
    const familyId = displayFamilyId;
    if (!familyId) return [] as StreamItem[];

    const nextItems: StreamItem[] = [
      ...diaries.map((entry) => ({
        id: `diary-${entry.id}`,
        kind: 'diary' as const,
        title: diaryTitle(entry),
        summary: diarySummary(entry),
        createdAt: entry.createdAt,
        href: familyLibraryItemHref(familyId, `diary-${entry.id}`),
        sourceLabel: '记录',
        accentClass: 'bg-rose-50 text-rose-700',
        actionHref: `/dashboard/heritage?familyId=${familyId}&type=ELDER_ADVICE`,
        actionLabel: '整理为经验',
      })),
      ...memories.map((memory) => ({
        id: `memory-${memory.id}`,
        kind: 'memory' as const,
        title: memoryTitle(memory),
        summary: memorySummary(memory),
        createdAt: memory.createdAt,
        href: familyLibraryItemHref(familyId, `memory-${memory.id}`),
        sourceLabel: '经验',
        accentClass: 'bg-amber-50 text-amber-700',
        actionHref: `/dashboard/memory?familyId=${familyId}`,
        actionLabel: '前往记忆库',
      })),
      ...growthRecords.map((record) => ({
        id: `growth-${record.id}`,
        kind: 'growth' as const,
        title: growthTitle(record),
        summary: growthSummary(record),
        createdAt: record.createdAt,
        href: familyLibraryItemHref(familyId, `growth-${record.id}`),
        sourceLabel: '守护',
        accentClass: 'bg-emerald-50 text-emerald-700',
        actionHref: `/dashboard/heritage?familyId=${familyId}&type=GROWTH_RISK`,
        actionLabel: '沉淀为提醒',
      })),
    ];

    return nextItems.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
  }, [diaries, displayFamilyId, growthRecords, memories]);

  const visibleStreamItems = useMemo(() => {
    if (streamFilter === 'all') return streamItems;
    return streamItems.filter((item) => item.kind === streamFilter);
  }, [streamFilter, streamItems]);

  useEffect(() => {
    setStreamPage(1);
  }, [displayFamilyId, streamFilter, streamQuery]);

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

  const quickLinks = useMemo(() => {
    if (!displayFamilyId) return [];
    return [
      {
        href: `/dashboard/diary?familyId=${displayFamilyId}`,
        label: '写记录',
        description: '继续记录当下，把日常沉淀成长期记忆。',
        icon: BookHeart,
      },
      {
        href: `/dashboard/agent?familyId=${displayFamilyId}`,
        label: '镜像 Agent',
        description: '基于授权资料查看成员镜像参考。',
        icon: Bot,
      },
      {
        href: `/dashboard/diary?familyId=${displayFamilyId}&tab=growth`,
        label: '成长守护',
        description: '查看成长观察与后续跟进。',
        icon: Shield,
      },
      {
        href: members[0]
          ? `/dashboard/family/member?familyId=${displayFamilyId}&userId=${members[0].userId}`
          : `/dashboard/family?tab=members&familyId=${displayFamilyId}`,
        label: '成员记忆',
        description: '进入成员视角查看授权可见资料。',
        icon: Users,
      },
    ];
  }, [displayFamilyId, members]);

  if (loadingFamilies) {
    return (
      <div className="flex h-60 items-center justify-center text-gray-400">
        <Loader2 className="mr-2 h-5 w-5 animate-spin" />
        正在加载家族空间...
      </div>
    );
  }

  return (
    <div className="mx-auto w-full max-w-7xl">
      <section className="mb-4 rounded-2xl border border-gray-200 bg-white p-5 sm:p-6">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <div className="mb-2 inline-flex items-center gap-2 rounded-full bg-blue-50 px-3 py-1 text-xs font-medium text-blue-700">
              <Sparkles className="h-3.5 w-3.5" />
              家族空间
            </div>
            <h1 className="text-2xl font-bold text-gray-900">把家族记忆、成员关系和长期沉淀放在一个入口里</h1>
            <p className="mt-2 max-w-3xl text-sm leading-6 text-gray-500">
              这里统一承载家族总览、记忆流、经验沉淀、记忆库与成员视图；
              高频写记录入口仍保留在日记页。
            </p>
          </div>

          <div className="w-full max-w-md">
            <p className="text-xs font-medium text-gray-500">当前家族</p>
            <div className="mt-2 flex flex-wrap gap-2">
              {families.length === 0 && (
                <span className="rounded-full bg-gray-100 px-3 py-1.5 text-xs text-gray-500">
                  暂无家族
                </span>
              )}
              {families.map((family) => (
                <button
                  key={family.id}
                  type="button"
                  onClick={() => {
                    setPendingFamilyId(family.id);
                    updateUrl(currentTab, family.id);
                  }}
                  className={`rounded-full border px-3 py-1.5 text-sm transition-colors ${
                    displayFamilyId === family.id
                      ? 'border-blue-200 bg-blue-50 text-blue-700'
                      : 'border-gray-200 bg-white text-gray-600 hover:border-blue-200 hover:bg-blue-50 hover:text-blue-700'
                  }`}
                >
                  {family.name}
                </button>
              ))}
            </div>
          </div>
        </div>

        <div className="mt-5 grid grid-cols-1 gap-2 lg:grid-cols-5">
          {tabs.map((tab) => (
            <button
              key={tab.value}
              type="button"
              onClick={() => updateUrl(tab.value, displayFamilyId)}
              className={`rounded-xl border p-3 text-left transition-colors ${
                currentTab === tab.value
                  ? 'border-blue-200 bg-blue-50'
                  : 'border-gray-100 bg-gray-50 hover:bg-gray-100'
              }`}
            >
              <p className="text-sm font-semibold text-gray-900">{tab.label}</p>
              <p className="mt-1 text-xs leading-5 text-gray-500">{tab.description}</p>
            </button>
          ))}
        </div>
      </section>

      {error && (
        <div className="mb-4 rounded-lg border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">
          {error}
        </div>
      )}

      {families.length === 0 ? (
        <section className="rounded-2xl border border-gray-200 bg-white p-4 sm:p-5">
          <FamilyMembersPanel
            viewerRole={viewerRole}
            families={families}
            focusedFamilyId={displayFamilyId}
          />
        </section>
      ) : (
        <>
          {currentTab === 'overview' && (
            <div className="space-y-4">
              <section className="grid grid-cols-2 gap-3 lg:grid-cols-4">
                {[
                  { label: '成员数', value: members.length, icon: Users, tone: 'bg-blue-50 text-blue-700' },
                  { label: '最近记录', value: diaries.length, icon: BookHeart, tone: 'bg-rose-50 text-rose-700' },
                  { label: '经验沉淀', value: memories.length, icon: ScrollText, tone: 'bg-amber-50 text-amber-700' },
                  { label: '成长观察', value: growthRecords.length, icon: HeartPulse, tone: 'bg-emerald-50 text-emerald-700' },
                ].map((item) => {
                  const Icon = item.icon;
                  return (
                    <article key={item.label} className="rounded-2xl border border-gray-200 bg-white p-4">
                      <span className={`inline-flex h-10 w-10 items-center justify-center rounded-xl ${item.tone}`}>
                        <Icon className="h-5 w-5" />
                      </span>
                      <p className="mt-3 text-2xl font-bold text-gray-900">{item.value}</p>
                      <p className="text-sm text-gray-500">{item.label}</p>
                    </article>
                  );
                })}
              </section>

              <section className="rounded-2xl border border-gray-200 bg-white p-4 sm:p-5">
                <div className="mb-4">
                  <h2 className="text-lg font-semibold text-gray-900">快捷入口</h2>
                  <p className="mt-1 text-sm text-gray-500">围绕当前家族继续记录、查看镜像、成长守护和成员记忆。</p>
                </div>

                <div className="grid grid-cols-1 gap-3 lg:grid-cols-4">
                  {quickLinks.map((link) => {
                    const Icon = link.icon;
                    return (
                      <Link
                        key={link.label}
                        href={link.href}
                        className="group rounded-xl border border-gray-100 bg-gray-50 p-4 transition-colors hover:border-blue-200 hover:bg-blue-50"
                      >
                        <Icon className="h-5 w-5 text-blue-600" />
                        <p className="mt-3 text-sm font-semibold text-gray-900">{link.label}</p>
                        <p className="mt-1 text-xs leading-5 text-gray-500">{link.description}</p>
                        <span className="mt-3 inline-flex items-center gap-1 text-xs font-medium text-blue-700">
                          进入
                          <ChevronRight className="h-3.5 w-3.5" />
                        </span>
                      </Link>
                    );
                  })}
                </div>
              </section>

              <section className="grid grid-cols-1 gap-4 xl:grid-cols-3">
                <article className="rounded-2xl border border-gray-200 bg-white p-4 sm:p-5">
                  <div className="mb-3 flex items-center justify-between">
                    <div>
                      <h2 className="text-lg font-semibold text-gray-900">最近记忆流</h2>
                      <p className="text-sm text-gray-500">记录与观察的最新动态。</p>
                    </div>
                    <button
                      type="button"
                      onClick={() => updateUrl('stream', displayFamilyId)}
                      className="text-xs font-medium text-blue-600 hover:underline"
                    >
                      查看全部
                    </button>
                  </div>
                  <div className="space-y-3">
                    {streamItems.slice(0, 3).map((item) => (
                      <div key={item.id} className="rounded-xl border border-gray-100 bg-gray-50 p-3">
                        <div className="mb-2 flex items-center gap-2">
                          <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${item.accentClass}`}>
                            {item.sourceLabel}
                          </span>
                          <span className="text-[11px] text-gray-400">{shortDate(item.createdAt)}</span>
                        </div>
                        <p className="text-sm font-medium text-gray-900">{item.title}</p>
                        <p className="mt-1 line-clamp-2 text-xs leading-5 text-gray-500">{item.summary}</p>
                      </div>
                    ))}
                    {streamItems.length === 0 && (
                      <p className="rounded-xl border border-dashed border-gray-200 px-3 py-6 text-center text-sm text-gray-400">
                        还没有最近动态，先写一条记录会更容易开始。
                      </p>
                    )}
                  </div>
                </article>

                <article className="rounded-2xl border border-gray-200 bg-white p-4 sm:p-5">
                  <div className="mb-3 flex items-center justify-between">
                    <div>
                      <h2 className="text-lg font-semibold text-gray-900">最近经验卡</h2>
                      <p className="text-sm text-gray-500">沉淀下来的经验与提醒。</p>
                    </div>
                    <button
                      type="button"
                      onClick={() => updateUrl('heritage', displayFamilyId)}
                      className="text-xs font-medium text-blue-600 hover:underline"
                    >
                      查看全部
                    </button>
                  </div>
                  <div className="space-y-3">
                    {memories.slice(0, 3).map((memory) => (
                      <div key={memory.id} className="rounded-xl border border-gray-100 bg-gray-50 p-3">
                        <p className="text-sm font-medium text-gray-900">{memoryTitle(memory)}</p>
                        <p className="mt-1 line-clamp-2 text-xs leading-5 text-gray-500">{memorySummary(memory)}</p>
                        <p className="mt-2 text-[11px] text-gray-400">{shortDate(memory.createdAt)}</p>
                      </div>
                    ))}
                    {memories.length === 0 && (
                      <p className="rounded-xl border border-dashed border-gray-200 px-3 py-6 text-center text-sm text-gray-400">
                        还没有沉淀经验，可以从一条日记或一次观察开始整理。
                      </p>
                    )}
                  </div>
                </article>

                <article className="rounded-2xl border border-gray-200 bg-white p-4 sm:p-5">
                  <div className="mb-3 flex items-center justify-between">
                    <div>
                      <h2 className="text-lg font-semibold text-gray-900">成员入口</h2>
                      <p className="text-sm text-gray-500">进入某位成员的授权可见记忆视图。</p>
                    </div>
                    <button
                      type="button"
                      onClick={() => updateUrl('members', displayFamilyId)}
                      className="text-xs font-medium text-blue-600 hover:underline"
                    >
                      管理成员
                    </button>
                  </div>
                  <div className="space-y-3">
                    {members.slice(0, 3).map((member) => (
                      <Link
                        key={member.id}
                        href={`/dashboard/family/member?familyId=${displayFamilyId}&userId=${member.userId}`}
                        className="flex items-center justify-between rounded-xl border border-gray-100 bg-gray-50 p-3 transition-colors hover:border-blue-200 hover:bg-blue-50"
                      >
                        <div>
                          <p className="text-sm font-medium text-gray-900">{memberName(member)}</p>
                          <p className="mt-1 text-xs text-gray-500">{member.username || member.nickname || '家族成员'}</p>
                        </div>
                        <ChevronRight className="h-4 w-4 text-gray-400" />
                      </Link>
                    ))}
                    {members.length === 0 && (
                      <p className="rounded-xl border border-dashed border-gray-200 px-3 py-6 text-center text-sm text-gray-400">
                        当前家族还没有可展示的成员信息。
                      </p>
                    )}
                  </div>
                </article>
              </section>
            </div>
          )}

          {currentTab === 'stream' && (
            <section className="rounded-2xl border border-gray-200 bg-white p-4 sm:p-5">
              <div className="mb-4 flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                <div>
                  <h2 className="text-xl font-semibold text-gray-900">家族记忆流</h2>
                  <p className="mt-1 text-sm text-gray-500">
                    集中浏览最近的记录、经验与成长观察，并进入统一详情查看来源依据与后续动作。
                  </p>
                </div>
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
                      className={`rounded-full px-3 py-1.5 text-xs font-medium transition-colors ${
                        streamFilter === filter.value
                          ? 'bg-blue-600 text-white'
                          : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                      }`}
                    >
                      {filter.label}
                    </button>
                  ))}
                  <Link
                    href={`/dashboard/diary${displayFamilyId ? `?familyId=${displayFamilyId}` : ''}`}
                    className="inline-flex h-8 items-center rounded-full bg-gray-900 px-3 text-xs font-medium text-white hover:bg-gray-800"
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
                <div className="flex h-48 items-center justify-center text-gray-400">
                  <Loader2 className="mr-2 h-5 w-5 animate-spin" />
                  正在整理记忆流...
                </div>
              ) : filteredStreamTotal === 0 ? (
                <div className="rounded-xl border border-dashed border-gray-200 px-4 py-10 text-center text-sm text-gray-400">
                  当前筛选下还没有内容，先写一条记录或切换分类看看。
                </div>
              ) : (
                <div className="space-y-3">
                  {pagedStreamItems.map((item) => (
                    <article key={item.id} className="rounded-xl border border-gray-100 bg-gray-50 p-4">
                      <div className="mb-2 flex flex-wrap items-center gap-2">
                        <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${item.accentClass}`}>
                          {item.sourceLabel}
                        </span>
                        <span className="text-[11px] text-gray-400">{shortDate(item.createdAt)}</span>
                      </div>
                      <h3 className="text-sm font-semibold text-gray-900">{item.title}</h3>
                      <p className="mt-1 whitespace-pre-wrap text-sm leading-6 text-gray-600">{item.summary}</p>
                      <div className="mt-3 flex flex-wrap gap-2">
                        <Link
                          href={item.href}
                          className="inline-flex h-8 items-center rounded-lg border border-gray-200 bg-white px-3 text-xs font-medium text-gray-600 hover:bg-gray-100"
                        >
                          查看详情
                        </Link>
                        {item.actionHref && item.actionLabel && (
                          <Link
                            href={item.actionHref}
                            className="inline-flex h-8 items-center rounded-lg bg-blue-600 px-3 text-xs font-medium text-white hover:bg-blue-700"
                          >
                            {item.actionLabel}
                          </Link>
                        )}
                      </div>
                    </article>
                  ))}
                </div>
              )}
            </section>
          )}

          {currentTab === 'heritage' && <HeritagePage embedded />}

          {currentTab === 'library' && <MemoryLibraryPage embedded />}

          {currentTab === 'members' && (
            <section className="rounded-2xl border border-gray-200 bg-white p-4 sm:p-5">
              <FamilyMembersPanel
                viewerRole={viewerRole}
                families={families}
                focusedFamilyId={displayFamilyId}
              />
            </section>
          )}
        </>
      )}

      {displayFamilyId && currentTab !== 'members' && (
        <div className="mt-4 flex justify-end">
          <button
            type="button"
            onClick={() => { void loadFamilySpaceData(); }}
            disabled={loadingData}
            className="inline-flex h-9 items-center gap-2 rounded-lg border border-gray-200 bg-white px-3 text-sm font-medium text-gray-600 hover:bg-gray-50 disabled:opacity-60"
          >
            <RefreshCw className={`h-4 w-4 ${loadingData ? 'animate-spin' : ''}`} />
            刷新当前空间
          </button>
        </div>
      )}

      {selectedFamily && currentTab === 'members' && (
        <p className="mt-4 text-xs text-gray-400">
          当前成员页已固定在「{selectedFamily.name}」，家族切换统一通过顶部家族选择器完成。
        </p>
      )}
    </div>
  );
}
