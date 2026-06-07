'use client';

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useAuthStore } from '@/stores/authStore';
import { diaryApi, familyApi, growthGuardApi, memoryApi } from '@/lib/api';
import { useViewerRole } from '@/hooks/useViewerRole';
import { isPlatformAdmin } from '@/lib/roles';
import type { DiaryEntry, FamilyMember, GrowthGuardRecord, MemoryEntry } from '@/types';
import {
  ArrowRight,
  Bot,
  BookHeart,
  BookOpen,
  CheckCircle,
  HeartPulse,
  Loader2,
  ScrollText,
  Sparkles,
  Users,
} from 'lucide-react';

function memberName(member?: FamilyMember) {
  if (!member) return '家族成员';
  return member.relationshipLabel?.trim() || member.nickname?.trim() || member.username?.trim() || `用户 ${member.userId}`;
}

function diaryTitle(entry: DiaryEntry) {
  return entry.structured?.title || entry.structured?.summary || entry.rawText.slice(0, 30) || '未命名记录';
}

function memoryTitle(memory: MemoryEntry) {
  return memory.summary || memory.content.slice(0, 30) || '未命名经验';
}

function followUpStatus(record: GrowthGuardRecord) {
  return String(record.metadata?.followUpStatus || 'PENDING').toUpperCase();
}

function isPendingGrowth(record: GrowthGuardRecord) {
  const status = followUpStatus(record);
  return status !== 'IMPROVED' && status !== 'ARCHIVED';
}

function recentDate(value?: string) {
  if (!value) return '';
  return new Date(value).toLocaleDateString('zh-CN');
}

export default function DashboardPage() {
  const user = useAuthStore((state) => state.user);
  const { families, activeFamilyId, activeFamily, viewerRole, isLoading: loadingFamilies } = useViewerRole();
  const [members, setMembers] = useState<FamilyMember[]>([]);
  const [diaries, setDiaries] = useState<DiaryEntry[]>([]);
  const [memories, setMemories] = useState<MemoryEntry[]>([]);
  const [growthRecords, setGrowthRecords] = useState<GrowthGuardRecord[]>([]);
  const [loadingData, setLoadingData] = useState(false);
  const platformAdmin = isPlatformAdmin(user);

  useEffect(() => {
    if (!activeFamilyId) {
      setMembers([]);
      setDiaries([]);
      setMemories([]);
      setGrowthRecords([]);
      return;
    }

    let active = true;
    setLoadingData(true);
    Promise.all([
      familyApi.getMembers(activeFamilyId).catch(() => [] as FamilyMember[]),
      diaryApi.listFamilyEntries(activeFamilyId, 12).catch(() => [] as DiaryEntry[]),
      memoryApi.listFamilyMemories(activeFamilyId, 12).catch(() => [] as MemoryEntry[]),
      growthGuardApi.listFamilyRecords(activeFamilyId, 20).catch(() => [] as GrowthGuardRecord[]),
    ])
      .then(([memberList, diaryList, memoryList, growthList]) => {
        if (!active) return;
        setMembers(Array.isArray(memberList) ? memberList : []);
        setDiaries(Array.isArray(diaryList) ? diaryList : []);
        setMemories(Array.isArray(memoryList) ? memoryList : []);
        setGrowthRecords(Array.isArray(growthList) ? growthList : []);
      })
      .finally(() => {
        if (active) setLoadingData(false);
      });

    return () => {
      active = false;
    };
  }, [activeFamilyId]);

  const pendingGrowth = useMemo(
    () => growthRecords.filter(isPendingGrowth),
    [growthRecords],
  );
  const sparseMembers = useMemo(() => {
    return members
      .map((member) => {
        const diaryCount = diaries.filter((entry) => entry.userId === member.userId).length;
        return { member, diaryCount };
      })
      .filter((item) => item.diaryCount < 2)
      .slice(0, 3);
  }, [diaries, members]);

  const todayActions = [
    {
      href: `/dashboard/diary?template=choice${activeFamilyId ? `&familyId=${activeFamilyId}` : ''}`,
      title: '补一条重要选择',
      desc: '让镜像理解真实决策方式',
      icon: BookHeart,
      color: 'bg-blue-50 text-blue-700 hover:bg-blue-100',
    },
    {
      href: `/dashboard/diary?template=family-message${activeFamilyId ? `&familyId=${activeFamilyId}` : ''}`,
      title: '给家人留一句话',
      desc: '沉淀关系和表达方式',
      icon: Sparkles,
      color: 'bg-rose-50 text-rose-700 hover:bg-rose-100',
    },
    {
      href: `/dashboard/heritage?type=ELDER_ADVICE&scenario=${encodeURIComponent('长者经验')}${activeFamilyId ? `&familyId=${activeFamilyId}` : ''}`,
      title: '补一条长者经验',
      desc: '把经验教训变成家族软资产',
      icon: ScrollText,
      color: 'bg-amber-50 text-amber-700 hover:bg-amber-100',
    },
    {
      href: `/dashboard/growth?category=VISION${activeFamilyId ? `&familyId=${activeFamilyId}` : ''}`,
      title: '补一条成长观察',
      desc: '记录视力、体态、睡眠等信号',
      icon: HeartPulse,
      color: 'bg-emerald-50 text-emerald-700 hover:bg-emerald-100',
    },
  ];

  if (loadingFamilies) {
    return (
      <div className="flex h-60 items-center justify-center text-gray-400">
        <Loader2 className="mr-2 h-5 w-5 animate-spin" />
        加载中...
      </div>
    );
  }

  if (families.length === 0) {
    return (
      <div className="mx-auto max-w-3xl rounded-lg border border-gray-200 bg-white p-10 text-center">
        <Users className="mx-auto mb-3 h-10 w-10 text-gray-300" />
        <h1 className="text-lg font-semibold text-gray-900">先创建一个家族空间</h1>
        <p className="mt-2 text-sm text-gray-500">FamilyAgent 以家族空间为单位沉淀日记、经验、成长守护和镜像参考。</p>
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
    <div className="mx-auto w-full max-w-7xl">
      <section className="mb-4 rounded-xl border border-gray-200 bg-white p-4 sm:p-5">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div className="min-w-0">
            <div className="mb-2 inline-flex items-center gap-1.5 rounded-full bg-purple-50 px-2.5 py-1 text-xs font-medium text-purple-700">
              <Sparkles className="h-3.5 w-3.5" />
              家族记忆建设
            </div>
            <h1 className="text-2xl font-bold text-gray-900">
              {activeFamily?.name || '家族空间'}驾驶舱
            </h1>
            <p className="mt-1 text-sm text-gray-500">
              今天的重点不是多打开一个功能，而是补足能让家族 AI 更懂这个家庭的真实记录。
            </p>
          </div>
          <div className="grid grid-cols-4 gap-2 text-center">
            <div className="rounded-lg bg-rose-50 px-3 py-2">
              <p className="text-lg font-bold text-rose-700">{diaries.length}</p>
              <p className="text-[11px] text-rose-600">人生记录</p>
            </div>
            <div className="rounded-lg bg-amber-50 px-3 py-2">
              <p className="text-lg font-bold text-amber-700">{memories.length}</p>
              <p className="text-[11px] text-amber-600">家族经验</p>
            </div>
            <div className="rounded-lg bg-emerald-50 px-3 py-2">
              <p className="text-lg font-bold text-emerald-700">{pendingGrowth.length}</p>
              <p className="text-[11px] text-emerald-600">待跟进</p>
            </div>
            <div className="rounded-lg bg-purple-50 px-3 py-2">
              <p className="text-lg font-bold text-purple-700">{members.length}</p>
              <p className="text-[11px] text-purple-600">成员</p>
            </div>
          </div>
        </div>
      </section>

      {loadingData && (
        <div className="mb-4 flex items-center rounded-lg border border-gray-200 bg-white px-4 py-3 text-sm text-gray-400">
          <Loader2 className="mr-2 h-4 w-4 animate-spin" />
          正在整理家族记忆状态...
        </div>
      )}

      <section className="mb-4 rounded-xl border border-gray-200 bg-white p-4 sm:p-5">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-gray-900">今日建议记录</h2>
          <span className="text-xs text-gray-400">低门槛输入，高价值输出</span>
        </div>
        <div className="grid grid-cols-1 gap-2 sm:grid-cols-2 xl:grid-cols-4">
          {todayActions.map((action) => {
            const Icon = action.icon;
            return (
              <Link
                key={action.href}
                href={action.href}
                className={`flex min-h-24 flex-col justify-between rounded-lg border border-transparent p-3 transition-colors ${action.color}`}
              >
                <Icon className="h-5 w-5" />
                <span>
                  <span className="block text-sm font-semibold">{action.title}</span>
                  <span className="mt-1 block text-xs opacity-80">{action.desc}</span>
                </span>
              </Link>
            );
          })}
        </div>
      </section>

      <div className="mb-4 grid grid-cols-1 gap-4 lg:grid-cols-[0.9fr_1.1fr]">
        <section className="rounded-xl border border-gray-200 bg-white p-4 sm:p-5">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-sm font-semibold text-gray-900">镜像资料提醒</h2>
            <Link href="/dashboard/mirror" className="text-xs text-blue-600 hover:underline">
              进入镜像 Agent
            </Link>
          </div>
          {sparseMembers.length === 0 ? (
            <div className="rounded-lg bg-green-50 p-4 text-sm text-green-700">
              <CheckCircle className="mb-2 h-5 w-5" />
              当前成员的基础记录比较充足，可以继续通过镜像 Agent 测试回答质量。
            </div>
          ) : (
            <div className="space-y-2">
              {sparseMembers.map(({ member, diaryCount }) => (
                <div key={member.userId} className="flex items-center justify-between gap-3 rounded-lg border border-gray-100 p-3">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-gray-900">{memberName(member)}</p>
                    <p className="mt-0.5 text-xs text-gray-400">授权日记约 {diaryCount} 条，镜像参考可能偏弱</p>
                  </div>
                  <Link
                    href={`/dashboard/mirror?familyId=${activeFamilyId || ''}&targetUserId=${member.userId}`}
                    className="inline-flex h-8 shrink-0 items-center rounded-lg bg-purple-50 px-3 text-xs font-medium text-purple-700 hover:bg-purple-100"
                  >
                    查看
                  </Link>
                </div>
              ))}
            </div>
          )}
        </section>

        <section className="rounded-xl border border-gray-200 bg-white p-4 sm:p-5">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-sm font-semibold text-gray-900">最近家族动态</h2>
            <Link href="/dashboard/diary" className="text-xs text-blue-600 hover:underline">
              查看全部
            </Link>
          </div>
          <div className="space-y-2">
            {diaries.slice(0, 3).map((entry) => (
              <Link key={`d-${entry.id}`} href="/dashboard/diary" className="block rounded-lg border border-gray-100 p-3 hover:bg-gray-50">
                <div className="mb-1 flex items-center gap-2">
                  <BookHeart className="h-4 w-4 text-rose-500" />
                  <span className="truncate text-sm font-medium text-gray-900">{diaryTitle(entry)}</span>
                </div>
                <p className="line-clamp-2 text-xs leading-5 text-gray-500">{entry.rawText}</p>
                <p className="mt-1 text-[11px] text-gray-400">{recentDate(entry.createdAt)}</p>
              </Link>
            ))}
            {memories.slice(0, 2).map((memory) => (
              <Link key={`m-${memory.id}`} href="/dashboard/heritage" className="block rounded-lg border border-gray-100 p-3 hover:bg-gray-50">
                <div className="mb-1 flex items-center gap-2">
                  <ScrollText className="h-4 w-4 text-amber-500" />
                  <span className="truncate text-sm font-medium text-gray-900">{memoryTitle(memory)}</span>
                </div>
                <p className="line-clamp-2 text-xs leading-5 text-gray-500">{memory.content}</p>
                <p className="mt-1 text-[11px] text-gray-400">{recentDate(memory.createdAt)}</p>
              </Link>
            ))}
            {diaries.length === 0 && memories.length === 0 && (
              <div className="rounded-lg border border-dashed border-gray-200 p-6 text-center">
                <BookHeart className="mx-auto mb-2 h-8 w-8 text-gray-300" />
                <p className="text-sm text-gray-500">这个家族还没有可见记录，从一条人生记录开始。</p>
              </div>
            )}
          </div>
        </section>
      </div>

      <section className="rounded-xl border border-gray-200 bg-white p-4 sm:p-5">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-gray-900">常用入口</h2>
          <Link href="/dashboard/family" className="text-xs text-blue-600 hover:underline">
            家族空间
          </Link>
        </div>
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-6">
          {[
            { href: '/dashboard/diary', label: '家族日记', icon: BookHeart, color: 'bg-rose-50 text-rose-700 hover:bg-rose-100' },
            { href: '/dashboard/mirror', label: '镜像 Agent', icon: Bot, color: 'bg-purple-50 text-purple-700 hover:bg-purple-100' },
            { href: '/dashboard/heritage', label: '家族经验', icon: ScrollText, color: 'bg-amber-50 text-amber-700 hover:bg-amber-100' },
            { href: '/dashboard/growth', label: '成长守护', icon: HeartPulse, color: 'bg-emerald-50 text-emerald-700 hover:bg-emerald-100' },
            { href: '/dashboard/family', label: '家族成员', icon: Users, color: 'bg-sky-50 text-sky-700 hover:bg-sky-100' },
            ...(platformAdmin ? [{ href: '/dashboard/knowledge', label: '题库资源', icon: BookOpen, color: 'bg-orange-50 text-orange-700 hover:bg-orange-100' }] : []),
          ].map((item) => {
            const Icon = item.icon;
            return (
              <Link
                key={item.href}
                href={item.href}
                className={`flex min-h-14 items-center gap-2 rounded-lg px-3 py-2 text-sm transition-colors ${item.color}`}
              >
                <Icon className="h-4 w-4 shrink-0" />
                <span className="truncate">{item.label}</span>
                <ArrowRight className="ml-auto h-3.5 w-3.5 opacity-50" />
              </Link>
            );
          })}
        </div>
      </section>
    </div>
  );
}
