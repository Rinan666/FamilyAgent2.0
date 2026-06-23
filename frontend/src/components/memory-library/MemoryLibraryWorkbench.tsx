'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import {
  Archive,
  BookHeart,
  Check,
  Edit3,
  HeartPulse,
  Loader2,
  MoreHorizontal,
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
import { useViewerRole } from '@/hooks/useViewerRole';
import type {
  FamilyMember,
  MemoryLibraryItem,
  MemoryLibraryItemType,
  PageResult,
} from '@/types';

type LibraryItemType = MemoryLibraryItemType;
type LibraryViewMode = 'ACTIVE' | 'ARCHIVED';
type EditDraft = {
  title: string;
  body: string;
  type: string;
  visibility: string;
  tagsText: string;
};

const pageSizeOptions = [6, 12, 24];
const familyVisibilityOptions = ['PRIVATE', 'CARE_VISIBLE', 'FAMILY_VISIBLE'];
const diaryVisibilityOptions = [...familyVisibilityOptions, 'LEGACY_VISIBLE'];
const diaryTypeOptions = ['DAILY', 'IMPORTANT_EVENT', 'LESSON', 'EMOTION', 'MESSAGE_TO_FAMILY', 'SELF_REFLECTION'];
const memoryTypeOptions = ['FAMILY_STORY', 'ELDER_ADVICE', 'HEALTH_REMINDER', 'GROWTH_RISK', 'VALUE', 'PLAN'];
const growthTypeOptions = ['POSTURE', 'DENTAL', 'VISION', 'SLEEP', 'EXERCISE', 'SCREEN_TIME', 'EMOTION', 'COMMUNICATION', 'OTHER'];

const diaryTypeLabels: Record<string, string> = {
  DAILY: '日常记录',
  IMPORTANT_EVENT: '重要事件',
  LESSON: '经验教训',
  EMOTION: '情绪记录',
  MESSAGE_TO_FAMILY: '家庭留言',
  SELF_REFLECTION: '自我反思',
};

const memoryTypeLabels: Record<string, string> = {
  FAMILY_STORY: '家庭故事',
  ELDER_ADVICE: '长辈建议',
  HEALTH_REMINDER: '健康提醒',
  GROWTH_RISK: '成长风险',
  VALUE: '价值观',
  PLAN: '计划安排',
};

const growthTypeLabels: Record<string, string> = {
  POSTURE: '体态',
  DENTAL: '牙齿',
  VISION: '视力',
  SLEEP: '睡眠',
  EXERCISE: '运动',
  SCREEN_TIME: '屏幕时间',
  EMOTION: '情绪观察',
  COMMUNICATION: '沟通',
  OTHER: '其他',
};

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
    badge: 'bg-slate-100 text-slate-700',
    tone: 'text-slate-700 bg-slate-100',
  },
};

const fallbackTypeMeta = {
  label: '记忆',
  icon: BookHeart,
  badge: 'bg-gray-100 text-gray-700',
  tone: 'text-gray-700 bg-gray-100',
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

function resolveTypeMeta(sourceType?: string) {
  return typeMeta[sourceType as LibraryItemType] || fallbackTypeMeta;
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
  if (text === 'CARE_VISIBLE') return '照护可见';
  if (text === 'LEGACY_VISIBLE') return '传承预留';
  if (text === 'FAMILY_VISIBLE' || text === 'FAMILY') return '全家可见';
  return value || '按权限可见';
}

function editTypeLabel(item: MemoryLibraryItem, value: string) {
  if (item.sourceType === 'LIFE_RECORD') return diaryTypeLabels[value] || value;
  if (item.sourceType === 'GROWTH_OBSERVATION') return growthTypeLabels[value] || value;
  return memoryTypeLabels[value] || value;
}

function formatDate(value?: string) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: 'numeric', day: 'numeric' });
}

function formatDateTime(value?: string) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function canVoteFamilyExperience(item: MemoryLibraryItem) {
  return item.sourceType === 'FAMILY_EXPERIENCE' && item.id.startsWith('memory-');
}

function isLegacyAiSummary(item: MemoryLibraryItem) {
  return item.sourceType === 'AI_SUMMARY';
}

function originLabel(item: MemoryLibraryItem) {
  const source = metadataText(item, 'source');
  if (source === 'FAMILY_COMPANION_TOOL') return '家族助手对话保存';
  if (source === 'MIRROR_AGENT_TOOL') return '镜像助手对话保存';
  if (source === 'FAMILY_WEEKLY_DIGEST') return 'AI 家庭周报生成';
  if (source.includes('DIGEST') || source.includes('SUMMARY') || item.sourceType === 'AI_SUMMARY') return '历史 AI 摘要';
  return '来源页面保存';
}

function evidenceDescription(item: MemoryLibraryItem) {
  if (item.sourceType === 'LIFE_RECORD') return '来自记录页，保留事件经过、当时语境和个人感受。';
  if (item.sourceType === 'FAMILY_EXPERIENCE') return '来自经验沉淀，适合长期复用和传承。';
  if (item.sourceType === 'GROWTH_OBSERVATION') return '来自观察记录，适合照护跟进和后续复核。';
  if (item.sourceType === 'AI_SUMMARY') return '来自历史 AI 摘要，仅供回看与清理，不再参与经验沉淀。';
  return '来自记忆库记录。';
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

function draftFromItem(item: MemoryLibraryItem): EditDraft {
  return {
    title: item.title || '',
    body: item.body || '',
    type: item.type || '',
    visibility: item.visibility || '',
    tagsText: (item.tags || []).join('，'),
  };
}

function tagsFromText(value: string) {
  return value
    .split(/[,，\n]/)
    .map((tag) => tag.trim())
    .filter(Boolean)
    .slice(0, 10);
}

function editVisibilityOptions(item: MemoryLibraryItem) {
  return item.sourceType === 'LIFE_RECORD' ? diaryVisibilityOptions : familyVisibilityOptions;
}

function editTypeOptions(item: MemoryLibraryItem) {
  if (item.sourceType === 'LIFE_RECORD') return diaryTypeOptions;
  if (item.sourceType === 'GROWTH_OBSERVATION') return growthTypeOptions;
  return memoryTypeOptions;
}

let cachedMembersByFamilyId: Record<number, FamilyMember[]> = {};
let cachedCountsByFamilyId: Record<number, Record<LibraryItemType, number | null>> = {};

function invalidateMemoryLibraryPageCache(familyId?: number | null) {
  if (!familyId) return;
  delete cachedMembersByFamilyId[familyId];
  delete cachedCountsByFamilyId[familyId];
}

interface MemoryLibraryWorkbenchProps {
  embedded?: boolean;
  simplified?: boolean;
  searchQuery?: string;
  libraryViewMode?: LibraryViewMode;
}

export default function MemoryLibraryWorkbench({
  embedded = false,
  simplified = false,
  searchQuery,
  libraryViewMode,
}: MemoryLibraryWorkbenchProps) {
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
  const [isLoading, setIsLoading] = useState(false);
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
  const [archivingItemId, setArchivingItemId] = useState('');
  const [restoringItemId, setRestoringItemId] = useState('');
  const [deletingItemId, setDeletingItemId] = useState('');
  const [editingItemId, setEditingItemId] = useState('');
  const [savingItemId, setSavingItemId] = useState('');
  const [editDraft, setEditDraft] = useState<EditDraft>(() => ({
    title: '',
    body: '',
    type: '',
    visibility: '',
    tagsText: '',
  }));
  const [votingActionKey, setVotingActionKey] = useState('');
  const [staleActionItemId, setStaleActionItemId] = useState('');
  const [detailMenuOpen, setDetailMenuOpen] = useState(false);
  const [openItemMenuId, setOpenItemMenuId] = useState('');

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
    !simplified
    &&
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
    simplified,
    viewMode,
    visibilityFilter,
  ]);

  const memberOptions = useMemo(
    () => members.map((member) => ({ id: member.userId, name: memberDisplayName(member) })),
    [members],
  );

  const selectedItem = useMemo(() => (
    pageData.items.find((item) => item.id === selectedItemId)
    || null
  ), [pageData.items, selectedItemId]);
  const isEditingSelectedItem = !!selectedItem && editingItemId === selectedItem.id;

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
      return;
    }

    const effectiveViewMode = simplified ? (libraryViewMode || 'ACTIVE') : viewMode;
    const memberUserId = simplified || memberFilter === 'ALL' ? undefined : Number(memberFilter);
    const visibility = simplified || visibilityFilter === 'ALL' ? undefined : visibilityFilter;
    const tag = simplified ? undefined : debouncedTagFilter || undefined;

    setIsLoading(true);
    setError('');
    try {
      const nextPage = await (effectiveViewMode === 'ARCHIVED' ? memoryLibraryApi.archived : memoryLibraryApi.search)({
        familyId: activeFamilyId,
        page: currentPage,
        pageSize,
        keyword: debouncedQuery,
        type: simplified ? 'ALL' : typeFilter,
        memberUserId,
        visibility,
        tag,
        dateFrom: simplified ? undefined : debouncedDateFrom || undefined,
        dateTo: simplified ? undefined : debouncedDateTo || undefined,
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
    simplified,
    typeFilter,
    libraryViewMode,
    viewMode,
    visibilityFilter,
  ]);

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
    if (searchQuery === undefined) return;
    setQuery(searchQuery);
  }, [searchQuery]);

  useEffect(() => {
    if (!simplified || !libraryViewMode || viewMode === libraryViewMode) return;
    setViewMode(libraryViewMode);
    setSelectedItemId('');
  }, [libraryViewMode, simplified, viewMode]);

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
    if (!simplified && !canManageLibrary && viewMode !== 'ACTIVE') {
      setViewMode('ACTIVE');
    }
  }, [canManageLibrary, simplified, viewMode]);

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

  useEffect(() => {
    setEditingItemId((current) => (current && current !== selectedItemId ? '' : current));
    setDetailMenuOpen(false);
    setOpenItemMenuId('');
  }, [selectedItemId]);

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
    ]);
  }, [activeFamilyId, canLoadSummaryCounts, loadCounts, loadData, loadMembers]);

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

  const handleDeleteItem = useCallback(async (item: MemoryLibraryItem) => {
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
      setSelectedItemId((current) => (current === item.id ? '' : current));
      setSuccess('已永久删除');
      await refreshAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : '删除失败');
    } finally {
      setDeletingItemId('');
    }
  }, [activeFamilyId, refreshAll, viewMode]);

  const startEditingItem = useCallback((item: MemoryLibraryItem) => {
    setEditDraft(draftFromItem(item));
    setEditingItemId(item.id);
    setError('');
    setSuccess('');
  }, []);

  const cancelEditingItem = useCallback(() => {
    setEditingItemId('');
    if (selectedItem) {
      setEditDraft(draftFromItem(selectedItem));
    }
  }, [selectedItem]);

  const handleSaveItem = useCallback(async (item: MemoryLibraryItem) => {
    if (!activeFamilyId) return;
    const body = editDraft.body.trim();
    if (!body) {
      setError('正文不能为空');
      return;
    }
    setSavingItemId(item.id);
    setError('');
    try {
      await memoryLibraryApi.updateItem({
        familyId: activeFamilyId,
        itemId: item.id,
        title: editDraft.title.trim() || undefined,
        body,
        type: editDraft.type.trim() || undefined,
        visibility: editDraft.visibility.trim() || undefined,
        tags: tagsFromText(editDraft.tagsText),
      });
      setSuccess('已保存');
      setEditingItemId('');
      await refreshAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存失败');
    } finally {
      setSavingItemId('');
    }
  }, [activeFamilyId, editDraft, refreshAll]);

  const handleUpdateVisibility = useCallback(async (item: MemoryLibraryItem, nextVisibility: string) => {
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
        <p className="mt-2 text-sm text-gray-500">记忆库会统一收纳记录、经验和观察。</p>
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
    <div className="mx-auto w-full max-w-[1500px]">
      {!simplified && (
      <section className="mb-3 rounded-md border border-gray-200 bg-white p-3 sm:p-4">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <h1 className="text-xl font-bold text-gray-900">{embedded ? '记忆库' : '记忆库'}</h1>
            <p className="mt-1 max-w-2xl text-sm leading-6 text-gray-500">
              统一查看和整理 {activeFamily?.name || '当前家族'} 的记录、经验和观察。
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

        <div className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-4">
          {typeOptions.filter((item) => item.value !== 'ALL').map((option) => {
            const meta = typeMeta[option.value as LibraryItemType];
            const Icon = meta.icon;
            return (
              <button
                key={option.value}
                type="button"
                onClick={() => setTypeFilter(option.value as LibraryItemType)}
                className={`rounded-md border px-3 py-2 text-left transition-colors ${
                  typeFilter === option.value ? 'border-blue-200 bg-blue-50' : 'border-gray-100 bg-gray-50 hover:bg-gray-100'
                }`}
              >
                <Icon className={`mb-1 h-4 w-4 rounded ${meta.tone}`} />
                <p className="text-base font-bold text-gray-900">
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
      )}

      {error && <div className="mb-4 rounded-lg border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">{error}</div>}
      {success && <div className="mb-4 rounded-lg border border-green-100 bg-green-50 px-4 py-3 text-sm text-green-700">{success}</div>}

      <div className={simplified ? '' : 'grid gap-4 xl:grid-cols-[17rem_minmax(0,1fr)]'}>
      {!simplified && (
      <section className="rounded-md border border-gray-200 bg-white p-3 xl:sticky xl:top-4 xl:self-start">
        <div className="mb-3">
          <h2 className="text-sm font-semibold text-gray-900">搜索与筛选</h2>
        </div>

        <div className="grid grid-cols-1 gap-2">
          <label className="relative">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="搜索标题、正文、成员、标签..."
              className="h-9 w-full rounded-md border border-gray-200 pl-9 pr-3 text-xs outline-none focus:ring-2 focus:ring-blue-500"
            />
          </label>
          <select
            value={typeFilter}
            onChange={(event) => setTypeFilter(event.target.value as LibraryItemType | 'ALL')}
            className="h-9 rounded-md border border-gray-200 bg-white px-3 text-xs text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
          >
            {typeOptions.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
          <select
            value={memberFilter}
            onChange={(event) => setMemberFilter(event.target.value)}
            className="h-9 rounded-md border border-gray-200 bg-white px-3 text-xs text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="ALL">全部成员</option>
            {memberOptions.map((member) => (
              <option key={member.id} value={member.id}>{member.name}</option>
            ))}
          </select>
          <select
            value={visibilityFilter}
            onChange={(event) => setVisibilityFilter(event.target.value)}
            className="h-9 rounded-md border border-gray-200 bg-white px-3 text-xs text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="ALL">全部可见范围</option>
            {diaryVisibilityOptions.map((option) => (
              <option key={option} value={option}>{visibilityLabel(option)}</option>
            ))}
          </select>
        </div>

        <div className="mt-2 grid grid-cols-1 gap-2">
          <input
            value={tagFilter}
            onChange={(event) => setTagFilter(event.target.value)}
            placeholder="按标签筛选，例如：照护"
            className="h-9 rounded-md border border-gray-200 px-3 text-xs text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
          />
          <label className="text-xs font-medium text-gray-500">
            开始时间
            <input
              type="date"
              value={dateFrom}
              onChange={(event) => setDateFrom(event.target.value)}
              className="mt-1 h-9 w-full rounded-md border border-gray-200 px-3 text-xs text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
            />
          </label>
          <label className="text-xs font-medium text-gray-500">
            结束时间
            <input
              type="date"
              value={dateTo}
              onChange={(event) => setDateTo(event.target.value)}
              className="mt-1 h-9 w-full rounded-md border border-gray-200 px-3 text-xs text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
            />
          </label>
        </div>

        <div className="mt-3 flex flex-wrap items-center gap-2 border-t border-gray-100 pt-3 text-xs text-gray-500">
          <span>共 {pageData.total} 条{viewMode === 'ARCHIVED' ? '归档结果' : '结果'}</span>
          {pageData.total > 0 && <span>当前显示第 {pageStart}-{pageEnd} 条</span>}
          {(query || typeFilter !== 'ALL' || memberFilter !== 'ALL' || visibilityFilter !== 'ALL' || tagFilter || dateFrom || dateTo) && (
            <button type="button" onClick={resetFilters} className="text-blue-600 hover:underline">
              清空筛选
            </button>
          )}
        </div>
      </section>
      )}

      <section className={simplified ? 'bg-transparent' : 'rounded-md border border-gray-200 bg-white'}>
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
          <div className={simplified ? 'space-y-4' : 'divide-y divide-gray-100'}>
            {pageData.items.map((item) => {
              if (simplified) {
                const tags = (item.tags || []).slice(0, 2);
                return (
                  <article
                    key={item.id}
                    role="button"
                    tabIndex={0}
                    onClick={() => setSelectedItemId(item.id)}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter' || event.key === ' ') {
                        event.preventDefault();
                        setSelectedItemId(item.id);
                      }
                    }}
                    className="relative cursor-pointer rounded-2xl bg-white px-6 py-5 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md"
                  >
                    <div className="flex items-start justify-between gap-4">
                      <div className="min-w-0">
                        <p className="text-sm text-stone-400">
                          {[item.memberName, formatDateTime(item.createdAt)].filter(Boolean).join(' · ') || '未记录时间'}
                        </p>
                        {tags.length > 0 && (
                          <div className="mt-4 flex flex-wrap gap-2">
                            {tags.map((tag) => (
                              <span
                                key={`${item.id}-simple-tag-${tag}`}
                                className="rounded-md bg-blue-50 px-2 py-1 text-sm font-medium text-blue-600"
                              >
                                #{tag}
                              </span>
                            ))}
                          </div>
                        )}
                      </div>
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
                      {openItemMenuId === item.id && (
                        <div
                          className="absolute right-6 top-14 z-20 w-60 overflow-hidden rounded-xl border border-stone-200 bg-white py-2 text-left shadow-2xl"
                          onClick={(event) => event.stopPropagation()}
                        >
                          {viewMode === 'ACTIVE' && canManageLibrary && !isLegacyAiSummary(item) && (
                            <>
                              <button
                                type="button"
                                onClick={() => {
                                  startEditingItem(item);
                                  setSelectedItemId(item.id);
                                  setOpenItemMenuId('');
                                }}
                                className="flex h-11 w-full items-center gap-3 px-4 text-sm text-stone-700 hover:bg-stone-50"
                              >
                                <Edit3 className="h-4 w-4" />
                                编辑
                              </button>
                              <div className="border-t border-stone-100 px-4 py-3">
                                <label className="block text-xs text-stone-400">
                                  权限
                                  <select
                                    value={item.visibility || ''}
                                    onChange={(event) => void handleUpdateVisibility(item, event.target.value)}
                                    disabled={savingItemId === item.id}
                                    className="mt-1 h-9 w-full rounded-md border border-stone-200 bg-white px-2 text-sm text-stone-700 outline-none transition focus:border-emerald-400 disabled:opacity-60"
                                  >
                                    {editVisibilityOptions(item).map((option) => (
                                      <option key={option} value={option}>{visibilityLabel(option)}</option>
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
                                {archivingItemId === item.id ? <Loader2 className="h-4 w-4 animate-spin" /> : <Archive className="h-4 w-4" />}
                                归档
                              </button>
                            </>
                          )}
                          {viewMode === 'ARCHIVED' && canManageLibrary && (
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
                                {restoringItemId === item.id ? <Loader2 className="h-4 w-4 animate-spin" /> : <RotateCcw className="h-4 w-4" />}
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
                                {deletingItemId === item.id ? <Loader2 className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
                                删除
                              </button>
                            </>
                          )}
                          {viewMode === 'ACTIVE' && canManageLibrary && isLegacyAiSummary(item) && (
                            <button
                              type="button"
                              onClick={() => {
                                setOpenItemMenuId('');
                                void handleDeleteItem(item);
                              }}
                              disabled={deletingItemId === item.id}
                              className="flex h-11 w-full items-center gap-3 px-4 text-sm text-red-600 hover:bg-red-50 disabled:opacity-60"
                            >
                              {deletingItemId === item.id ? <Loader2 className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
                              删除
                            </button>
                          )}
                          {!canManageLibrary && (
                            <div className="px-4 py-3 text-sm text-stone-400">暂无可用操作</div>
                          )}
                        </div>
                      )}
                    </div>

                    <h2 className="mt-5 text-xl font-semibold leading-8 text-stone-950">{item.title || '未命名记忆'}</h2>
                    <p className="mt-3 line-clamp-8 whitespace-pre-wrap text-lg leading-9 text-stone-800">
                      {item.body || '暂无正文内容。'}
                    </p>
                  </article>
                );
              }

              const meta = resolveTypeMeta(item.sourceType);
              const Icon = meta.icon;
              const status = assetStatus(item);
              const signals = itemSignals(item);
              const voteStats = familyExperienceVoteStats(item);
              const staleStats = growthObservationStalenessStats(item);
              return (
                <article
                  key={item.id}
                  role={simplified ? 'button' : undefined}
                  tabIndex={simplified ? 0 : undefined}
                  onClick={simplified ? () => setSelectedItemId(item.id) : undefined}
                  onKeyDown={simplified ? (event) => {
                    if (event.key === 'Enter' || event.key === ' ') {
                      event.preventDefault();
                      setSelectedItemId(item.id);
                    }
                  } : undefined}
                  className={`p-5 transition-colors hover:bg-gray-50 sm:p-6 ${simplified ? 'cursor-pointer' : ''}`}
                >
                  <div className="flex items-start gap-4">
                    <span className={`inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-md ${meta.tone}`}>
                      <Icon className="h-5 w-5" />
                    </span>
                    <div className="min-w-0 flex-1">
                      <div className="mb-1 flex flex-wrap items-center gap-2">
                        <button
                          type="button"
                          onClick={() => setSelectedItemId(item.id)}
                          className="min-w-0 truncate text-left text-base font-semibold text-gray-900 hover:text-blue-700"
                        >
                          {item.title}
                        </button>
                        <span className={`rounded px-2 py-0.5 text-[11px] font-medium ${meta.badge}`}>
                          {meta.label}
                        </span>
                        <span className={`rounded px-2 py-0.5 text-[11px] font-medium ${status.tone}`}>
                          {status.label}
                        </span>
                        <span className="inline-flex items-center gap-1 rounded bg-gray-100 px-2 py-0.5 text-[11px] text-gray-600">
                          <Shield className="h-3 w-3" />
                          {visibilityLabel(item.visibility)}
                        </span>
                      </div>
                      <p className="line-clamp-3 text-base leading-7 text-gray-700">{item.body || '暂无正文内容。'}</p>
                      <div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-gray-400">
                        <span>{item.memberName}</span>
                        <span>{formatDate(item.createdAt)}</span>
                        <span className="rounded bg-gray-100 px-2 py-0.5 text-gray-500">{originLabel(item)}</span>
                      </div>
                          {!simplified && signals.length > 0 && (
                        <div className="mt-2 flex flex-wrap gap-1.5">
                          {signals.map((signal) => (
                            <button
                              key={`${item.id}-${signal}`}
                              type="button"
                              onClick={() => {
                                setTagFilter(signal);
                                setSelectedItemId('');
                              }}
                              className="rounded bg-blue-50 px-2 py-0.5 text-[11px] text-blue-700 hover:bg-blue-100"
                            >
                              #{signal}
                            </button>
                          ))}
                        </div>
                      )}
                      {!simplified && viewMode === 'ACTIVE' && canVoteFamilyExperience(item) && (
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
                      {!simplified && viewMode === 'ACTIVE' && item.sourceType === 'GROWTH_OBSERVATION' && (
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
      </div>

      {selectedItem && (
        <div className="fixed inset-0 z-50">
          <button
            type="button"
            className="absolute inset-0 bg-gray-900/35"
            aria-label="关闭详情"
            onClick={() => setSelectedItemId('')}
          />
          <aside className="absolute inset-y-0 right-0 flex w-full max-w-2xl flex-col bg-[#fbfaf8] shadow-2xl">
            <div className="flex items-start justify-between gap-4 px-6 pb-3 pt-6">
              <div className="min-w-0">
                <p className="text-sm text-stone-400">
                  {[selectedItem.memberName, formatDateTime(selectedItem.createdAt)].filter(Boolean).join(' · ') || '未记录时间'}
                </p>
                <div className="mt-4 flex flex-wrap items-center gap-2">
                  {(selectedItem.tags || []).slice(0, 3).map((tag) => (
                    <span key={`${selectedItem.id}-tag-${tag}`} className="rounded-md bg-blue-50 px-2 py-1 text-sm font-medium text-blue-600">
                      #{tag}
                    </span>
                  ))}
                  <span className={`rounded-md px-2 py-1 text-sm font-medium ${resolveTypeMeta(selectedItem.sourceType).badge}`}>
                    {resolveTypeMeta(selectedItem.sourceType).label}
                  </span>
                </div>
              </div>
              <div className="relative flex shrink-0 items-center gap-2">
                <button
                  type="button"
                  onClick={() => setSelectedItemId('')}
                  className="inline-flex h-10 w-10 items-center justify-center rounded-md text-stone-500 hover:bg-stone-100"
                  aria-label="关闭详情"
                >
                  <X className="h-5 w-5" />
                </button>
                <button
                  type="button"
                  onClick={() => setDetailMenuOpen((current) => !current)}
                  className="inline-flex h-10 w-10 items-center justify-center rounded-md bg-stone-100 text-stone-700 hover:bg-stone-200"
                  aria-label="更多操作"
                  aria-expanded={detailMenuOpen}
                >
                  <MoreHorizontal className="h-5 w-5" />
                </button>
                {detailMenuOpen && (
                  <div className="absolute right-0 top-12 z-10 w-56 overflow-hidden rounded-xl border border-stone-200 bg-white py-2 shadow-2xl">
                    {viewMode === 'ACTIVE' && canManageLibrary && !isLegacyAiSummary(selectedItem) && (
                      <>
                        <button
                          type="button"
                          onClick={() => {
                            setDetailMenuOpen(false);
                            startEditingItem(selectedItem);
                          }}
                          className="flex h-11 w-full items-center gap-3 px-4 text-left text-sm text-stone-700 hover:bg-stone-50"
                        >
                          <Edit3 className="h-4 w-4" />
                          编辑
                        </button>
                        <button
                          type="button"
                          onClick={() => {
                            setDetailMenuOpen(false);
                            void handleArchiveItem(selectedItem);
                          }}
                          disabled={archivingItemId === selectedItem.id}
                          className="flex h-11 w-full items-center gap-3 px-4 text-left text-sm text-stone-700 hover:bg-stone-50 disabled:opacity-60"
                        >
                          {archivingItemId === selectedItem.id ? <Loader2 className="h-4 w-4 animate-spin" /> : <Archive className="h-4 w-4" />}
                          归档
                        </button>
                      </>
                    )}
                    {viewMode === 'ARCHIVED' && canManageLibrary && (
                      <button
                        type="button"
                        onClick={() => {
                          setDetailMenuOpen(false);
                          void handleRestoreItem(selectedItem);
                        }}
                        disabled={restoringItemId === selectedItem.id}
                        className="flex h-11 w-full items-center gap-3 px-4 text-left text-sm text-stone-700 hover:bg-stone-50 disabled:opacity-60"
                      >
                        {restoringItemId === selectedItem.id ? <Loader2 className="h-4 w-4 animate-spin" /> : <RotateCcw className="h-4 w-4" />}
                        恢复
                      </button>
                    )}
                    {canManageLibrary && (viewMode === 'ARCHIVED' || isLegacyAiSummary(selectedItem)) && (
                      <button
                        type="button"
                        onClick={() => {
                          setDetailMenuOpen(false);
                          void handleDeleteItem(selectedItem);
                        }}
                        disabled={deletingItemId === selectedItem.id}
                        className="flex h-11 w-full items-center gap-3 px-4 text-left text-sm text-red-600 hover:bg-red-50 disabled:opacity-60"
                      >
                        {deletingItemId === selectedItem.id ? <Loader2 className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
                        删除
                      </button>
                    )}
                    <div className="mt-2 space-y-3 border-t border-stone-100 px-4 py-3 text-xs leading-5 text-stone-400">
                      <p>成员：{selectedItem.memberName || '未关联成员'}</p>
                      <label className="block">
                        <span className="mb-1 block">权限</span>
                        {canManageLibrary ? (
                          <select
                            value={selectedItem.visibility || ''}
                            onChange={(event) => void handleUpdateVisibility(selectedItem, event.target.value)}
                            disabled={savingItemId === selectedItem.id}
                            className="h-9 w-full rounded-md border border-stone-200 bg-white px-2 text-sm text-stone-700 outline-none transition focus:border-emerald-400 disabled:opacity-60"
                          >
                            {editVisibilityOptions(selectedItem).map((option) => (
                              <option key={option} value={option}>{visibilityLabel(option)}</option>
                            ))}
                          </select>
                        ) : (
                          <span className="text-sm text-stone-600">{visibilityLabel(selectedItem.visibility)}</span>
                        )}
                      </label>
                    </div>
                  </div>
                )}
              </div>
            </div>

            <div className="min-h-0 flex-1 overflow-y-auto px-6 pb-6">
              {!isEditingSelectedItem && (
                <div className="pb-5">
                  <h2 className="text-2xl font-semibold leading-10 text-stone-950">{selectedItem.title || '未命名记忆'}</h2>
                </div>
              )}

              {!simplified && !isEditingSelectedItem && (selectedItem.tags || []).length > 0 && (
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

              {isEditingSelectedItem ? (
                <div className="rounded-2xl border border-emerald-100 bg-white p-4 shadow-sm">
                  <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                    <label className="sm:col-span-2">
                      <span className="mb-1 block text-xs font-medium text-emerald-700">标题</span>
                      <input
                        value={editDraft.title}
                        onChange={(event) => setEditDraft((draft) => ({ ...draft, title: event.target.value }))}
                        maxLength={120}
                        className="h-10 w-full rounded-lg border border-emerald-100 bg-white px-3 text-sm text-gray-800 outline-none focus:border-emerald-300"
                      />
                    </label>
                    <div>
                      <span className="mb-1 block text-xs font-medium text-emerald-700">类型</span>
                      <div className="flex h-10 items-center rounded-lg border border-emerald-100 bg-emerald-50 px-3 text-sm font-medium text-emerald-900">
                        {resolveTypeMeta(selectedItem.sourceType).label}
                      </div>
                    </div>
                    <label>
                      <span className="mb-1 block text-xs font-medium text-emerald-700">细分类型</span>
                      <select
                        value={editDraft.type}
                        onChange={(event) => setEditDraft((draft) => ({ ...draft, type: event.target.value }))}
                        className="h-10 w-full rounded-lg border border-emerald-100 bg-white px-3 text-sm text-gray-800 outline-none focus:border-emerald-300"
                      >
                        {editTypeOptions(selectedItem).map((option) => (
                          <option key={option} value={option}>{editTypeLabel(selectedItem, option)}</option>
                        ))}
                      </select>
                    </label>
                    <label className="sm:col-span-2">
                      <span className="mb-1 block text-xs font-medium text-emerald-700">可见范围</span>
                      <select
                        value={editDraft.visibility}
                        onChange={(event) => setEditDraft((draft) => ({ ...draft, visibility: event.target.value }))}
                        className="h-10 w-full rounded-lg border border-emerald-100 bg-white px-3 text-sm text-gray-800 outline-none focus:border-emerald-300"
                      >
                        {editVisibilityOptions(selectedItem).map((option) => (
                          <option key={option} value={option}>{visibilityLabel(option)}</option>
                        ))}
                      </select>
                    </label>
                    <label className="sm:col-span-2">
                      <span className="mb-1 block text-xs font-medium text-emerald-700">标签</span>
                      <input
                        value={editDraft.tagsText}
                        onChange={(event) => setEditDraft((draft) => ({ ...draft, tagsText: event.target.value }))}
                        placeholder="用逗号分隔"
                        className="h-10 w-full rounded-lg border border-emerald-100 bg-white px-3 text-sm text-gray-800 outline-none focus:border-emerald-300"
                      />
                    </label>
                    <label className="sm:col-span-2">
                      <span className="mb-1 block text-xs font-medium text-emerald-700">正文</span>
                      <textarea
                        value={editDraft.body}
                        onChange={(event) => setEditDraft((draft) => ({ ...draft, body: event.target.value }))}
                        rows={9}
                        maxLength={2000}
                        className="w-full resize-y rounded-lg border border-emerald-100 bg-white px-3 py-2 text-sm leading-6 text-gray-800 outline-none focus:border-emerald-300"
                      />
                    </label>
                  </div>
                </div>
              ) : (
                <div className="rounded-2xl bg-white p-6 shadow-sm">
                  <p className="whitespace-pre-wrap text-lg leading-10 text-stone-800">
                    {selectedItem.body || '暂无正文内容。'}
                  </p>
                </div>
              )}

              {!simplified && (
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
                    同类型：{resolveTypeMeta(selectedItem.sourceType).label}
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
              )}
            </div>

            <div className={isEditingSelectedItem ? 'border-t border-stone-200 bg-white p-4' : 'hidden'}>
              {viewMode === 'ACTIVE' && canManageLibrary && !isLegacyAiSummary(selectedItem) && (
                <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
                  {isEditingSelectedItem ? (
                    <>
                      <button
                        type="button"
                        onClick={cancelEditingItem}
                        disabled={savingItemId === selectedItem.id}
                        className="inline-flex h-10 items-center justify-center rounded-lg border border-gray-200 bg-white px-4 text-sm font-medium text-gray-600 hover:bg-gray-50 disabled:opacity-60"
                      >
                        取消
                      </button>
                      <button
                        type="button"
                        onClick={() => void handleSaveItem(selectedItem)}
                        disabled={savingItemId === selectedItem.id}
                        className="inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-60"
                      >
                        {savingItemId === selectedItem.id ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />}
                        保存
                      </button>
                    </>
                  ) : (
                    <>
                      <button
                        type="button"
                        onClick={() => void handleArchiveItem(selectedItem)}
                        disabled={archivingItemId === selectedItem.id}
                        className="inline-flex h-10 items-center justify-center gap-2 rounded-lg border border-amber-200 bg-white px-4 text-sm font-medium text-amber-700 hover:bg-amber-50 disabled:opacity-60"
                      >
                        {archivingItemId === selectedItem.id ? <Loader2 className="h-4 w-4 animate-spin" /> : <Archive className="h-4 w-4" />}
                        归档
                      </button>
                      <button
                        type="button"
                        onClick={() => startEditingItem(selectedItem)}
                        className="inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 text-sm font-medium text-white hover:bg-blue-700"
                      >
                        <Edit3 className="h-4 w-4" />
                        编辑
                      </button>
                    </>
                  )}
                </div>
              )}

              {viewMode === 'ACTIVE' && canManageLibrary && isLegacyAiSummary(selectedItem) && (
                <div className="flex justify-end">
                  <button
                    type="button"
                    onClick={() => void handleDeleteItem(selectedItem)}
                    disabled={deletingItemId === selectedItem.id}
                    className="inline-flex h-10 items-center justify-center gap-2 rounded-lg border border-red-200 bg-white px-4 text-sm font-medium text-red-600 hover:bg-red-50 disabled:opacity-60"
                  >
                    {deletingItemId === selectedItem.id ? <Loader2 className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
                    直接删除
                  </button>
                </div>
              )}

              {viewMode === 'ARCHIVED' && canManageLibrary && (
                <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
                  <button
                    type="button"
                    onClick={() => void handleDeleteItem(selectedItem)}
                    disabled={deletingItemId === selectedItem.id || restoringItemId === selectedItem.id}
                    className="inline-flex h-10 items-center justify-center gap-2 rounded-lg border border-red-200 bg-white px-4 text-sm font-medium text-red-600 hover:bg-red-50 disabled:opacity-60"
                  >
                    {deletingItemId === selectedItem.id ? <Loader2 className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
                    永久删除
                  </button>
                  <button
                    type="button"
                    onClick={() => void handleRestoreItem(selectedItem)}
                    disabled={restoringItemId === selectedItem.id || deletingItemId === selectedItem.id}
                    className="inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-amber-600 px-4 text-sm font-medium text-white hover:bg-amber-700 disabled:opacity-60"
                  >
                    {restoringItemId === selectedItem.id ? <Loader2 className="h-4 w-4 animate-spin" /> : <RotateCcw className="h-4 w-4" />}
                    恢复
                  </button>
                </div>
              )}
            </div>
          </aside>
        </div>
      )}
    </div>
  );
}
