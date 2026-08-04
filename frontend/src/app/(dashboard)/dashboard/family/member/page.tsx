'use client';

import { type ReactNode, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import {
  ArrowLeft,
  BookHeart,
  CalendarDays,
  HeartPulse,
  Home,
  Layers,
  Loader2,
  MessageCircle,
  ScrollText,
  Settings,
  UserRound,
  Users,
} from 'lucide-react';
import { diaryApi, familyApi, growthGuardApi, memoryApi, mirrorApi } from '@/lib/api';
import { useViewerRole } from '@/hooks/useViewerRole';
import { familyRoleLabel } from '@/lib/roles';
import { cn } from '@/lib/utils';
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
  return (
    member.relationshipLabel?.trim() ||
    member.nickname?.trim() ||
    member.username?.trim() ||
    `用户 ${member.userId}`
  );
}

function memberAccountName(member?: FamilyMember | null) {
  if (!member) return '';
  return member.nickname?.trim() || member.username?.trim() || `用户 ${member.userId}`;
}

function memberInitial(member?: FamilyMember | null) {
  return memberDisplayName(member).charAt(0).toUpperCase();
}

function memberBirthDate(member?: FamilyMember | null) {
  if (!member) return '';
  const value =
    member.birthDate ||
    (typeof member.metadata?.birthDate === 'string' ? member.metadata.birthDate : '') ||
    (typeof member.metadata?.birthday === 'string' ? member.metadata.birthday : '') ||
    (typeof member.metadata?.dateOfBirth === 'string' ? member.metadata.dateOfBirth : '');
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

  const year = Number(
    member?.birthYear || member?.metadata?.birthYear || member?.metadata?.yearOfBirth,
  );
  if (Number.isFinite(year) && year > 1870 && year <= new Date().getFullYear()) {
    return new Date().getFullYear() - year;
  }

  return null;
}

function memberSignature(member?: FamilyMember | null) {
  const value =
    member?.metadata?.signature || member?.metadata?.bio || member?.metadata?.statusText;
  return typeof value === 'string' && value.trim()
    ? value.trim()
    : '个性签名即将支持由成员自行上传。';
}

function diaryTitle(entry: DiaryEntry) {
  return (
    entry.structured?.title?.trim() ||
    entry.structured?.summary?.trim() ||
    entry.rawText.trim().slice(0, 34) ||
    '未命名记录'
  );
}

function memoryTitle(memory: MemoryEntry) {
  if (memory.summary?.trim()) return memory.summary.trim();
  if (typeof memory.metadata?.scenario === 'string' && memory.metadata.scenario.trim()) {
    return memory.metadata.scenario.trim();
  }
  return memory.content.trim().slice(0, 34) || '未命名经验';
}

function growthTitle(record: GrowthGuardRecord) {
  return record.content.trim().slice(0, 34) || growthCategoryLabel(record.category);
}

function growthCategoryLabel(category?: string) {
  switch ((category || '').toUpperCase()) {
    case 'POSTURE':
      return '体态观察';
    case 'DENTAL':
      return '牙齿观察';
    case 'VISION':
      return '视力观察';
    case 'SLEEP':
      return '睡眠观察';
    case 'EXERCISE':
      return '运动观察';
    case 'SCREEN_TIME':
      return '屏幕观察';
    case 'EMOTION':
      return '情绪观察';
    case 'COMMUNICATION':
      return '沟通观察';
    default:
      return '成长观察';
  }
}

function shortDate(value?: string) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' });
}

type VideoRecordSectionProps<T> = {
  icon: ReactNode;
  title: string;
  total: number;
  items: T[];
  loading: boolean;
  error: string;
  emptyText: string;
  activeTone: string;
  currentPage: number;
  pageCount: number;
  onPageChange: (page: number) => void;
  getKey: (item: T) => string | number;
  getTitle: (item: T) => string;
  getDate: (item: T) => string;
};

function VideoRecordSection<T>({
  icon,
  title,
  total,
  items,
  loading,
  error,
  emptyText,
  activeTone,
  currentPage,
  pageCount,
  onPageChange,
  getKey,
  getTitle,
  getDate,
}: VideoRecordSectionProps<T>) {
  return (
    <section className="border-t border-stone-200 py-7">
      <div className="mb-5 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-3">
          <h2 className="text-2xl font-semibold text-stone-950">{title}</h2>
          <span className="text-lg text-stone-400">·</span>
          <span className="text-lg text-stone-500">{total}</span>
          {loading && <Loader2 className="h-4 w-4 animate-spin text-stone-400" />}
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            className={cn('h-9 rounded-md px-4 text-sm font-medium text-white', activeTone)}
          >
            最新发布
          </button>
          <button
            type="button"
            className="h-9 rounded-md bg-stone-100 px-4 text-sm font-medium text-stone-500"
          >
            家族记录
          </button>
        </div>
      </div>

      {error ? (
        <div className="rounded-md border border-rose-100 bg-rose-50 px-4 py-3 text-sm text-rose-600">
          {error}
        </div>
      ) : loading && items.length === 0 ? (
        <div className="flex h-36 items-center justify-center text-sm text-stone-400">
          <Loader2 className="mr-2 h-4 w-4 animate-spin" />
          正在加载...
        </div>
      ) : items.length === 0 ? (
        <div className="flex h-36 items-center justify-center rounded-md border border-dashed border-stone-200 text-sm text-stone-400">
          {emptyText}
        </div>
      ) : (
        <>
          <div className="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
            {items.map((item) => (
              <article key={getKey(item)} className="group">
                <div className="relative aspect-video overflow-hidden rounded-md bg-stone-100 shadow-sm">
                  <div className="absolute inset-0 bg-[radial-gradient(circle_at_30%_20%,rgba(14,165,233,0.28),transparent_35%),linear-gradient(135deg,#f5f5f4,#e7e5e4)]" />
                  <div className="absolute left-3 top-3 inline-flex items-center gap-1 rounded bg-stone-950/75 px-2 py-1 text-xs font-medium text-white">
                    {icon}
                    FamilyAgent
                  </div>
                  <div className="absolute bottom-2 right-2 rounded bg-stone-950/75 px-2 py-0.5 text-xs text-white">
                    {getDate(item) || '未记录'}
                  </div>
                </div>
                <h3 className="mt-2 line-clamp-2 text-sm font-medium leading-6 text-stone-950 group-hover:text-sky-700">
                  {getTitle(item)}
                </h3>
              </article>
            ))}
          </div>

          {pageCount > 1 && (
            <div className="mt-5 flex items-center gap-3 text-sm text-stone-500">
              <button
                type="button"
                onClick={() => onPageChange(Math.max(1, currentPage - 1))}
                disabled={currentPage <= 1}
                className="rounded-md border border-stone-200 px-3 py-1.5 disabled:opacity-40"
              >
                上一页
              </button>
              <span>
                {currentPage} / {pageCount}
              </span>
              <button
                type="button"
                onClick={() => onPageChange(Math.min(pageCount, currentPage + 1))}
                disabled={currentPage >= pageCount}
                className="rounded-md border border-stone-200 px-3 py-1.5 disabled:opacity-40"
              >
                下一页
              </button>
            </div>
          )}
        </>
      )}
    </section>
  );
}

export default function FamilyMemberMemoryPage() {
  const searchParams = useSearchParams();
  const {
    families,
    activeFamilyId,
    setActiveFamilyId,
    isLoading: loadingFamilies,
  } = useViewerRole();

  const [selectedFamilyId, setSelectedFamilyId] = useState<number | null>(null);
  const [targetUserId, setTargetUserId] = useState<number | null>(null);
  const [members, setMembers] = useState<FamilyMember[]>([]);
  const [mirrorContext, setMirrorContext] = useState<MirrorContextResponse | null>(null);
  const [loadingData, setLoadingData] = useState(false);
  const [error, setError] = useState('');

  const [diaryPage, setDiaryPage] = useState(1);
  const [diaryResults, setDiaryResults] = useState<PageResult<DiaryEntry>>(createEmptyPage());
  const [loadingDiaries, setLoadingDiaries] = useState(false);
  const [diaryError, setDiaryError] = useState('');

  const [memoryPage, setMemoryPage] = useState(1);
  const [memoryResults, setMemoryResults] = useState<PageResult<MemoryEntry>>(createEmptyPage());
  const [loadingMemories, setLoadingMemories] = useState(false);
  const [memoryError, setMemoryError] = useState('');

  const [growthPage, setGrowthPage] = useState(1);
  const [growthResults, setGrowthResults] =
    useState<PageResult<GrowthGuardRecord>>(createEmptyPage());
  const [loadingGrowthRecords, setLoadingGrowthRecords] = useState(false);
  const [growthError, setGrowthError] = useState('');

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

    const familyFromQuery =
      requestedFamilyId && families.some((family) => family.id === requestedFamilyId)
        ? requestedFamilyId
        : null;
    const nextFamilyId =
      familyFromQuery ||
      (activeFamilyId && families.some((family) => family.id === activeFamilyId)
        ? activeFamilyId
        : null) ||
      families[0]?.id ||
      null;

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

    familyApi
      .getMembers(selectedFamilyId)
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
            : (nextMembers[0]?.userId ?? null);
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
    mirrorApi
      .getContext(selectedFamilyId, targetUserId)
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
    if (!selectedFamilyId || !targetUserId) {
      setDiaryResults(createEmptyPage());
      setDiaryError('');
      return;
    }

    let active = true;
    setLoadingDiaries(true);
    setDiaryError('');

    diaryApi
      .searchFamilyEntries({
        familyId: selectedFamilyId,
        targetUserId,
        page: diaryPage,
        pageSize: PAGE_SIZE,
      })
      .then((pageResult) => {
        if (!active) return;
        setDiaryResults(pageResult);
        if (pageResult.page !== diaryPage) setDiaryPage(pageResult.page);
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
  }, [diaryPage, selectedFamilyId, targetUserId]);

  useEffect(() => {
    if (!selectedFamilyId || !targetUserId) {
      setMemoryResults(createEmptyPage());
      setMemoryError('');
      return;
    }

    let active = true;
    setLoadingMemories(true);
    setMemoryError('');

    memoryApi
      .searchFamilyMemories({
        familyId: selectedFamilyId,
        targetUserId,
        page: memoryPage,
        pageSize: PAGE_SIZE,
      })
      .then((pageResult) => {
        if (!active) return;
        setMemoryResults(pageResult);
        if (pageResult.page !== memoryPage) setMemoryPage(pageResult.page);
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
  }, [memoryPage, selectedFamilyId, targetUserId]);

  useEffect(() => {
    if (!selectedFamilyId || !targetUserId) {
      setGrowthResults(createEmptyPage());
      setGrowthError('');
      return;
    }

    let active = true;
    setLoadingGrowthRecords(true);
    setGrowthError('');

    growthGuardApi
      .searchFamilyRecords({
        familyId: selectedFamilyId,
        targetUserId,
        page: growthPage,
        pageSize: PAGE_SIZE,
      })
      .then((pageResult) => {
        if (!active) return;
        setGrowthResults(pageResult);
        if (pageResult.page !== growthPage) setGrowthPage(pageResult.page);
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
  }, [growthPage, selectedFamilyId, targetUserId]);

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
    return (
      members.find((member) => member.userId === targetUserId) ||
      mirrorContext?.targetMember ||
      null
    );
  }, [members, mirrorContext?.targetMember, missingRequestedUser, targetUserId]);

  if (loadingFamilies) {
    return (
      <div className="flex h-60 items-center justify-center text-stone-400">
        <Loader2 className="mr-2 h-5 w-5 animate-spin" />
        正在加载成员视图...
      </div>
    );
  }

  if (families.length === 0) {
    return (
      <div className="mx-auto max-w-3xl rounded-md border border-stone-200 bg-white p-10 text-center">
        <Users className="mx-auto mb-3 h-10 w-10 text-stone-300" />
        <h1 className="text-lg font-semibold text-stone-950">还没有家族空间</h1>
        <Link
          href="/dashboard/family"
          className="mt-5 inline-flex h-10 items-center rounded-md bg-stone-950 px-4 text-sm font-medium text-white hover:bg-stone-800"
        >
          前往家族空间
        </Link>
      </div>
    );
  }

  const avatarUrl = targetMember?.avatarUrl;
  const age = memberAge(targetMember);

  return (
    <div className="mx-auto w-full max-w-[1500px]">
      <Link
        href={`/dashboard/family?tab=members${selectedFamilyId ? `&familyId=${selectedFamilyId}` : ''}`}
        className="mb-4 inline-flex items-center gap-2 text-sm text-sky-700 hover:text-sky-800 hover:underline"
      >
        <ArrowLeft className="h-4 w-4" />
        返回家族成员
      </Link>

      {error && (
        <div className="mb-4 rounded-md border border-rose-100 bg-rose-50 px-4 py-3 text-sm text-rose-600">
          {error}
        </div>
      )}

      {loadingData ? (
        <div className="flex h-60 items-center justify-center rounded-md border border-stone-200 bg-white text-stone-400">
          <Loader2 className="mr-2 h-5 w-5 animate-spin" />
          正在整理成员资料...
        </div>
      ) : !targetMember ? (
        <div className="rounded-md border border-stone-200 bg-white p-10 text-center">
          <UserRound className="mx-auto mb-3 h-10 w-10 text-stone-300" />
          <h2 className="text-lg font-semibold text-stone-950">没有找到对应成员</h2>
        </div>
      ) : (
        <>
          <section className="overflow-hidden rounded-md border border-stone-200 bg-white">
            <div className="relative h-44 bg-[radial-gradient(circle_at_20%_20%,rgba(14,165,233,0.35),transparent_28%),linear-gradient(120deg,#57534e,#a8a29e_48%,#d6d3d1)]">
              <div className="absolute inset-0 opacity-40 [background-image:linear-gradient(90deg,rgba(255,255,255,0.16)_1px,transparent_1px),linear-gradient(rgba(255,255,255,0.12)_1px,transparent_1px)] [background-size:42px_42px]" />
              <div className="absolute bottom-5 left-6 flex items-end gap-5">
                <div className="relative h-24 w-24 overflow-hidden rounded-full border-4 border-white bg-sky-100 shadow-lg">
                  {avatarUrl ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img
                      src={avatarUrl}
                      alt={memberDisplayName(targetMember)}
                      className="h-full w-full object-cover"
                    />
                  ) : (
                    <div className="flex h-full w-full items-center justify-center text-3xl font-bold text-sky-700">
                      {memberInitial(targetMember)}
                    </div>
                  )}
                </div>
                <div className="pb-2 text-white drop-shadow">
                  <div className="flex flex-wrap items-center gap-2">
                    <h1 className="text-3xl font-bold">{memberDisplayName(targetMember)}</h1>
                    <span className="rounded bg-white/20 px-2 py-0.5 text-xs font-medium">
                      {familyRoleLabel(targetMember.role)}
                    </span>
                  </div>
                  <p className="mt-2 max-w-3xl text-sm font-medium text-white/90">
                    {memberSignature(targetMember)}
                  </p>
                </div>
              </div>
              <div className="absolute bottom-6 right-6 hidden rounded-md bg-white/15 px-3 py-2 text-sm font-medium text-white ring-1 ring-white/25 backdrop-blur md:block">
                视角：家族成员
              </div>
            </div>

            <div className="flex flex-col gap-4 px-6 py-4 lg:flex-row lg:items-center lg:justify-between">
              <nav className="flex flex-wrap items-center gap-8 text-sm font-medium">
                <span className="inline-flex items-center gap-2 border-b-2 border-sky-500 pb-3 text-sky-700">
                  <Home className="h-4 w-4" />
                  主页
                </span>
                <span className="inline-flex items-center gap-2 pb-3 text-stone-500">
                  <BookHeart className="h-4 w-4" />
                  记录 {diaryResults.total}
                </span>
                <span className="inline-flex items-center gap-2 pb-3 text-stone-500">
                  <Layers className="h-4 w-4" />
                  经验 {memoryResults.total}
                </span>
                <span className="inline-flex items-center gap-2 pb-3 text-stone-500">
                  <Settings className="h-4 w-4" />
                  设置
                </span>
              </nav>
              <div className="grid grid-cols-4 gap-5 text-center text-sm">
                <div>
                  <p className="text-stone-500">记录</p>
                  <p className="mt-1 text-lg font-semibold text-stone-950">{diaryResults.total}</p>
                </div>
                <div>
                  <p className="text-stone-500">经验</p>
                  <p className="mt-1 text-lg font-semibold text-stone-950">{memoryResults.total}</p>
                </div>
                <div>
                  <p className="text-stone-500">观察</p>
                  <p className="mt-1 text-lg font-semibold text-stone-950">{growthResults.total}</p>
                </div>
                <div>
                  <p className="text-stone-500">年龄</p>
                  <p className="mt-1 text-lg font-semibold text-stone-950">{age ?? '-'}</p>
                </div>
              </div>
            </div>
          </section>

          <div className="grid gap-8 pt-8 xl:grid-cols-[minmax(0,1fr)_20rem]">
            <main className="min-w-0">
              <section className="mb-8 flex min-h-40 items-center justify-center rounded-md border border-stone-200 bg-white px-6 text-center">
                <div>
                  <MessageCircle className="mx-auto mb-3 h-10 w-10 text-sky-500" />
                  <Link
                    href={`/dashboard/agent${selectedFamilyId && targetUserId ? `?familyId=${selectedFamilyId}&targetUserId=${targetUserId}` : ''}`}
                    className="inline-flex h-10 items-center rounded-md bg-sky-600 px-4 text-sm font-medium text-white hover:bg-sky-700"
                  >
                    进入镜像 Agent
                  </Link>
                </div>
              </section>

              <VideoRecordSection
                icon={<BookHeart className="h-3.5 w-3.5" />}
                title="记录"
                total={diaryResults.total}
                items={diaryResults.items}
                loading={loadingDiaries}
                error={diaryError}
                emptyText="暂无该成员相关记录。"
                activeTone="bg-sky-500"
                currentPage={diaryResults.page}
                pageCount={Math.max(diaryResults.totalPages, 1)}
                onPageChange={setDiaryPage}
                getKey={(entry) => entry.id}
                getTitle={diaryTitle}
                getDate={(entry) => shortDate(entry.createdAt)}
              />

              <VideoRecordSection
                icon={<ScrollText className="h-3.5 w-3.5" />}
                title="经验"
                total={memoryResults.total}
                items={memoryResults.items}
                loading={loadingMemories}
                error={memoryError}
                emptyText="暂无该成员相关经验。"
                activeTone="bg-amber-500"
                currentPage={memoryResults.page}
                pageCount={Math.max(memoryResults.totalPages, 1)}
                onPageChange={setMemoryPage}
                getKey={(memory) => memory.id}
                getTitle={memoryTitle}
                getDate={(memory) => shortDate(memory.createdAt)}
              />

              <VideoRecordSection
                icon={<HeartPulse className="h-3.5 w-3.5" />}
                title="成长观察"
                total={growthResults.total}
                items={growthResults.items}
                loading={loadingGrowthRecords}
                error={growthError}
                emptyText="暂无该成员成长观察。"
                activeTone="bg-sky-500"
                currentPage={growthResults.page}
                pageCount={Math.max(growthResults.totalPages, 1)}
                onPageChange={setGrowthPage}
                getKey={(record) => record.id}
                getTitle={growthTitle}
                getDate={(record) => shortDate(record.observedAt || record.createdAt)}
              />
            </main>

            <aside className="space-y-4">
              <section className="rounded-md bg-stone-100 p-5">
                <h2 className="text-lg font-semibold text-stone-950">资料</h2>
                <div className="mt-4 space-y-3 text-sm text-stone-600">
                  <p>账号：{memberAccountName(targetMember) || '-'}</p>
                  <p>称呼：{targetMember.relationshipLabel?.trim() || '未设置'}</p>
                  <p>生日：{memberBirthDate(targetMember) || '未设置'}</p>
                  <p>当前家族：{selectedFamily?.name || '未选择'}</p>
                </div>
              </section>

              <section className="rounded-md bg-stone-100 p-5">
                <h2 className="text-lg font-semibold text-stone-950">镜像参考</h2>
                <p className="mt-4 text-sm leading-6 text-stone-500">
                  {mirrorContext?.sourceSummary?.trim() || '暂无足够镜像上下文。'}
                </p>
              </section>
            </aside>
          </div>
        </>
      )}
    </div>
  );
}
