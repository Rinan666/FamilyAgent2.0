'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import {
  AlertTriangle,
  Archive,
  BookHeart,
  CheckCircle,
  HeartPulse,
  Loader2,
  RefreshCw,
  RotateCcw,
  ScrollText,
  Search,
  Shield,
  ThumbsDown,
  ThumbsUp,
  Trash2,
  X,
} from 'lucide-react';
import { familyApi, growthGuardApi, memoryApi, memoryLibraryApi } from '@/lib/api';
import LegacyWorkpageNotice from '@/components/family/LegacyWorkpageNotice';
import { useViewerRole } from '@/hooks/useViewerRole';
import type {
  FamilyMember,
  MemoryLibraryItem,
  MemoryLibraryItemType,
  MemoryMaintenanceSuggestion,
  PageResult,
} from '@/types';

type LibraryItemType = MemoryLibraryItemType;
type LibraryViewMode = 'ACTIVE' | 'ARCHIVED';

const pageSizeOptions = [6, 12, 24];
const visibilityOptions = ['PRIVATE', 'FAMILY_VISIBLE', 'CARE_VISIBLE', 'LEGACY_VISIBLE'];

const typeOptions: { value: LibraryItemType | 'ALL'; label: string }[] = [
  { value: 'ALL', label: '全部类型' },
  { value: 'LIFE_RECORD', label: '记录' },
  { value: 'FAMILY_EXPERIENCE', label: '经验' },
  { value: 'GROWTH_OBSERVATION', label: '观察' },
  { value: 'AI_SUMMARY', label: 'AI 摘要' },
];

const typeMeta: Record<LibraryItemType, {
  label: string;
  icon: typeof BookHeart;
  badge: string;
  tone: string;
}> = {
  LIFE_RECORD: {
    label: '记录',
    icon: BookHeart,
    badge: 'bg-rose-50 text-rose-700',
    tone: 'text-rose-700 bg-rose-50',
  },
  FAMILY_EXPERIENCE: {
    label: '经验',
    icon: ScrollText,
    badge: 'bg-amber-50 text-amber-700',
    tone: 'text-amber-700 bg-amber-50',
  },
  GROWTH_OBSERVATION: {
    label: '观察',
    icon: HeartPulse,
    badge: 'bg-emerald-50 text-emerald-700',
    tone: 'text-emerald-700 bg-emerald-50',
  },
  AI_SUMMARY: {
    label: 'AI 摘要',
    icon: ScrollText,
    badge: 'bg-violet-50 text-violet-700',
    tone: 'text-violet-700 bg-violet-50',
  },
};

function emptyPage(pageSize: number): PageResult<MemoryLibraryItem> {
  return { items: [], page: 1, pageSize, total: 0, totalPages: 0 };
}

function emptyCounts(): Record<LibraryItemType, number | null> {
  return {
    LIFE_RECORD: null,
    FAMILY_EXPERIENCE: null,
    GROWTH_OBSERVATION: null,
    AI_SUMMARY: null,
  };
}

function parseNumericId(itemId: string | undefined, prefix: string) {
  if (!itemId || !itemId.startsWith(`${prefix}-`)) return null;
  const value = Number(itemId.slice(prefix.length + 1));
  return Number.isFinite(value) && value > 0 ? value : null;
}

function metadataObject(item: MemoryLibraryItem, key: string) {
  const value = item.metadata?.[key];
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  return value as Record<string, unknown>;
}

function metadataText(item: MemoryLibraryItem, key: string) {
  const value = item.metadata?.[key];
  return typeof value === 'string' ? value.trim() : '';
}

function metadataBoolean(item: MemoryLibraryItem, key: string) {
  return item.metadata?.[key] === true || item.metadata?.[key] === 'true';
}

function visibilityLabel(value?: string) {
  const text = String(value || '').toUpperCase();
  if (text === 'PRIVATE') return '仅自己可见';
  if (text === 'CARE_VISIBLE' || text === 'PARENT_VISIBLE') return '照护可见';
  if (text === 'LEGACY_VISIBLE') return '传承预留';
  if (text === 'FAMILY_VISIBLE' || text === 'FAMILY') return '全家可见';
  return value || '按权限可见';
}

function formatDate(value?: string) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: 'numeric', day: 'numeric' });
}

function sourceHref(item: MemoryLibraryItem, familyId?: number | null) {
  const params = new URLSearchParams();
  if (familyId) params.set('familyId', String(familyId));
  if (item.memberUserId) params.set('relatedUserId', String(item.memberUserId));
  if (item.sourceType === 'GROWTH_OBSERVATION') {
    params.set('writeCategory', 'OBSERVATION');
    if (item.type) params.set('growthCategory', item.type);
    return `/dashboard/diary?${params.toString()}`;
  }
  if (item.sourceType === 'FAMILY_EXPERIENCE') {
    params.set('writeCategory', 'EXPERIENCE');
    params.set('memoryType', item.type || 'ELDER_ADVICE');
    return `/dashboard/diary?${params.toString()}`;
  }
  return `/dashboard/diary${params.toString() ? `?${params.toString()}` : ''}`;
}

function canVoteFamilyExperience(item: MemoryLibraryItem) {
  return item.sourceType === 'FAMILY_EXPERIENCE' && item.id.startsWith('memory-');
}

function originLabel(item: MemoryLibraryItem) {
  const source = metadataText(item, 'source');
  if (source === 'FAMILY_COMPANION_TOOL') return '家族助手对话保存';
  if (source === 'MIRROR_AGENT_TOOL') return '镜像助手对话保存';
  if (source === 'FAMILY_WEEKLY_DIGEST') return 'AI 家庭周报生成';
  if (source.includes('DIGEST') || source.includes('SUMMARY')) return 'AI 摘要生成';
  if (item.sourceType === 'AI_SUMMARY') return 'AI 摘要生成';
  return '来源页面保存';
}

function evidenceDescription(item: MemoryLibraryItem) {
  if (item.sourceType === 'LIFE_RECORD') return '来自记录页，保留事件经过、当时语境和个人感受。';
  if (item.sourceType === 'FAMILY_EXPERIENCE') return '来自经验沉淀，适合长期复用和传承。';
  if (item.sourceType === 'GROWTH_OBSERVATION') return '来自观察记录，适合照护跟进和后续复核。';
  return '来自 AI 生成摘要，适合快速定位上下文。';
}

function itemSignals(item: MemoryLibraryItem) {
  const tags = new Set<string>();
  (item.tags || []).forEach((tag) => tags.add(tag));
  if (item.memberName) tags.add(item.memberName);
  const scenario = metadataText(item, 'scenario');
  const followUpStatus = metadataText(item, 'followUpStatus');
  if (scenario) tags.add(scenario);
  if (followUpStatus) tags.add(followUpStatus);
  return Array.from(tags).slice(0, 6);
}

function assetStatus(item: MemoryLibraryItem) {
  const missing = missingInfoLabels(item);
  if (metadataBoolean(item, 'coreMemory') || item.tags?.includes('核心记忆')) {
    return { label: '核心记忆', tone: 'bg-violet-50 text-violet-700' };
  }
  if (missing.length === 0) return { label: '信息完整', tone: 'bg-green-50 text-green-700' };
  if (missing.length <= 2) return { label: '可补充', tone: 'bg-yellow-50 text-yellow-700' };
  return { label: '待整理', tone: 'bg-gray-100 text-gray-600' };
}

function missingInfoLabels(item: MemoryLibraryItem) {
  const labels: string[] = [];
  if (!item.title?.trim()) labels.push('缺标题');
  if (!item.body?.trim() || item.body.trim().length < 30) labels.push('缺背景');
  if (!item.tags || item.tags.length === 0) labels.push('缺标签');
  if (!item.memberName?.trim()) labels.push('缺成员');
  if (item.sourceType === 'FAMILY_EXPERIENCE' && !metadataText(item, 'scenario')) labels.push('缺场景');
  if (item.sourceType === 'GROWTH_OBSERVATION' && !metadataText(item, 'followUpStatus')) labels.push('缺跟进状态');
  return labels.slice(0, 4);
}

function familyExperienceVoteStats(item: MemoryLibraryItem) {
  const stats = metadataObject(item, 'voteStats');
  return {
    upVotes: typeof stats?.upVotes === 'number' ? stats.upVotes : Number(stats?.upVotes || 0),
    downVotes: typeof stats?.downVotes === 'number' ? stats.downVotes : Number(stats?.downVotes || 0),
    voteScore: typeof stats?.voteScore === 'number' ? stats.voteScore : Number(stats?.voteScore || 0),
    myVote: typeof stats?.myVote === 'string' ? stats.myVote.toUpperCase() : '',
  };
}

function growthObservationStalenessStats(item: MemoryLibraryItem) {
  const stats = metadataObject(item, 'stalenessStats');
  return {
    staleVotes: typeof stats?.staleVotes === 'number' ? stats.staleVotes : Number(stats?.staleVotes || 0),
    myVoted: stats?.myVoted === true || stats?.myVoted === 'true',
  };
}

function memberDisplayName(member?: FamilyMember) {
  if (!member) return '家庭成员';
  return member.relationshipLabel?.trim()
    || member.nickname?.trim()
    || member.username?.trim()
    || `用户 ${member.userId}`;
}

function maintenanceMeta(action?: string) {
  if (action === 'MERGE_REVIEW') {
    return { label: '建议合并', tone: 'border-blue-100 bg-blue-50 text-blue-700' };
  }
  if (action === 'DELETE_REVIEW') {
    return { label: '待清理', tone: 'border-red-100 bg-red-50 text-red-700' };
  }
  return { label: '建议归档', tone: 'border-amber-100 bg-amber-50 text-amber-700' };
}

let cachedMembersByFamilyId: Record<number, FamilyMember[]> = {};
let cachedCountsByFamilyId: Record<number, Record<LibraryItemType, number | null>> = {};

function invalidateMemoryLibraryPageCache(familyId?: number | null) {
  if (!familyId) return;
  delete cachedMembersByFamilyId[familyId];
  delete cachedCountsByFamilyId[familyId];
}

export default function MemoryLibraryPage({ embedded = false }: { embedded?: boolean }) {
  const searchParams = useSearchParams();
  const {
    families,
    activeFamilyId,
    activeFamily,
    activeMembership,
    viewerRole,
    setActiveFamilyId,
    isLoading: loadingFamilies,
  } = useViewerRole();

  const [members, setMembers] = useState<FamilyMember[]>([]);
  const [pageData, setPageData] = useState<PageResult<MemoryLibraryItem>>(() => emptyPage(pageSizeOptions[0]));
  const [counts, setCounts] = useState<Record<LibraryItemType, number | null>>(() => emptyCounts());
  const [maintenanceSuggestions, setMaintenanceSuggestions] = useState<MemoryMaintenanceSuggestion[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [loadingMaintenance, setLoadingMaintenance] = useState(false);
  const [maintenanceLoaded, setMaintenanceLoaded] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [query, setQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [typeFilter, setTypeFilter] = useState<LibraryItemType | 'ALL'>('ALL');
  const [memberFilter, setMemberFilter] = useState('ALL');
  const [visibilityFilter, setVisibilityFilter] = useState('ALL');
  const [tagFilter, setTagFilter] = useState('');
  const [debouncedTagFilter, setDebouncedTagFilter] = useState('');
  const [dateFrom, setDateFrom] = useState('');
  const [debouncedDateFrom, setDebouncedDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [debouncedDateTo, setDebouncedDateTo] = useState('');
  const [viewMode, setViewMode] = useState<LibraryViewMode>('ACTIVE');
  const [pageSize, setPageSize] = useState(pageSizeOptions[0]);
  const [currentPage, setCurrentPage] = useState(1);
  const [selectedItemId, setSelectedItemId] = useState('');
  const [mergingSuggestionKey, setMergingSuggestionKey] = useState('');
  const [archivingItemId, setArchivingItemId] = useState('');
  const [restoringItemId, setRestoringItemId] = useState('');
  const [deletingItemId, setDeletingItemId] = useState('');
  const [votingActionKey, setVotingActionKey] = useState('');
  const [staleActionItemId, setStaleActionItemId] = useState('');

  const requestedFamilyId = useMemo(() => {
    const value = Number(searchParams.get('familyId'));
    return Number.isFinite(value) && value > 0 ? value : null;
  }, [searchParams]);

  const requestedItemId = useMemo(
    () => searchParams.get('itemId')?.trim() || '',
    [searchParams],
  );

  const canManageLibrary = activeMembership?.role === 'OWNER' || viewerRole === 'ADMIN';
  const canLoadSummaryCounts = useMemo(() => (
    viewMode === 'ACTIVE'
    && memberFilter === 'ALL'
    && visibilityFilter === 'ALL'
    && !debouncedQuery
    && !debouncedTagFilter
    && !debouncedDateFrom
    && !debouncedDateTo
  ), [
    debouncedDateFrom,
    debouncedDateTo,
    debouncedQuery,
    debouncedTagFilter,
    memberFilter,
    viewMode,
    visibilityFilter,
  ]);

  const memberOptions = useMemo(
    () => members.map((member) => ({ id: member.userId, name: memberDisplayName(member) })),
    [members],
  );

  const selectedItem = useMemo(() => (
    pageData.items.find((item) => item.id === selectedItemId)
    || maintenanceSuggestions.flatMap((item) => item.items || []).find((item) => item.id === selectedItemId)
    || null
  ), [maintenanceSuggestions, pageData.items, selectedItemId]);

  const pageStart = pageData.total === 0 ? 0 : (pageData.page - 1) * pageData.pageSize + 1;
  const pageEnd = Math.min(pageData.page * pageData.pageSize, pageData.total);
  const totalPages = Math.max(1, pageData.totalPages || 1);

  const loadCounts = useCallback(async (familyId: number, forceRefresh: boolean = false) => {
    if (!forceRefresh) {
      const cachedCounts = cachedCountsByFamilyId[familyId];
      if (cachedCounts) {
        setCounts(cachedCounts);
        return;
      }
    }

    const nextCounts = await Promise.all(
      typeOptions
        .filter((option) => option.value !== 'ALL')
        .map(async (option) => {
          const result = await memoryLibraryApi.search({
            familyId,
            page: 1,
            pageSize: 1,
            type: option.value as LibraryItemType,
          });
          return [option.value, result.total] as const;
        }),
    );
    const nextRecord = Object.fromEntries(nextCounts) as Record<LibraryItemType, number | null>;
    cachedCountsByFamilyId[familyId] = nextRecord;
    setCounts(nextRecord);
  }, []);

  const loadMembers = useCallback(async (familyId: number, forceRefresh: boolean = false) => {
    if (!forceRefresh) {
      const cachedMembers = cachedMembersByFamilyId[familyId];
      if (cachedMembers) {
        setMembers(cachedMembers);
        return;
      }
    }

    const memberList = await familyApi.getMembers(familyId).catch(() => [] as FamilyMember[]);
    const nextMembers = Array.isArray(memberList) ? memberList : [];
    cachedMembersByFamilyId[familyId] = nextMembers;
    setMembers(nextMembers);
  }, []);

  const loadData = useCallback(async () => {
    if (!activeFamilyId) {
      setPageData(emptyPage(pageSize));
      setCounts(emptyCounts());
      setMaintenanceSuggestions([]);
      setMaintenanceLoaded(false);
      return;
    }

    const memberUserId = memberFilter === 'ALL' ? undefined : Number(memberFilter);
    const visibility = visibilityFilter === 'ALL' ? undefined : visibilityFilter;
    const tag = debouncedTagFilter || undefined;

    setIsLoading(true);
    setError('');
    try {
      const nextPage = await (viewMode === 'ARCHIVED' ? memoryLibraryApi.archived : memoryLibraryApi.search)({
        familyId: activeFamilyId,
        page: currentPage,
        pageSize,
        keyword: debouncedQuery,
        type: typeFilter,
        memberUserId,
        visibility,
        tag,
        dateFrom: debouncedDateFrom || undefined,
        dateTo: debouncedDateTo || undefined,
      });
      setPageData(nextPage);
      setSelectedItemId((current) => (
        current && nextPage.items.some((item) => item.id === current) ? current : ''
      ));
    } catch (err) {
      setError(err instanceof Error ? err.message : '记忆库加载失败');
      setPageData(emptyPage(pageSize));
    } finally {
      setIsLoading(false);
    }
  }, [
    activeFamilyId,
    currentPage,
    debouncedDateFrom,
    debouncedDateTo,
    debouncedQuery,
    debouncedTagFilter,
    memberFilter,
    pageSize,
    typeFilter,
    viewMode,
    visibilityFilter,
  ]);

  const loadMaintenanceSuggestions = useCallback(async () => {
    if (!activeFamilyId || viewMode !== 'ACTIVE') {
      setMaintenanceSuggestions([]);
      setMaintenanceLoaded(false);
      return;
    }
    setLoadingMaintenance(true);
    try {
      const suggestions = await memoryLibraryApi.maintenanceSuggestions(activeFamilyId);
      setMaintenanceSuggestions(Array.isArray(suggestions) ? suggestions : []);
      setMaintenanceLoaded(true);
    } catch {
      setMaintenanceSuggestions([]);
      setMaintenanceLoaded(true);
    } finally {
      setLoadingMaintenance(false);
    }
  }, [activeFamilyId, viewMode]);

  const resetFilters = useCallback(() => {
    setQuery('');
    setDebouncedQuery('');
    setTypeFilter('ALL');
    setMemberFilter('ALL');
    setVisibilityFilter('ALL');
    setTagFilter('');
    setDateFrom('');
    setDateTo('');
    setCurrentPage(1);
    setSelectedItemId('');
  }, []);

  useEffect(() => {
    const nextFamilyId = (
      requestedFamilyId && families.some((family) => family.id === requestedFamilyId)
        ? requestedFamilyId
        : activeFamilyId && families.some((family) => family.id === activeFamilyId)
          ? activeFamilyId
          : families[0]?.id
    ) || null;
    if (nextFamilyId && activeFamilyId !== nextFamilyId) {
      setActiveFamilyId(nextFamilyId);
    }
  }, [activeFamilyId, families, requestedFamilyId, setActiveFamilyId]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedQuery(query.trim());
      setCurrentPage(1);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [query]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedTagFilter(tagFilter.trim());
      setDebouncedDateFrom(dateFrom);
      setDebouncedDateTo(dateTo);
      setCurrentPage(1);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [tagFilter, dateFrom, dateTo]);

  useEffect(() => {
    setCurrentPage(1);
  }, [typeFilter, memberFilter, visibilityFilter, tagFilter, dateFrom, dateTo, viewMode, pageSize]);

  useEffect(() => {
    if (!canManageLibrary && viewMode !== 'ACTIVE') {
      setViewMode('ACTIVE');
    }
  }, [canManageLibrary, viewMode]);

  useEffect(() => {
    if (!activeFamilyId) {
      setMembers([]);
      return;
    }

    void loadMembers(activeFamilyId).catch(() => setMembers([]));
  }, [activeFamilyId, loadMembers]);

  useEffect(() => {
    if (!activeFamilyId || !canLoadSummaryCounts) {
      setCounts(emptyCounts());
      return;
    }

    void loadCounts(activeFamilyId).catch(() => setCounts(emptyCounts()));
  }, [activeFamilyId, canLoadSummaryCounts, loadCounts]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  useEffect(() => {
    if (viewMode !== 'ACTIVE' || !canManageLibrary) {
      setMaintenanceSuggestions([]);
      setMaintenanceLoaded(false);
    }
  }, [canManageLibrary, viewMode]);

  useEffect(() => {
    if (!requestedItemId) return;
    if (pageData.items.some((item) => item.id === requestedItemId)) {
      setSelectedItemId(requestedItemId);
    }
  }, [pageData.items, requestedItemId]);

  useEffect(() => {
    if (pageData.totalPages > 0 && currentPage > pageData.totalPages) {
      setCurrentPage(pageData.totalPages);
    }
  }, [currentPage, pageData.totalPages]);

  const refreshAll = useCallback(async () => {
    if (!activeFamilyId) {
      await loadData();
      return;
    }

    invalidateMemoryLibraryPageCache(activeFamilyId);

    await Promise.all([
      loadMembers(activeFamilyId, true),
      loadData(),
      canLoadSummaryCounts ? loadCounts(activeFamilyId, true) : Promise.resolve(),
      maintenanceLoaded ? loadMaintenanceSuggestions() : Promise.resolve(),
    ]);
  }, [activeFamilyId, canLoadSummaryCounts, loadCounts, loadData, loadMaintenanceSuggestions, loadMembers, maintenanceLoaded]);

  const handleMergeSuggestion = useCallback(async (suggestion: MemoryMaintenanceSuggestion) => {
    if (!activeFamilyId) return;
    const [primary, secondary] = (suggestion.items || []).slice(0, 2);
    if (!primary || !secondary) return;
    if (!window.confirm('确认将这两条内容合并为一条更清晰的记忆吗？')) return;
    const key = `${primary.id}-${secondary.id}`;
    setMergingSuggestionKey(key);
    setError('');
    try {
      await memoryLibraryApi.mergeItems(activeFamilyId, primary.id, secondary.id);
      setSelectedItemId(primary.id);
      setSuccess('已完成合并');
      await refreshAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : '合并失败');
    } finally {
      setMergingSuggestionKey('');
    }
  }, [activeFamilyId, refreshAll]);

  const handleArchiveItem = useCallback(async (item: MemoryLibraryItem) => {
    if (!activeFamilyId) return;
    if (!window.confirm('确认归档这条记忆吗？')) return;
    setArchivingItemId(item.id);
    setError('');
    try {
      await memoryLibraryApi.archiveItem(activeFamilyId, item.id);
      setSelectedItemId((current) => (current === item.id ? '' : current));
      setSuccess('已归档');
      await refreshAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : '归档失败');
    } finally {
      setArchivingItemId('');
    }
  }, [activeFamilyId, refreshAll]);

  const handleRestoreItem = useCallback(async (item: MemoryLibraryItem) => {
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
  }, [activeFamilyId, refreshAll]);

  const handleDeleteArchivedItem = useCallback(async (item: MemoryLibraryItem) => {
    if (!activeFamilyId) return;
    if (!window.confirm('确认永久删除这条已归档记忆吗？删除后无法恢复。')) return;
    setDeletingItemId(item.id);
    setError('');
    try {
      await memoryLibraryApi.deleteArchivedItem(activeFamilyId, item.id);
      setSelectedItemId((current) => (current === item.id ? '' : current));
      setSuccess('已永久删除');
      await refreshAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : '删除失败');
    } finally {
      setDeletingItemId('');
    }
  }, [activeFamilyId, refreshAll]);

  const handleVoteExperience = useCallback(async (item: MemoryLibraryItem, voteType: 'UP' | 'DOWN') => {
    const memoryId = parseNumericId(item.id, 'memory');
    if (!memoryId) return;
    const actionKey = `${item.id}-${voteType}`;
    setVotingActionKey(actionKey);
    setError('');
    try {
      await memoryApi.voteFamilyMemory(memoryId, voteType);
      await refreshAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : '提交反馈失败');
    } finally {
      setVotingActionKey('');
    }
  }, [refreshAll]);

  const handleMarkObservationStale = useCallback(async (item: MemoryLibraryItem) => {
    const recordId = parseNumericId(item.id, 'growth');
    if (!recordId) return;
    setStaleActionItemId(item.id);
    setError('');
    try {
      await growthGuardApi.markStale(recordId);
      await refreshAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : '标记失败');
    } finally {
      setStaleActionItemId('');
    }
  }, [refreshAll]);

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
        <p className="mt-2 text-sm text-gray-500">记忆库会统一收纳记录、经验、观察和 AI 摘要。</p>
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
      {!embedded && <LegacyWorkpageNotice tab="library" label="全部记忆" />}

      <section className="mb-4 rounded-xl border border-gray-200 bg-white p-4 sm:p-5">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <h1 className="text-xl font-bold text-gray-900">{embedded ? '记忆库' : '记忆库'}</h1>
            <p className="mt-1 max-w-2xl text-sm leading-6 text-gray-500">
              统一查看和整理 {activeFamily?.name || '当前家族'} 的记录、经验、观察和 AI 摘要。
            </p>
          </div>
          <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
            {canManageLibrary ? (
              <div className="inline-flex rounded-lg border border-gray-200 bg-gray-50 p-1">
                {([
                  { value: 'ACTIVE', label: '当前记忆', icon: BookHeart },
                  { value: 'ARCHIVED', label: '归档箱', icon: Archive },
                ] as const).map((option) => {
                  const Icon = option.icon;
                  const active = viewMode === option.value;
                  return (
                    <button
                      key={option.value}
                      type="button"
                      onClick={() => {
                        setViewMode(option.value);
                        setSelectedItemId('');
                      }}
                      className={`inline-flex h-8 items-center gap-1.5 rounded-md px-3 text-xs font-medium ${
                        active ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-700'
                      }`}
                    >
                      <Icon className="h-3.5 w-3.5" />
                      {option.label}
                    </button>
                  );
                })}
              </div>
            ) : (
              <div className="inline-flex h-9 items-center rounded-lg bg-gray-50 px-3 text-xs font-medium text-gray-600">
                当前记忆
              </div>
            )}
            <button
              type="button"
              onClick={() => void refreshAll()}
              disabled={isLoading}
              className="inline-flex h-9 items-center justify-center gap-2 rounded-lg border border-gray-200 bg-white px-3 text-sm font-medium text-gray-600 hover:bg-gray-50 disabled:opacity-60"
            >
              <RefreshCw className={`h-4 w-4 ${isLoading ? 'animate-spin' : ''}`} />
              刷新
            </button>
          </div>
        </div>

        <div className="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-4">
          {typeOptions.filter((item) => item.value !== 'ALL').map((option) => {
            const meta = typeMeta[option.value as LibraryItemType];
            const Icon = meta.icon;
            return (
              <button
                key={option.value}
                type="button"
                onClick={() => setTypeFilter(option.value as LibraryItemType)}
                className={`rounded-lg border p-3 text-left transition-colors ${
                  typeFilter === option.value ? 'border-blue-200 bg-blue-50' : 'border-gray-100 bg-gray-50 hover:bg-gray-100'
                }`}
              >
                <Icon className={`mb-2 h-5 w-5 rounded ${meta.tone}`} />
                <p className="text-lg font-bold text-gray-900">
                  {canLoadSummaryCounts ? (counts[option.value as LibraryItemType] ?? '-') : '-'}
                </p>
                <p className="text-xs text-gray-500">{option.label}</p>
              </button>
            );
          })}
        </div>
        {!canLoadSummaryCounts && (
          <p className="mt-2 text-xs text-gray-400">
            当前卡片统计仅在默认筛选下展示，避免筛选时额外触发多次搜索请求。
          </p>
        )}
      </section>

      {error && <div className="mb-4 rounded-lg border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">{error}</div>}
      {success && <div className="mb-4 rounded-lg border border-green-100 bg-green-50 px-4 py-3 text-sm text-green-700">{success}</div>}

      {viewMode === 'ACTIVE' && canManageLibrary && (
        <section className="mb-4 rounded-xl border border-gray-200 bg-white p-4">
          <div className="mb-3 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h2 className="text-sm font-semibold text-gray-900">轻提醒式整理建议</h2>
              <p className="mt-1 text-sm leading-6 text-gray-500">系统只给建议，不会自动合并或自动删除。</p>
            </div>
            <button
              type="button"
              onClick={() => void loadMaintenanceSuggestions()}
              disabled={loadingMaintenance || !activeFamilyId}
              className="inline-flex h-8 items-center justify-center gap-2 rounded-lg border border-gray-200 bg-white px-3 text-xs font-medium text-gray-600 hover:bg-gray-50 disabled:opacity-60"
            >
              <RefreshCw className={`h-3.5 w-3.5 ${loadingMaintenance ? 'animate-spin' : ''}`} />
              重新评估
            </button>
          </div>

          {loadingMaintenance ? (
            <div className="flex h-20 items-center justify-center text-sm text-gray-400">
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              正在评估...
            </div>
          ) : !maintenanceLoaded ? (
            <div className="rounded-lg border border-dashed border-gray-200 px-3 py-2 text-sm text-gray-500">
              点击“重新评估”后再加载整理建议，默认不在进入页面时自动请求。
            </div>
          ) : maintenanceSuggestions.length === 0 ? (
            <div className="rounded-lg border border-green-100 bg-green-50 px-3 py-2 text-sm text-green-700">
              暂无明显需要处理的建议。
            </div>
          ) : (
            <div className="grid grid-cols-1 gap-3 lg:grid-cols-3">
              {maintenanceSuggestions.slice(0, 6).map((suggestion, index) => {
                const meta = maintenanceMeta(suggestion.action);
                const [primary, secondary] = (suggestion.items || []).slice(0, 2);
                const suggestionKey = primary && secondary ? `${primary.id}-${secondary.id}` : '';
                return (
                  <article key={`${suggestion.action}-${index}-${primary?.id || 'item'}`} className={`rounded-lg border p-3 ${meta.tone}`}>
                    <div className="mb-2 flex items-start justify-between gap-2">
                      <div className="flex items-center gap-2">
                        <AlertTriangle className="h-4 w-4" />
                        <span className="text-xs font-semibold">{meta.label}</span>
                      </div>
                      <span className="rounded-full bg-white/80 px-2 py-0.5 text-[11px] font-medium">
                        {suggestion.score} 分
                      </span>
                    </div>
                    <p className="text-sm font-semibold text-gray-900">{suggestion.title}</p>
                    <p className="mt-1 text-xs leading-5 text-gray-600">{suggestion.reason}</p>
                    <div className="mt-2 space-y-2">
                      {(suggestion.items || []).slice(0, 2).map((item) => (
                        <button
                          key={item.id}
                          type="button"
                          onClick={() => setSelectedItemId(item.id)}
                          className="block w-full rounded-lg bg-white/80 px-3 py-2 text-left text-xs text-gray-700 hover:bg-white"
                        >
                          <span className="font-medium">{typeMeta[item.sourceType].label}</span>
                          <span className="ml-2">{item.title}</span>
                        </button>
                      ))}
                    </div>
                    {suggestion.action === 'MERGE_REVIEW' && (
                      <button
                        type="button"
                        onClick={() => void handleMergeSuggestion(suggestion)}
                        disabled={!suggestionKey || mergingSuggestionKey === suggestionKey}
                        className="mt-3 inline-flex h-8 items-center gap-1.5 rounded-lg border border-blue-200 bg-white px-3 text-xs font-medium text-blue-700 hover:bg-blue-50 disabled:opacity-60"
                      >
                        {mergingSuggestionKey === suggestionKey && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
                        确认合并
                      </button>
                    )}
                  </article>
                );
              })}
            </div>
          )}
        </section>
      )}

      <section className="mb-4 rounded-xl border border-gray-200 bg-white p-4">
        <div className="mb-3">
          <h2 className="text-sm font-semibold text-gray-900">搜索与筛选</h2>
          <p className="mt-1 text-sm leading-6 text-gray-500">先缩小范围，再看列表。</p>
        </div>

        <div className="grid grid-cols-1 gap-3 lg:grid-cols-[1.3fr_0.75fr_0.75fr_0.75fr]">
          <label className="relative">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="搜索标题、正文、成员、标签..."
              className="h-10 w-full rounded-lg border border-gray-200 pl-9 pr-3 text-sm outline-none focus:ring-2 focus:ring-blue-500"
            />
          </label>
          <select
            value={typeFilter}
            onChange={(event) => setTypeFilter(event.target.value as LibraryItemType | 'ALL')}
            className="h-10 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
          >
            {typeOptions.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
          <select
            value={memberFilter}
            onChange={(event) => setMemberFilter(event.target.value)}
            className="h-10 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="ALL">全部成员</option>
            {memberOptions.map((member) => (
              <option key={member.id} value={member.id}>{member.name}</option>
            ))}
          </select>
          <select
            value={visibilityFilter}
            onChange={(event) => setVisibilityFilter(event.target.value)}
            className="h-10 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="ALL">全部可见范围</option>
            {visibilityOptions.map((option) => (
              <option key={option} value={option}>{visibilityLabel(option)}</option>
            ))}
          </select>
        </div>

        <div className="mt-3 grid grid-cols-1 gap-3 lg:grid-cols-3">
          <input
            value={tagFilter}
            onChange={(event) => setTagFilter(event.target.value)}
            placeholder="按标签筛选，例如：照护"
            className="h-10 rounded-lg border border-gray-200 px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
          />
          <label className="text-xs font-medium text-gray-500">
            开始时间
            <input
              type="date"
              value={dateFrom}
              onChange={(event) => setDateFrom(event.target.value)}
              className="mt-1 h-10 w-full rounded-lg border border-gray-200 px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
            />
          </label>
          <label className="text-xs font-medium text-gray-500">
            结束时间
            <input
              type="date"
              value={dateTo}
              onChange={(event) => setDateTo(event.target.value)}
              className="mt-1 h-10 w-full rounded-lg border border-gray-200 px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
            />
          </label>
        </div>

        <div className="mt-3 flex flex-wrap items-center gap-2 text-xs text-gray-500">
          <span>共 {pageData.total} 条{viewMode === 'ARCHIVED' ? '归档结果' : '结果'}</span>
          {pageData.total > 0 && <span>当前显示第 {pageStart}-{pageEnd} 条</span>}
          {(query || typeFilter !== 'ALL' || memberFilter !== 'ALL' || visibilityFilter !== 'ALL' || tagFilter || dateFrom || dateTo) && (
            <button type="button" onClick={resetFilters} className="text-blue-600 hover:underline">
              清空筛选
            </button>
          )}
        </div>
      </section>

      <section className="rounded-xl border border-gray-200 bg-white">
        {isLoading ? (
          <div className="flex h-64 items-center justify-center text-gray-400">
            <Loader2 className="mr-2 h-5 w-5 animate-spin" />
            正在整理记忆库...
          </div>
        ) : pageData.items.length === 0 ? (
          <div className="flex h-64 items-center justify-center text-center">
            <div className="text-gray-400">
              <BookHeart className="mx-auto mb-3 h-10 w-10 opacity-40" />
              <p className="text-sm">{viewMode === 'ARCHIVED' ? '归档箱里暂无匹配内容。' : '没有找到匹配的记忆。'}</p>
            </div>
          </div>
        ) : (
          <div className="divide-y divide-gray-100">
            {pageData.items.map((item) => {
              const meta = typeMeta[item.sourceType];
              const Icon = meta.icon;
              const status = assetStatus(item);
              const signals = itemSignals(item);
              const voteStats = familyExperienceVoteStats(item);
              const staleStats = growthObservationStalenessStats(item);
              return (
                <article key={item.id} className="p-4 transition-colors hover:bg-gray-50 sm:p-5">
                  <div className="flex items-start gap-3">
                    <span className={`inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-lg ${meta.tone}`}>
                      <Icon className="h-5 w-5" />
                    </span>
                    <div className="min-w-0 flex-1">
                      <div className="mb-1 flex flex-wrap items-center gap-2">
                        <button
                          type="button"
                          onClick={() => setSelectedItemId(item.id)}
                          className="min-w-0 truncate text-left text-sm font-semibold text-gray-900 hover:text-blue-700"
                        >
                          {item.title}
                        </button>
                        <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${meta.badge}`}>
                          {meta.label}
                        </span>
                        <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${status.tone}`}>
                          {status.label}
                        </span>
                        <span className="inline-flex items-center gap-1 rounded-full bg-gray-100 px-2 py-0.5 text-[11px] text-gray-600">
                          <Shield className="h-3 w-3" />
                          {visibilityLabel(item.visibility)}
                        </span>
                      </div>
                      <p className="line-clamp-2 text-sm leading-6 text-gray-600">{item.body || '暂无正文内容。'}</p>
                      <div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-gray-400">
                        <span>{item.memberName}</span>
                        <span>{formatDate(item.createdAt)}</span>
                        <span className="rounded bg-gray-100 px-2 py-0.5 text-gray-500">{originLabel(item)}</span>
                      </div>
                      {signals.length > 0 && (
                        <div className="mt-2 flex flex-wrap gap-1.5">
                          {signals.map((signal) => (
                            <button
                              key={`${item.id}-${signal}`}
                              type="button"
                              onClick={() => {
                                setTagFilter(signal);
                                setSelectedItemId('');
                              }}
                              className="rounded-full bg-blue-50 px-2 py-0.5 text-[11px] text-blue-700 hover:bg-blue-100"
                            >
                              #{signal}
                            </button>
                          ))}
                        </div>
                      )}
                      {viewMode === 'ACTIVE' && canVoteFamilyExperience(item) && (
                        <div className="mt-3 flex flex-wrap gap-2">
                          <span className="rounded-full bg-amber-50 px-2 py-0.5 text-[11px] text-amber-700">
                            赞 {voteStats.upVotes} / 踩 {voteStats.downVotes}
                          </span>
                          <button
                            type="button"
                            onClick={() => void handleVoteExperience(item, 'UP')}
                            disabled={votingActionKey === `${item.id}-UP`}
                            className="inline-flex h-7 items-center gap-1 rounded-md border border-amber-200 bg-white px-2.5 text-[11px] font-medium text-amber-700 hover:bg-amber-50 disabled:opacity-60"
                          >
                            {votingActionKey === `${item.id}-UP` ? <Loader2 className="h-3 w-3 animate-spin" /> : <ThumbsUp className="h-3 w-3" />}
                            点赞
                          </button>
                          <button
                            type="button"
                            onClick={() => void handleVoteExperience(item, 'DOWN')}
                            disabled={votingActionKey === `${item.id}-DOWN`}
                            className="inline-flex h-7 items-center gap-1 rounded-md border border-rose-200 bg-white px-2.5 text-[11px] font-medium text-rose-700 hover:bg-rose-50 disabled:opacity-60"
                          >
                            {votingActionKey === `${item.id}-DOWN` ? <Loader2 className="h-3 w-3 animate-spin" /> : <ThumbsDown className="h-3 w-3" />}
                            点踩
                          </button>
                        </div>
                      )}
                      {viewMode === 'ACTIVE' && item.sourceType === 'GROWTH_OBSERVATION' && (
                        <div className="mt-3 flex flex-wrap gap-2">
                          <span className="rounded-full bg-emerald-50 px-2 py-0.5 text-[11px] text-emerald-700">
                            可能过时 {staleStats.staleVotes}
                          </span>
                          <button
                            type="button"
                            onClick={() => void handleMarkObservationStale(item)}
                            disabled={staleStats.myVoted || staleActionItemId === item.id}
                            className="inline-flex h-7 items-center gap-1 rounded-md border border-emerald-200 bg-white px-2.5 text-[11px] font-medium text-emerald-700 hover:bg-emerald-50 disabled:opacity-60"
                          >
                            {staleActionItemId === item.id && <Loader2 className="h-3 w-3 animate-spin" />}
                            {staleStats.myVoted ? '已标记' : '标记过时'}
                          </button>
                        </div>
                      )}
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
        )}

        {pageData.total > pageData.pageSize && (
          <div className="flex flex-col gap-3 border-t border-gray-100 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-center gap-2 text-xs text-gray-500">
              <span>每页</span>
              <select
                value={pageSize}
                onChange={(event) => setPageSize(Number(event.target.value))}
                className="h-8 rounded-lg border border-gray-200 bg-white px-2 text-xs text-gray-700 outline-none"
              >
                {pageSizeOptions.map((option) => (
                  <option key={option} value={option}>{option}</option>
                ))}
              </select>
              <span>条</span>
            </div>
            <div className="flex items-center gap-2 text-xs text-gray-500">
              <span>第 {pageData.page} / {totalPages} 页</span>
              <button
                type="button"
                onClick={() => setCurrentPage((page) => Math.max(1, page - 1))}
                disabled={(pageData.page || 1) <= 1}
                className="h-8 rounded-lg border border-gray-200 bg-white px-3 text-xs font-medium text-gray-600 hover:bg-gray-50 disabled:opacity-50"
              >
                上一页
              </button>
              <button
                type="button"
                onClick={() => setCurrentPage((page) => Math.min(totalPages, page + 1))}
                disabled={(pageData.page || 1) >= totalPages}
                className="h-8 rounded-lg border border-gray-200 bg-white px-3 text-xs font-medium text-gray-600 hover:bg-gray-50 disabled:opacity-50"
              >
                下一页
              </button>
            </div>
          </div>
        )}
      </section>

      {selectedItem && (
        <div className="fixed inset-0 z-50">
          <button
            type="button"
            className="absolute inset-0 bg-gray-900/35"
            aria-label="关闭详情"
            onClick={() => setSelectedItemId('')}
          />
          <aside className="absolute inset-y-0 right-0 flex w-full max-w-xl flex-col bg-white shadow-xl">
            <div className="flex items-start justify-between gap-3 border-b border-gray-200 p-4">
              <div className="min-w-0">
                <div className="mb-2 flex flex-wrap items-center gap-2">
                  <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${typeMeta[selectedItem.sourceType].badge}`}>
                    {typeMeta[selectedItem.sourceType].label}
                  </span>
                  <span className="inline-flex items-center gap-1 rounded-full bg-gray-100 px-2 py-0.5 text-[11px] text-gray-600">
                    <Shield className="h-3 w-3" />
                    {visibilityLabel(selectedItem.visibility)}
                  </span>
                </div>
                <h2 className="text-lg font-semibold leading-7 text-gray-900">{selectedItem.title}</h2>
                <p className="mt-1 text-xs text-gray-400">
                  {selectedItem.memberName} · {formatDate(selectedItem.createdAt)}
                </p>
              </div>
              <button
                type="button"
                onClick={() => setSelectedItemId('')}
                className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-gray-500 hover:bg-gray-50"
                aria-label="关闭详情"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <div className="min-h-0 flex-1 overflow-y-auto p-4">
              {viewMode === 'ACTIVE' && canManageLibrary && (
                <div className="mb-4 rounded-lg border border-amber-100 bg-amber-50 p-3">
                  <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                    <p className="text-sm leading-6 text-amber-800">这条记忆可以在记忆库里归档，保持主列表更清晰。</p>
                    <button
                      type="button"
                      onClick={() => void handleArchiveItem(selectedItem)}
                      disabled={archivingItemId === selectedItem.id}
                      className="inline-flex h-8 shrink-0 items-center justify-center gap-1.5 rounded-lg bg-white px-3 text-xs font-medium text-amber-700 ring-1 ring-amber-100 hover:bg-amber-100 disabled:opacity-60"
                    >
                      {archivingItemId === selectedItem.id ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Archive className="h-3.5 w-3.5" />}
                      归档
                    </button>
                  </div>
                </div>
              )}

              {viewMode === 'ARCHIVED' && canManageLibrary && (
                <div className="mb-4 rounded-lg border border-amber-100 bg-amber-50 p-3">
                  <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                    <p className="text-sm leading-6 text-amber-800">这条记忆已从默认展示里移出，你可以恢复或永久删除。</p>
                    <div className="flex shrink-0 flex-wrap gap-2">
                      <button
                        type="button"
                        onClick={() => void handleRestoreItem(selectedItem)}
                        disabled={restoringItemId === selectedItem.id || deletingItemId === selectedItem.id}
                        className="inline-flex h-8 items-center justify-center gap-1.5 rounded-lg bg-white px-3 text-xs font-medium text-amber-700 ring-1 ring-amber-100 hover:bg-amber-100 disabled:opacity-60"
                      >
                        {restoringItemId === selectedItem.id ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <RotateCcw className="h-3.5 w-3.5" />}
                        恢复
                      </button>
                      <button
                        type="button"
                        onClick={() => void handleDeleteArchivedItem(selectedItem)}
                        disabled={deletingItemId === selectedItem.id || restoringItemId === selectedItem.id}
                        className="inline-flex h-8 items-center justify-center gap-1.5 rounded-lg bg-white px-3 text-xs font-medium text-red-600 ring-1 ring-red-100 hover:bg-red-50 disabled:opacity-60"
                      >
                        {deletingItemId === selectedItem.id ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Trash2 className="h-3.5 w-3.5" />}
                        永久删除
                      </button>
                    </div>
                  </div>
                </div>
              )}

              <div className="mb-4 rounded-lg border border-gray-100 bg-white p-3">
                <p className="mb-2 text-xs font-medium text-gray-400">来源依据</p>
                <p className="text-sm leading-6 text-gray-700">{evidenceDescription(selectedItem)}</p>
                <div className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
                  <div className="rounded-lg bg-gray-50 p-3">
                    <p className="text-xs font-medium text-gray-400">保存来源</p>
                    <p className="mt-1 text-sm text-gray-800">{originLabel(selectedItem)}</p>
                  </div>
                  <div className="rounded-lg bg-gray-50 p-3">
                    <p className="text-xs font-medium text-gray-400">可见范围</p>
                    <p className="mt-1 text-sm text-gray-800">{visibilityLabel(selectedItem.visibility)}</p>
                  </div>
                  <div className="rounded-lg bg-gray-50 p-3">
                    <p className="text-xs font-medium text-gray-400">关联成员</p>
                    <p className="mt-1 text-sm text-gray-800">{selectedItem.memberName}</p>
                  </div>
                  <div className="rounded-lg bg-gray-50 p-3">
                    <p className="text-xs font-medium text-gray-400">记录时间</p>
                    <p className="mt-1 text-sm text-gray-800">{formatDate(selectedItem.createdAt) || '未知'}</p>
                  </div>
                </div>
              </div>

              {(selectedItem.tags || []).length > 0 && (
                <div className="mb-4 rounded-lg border border-gray-100 bg-white p-3">
                  <p className="mb-2 text-xs font-medium text-gray-400">标签</p>
                  <div className="flex flex-wrap gap-2">
                    {(selectedItem.tags || []).map((tag) => (
                      <button
                        key={`${selectedItem.id}-tag-${tag}`}
                        type="button"
                        onClick={() => {
                          setTagFilter(tag);
                          setSelectedItemId('');
                        }}
                        className="rounded-full bg-gray-100 px-2 py-1 text-xs text-gray-600 hover:bg-gray-200"
                      >
                        #{tag}
                      </button>
                    ))}
                  </div>
                </div>
              )}

              <div className="rounded-lg bg-gray-50 p-4">
                <p className="whitespace-pre-wrap text-sm leading-7 text-gray-700">
                  {selectedItem.body || '暂无正文内容。'}
                </p>
              </div>

              <div className="mt-4 rounded-lg border border-blue-100 bg-blue-50 p-3">
                <p className="mb-2 text-xs font-medium text-blue-700">继续筛选</p>
                <div className="flex flex-wrap gap-2">
                  <button
                    type="button"
                    onClick={() => {
                      setTypeFilter(selectedItem.sourceType);
                      setSelectedItemId('');
                    }}
                    className="rounded-lg bg-white px-3 py-1.5 text-xs font-medium text-blue-700 ring-1 ring-blue-100 hover:bg-blue-100"
                  >
                    同类型：{typeMeta[selectedItem.sourceType].label}
                  </button>
                  {selectedItem.memberUserId && (
                    <button
                      type="button"
                      onClick={() => {
                        setMemberFilter(String(selectedItem.memberUserId));
                        setSelectedItemId('');
                      }}
                      className="rounded-lg bg-white px-3 py-1.5 text-xs font-medium text-blue-700 ring-1 ring-blue-100 hover:bg-blue-100"
                    >
                      同成员：{selectedItem.memberName}
                    </button>
                  )}
                  <button
                    type="button"
                    onClick={() => {
                      setVisibilityFilter(selectedItem.visibility);
                      setSelectedItemId('');
                    }}
                    className="rounded-lg bg-white px-3 py-1.5 text-xs font-medium text-blue-700 ring-1 ring-blue-100 hover:bg-blue-100"
                  >
                    同权限：{visibilityLabel(selectedItem.visibility)}
                  </button>
                </div>
              </div>
            </div>

            <div className="border-t border-gray-200 p-4">
              <Link
                href={sourceHref(selectedItem, activeFamilyId)}
                className="inline-flex h-10 w-full items-center justify-center gap-2 rounded-lg border border-gray-200 bg-white px-4 text-sm font-medium text-gray-600 hover:bg-gray-50"
              >
                <CheckCircle className="h-4 w-4" />
                前往来源页面补充
              </Link>
            </div>
          </aside>
        </div>
      )}
    </div>
  );
}
