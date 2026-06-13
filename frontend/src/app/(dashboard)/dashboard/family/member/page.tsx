'use client';

import { type ReactNode, useDeferredValue, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import {
  ArrowLeft,
  BookHeart,
  HeartPulse,
  Loader2,
  RefreshCw,
  ScrollText,
  Shield,
  Sparkles,
  UserRound,
  Users,
} from 'lucide-react';
import SearchPaginationControls from '@/components/family/SearchPaginationControls';
import { diaryApi, familyApi, growthGuardApi, memoryApi, mirrorApi } from '@/lib/api';
import { useViewerRole } from '@/hooks/useViewerRole';
import { familyRoleLabel } from '@/lib/roles';
import type {
  DiaryEntry,
  FamilyMember,
  GrowthGuardRecord,
  MemoryEntry,
  MirrorContextResponse,
  PageResult,
} from '@/types';

const PAGE_SIZE = 6;

function createEmptyPage<T>(): PageResult<T> {
  return {
    items: [],
    page: 1,
    pageSize: PAGE_SIZE,
    total: 0,
    totalPages: 1,
  };
}

function memberDisplayName(member?: FamilyMember | null) {
  if (!member) return '家族成员';
  return member.relationshipLabel?.trim() || member.nickname?.trim() || member.username?.trim() || `用户 ${member.userId}`;
}

function memberAccountName(member?: FamilyMember | null) {
  if (!member) return '';
  return member.nickname?.trim() || member.username?.trim() || `用户 ${member.userId}`;
}

function memberBirthDate(member?: FamilyMember | null) {
  if (!member) return '';
  const value = member.birthDate
    || (typeof member.metadata?.birthDate === 'string' ? member.metadata.birthDate : '')
    || (typeof member.metadata?.birthday === 'string' ? member.metadata.birthday : '')
    || (typeof member.metadata?.dateOfBirth === 'string' ? member.metadata.dateOfBirth : '');
  return value ? value.slice(0, 10) : '';
}

function memberAge(member?: FamilyMember | null) {
  const birthDate = memberBirthDate(member);
  if (birthDate) {
    const date = new Date(birthDate);
    if (!Number.isNaN(date.getTime())) {
      const now = new Date();
      let age = now.getFullYear() - date.getFullYear();
      const monthDelta = now.getMonth() - date.getMonth();
      if (monthDelta < 0 || (monthDelta === 0 && now.getDate() < date.getDate())) age -= 1;
      if (age >= 0 && age <= 130) return age;
    }
  }

  const year = Number(member?.birthYear || member?.metadata?.birthYear || member?.metadata?.yearOfBirth);
  if (Number.isFinite(year) && year > 1870 && year <= new Date().getFullYear()) {
    return new Date().getFullYear() - year;
  }

  return null;
}

function memberProfileLine(member?: FamilyMember | null) {
  const birthDate = memberBirthDate(member);
  const age = memberAge(member);
  const birthText = birthDate ? `生日：${birthDate}` : '生日：未设置';
  const ageText = age == null ? '年龄：未设置' : `年龄：${age} 岁`;
  return `${birthText} · ${ageText}`;
}

function diaryTitle(entry: DiaryEntry) {
  return entry.structured?.title?.trim()
    || entry.structured?.summary?.trim()
    || entry.rawText.trim().slice(0, 34)
    || '未命名记录';
}

function memoryTitle(memory: MemoryEntry) {
  if (memory.summary?.trim()) return memory.summary.trim();
  if (typeof memory.metadata?.scenario === 'string' && memory.metadata.scenario.trim()) {
    return `${memory.metadata.scenario.trim()}相关经验`;
  }
  return memory.content.trim().slice(0, 34) || '未命名经验';
}

function growthCategoryLabel(category?: string) {
  switch ((category || '').toUpperCase()) {
    case 'POSTURE': return '体态';
    case 'DENTAL': return '牙齿';
    case 'VISION': return '视力';
    case 'SLEEP': return '睡眠';
    case 'EXERCISE': return '运动';
    case 'SCREEN_TIME': return '屏幕';
    case 'EMOTION': return '情绪';
    case 'COMMUNICATION': return '沟通';
    default: return '其他';
  }
}

function shortDate(value?: string) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleDateString('zh-CN');
}

function pageBounds(pageResult: PageResult<unknown>) {
  if (pageResult.total === 0) {
    return { start: 0, end: 0 };
  }
  const start = (pageResult.page - 1) * pageResult.pageSize + 1;
  const end = Math.min(pageResult.page * pageResult.pageSize, pageResult.total);
  return { start, end };
}

type RecordSectionProps<T> = {
  icon: ReactNode;
  title: string;
  itemLabel: string;
  searchPlaceholder: string;
  query: string;
  onQueryChange: (value: string) => void;
  data: PageResult<T>;
  loading: boolean;
  error: string;
  onPageChange: (page: number) => void;
  emptyText: string;
  renderItem: (item: T) => ReactNode;
};

function RecordSection<T>({
  icon,
  title,
  itemLabel,
  searchPlaceholder,
  query,
  onQueryChange,
  data,
  loading,
  error,
  onPageChange,
  emptyText,
  renderItem,
}: RecordSectionProps<T>) {
  const { start, end } = pageBounds(data);

  return (
    <section className="rounded-2xl border border-gray-200 bg-white p-5">
      <div className="mb-4 flex items-center gap-2">
        {icon}
        <h2 className="text-base font-semibold text-gray-900">{title}</h2>
        {loading && <Loader2 className="h-4 w-4 animate-spin text-gray-400" />}
      </div>

      <SearchPaginationControls
        searchValue={query}
        onSearchChange={onQueryChange}
        searchPlaceholder={searchPlaceholder}
        itemLabel={itemLabel}
        currentPage={data.page}
        pageCount={Math.max(data.totalPages, 1)}
        onPageChange={onPageChange}
        startIndex={start}
        endIndex={end}
        filteredTotal={data.total}
        total={data.total}
        className="mb-4"
      />

      {error ? (
        <div className="rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">
          {error}
        </div>
      ) : loading && data.items.length === 0 ? (
        <div className="flex h-40 items-center justify-center rounded-xl border border-dashed border-gray-200 text-sm text-gray-400">
          <Loader2 className="mr-2 h-4 w-4 animate-spin" />
          正在加载列表...
        </div>
      ) : data.items.length === 0 ? (
        <p className="rounded-xl border border-dashed border-gray-200 px-3 py-8 text-center text-sm text-gray-400">
          {emptyText}
        </p>
      ) : (
        <div className="space-y-3">
          {data.items.map((item) => renderItem(item))}
        </div>
      )}
    </section>
  );
}

export default function FamilyMemberMemoryPage() {
  const searchParams = useSearchParams();
  const { families, activeFamilyId, setActiveFamilyId, isLoading: loadingFamilies } = useViewerRole();

  const [selectedFamilyId, setSelectedFamilyId] = useState<number | null>(null);
  const [targetUserId, setTargetUserId] = useState<number | null>(null);
  const [members, setMembers] = useState<FamilyMember[]>([]);
  const [mirrorContext, setMirrorContext] = useState<MirrorContextResponse | null>(null);
  const [loadingData, setLoadingData] = useState(false);
  const [error, setError] = useState('');

  const [diaryQuery, setDiaryQuery] = useState('');
  const [diaryPage, setDiaryPage] = useState(1);
  const [diaryResults, setDiaryResults] = useState<PageResult<DiaryEntry>>(createEmptyPage());
  const [loadingDiaries, setLoadingDiaries] = useState(false);
  const [diaryError, setDiaryError] = useState('');

  const [memoryQuery, setMemoryQuery] = useState('');
  const [memoryPage, setMemoryPage] = useState(1);
  const [memoryResults, setMemoryResults] = useState<PageResult<MemoryEntry>>(createEmptyPage());
  const [loadingMemories, setLoadingMemories] = useState(false);
  const [memoryError, setMemoryError] = useState('');

  const [growthQuery, setGrowthQuery] = useState('');
  const [growthPage, setGrowthPage] = useState(1);
  const [growthResults, setGrowthResults] = useState<PageResult<GrowthGuardRecord>>(createEmptyPage());
  const [loadingGrowthRecords, setLoadingGrowthRecords] = useState(false);
  const [growthError, setGrowthError] = useState('');

  const deferredDiaryQuery = useDeferredValue(diaryQuery);
  const deferredMemoryQuery = useDeferredValue(memoryQuery);
  const deferredGrowthQuery = useDeferredValue(growthQuery);

  const requestedFamilyId = useMemo(() => {
    const value = Number(searchParams.get('familyId'));
    return Number.isFinite(value) && value > 0 ? value : null;
  }, [searchParams]);

  const requestedUserId = useMemo(() => {
    const value = Number(searchParams.get('userId'));
    return Number.isFinite(value) && value > 0 ? value : null;
  }, [searchParams]);

  useEffect(() => {
    if (loadingFamilies) return;

    const familyFromQuery = requestedFamilyId && families.some((family) => family.id === requestedFamilyId)
      ? requestedFamilyId
      : null;
    const nextFamilyId = familyFromQuery
      || (activeFamilyId && families.some((family) => family.id === activeFamilyId) ? activeFamilyId : null)
      || families[0]?.id
      || null;

    setSelectedFamilyId(nextFamilyId);
    if (familyFromQuery && familyFromQuery !== activeFamilyId) {
      setActiveFamilyId(familyFromQuery);
    }
  }, [activeFamilyId, families, loadingFamilies, requestedFamilyId, setActiveFamilyId]);

  useEffect(() => {
    if (!selectedFamilyId) {
      setMembers([]);
      setTargetUserId(null);
      setMirrorContext(null);
      return;
    }

    let active = true;
    setLoadingData(true);
    setError('');

    familyApi.getMembers(selectedFamilyId)
      .then((memberList) => {
        if (!active) return;
        const nextMembers = Array.isArray(memberList) ? memberList : [];
        setMembers(nextMembers);
        setTargetUserId((current) => {
          if (requestedUserId) {
            return nextMembers.some((member) => member.userId === requestedUserId)
              ? requestedUserId
              : null;
          }

          return current && nextMembers.some((member) => member.userId === current)
            ? current
            : nextMembers[0]?.userId ?? null;
        });
      })
      .catch((err) => {
        if (!active) return;
        setError(err instanceof Error ? err.message : '加载成员记忆视图失败');
        setMembers([]);
        setTargetUserId(null);
      })
      .finally(() => {
        if (active) setLoadingData(false);
      });

    return () => {
      active = false;
    };
  }, [requestedUserId, selectedFamilyId]);

  useEffect(() => {
    if (!selectedFamilyId || !targetUserId) {
      setMirrorContext(null);
      return;
    }

    let active = true;
    mirrorApi.getContext(selectedFamilyId, targetUserId)
      .then((context) => {
        if (active) setMirrorContext(context);
      })
      .catch(() => {
        if (active) setMirrorContext(null);
      });

    return () => {
      active = false;
    };
  }, [selectedFamilyId, targetUserId]);

  useEffect(() => {
    setDiaryPage(1);
  }, [selectedFamilyId, targetUserId, diaryQuery]);

  useEffect(() => {
    setMemoryPage(1);
  }, [selectedFamilyId, targetUserId, memoryQuery]);

  useEffect(() => {
    setGrowthPage(1);
  }, [selectedFamilyId, targetUserId, growthQuery]);

  useEffect(() => {
    if (!selectedFamilyId || !targetUserId) {
      setDiaryResults(createEmptyPage());
      setDiaryError('');
      return;
    }

    let active = true;
    setLoadingDiaries(true);
    setDiaryError('');

    diaryApi.searchFamilyEntries({
      familyId: selectedFamilyId,
      targetUserId,
      keyword: deferredDiaryQuery,
      page: diaryPage,
      pageSize: PAGE_SIZE,
    })
      .then((pageResult) => {
        if (!active) return;
        setDiaryResults(pageResult);
        if (pageResult.page !== diaryPage) {
          setDiaryPage(pageResult.page);
        }
      })
      .catch((err) => {
        if (!active) return;
        setDiaryError(err instanceof Error ? err.message : '加载相关记录失败');
        setDiaryResults(createEmptyPage());
      })
      .finally(() => {
        if (active) setLoadingDiaries(false);
      });

    return () => {
      active = false;
    };
  }, [deferredDiaryQuery, diaryPage, selectedFamilyId, targetUserId]);

  useEffect(() => {
    if (!selectedFamilyId || !targetUserId) {
      setMemoryResults(createEmptyPage());
      setMemoryError('');
      return;
    }

    let active = true;
    setLoadingMemories(true);
    setMemoryError('');

    memoryApi.searchFamilyMemories({
      familyId: selectedFamilyId,
      targetUserId,
      keyword: deferredMemoryQuery,
      page: memoryPage,
      pageSize: PAGE_SIZE,
    })
      .then((pageResult) => {
        if (!active) return;
        setMemoryResults(pageResult);
        if (pageResult.page !== memoryPage) {
          setMemoryPage(pageResult.page);
        }
      })
      .catch((err) => {
        if (!active) return;
        setMemoryError(err instanceof Error ? err.message : '加载相关经验失败');
        setMemoryResults(createEmptyPage());
      })
      .finally(() => {
        if (active) setLoadingMemories(false);
      });

    return () => {
      active = false;
    };
  }, [deferredMemoryQuery, memoryPage, selectedFamilyId, targetUserId]);

  useEffect(() => {
    if (!selectedFamilyId || !targetUserId) {
      setGrowthResults(createEmptyPage());
      setGrowthError('');
      return;
    }

    let active = true;
    setLoadingGrowthRecords(true);
    setGrowthError('');

    growthGuardApi.searchFamilyRecords({
      familyId: selectedFamilyId,
      targetUserId,
      keyword: deferredGrowthQuery,
      page: growthPage,
      pageSize: PAGE_SIZE,
    })
      .then((pageResult) => {
        if (!active) return;
        setGrowthResults(pageResult);
        if (pageResult.page !== growthPage) {
          setGrowthPage(pageResult.page);
        }
      })
      .catch((err) => {
        if (!active) return;
        setGrowthError(err instanceof Error ? err.message : '加载成长观察失败');
        setGrowthResults(createEmptyPage());
      })
      .finally(() => {
        if (active) setLoadingGrowthRecords(false);
      });

    return () => {
      active = false;
    };
  }, [deferredGrowthQuery, growthPage, selectedFamilyId, targetUserId]);

  const selectedFamily = useMemo(
    () => families.find((family) => family.id === selectedFamilyId) || null,
    [families, selectedFamilyId],
  );

  const requestedUserExists = useMemo(
    () => requestedUserId != null && members.some((member) => member.userId === requestedUserId),
    [members, requestedUserId],
  );

  const missingRequestedUser = requestedUserId != null && !loadingData && !requestedUserExists;

  const targetMember = useMemo(() => {
    if (missingRequestedUser) return null;
    return members.find((member) => member.userId === targetUserId) || mirrorContext?.targetMember || null;
  }, [members, mirrorContext?.targetMember, missingRequestedUser, targetUserId]);

  if (loadingFamilies) {
    return (
      <div className="flex h-60 items-center justify-center text-gray-400">
        <Loader2 className="mr-2 h-5 w-5 animate-spin" />
        正在加载成员视图...
      </div>
    );
  }

  if (families.length === 0) {
    return (
      <div className="mx-auto max-w-3xl rounded-lg border border-gray-200 bg-white p-10 text-center">
        <Users className="mx-auto mb-3 h-10 w-10 text-gray-300" />
        <h1 className="text-lg font-semibold text-gray-900">还没有家族空间</h1>
        <p className="mt-2 text-sm text-gray-500">先创建或加入家族，再查看成员记忆资产。</p>
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
    <div className="mx-auto w-full max-w-6xl">
      <div className="mb-4">
        <Link
          href={`/dashboard/family?tab=members${selectedFamilyId ? `&familyId=${selectedFamilyId}` : ''}`}
          className="inline-flex items-center gap-2 text-sm text-blue-600 hover:text-blue-700 hover:underline"
        >
          <ArrowLeft className="h-4 w-4" />
          返回家族成员
        </Link>
      </div>

      {error && (
        <div className="mb-4 rounded-lg border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">
          {error}
        </div>
      )}

      <section className="mb-4 rounded-2xl border border-gray-200 bg-white p-5 sm:p-6">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <div className="mb-2 inline-flex items-center gap-2 rounded-full bg-purple-50 px-3 py-1 text-xs font-medium text-purple-700">
              <Sparkles className="h-3.5 w-3.5" />
              成员记忆
            </div>
            <h1 className="text-2xl font-bold text-gray-900">{memberDisplayName(targetMember)}</h1>
            <p className="mt-2 text-sm leading-6 text-gray-500">
              查看该成员在当前授权范围下可见的记录、经验与成长观察。
              镜像 Agent 只能基于授权资料做参考，不能代表本人真实表达。
            </p>
          </div>

          <div className="w-full max-w-xs rounded-xl border border-gray-100 bg-gray-50 p-4">
            <p className="text-xs font-medium text-gray-500">当前家族</p>
            <p className="mt-1 text-sm font-semibold text-gray-900">{selectedFamily?.name || '未选择'}</p>
            <p className="mt-2 text-xs text-gray-500">
              {selectedFamily?.description || '这里展示当前成员所属的家族空间。'}
            </p>
          </div>
        </div>
      </section>

      {loadingData ? (
        <div className="flex h-60 items-center justify-center rounded-2xl border border-gray-200 bg-white text-gray-400">
          <RefreshCw className="mr-2 h-5 w-5 animate-spin" />
          正在整理成员资料...
        </div>
      ) : !targetMember ? (
        <div className="rounded-2xl border border-gray-200 bg-white p-10 text-center">
          <UserRound className="mx-auto mb-3 h-10 w-10 text-gray-300" />
          <h2 className="text-lg font-semibold text-gray-900">没有找到对应成员</h2>
          <p className="mt-2 text-sm text-gray-500">请返回成员列表重新选择，或确认当前成员仍在该家族中。</p>
        </div>
      ) : (
        <>
          <section className="mb-4 grid grid-cols-1 gap-4 xl:grid-cols-[1.15fr_0.85fr]">
            <article className="rounded-2xl border border-gray-200 bg-white p-5">
              <div className="flex items-start gap-4">
                <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-purple-100 text-xl font-bold text-purple-700">
                  {memberDisplayName(targetMember).charAt(0).toUpperCase()}
                </div>
                <div className="min-w-0">
                  <h2 className="text-xl font-semibold text-gray-900">{memberDisplayName(targetMember)}</h2>
                  <p className="mt-1 text-sm text-gray-500">{memberAccountName(targetMember) || '家族成员账号'}</p>
                  <p className="mt-2 text-sm text-gray-500">{memberProfileLine(targetMember)}</p>
                  <div className="mt-3 flex flex-wrap gap-2">
                    <span className="rounded-full bg-gray-100 px-2.5 py-1 text-xs text-gray-600">
                      身份：{familyRoleLabel(targetMember.role)}
                    </span>
                    <span className="rounded-full bg-blue-50 px-2.5 py-1 text-xs text-blue-700">
                      称呼：{targetMember.relationshipLabel?.trim() || '未设置'}
                    </span>
                  </div>
                </div>
              </div>
            </article>

            <article className="rounded-2xl border border-gray-200 bg-white p-5">
              <h2 className="text-base font-semibold text-gray-900">资料概况</h2>
              <div className="mt-4 grid grid-cols-2 gap-3">
                <div className="rounded-xl bg-rose-50 px-4 py-3">
                  <p className="text-2xl font-bold text-rose-700">{diaryResults.total}</p>
                  <p className="text-xs text-rose-600">相关记录</p>
                </div>
                <div className="rounded-xl bg-amber-50 px-4 py-3">
                  <p className="text-2xl font-bold text-amber-700">{memoryResults.total}</p>
                  <p className="text-xs text-amber-600">相关经验</p>
                </div>
                <div className="rounded-xl bg-emerald-50 px-4 py-3">
                  <p className="text-2xl font-bold text-emerald-700">{growthResults.total}</p>
                  <p className="text-xs text-emerald-600">成长观察</p>
                </div>
                <div className="rounded-xl bg-purple-50 px-4 py-3">
                  <p className="text-sm font-semibold text-purple-700">
                    {mirrorContext?.sourceSummary?.trim() ? '已准备' : '有限'}
                  </p>
                  <p className="mt-1 text-xs text-purple-600">镜像上下文</p>
                </div>
              </div>
              <div className="mt-4 rounded-xl border border-yellow-100 bg-yellow-50 p-3 text-xs leading-5 text-yellow-800">
                成员记忆只展示后端按权限过滤后的内容。若内容较少，通常意味着当前授权范围有限，或相关记录本身还不够完整。
              </div>
            </article>
          </section>

          {mirrorContext?.sourceSummary && (
            <section className="mb-4 rounded-2xl border border-purple-100 bg-purple-50 p-5">
              <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <h2 className="text-base font-semibold text-purple-900">镜像参考摘要</h2>
                  <p className="mt-1 text-sm leading-6 text-purple-800">{mirrorContext.sourceSummary}</p>
                </div>
                <Link
                  href={`/dashboard/agent${selectedFamilyId && targetUserId ? `?familyId=${selectedFamilyId}&targetUserId=${targetUserId}` : ''}`}
                  className="inline-flex h-10 shrink-0 items-center justify-center rounded-lg bg-purple-600 px-4 text-sm font-medium text-white hover:bg-purple-700"
                >
                  查看镜像 Agent
                </Link>
              </div>
            </section>
          )}

          <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
            <RecordSection
              icon={<BookHeart className="h-4 w-4 text-rose-500" />}
              title="相关记录"
              itemLabel="条记录"
              searchPlaceholder="搜索标题、摘要或正文"
              query={diaryQuery}
              onQueryChange={setDiaryQuery}
              data={diaryResults}
              loading={loadingDiaries}
              error={diaryError}
              onPageChange={setDiaryPage}
              emptyText="暂无该成员相关记录。"
              renderItem={(entry) => (
                <article key={entry.id} className="rounded-xl border border-gray-100 bg-gray-50 p-3">
                  <p className="text-sm font-medium text-gray-900">{diaryTitle(entry)}</p>
                  <p className="mt-1 line-clamp-3 text-sm leading-6 text-gray-600">{entry.rawText}</p>
                  <p className="mt-2 text-[11px] text-gray-400">{shortDate(entry.createdAt)}</p>
                </article>
              )}
            />

            <RecordSection
              icon={<ScrollText className="h-4 w-4 text-amber-500" />}
              title="相关经验"
              itemLabel="条经验"
              searchPlaceholder="搜索摘要、场景或正文"
              query={memoryQuery}
              onQueryChange={setMemoryQuery}
              data={memoryResults}
              loading={loadingMemories}
              error={memoryError}
              onPageChange={setMemoryPage}
              emptyText="暂无该成员相关经验。"
              renderItem={(memory) => (
                <article key={memory.id} className="rounded-xl border border-gray-100 bg-gray-50 p-3">
                  <p className="text-sm font-medium text-gray-900">{memoryTitle(memory)}</p>
                  <p className="mt-1 line-clamp-3 text-sm leading-6 text-gray-600">
                    {memory.summary || memory.content}
                  </p>
                  <p className="mt-2 text-[11px] text-gray-400">{shortDate(memory.createdAt)}</p>
                </article>
              )}
            />

            <RecordSection
              icon={<HeartPulse className="h-4 w-4 text-emerald-500" />}
              title="成长观察"
              itemLabel="条观察"
              searchPlaceholder="搜索观察内容或类别"
              query={growthQuery}
              onQueryChange={setGrowthQuery}
              data={growthResults}
              loading={loadingGrowthRecords}
              error={growthError}
              onPageChange={setGrowthPage}
              emptyText="暂无该成员成长观察。"
              renderItem={(record) => (
                <article key={record.id} className="rounded-xl border border-gray-100 bg-gray-50 p-3">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="rounded-full bg-emerald-50 px-2 py-0.5 text-[11px] font-medium text-emerald-700">
                      {growthCategoryLabel(record.category)}
                    </span>
                    <span className="text-[11px] text-gray-400">{shortDate(record.observedAt || record.createdAt)}</span>
                  </div>
                  <p className="mt-2 line-clamp-3 text-sm leading-6 text-gray-600">{record.content}</p>
                </article>
              )}
            />
          </div>

          <section className="mt-4 rounded-2xl border border-gray-200 bg-white p-5">
            <div className="mb-3 flex items-center gap-2">
              <Shield className="h-4 w-4 text-blue-600" />
              <h2 className="text-base font-semibold text-gray-900">继续补充这位成员的资料</h2>
            </div>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
              <Link
                href={`/dashboard/diary${selectedFamilyId ? `?familyId=${selectedFamilyId}` : ''}`}
                className="rounded-xl border border-gray-100 bg-gray-50 p-4 hover:border-emerald-200 hover:bg-emerald-50"
              >
                <p className="text-sm font-semibold text-gray-900">写日记</p>
                <p className="mt-1 text-xs leading-5 text-gray-500">补充和 TA 相关的日常、事件或回忆。</p>
              </Link>
              <Link
                href={`/dashboard/diary${selectedFamilyId ? `?familyId=${selectedFamilyId}&writeCategory=EXPERIENCE` : '?writeCategory=EXPERIENCE'}`}
                className="rounded-xl border border-gray-100 bg-gray-50 p-4 hover:border-emerald-200 hover:bg-emerald-50"
              >
                <p className="text-sm font-semibold text-gray-900">写经验日记</p>
                <p className="mt-1 text-xs leading-5 text-gray-500">把对 TA 有帮助的经验或提醒沉淀下来。</p>
              </Link>
              <Link
                href={`/dashboard/diary?writeCategory=OBSERVATION${selectedFamilyId ? `&familyId=${selectedFamilyId}` : ''}${targetUserId ? `&relatedUserId=${targetUserId}` : ''}`}
                className="rounded-xl border border-gray-100 bg-gray-50 p-4 hover:border-emerald-200 hover:bg-emerald-50"
              >
                <p className="text-sm font-semibold text-gray-900">写观察日记</p>
                <p className="mt-1 text-xs leading-5 text-gray-500">把成长观察和后续提醒继续补完整。</p>
              </Link>
            </div>
          </section>
        </>
      )}
    </div>
  );
}
