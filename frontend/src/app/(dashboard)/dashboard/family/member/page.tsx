'use client';

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import {
  ArrowLeft,
  Bot,
  BookHeart,
  CheckCircle,
  HeartPulse,
  Loader2,
  RefreshCw,
  ScrollText,
  Shield,
  Sparkles,
  UserRound,
  Users,
} from 'lucide-react';
import { diaryApi, familyApi, growthGuardApi, memoryApi, mirrorApi } from '@/lib/api';
import { familyRoleLabel } from '@/lib/roles';
import { useViewerRole } from '@/hooks/useViewerRole';
import type { DiaryEntry, FamilyMember, GrowthGuardRecord, MemoryEntry, MirrorContextResponse } from '@/types';

function memberName(member?: FamilyMember | null) {
  if (!member) return '家族成员';
  return member.relationshipLabel?.trim() || member.nickname?.trim() || member.username?.trim() || `用户 ${member.userId}`;
}

function accountName(member?: FamilyMember | null) {
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
      if (age >= 0 && age <= 130) return { age, isDefault: false };
    }
  }
  const year = Number(member?.birthYear || member?.metadata?.birthYear || member?.metadata?.yearOfBirth);
  if (Number.isFinite(year) && year > 1870 && year <= new Date().getFullYear()) {
    return { age: new Date().getFullYear() - year, isDefault: false };
  }
  return { age: 20, isDefault: true };
}

function memberProfileLine(member?: FamilyMember | null) {
  const birthDate = memberBirthDate(member);
  const age = memberAge(member);
  return `${birthDate ? `生日：${birthDate}` : '生日未设置'} · 年龄：${age.age} 岁${age.isDefault ? '（默认）' : ''}`;
}

function diaryTitle(entry: DiaryEntry) {
  return entry.structured?.title || entry.structured?.summary || entry.rawText.slice(0, 34) || '未命名记录';
}

function memoryTitle(memory: MemoryEntry) {
  if (memory.summary?.trim()) return memory.summary.trim();
  if (typeof memory.metadata?.scenario === 'string' && memory.metadata.scenario.trim()) {
    return `${memory.metadata.scenario.trim()}相关经验`;
  }
  return memory.content.slice(0, 34) || '未命名经验';
}

function categoryLabel(category?: string) {
  switch (category) {
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

function followUpLabel(record: GrowthGuardRecord) {
  const status = String(record.metadata?.followUpStatus || 'PENDING').toUpperCase();
  if (status === 'WATCHING') return '继续关注';
  if (status === 'IMPROVED') return '已改善';
  if (status === 'ARCHIVED') return '已归档';
  return '待观察';
}

function shortDate(value?: string) {
  if (!value) return '';
  return new Date(value).toLocaleDateString('zh-CN');
}

function relatedUserId(entry: DiaryEntry) {
  const value = Number(entry.metadata?.relatedUserId);
  return Number.isFinite(value) && value > 0 ? value : null;
}

function isRelatedDiary(entry: DiaryEntry) {
  return entry.metadata?.mirrorSourceType === 'RELATED_BY_FAMILY'
    || Boolean(entry.metadata?.relatedUserId);
}

function diarySourceCode(entry: DiaryEntry, index: number) {
  return `${isRelatedDiary(entry) ? 'R' : 'D'}${index + 1}`;
}

function temporalLayerLabel(item: DiaryEntry | MemoryEntry) {
  if (isCoreMemory(item as MemoryEntry)) return '沉淀记忆';
  const label = item.metadata?.temporalLayerLabel;
  return typeof label === 'string' && label.trim() ? label.trim() : '未分层';
}

function temporalLayerClass(item: DiaryEntry | MemoryEntry) {
  if (isCoreMemory(item as MemoryEntry)) return 'bg-purple-50 text-purple-700';
  switch (item.metadata?.temporalLayer) {
    case 'FRESH':
      return 'bg-green-50 text-green-700';
    case 'FADING':
      return 'bg-yellow-50 text-yellow-700';
    case 'CORE_MEMORY':
      return 'bg-purple-50 text-purple-700';
    case 'IMPRESSION':
      return 'bg-gray-100 text-gray-600';
    default:
      return 'bg-gray-100 text-gray-500';
  }
}

function metadataText(memory: MemoryEntry, key: string) {
  const value = memory.metadata?.[key];
  return typeof value === 'string' && value.trim() ? value.trim() : '';
}

function metadataNumber(memory: MemoryEntry, key: string) {
  const value = Number(memory.metadata?.[key]);
  return Number.isFinite(value) && value > 0 ? value : null;
}

function isCoreMemory(memory?: MemoryEntry | null) {
  if (!memory) return false;
  if (memory.metadata?.coreMemory === true || memory.metadata?.coreMemory === 'true') return true;
  if (memory.metadata?.temporalLayer === 'CORE_MEMORY') return true;
  return Number(memory.importance) >= 5;
}

function hasMirrorProfile(context?: MirrorContextResponse | null) {
  return Boolean(context?.mirrorProfile && Object.keys(context.mirrorProfile).length > 0);
}

function completenessLevel(diaries: DiaryEntry[], growthRecords: GrowthGuardRecord[], context?: MirrorContextResponse | null) {
  const profileBonus = hasMirrorProfile(context) ? 1 : 0;
  const sourceCount = diaries.length + growthRecords.length + (context?.memories?.length || 0) + profileBonus;
  if (sourceCount >= 8) return { label: '资料较充分', className: 'bg-green-50 text-green-700', hint: '可以进入镜像 Agent 测试更具体的问题。' };
  if (sourceCount >= 4) return { label: '可谨慎参考', className: 'bg-blue-50 text-blue-700', hint: '建议继续补充关键选择、家人留言或成长观察。' };
  return { label: '资料偏少', className: 'bg-yellow-50 text-yellow-700', hint: '先补几条高信息密度记录，镜像回答会更稳。' };
}

export default function FamilyMemberMemoryPage() {
  const searchParams = useSearchParams();
  const { families, activeFamilyId, setActiveFamilyId, isLoading: loadingFamilies } = useViewerRole();
  const [selectedFamilyId, setSelectedFamilyId] = useState<number | null>(null);
  const [targetUserId, setTargetUserId] = useState<number | null>(null);
  const [members, setMembers] = useState<FamilyMember[]>([]);
  const [diaries, setDiaries] = useState<DiaryEntry[]>([]);
  const [memories, setMemories] = useState<MemoryEntry[]>([]);
  const [growthRecords, setGrowthRecords] = useState<GrowthGuardRecord[]>([]);
  const [mirrorContext, setMirrorContext] = useState<MirrorContextResponse | null>(null);
  const [loadingData, setLoadingData] = useState(false);
  const [error, setError] = useState('');

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
    if (familyFromQuery && activeFamilyId !== familyFromQuery) {
      setActiveFamilyId(familyFromQuery);
    }
  }, [activeFamilyId, families, loadingFamilies, requestedFamilyId, setActiveFamilyId]);

  useEffect(() => {
    if (!selectedFamilyId) {
      setMembers([]);
      setDiaries([]);
      setMemories([]);
      setGrowthRecords([]);
      setMirrorContext(null);
      return;
    }

    let active = true;
    setLoadingData(true);
    setError('');
    Promise.all([
      familyApi.getMembers(selectedFamilyId),
      diaryApi.listFamilyEntries(selectedFamilyId, 60).catch(() => [] as DiaryEntry[]),
      memoryApi.listFamilyMemories(selectedFamilyId, 60).catch(() => [] as MemoryEntry[]),
      growthGuardApi.listFamilyRecords(selectedFamilyId, 60).catch(() => [] as GrowthGuardRecord[]),
    ])
      .then(([memberList, diaryList, memoryList, growthList]) => {
        if (!active) return;
        const nextMembers = Array.isArray(memberList) ? memberList : [];
        setMembers(nextMembers);
        setDiaries(Array.isArray(diaryList) ? diaryList : []);
        setMemories(Array.isArray(memoryList) ? memoryList : []);
        setGrowthRecords(Array.isArray(growthList) ? growthList : []);
        setTargetUserId((current) => (
          requestedUserId && nextMembers.some((member) => member.userId === requestedUserId)
            ? requestedUserId
            : current && nextMembers.some((member) => member.userId === current)
              ? current
              : nextMembers[0]?.userId ?? null
        ));
      })
      .catch((err) => {
        if (!active) return;
        setError(err instanceof Error ? err.message : '成员记忆加载失败');
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

  const selectedFamily = useMemo(
    () => families.find((family) => family.id === selectedFamilyId) || null,
    [families, selectedFamilyId],
  );

  const targetMember = useMemo(
    () => members.find((member) => member.userId === targetUserId) || mirrorContext?.targetMember || null,
    [members, mirrorContext, targetUserId],
  );

  const targetDiaries = useMemo(
    () => diaries.filter((entry) => entry.userId === targetUserId),
    [diaries, targetUserId],
  );

  const relatedDiaries = useMemo(
    () => diaries.filter((entry) => entry.userId !== targetUserId && relatedUserId(entry) === targetUserId),
    [diaries, targetUserId],
  );

  const memberLifeRecords = useMemo(
    () => [...targetDiaries, ...relatedDiaries]
      .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()),
    [relatedDiaries, targetDiaries],
  );

  const targetGrowthRecords = useMemo(
    () => growthRecords.filter((record) => record.targetUserId === targetUserId),
    [growthRecords, targetUserId],
  );

  const level = useMemo(
    () => completenessLevel(memberLifeRecords, targetGrowthRecords, mirrorContext),
    [memberLifeRecords, mirrorContext, targetGrowthRecords],
  );

  const mirrorMemoryCount = mirrorContext?.memories?.length ?? memories.length;
  const mirrorDiaryCount = mirrorContext?.diaries?.length ?? targetDiaries.length;
  const coreMemories = useMemo(() => {
    const byId = new Map<number, MemoryEntry>();
    [...memories, ...(mirrorContext?.memories || [])].forEach((memory) => {
      if (!isCoreMemory(memory)) return;
      byId.set(memory.id, { ...byId.get(memory.id), ...memory });
    });
    return [...byId.values()].sort((a, b) => {
      const aTime = metadataText(a, 'promotedAt') || a.updatedAt || a.createdAt;
      const bTime = metadataText(b, 'promotedAt') || b.updatedAt || b.createdAt;
      return new Date(bTime).getTime() - new Date(aTime).getTime();
    });
  }, [memories, mirrorContext?.memories]);
  const relatedParams = targetUserId
    ? `&relatedUserId=${targetUserId}&relatedMemberName=${encodeURIComponent(memberName(targetMember))}`
    : '';

  if (loadingFamilies) {
    return (
      <div className="flex h-60 items-center justify-center text-gray-400">
        <RefreshCw className="mr-2 h-5 w-5 animate-spin" />
        加载中...
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
    <div className="mx-auto w-full max-w-7xl">
      <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <Link href="/dashboard/family" className="mb-2 inline-flex items-center gap-1 text-xs text-gray-500 hover:text-blue-600">
            <ArrowLeft className="h-3.5 w-3.5" />
            返回家族空间
          </Link>
          <h1 className="text-xl font-bold text-gray-900">成员记忆视图</h1>
          <p className="mt-1 text-sm text-gray-500">查看某位成员在当前家族中的可见记录、成长观察与镜像资料完整度。</p>
        </div>
        <div className="grid gap-2 sm:grid-cols-1">
          <select
            value={targetUserId ?? ''}
            onChange={(event) => {
              setTargetUserId(Number(event.target.value) || null);
              setMirrorContext(null);
            }}
            className="h-10 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
          >
            {members.map((member) => (
              <option key={member.userId} value={member.userId}>{memberName(member)}</option>
            ))}
          </select>
        </div>
      </div>

      {error && <div className="mb-4 rounded-lg bg-red-50 px-4 py-2 text-sm text-red-600">{error}</div>}
      {loadingData && (
        <div className="mb-4 flex items-center rounded-lg border border-gray-200 bg-white px-4 py-3 text-sm text-gray-400">
          <Loader2 className="mr-2 h-4 w-4 animate-spin" />
          正在整理成员记忆...
        </div>
      )}

      <section className="mb-4 rounded-xl border border-gray-200 bg-white p-4 sm:p-5">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex min-w-0 items-center gap-3">
            <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-xl bg-purple-50 text-lg font-bold text-purple-700">
              {memberName(targetMember).charAt(0)}
            </div>
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <h2 className="truncate text-lg font-semibold text-gray-900">{memberName(targetMember)}</h2>
                {targetMember && (
                  <span className="rounded-full bg-gray-100 px-2 py-0.5 text-[11px] font-medium text-gray-600">
                    {familyRoleLabel(targetMember.role)}
                  </span>
                )}
                <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${level.className}`}>
                  {level.label}
                </span>
              </div>
              <p className="mt-1 text-sm text-gray-500">
                {selectedFamily?.name || '当前家族'}
                {targetMember?.relationshipLabel?.trim() ? ` · 账号：${accountName(targetMember)}` : ''}
              </p>
              <p className="mt-1 text-xs text-gray-400">{memberProfileLine(targetMember)}</p>
              <p className="mt-1 text-xs text-gray-400">{level.hint}</p>
            </div>
          </div>
          <div className="grid grid-cols-4 gap-2 text-center">
            <div className="rounded-lg bg-rose-50 px-3 py-2">
              <p className="text-lg font-bold text-rose-700">{memberLifeRecords.length}</p>
              <p className="text-[11px] text-rose-600">人生记录</p>
            </div>
            <div className="rounded-lg bg-emerald-50 px-3 py-2">
              <p className="text-lg font-bold text-emerald-700">{targetGrowthRecords.length}</p>
              <p className="text-[11px] text-emerald-600">成长观察</p>
            </div>
            <div className="rounded-lg bg-amber-50 px-3 py-2">
              <p className="text-lg font-bold text-amber-700">{mirrorMemoryCount}</p>
              <p className="text-[11px] text-amber-600">可见经验</p>
            </div>
            <div className="rounded-lg bg-purple-50 px-3 py-2">
              <p className="text-lg font-bold text-purple-700">{hasMirrorProfile(mirrorContext) ? '有' : '无'}</p>
              <p className="text-[11px] text-purple-600">镜像画像</p>
            </div>
          </div>
        </div>
      </section>

      <section className="mb-4 grid grid-cols-1 gap-2 sm:grid-cols-2 xl:grid-cols-5">
        <Link
          href={`/dashboard/mirror${selectedFamilyId && targetUserId ? `?familyId=${selectedFamilyId}&targetUserId=${targetUserId}` : ''}`}
          className="flex min-h-20 flex-col justify-between rounded-lg bg-purple-50 p-3 text-purple-700 hover:bg-purple-100"
        >
          <Bot className="h-5 w-5" />
          <span className="text-sm font-semibold">进入镜像 Agent</span>
        </Link>
        <Link
          href={`/dashboard/diary?template=choice${selectedFamilyId ? `&familyId=${selectedFamilyId}` : ''}${relatedParams}`}
          className="flex min-h-20 flex-col justify-between rounded-lg bg-blue-50 p-3 text-blue-700 hover:bg-blue-100"
        >
          <BookHeart className="h-5 w-5" />
          <span className="text-sm font-semibold">补重要选择</span>
        </Link>
        <Link
          href={`/dashboard/diary?template=family-message${selectedFamilyId ? `&familyId=${selectedFamilyId}` : ''}${relatedParams}`}
          className="flex min-h-20 flex-col justify-between rounded-lg bg-rose-50 p-3 text-rose-700 hover:bg-rose-100"
        >
          <Sparkles className="h-5 w-5" />
          <span className="text-sm font-semibold">补家人留言</span>
        </Link>
        <Link
          href={`/dashboard/growth?category=VISION${targetUserId ? `&targetUserId=${targetUserId}` : ''}${selectedFamilyId ? `&familyId=${selectedFamilyId}` : ''}`}
          className="flex min-h-20 flex-col justify-between rounded-lg bg-emerald-50 p-3 text-emerald-700 hover:bg-emerald-100"
        >
          <HeartPulse className="h-5 w-5" />
          <span className="text-sm font-semibold">补成长观察</span>
        </Link>
        <Link
          href={`/dashboard/heritage?type=ELDER_ADVICE&scenario=${encodeURIComponent(memberName(targetMember))}${selectedFamilyId ? `&familyId=${selectedFamilyId}` : ''}`}
          className="flex min-h-20 flex-col justify-between rounded-lg bg-amber-50 p-3 text-amber-700 hover:bg-amber-100"
        >
          <ScrollText className="h-5 w-5" />
          <span className="text-sm font-semibold">补相关经验</span>
        </Link>
      </section>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[1.1fr_0.9fr]">
        <section className="rounded-xl border border-purple-100 bg-white p-4 sm:p-5 lg:col-span-2">
          <div className="mb-3 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h2 className="text-sm font-semibold text-gray-900">沉淀记忆</h2>
              <p className="mt-1 text-xs text-gray-500">这些内容被家族成员主动保留下来，适合作为长期经验、家风或价值观参考。</p>
            </div>
            <Link
              href={`/dashboard/heritage${selectedFamilyId ? `?familyId=${selectedFamilyId}` : ''}`}
              className="text-xs text-purple-700 hover:underline"
            >
              查看家族经验
            </Link>
          </div>
          {coreMemories.length === 0 ? (
            <div className="rounded-lg border border-dashed border-purple-100 p-6 text-center">
              <ScrollText className="mx-auto mb-2 h-8 w-8 text-purple-200" />
              <p className="text-sm text-gray-500">还没有核心记忆。可以从家族日记详情里选择“核心记忆”，写下保留原因。</p>
            </div>
          ) : (
            <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
              {coreMemories.slice(0, 3).map((memory) => {
                const coreReason = metadataText(memory, 'coreReason');
                const promotedBy = metadataText(memory, 'promotedByName');
                const promotedAt = metadataText(memory, 'promotedAt');
                const sourceDiaryId = metadataNumber(memory, 'sourceDiaryId') || metadataNumber(memory, 'promotedFromDiaryId');
                return (
                  <article key={memory.id} className="rounded-lg border border-purple-100 bg-purple-50/40 p-4">
                    <div className="mb-2 flex flex-wrap items-center gap-2">
                      <span className="text-sm font-semibold text-gray-900">{memoryTitle(memory)}</span>
                      <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${temporalLayerClass(memory)}`}>
                        {temporalLayerLabel(memory)}
                      </span>
                    </div>
                    <p className="line-clamp-3 text-sm leading-6 text-gray-700">{memory.summary || memory.content}</p>
                    {coreReason && (
                      <div className="mt-3 rounded-lg bg-white p-3">
                        <p className="text-[11px] font-medium text-purple-700">保留原因</p>
                        <p className="mt-1 text-xs leading-5 text-gray-600">{coreReason}</p>
                      </div>
                    )}
                    <div className="mt-3 flex flex-wrap items-center gap-2 text-[11px] text-gray-400">
                      {promotedBy && <span>沉淀人：{promotedBy}</span>}
                      <span>{shortDate(promotedAt || memory.updatedAt || memory.createdAt)}</span>
                      {sourceDiaryId && (
                        <Link href="/dashboard/diary" className="text-purple-700 hover:underline">
                          来源日记 #{sourceDiaryId}
                        </Link>
                      )}
                    </div>
                  </article>
                );
              })}
            </div>
          )}
        </section>

        <section className="rounded-xl border border-gray-200 bg-white p-4 sm:p-5">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-sm font-semibold text-gray-900">最近人生记录</h2>
            <span className="text-xs text-gray-400">后端权限过滤后可见</span>
          </div>
          <div className="space-y-3">
            {memberLifeRecords.slice(0, 3).map((entry) => {
              const isRelated = entry.userId !== targetUserId && relatedUserId(entry) === targetUserId;
              return (
              <article key={entry.id} className="rounded-lg border border-gray-100 p-3">
                <div className="mb-1 flex flex-wrap items-center gap-2">
                  <BookHeart className="h-4 w-4 text-rose-500" />
                  <span className="truncate text-sm font-medium text-gray-900">{diaryTitle(entry)}</span>
                  {isRelated && (
                    <span className="rounded-full bg-purple-50 px-2 py-0.5 text-[11px] font-medium text-purple-700">
                      他人补充
                    </span>
                  )}
                  <span className="rounded-full bg-gray-100 px-2 py-0.5 text-[11px] text-gray-500">{entry.visibility}</span>
                </div>
                <p className="line-clamp-3 text-sm leading-6 text-gray-600">{entry.rawText}</p>
                <div className="mt-2 flex flex-wrap gap-2 text-[11px] text-gray-400">
                  <span>{shortDate(entry.createdAt)}</span>
                  {(entry.tags || []).slice(0, 4).map((tag) => <span key={tag}>#{tag}</span>)}
                </div>
              </article>
              );
            })}
            {memberLifeRecords.length === 0 && (
              <div className="rounded-lg border border-dashed border-gray-200 p-8 text-center text-sm text-gray-400">
                暂无该成员本人或相关可见人生记录。
              </div>
            )}
          </div>
        </section>

        <section className="rounded-xl border border-gray-200 bg-white p-4 sm:p-5">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-sm font-semibold text-gray-900">镜像可引用来源</h2>
            <span className="text-xs text-gray-400">{mirrorDiaryCount} 条日记 · {mirrorMemoryCount} 条经验</span>
          </div>
          <div className="rounded-lg bg-gray-50 p-3 text-xs leading-5 text-gray-500">
            {mirrorContext?.sourceSummary || '镜像上下文会基于授权记录生成。若此处为空，说明可引用资料不足或当前用户权限有限。'}
          </div>
          <div className="mt-3 space-y-2">
            {(mirrorContext?.diaries || []).slice(0, 3).map((entry, index) => (
              <div key={`md-${entry.id}`} className="rounded-lg border border-gray-100 p-2">
                <div className="mb-1 flex items-center gap-1.5">
                  <span className={`rounded px-1.5 py-0.5 text-[10px] font-semibold ${
                    isRelatedDiary(entry) ? 'bg-purple-50 text-purple-700' : 'bg-blue-50 text-blue-700'
                  }`}
                  >
                    {diarySourceCode(entry, index)}
                  </span>
                  <span className="truncate text-xs font-medium text-gray-800">{diaryTitle(entry)}</span>
                </div>
                <p className="mt-1 line-clamp-2 text-xs leading-5 text-gray-500">{entry.rawText}</p>
                <span className={`mt-1 inline-flex rounded px-1.5 py-0.5 text-[10px] font-medium ${temporalLayerClass(entry)}`}>
                  {temporalLayerLabel(entry)}
                </span>
              </div>
            ))}
            {(mirrorContext?.memories || []).slice(0, 3).map((memory, index) => (
              <div key={`mm-${memory.id}`} className="rounded-lg border border-gray-100 p-2">
                <p className="truncate text-xs font-medium text-gray-800">M{index + 1} · {memoryTitle(memory)}</p>
                <p className="mt-1 line-clamp-2 text-xs leading-5 text-gray-500">{memory.content}</p>
                <span className={`mt-1 inline-flex rounded px-1.5 py-0.5 text-[10px] font-medium ${temporalLayerClass(memory)}`}>
                  {temporalLayerLabel(memory)}
                </span>
              </div>
            ))}
            {!mirrorContext && (
              <div className="rounded-lg border border-dashed border-gray-200 p-5 text-center text-xs text-gray-400">
                暂未取得镜像上下文。
              </div>
            )}
          </div>
        </section>

        <section className="rounded-xl border border-gray-200 bg-white p-4 sm:p-5">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-sm font-semibold text-gray-900">成长观察</h2>
            <Link href={`/dashboard/growth${selectedFamilyId ? `?familyId=${selectedFamilyId}` : ''}`} className="text-xs text-blue-600 hover:underline">
              查看全部
            </Link>
          </div>
          <div className="space-y-3">
            {targetGrowthRecords.slice(0, 3).map((record) => (
              <article key={record.id} className="rounded-lg border border-gray-100 p-3">
                <div className="mb-1 flex flex-wrap items-center gap-2">
                  <HeartPulse className="h-4 w-4 text-emerald-500" />
                  <span className="rounded-full bg-emerald-50 px-2 py-0.5 text-[11px] font-medium text-emerald-700">
                    {categoryLabel(record.category)}
                  </span>
                  <span className="rounded-full bg-gray-100 px-2 py-0.5 text-[11px] text-gray-500">{followUpLabel(record)}</span>
                </div>
                <p className="line-clamp-3 text-sm leading-6 text-gray-600">{record.content}</p>
                <div className="mt-2 flex flex-wrap gap-2 text-[11px] text-gray-400">
                  <span>观察：{shortDate(record.observedAt)}</span>
                  {record.followUpAt && <span>跟进：{shortDate(record.followUpAt)}</span>}
                  <span>严重度：{record.severity}/5</span>
                </div>
              </article>
            ))}
            {targetGrowthRecords.length === 0 && (
              <div className="rounded-lg border border-dashed border-gray-200 p-8 text-center text-sm text-gray-400">
                暂无该成员成长观察。
              </div>
            )}
          </div>
        </section>

        <section className="rounded-xl border border-gray-200 bg-white p-4 sm:p-5">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-sm font-semibold text-gray-900">关系与权限提示</h2>
            <Shield className="h-4 w-4 text-gray-400" />
          </div>
          <div className="space-y-3 text-sm leading-6 text-gray-600">
            <div className="rounded-lg bg-gray-50 p-3">
              <p className="text-xs font-medium text-gray-500">我对 TA 的称呼</p>
              <p className="mt-1 font-medium text-gray-900">{targetMember?.relationshipLabel?.trim() || '尚未设置'}</p>
            </div>
            <div className="rounded-lg bg-gray-50 p-3">
              <p className="text-xs font-medium text-gray-500">TA 在此家族中的身份</p>
              <p className="mt-1 font-medium text-gray-900">{targetMember ? familyRoleLabel(targetMember.role) : '未知'}</p>
            </div>
            <div className="rounded-lg border border-yellow-100 bg-yellow-50 p-3 text-xs leading-5 text-yellow-700">
              成员记忆只展示后端允许当前用户查看的数据。镜像 Agent 只能作为风格和经验参考，不能代表本人真实承诺或实时想法。
            </div>
            <Link
              href="/dashboard/family"
              className="inline-flex h-9 items-center gap-2 rounded-lg border border-gray-200 px-3 text-xs font-medium text-gray-600 hover:bg-gray-50"
            >
              <UserRound className="h-3.5 w-3.5" />
              调整称呼或授权
            </Link>
          </div>
        </section>
      </div>

      <section className="mt-4 rounded-xl border border-gray-200 bg-white p-4 sm:p-5">
        <div className="mb-3 flex items-center gap-2">
          <CheckCircle className="h-4 w-4 text-green-600" />
          <h2 className="text-sm font-semibold text-gray-900">下一步补全建议</h2>
        </div>
        <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
          <p className="rounded-lg bg-gray-50 p-3 text-xs leading-5 text-gray-500">补 2-3 条关键选择：让 AI 理解 TA 遇事时如何判断取舍。</p>
          <p className="rounded-lg bg-gray-50 p-3 text-xs leading-5 text-gray-500">补 1 条给家人的话：让镜像参考更接近真实表达方式。</p>
          <p className="rounded-lg bg-gray-50 p-3 text-xs leading-5 text-gray-500">补 1 条成长观察：让家庭建议能连接到具体生活细节。</p>
        </div>
      </section>
    </div>
  );
}
