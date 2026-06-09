'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import {
  AlertTriangle,
  Archive,
  Bot,
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
  AuthorizedMemoryRecallResult,
  FamilyMember,
  HeritageClassicalDraft,
  MemoryLibraryItem,
  MemoryLibraryItemType,
  MemoryMaintenanceSuggestion,
  PageResult,
  RagRecallSource,
} from '@/types';

type LibraryItemType = MemoryLibraryItemType;
type LibraryViewMode = 'ACTIVE' | 'ARCHIVED';

const pageSizeOptions = [3, 6, 9];
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
  tone: string;
  badge: string;
}> = {
  LIFE_RECORD: {
    label: '记录',
    icon: BookHeart,
    tone: 'text-rose-700 bg-rose-50',
    badge: 'bg-rose-50 text-rose-700',
  },
  FAMILY_EXPERIENCE: {
    label: '经验',
    icon: ScrollText,
    tone: 'text-amber-700 bg-amber-50',
    badge: 'bg-amber-50 text-amber-700',
  },
  GROWTH_OBSERVATION: {
    label: '观察',
    icon: HeartPulse,
    tone: 'text-emerald-700 bg-emerald-50',
    badge: 'bg-emerald-50 text-emerald-700',
  },
  AI_SUMMARY: {
    label: 'AI 摘要',
    icon: Bot,
    tone: 'text-purple-700 bg-purple-50',
    badge: 'bg-purple-50 text-purple-700',
  },
};

function parseLibraryItemNumericId(itemId: string | undefined, expectedPrefix: string) {
  if (!itemId || !itemId.startsWith(`${expectedPrefix}-`)) return null;
  const numericId = Number(itemId.slice(expectedPrefix.length + 1));
  return Number.isFinite(numericId) && numericId > 0 ? numericId : null;
}

function metadataObject(item: MemoryLibraryItem, key: string) {
  const value = item.metadata?.[key];
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  return value as Record<string, unknown>;
}

function metadataNumber(source: Record<string, unknown> | null, key: string) {
  const value = source?.[key];
  return typeof value === 'number' ? value : Number(value || 0) || 0;
}

function metadataBoolean(source: Record<string, unknown> | null, key: string) {
  const value = source?.[key];
  return value === true || value === 'true';
}

function familyExperienceVoteStats(item: MemoryLibraryItem) {
  const stats = metadataObject(item, 'voteStats');
  return {
    upVotes: metadataNumber(stats, 'upVotes'),
    downVotes: metadataNumber(stats, 'downVotes'),
    voteScore: metadataNumber(stats, 'voteScore'),
    myVote: typeof stats?.myVote === 'string' ? stats.myVote.toUpperCase() : '',
  };
}

function growthObservationStalenessStats(item: MemoryLibraryItem) {
  const stats = metadataObject(item, 'stalenessStats');
  return {
    staleVotes: metadataNumber(stats, 'staleVotes'),
    myVoted: metadataBoolean(stats, 'myVoted'),
  };
}

function canVoteFamilyExperience(item: MemoryLibraryItem) {
  return item.sourceType === 'FAMILY_EXPERIENCE' && parseLibraryItemNumericId(item.id, 'memory') !== null;
}

function canMarkGrowthObservationStale(item: MemoryLibraryItem) {
  return item.sourceType === 'GROWTH_OBSERVATION' && parseLibraryItemNumericId(item.id, 'growth') !== null;
}

function formatDate(value?: string) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: 'numeric', day: 'numeric' });
}

function visibilityLabel(value?: string) {
  const text = String(value || '').toUpperCase();
  if (text === 'PRIVATE') return '仅自己可见';
  if (text === 'CARE_VISIBLE' || text === 'PARENT_VISIBLE') return '照护可见';
  if (text === 'LEGACY_VISIBLE') return '传承预留';
  if (text === 'FAMILY_VISIBLE' || text === 'FAMILY') return '全家可见';
  return value || '按权限可见';
}

function sourceLabel(item: MemoryLibraryItem) {
  return typeMeta[item.sourceType]?.label || '家族记忆';
}

function sourceHref(item: MemoryLibraryItem, familyId?: number | null) {
  const params = new URLSearchParams();
  if (familyId) params.set('familyId', String(familyId));
  if (item.memberUserId) params.set('targetUserId', String(item.memberUserId));
  if (item.sourceType === 'GROWTH_OBSERVATION') {
    params.set('tab', 'growth');
    const query = params.toString();
    return `/dashboard/diary${query ? `?${query}` : ''}`;
  }
  const query = params.toString();
  if (item.sourceType === 'LIFE_RECORD') return `/dashboard/diary${query ? `?${query}` : ''}`;
  if (item.sourceType === 'FAMILY_EXPERIENCE') return `/dashboard/heritage${query ? `?${query}` : ''}`;
  return `/dashboard/memory${query ? `?${query}` : ''}`;
}

function metadataText(item: MemoryLibraryItem, key: string) {
  const value = item.metadata?.[key];
  return typeof value === 'string' ? value.trim() : '';
}

function originLabel(item: MemoryLibraryItem) {
  const source = metadataText(item, 'source');
  if (source === 'FAMILY_COMPANION_TOOL') return '家族Agent 对话保存';
  if (source === 'MIRROR_AGENT_TOOL') return '镜像 Agent 对话保存';
  if (source === 'FAMILY_WEEKLY_DIGEST') return 'AI 家庭周报生成';
  if (source.includes('DIGEST') || source.includes('SUMMARY')) return 'AI 摘要生成';
  if (item.sourceType === 'AI_SUMMARY') return 'AI 摘要生成';
  return '手动录入或来源页面保存';
}

function evidenceDescription(item: MemoryLibraryItem) {
  const scenario = metadataText(item, 'scenario');
  const followUpStatus = metadataText(item, 'followUpStatus');
  const isCoreMemory = item.metadata?.coreMemory === true || item.tags?.includes('鏍稿績璁板繂');

  let description = '来自已授权并按家庭权限过滤的资料，可继续用于整理、回顾和权限范围内召回。';
  if (item.sourceType === 'LIFE_RECORD') {
    description = '来自已授权的人生记录，适合保留事件经过、当时语境和家庭视角。';
  } else if (item.sourceType === 'FAMILY_EXPERIENCE') {
    description = '来自家族经验沉淀，适合在家庭陪伴、传承和镜像参考场景中复用。';
  } else if (item.sourceType === 'GROWTH_OBSERVATION') {
    description = '来自成长观察记录，适合照护跟进和后续复核，不构成诊断或医疗结论。';
  } else if (item.sourceType === 'AI_SUMMARY') {
    description = '来自 AI 对家族资料的整理摘要，可作为快速参考，但关键细节应回看原始记录确认。';
  }

  if (scenario) {
    description += ` 当前已补充的适用场景：${scenario}。`;
  }
  if (followUpStatus) {
    description += ` 当前跟进状态：${followUpStatus}。`;
  }
  if (isCoreMemory) {
    description += ' 这条内容已被家庭标记为核心记忆，适合长期保留。';
  }

  return description;
}

function aiUsageLabels(item: MemoryLibraryItem) {
  const visibility = String(item.visibility || '').toUpperCase();
  const labels = new Set<string>();

  if (visibility === 'PRIVATE') {
    labels.add('家族Agent 本人视角可参考');
  } else if (visibility === 'FAMILY_VISIBLE' || visibility === 'FAMILY') {
    labels.add('家族Agent 可参考');
    labels.add('镜像 Agent 可参考');
    labels.add('家庭摘要可参考');
  } else if (visibility === 'CARE_VISIBLE' || visibility === 'PARENT_VISIBLE') {
    labels.add('家族Agent 照护视角可参考');
    labels.add('成长观察摘要可参考');
    labels.add('授权镜像可参考');
  } else if (visibility === 'LEGACY_VISIBLE') {
    labels.add('传承场景可参考');
  } else {
    labels.add('按权限过滤后可参考');
  }

  if (item.sourceType === 'GROWTH_OBSERVATION') labels.add('成长观察摘要可参考');
  if (item.sourceType === 'AI_SUMMARY') labels.add('家族Agent 可参考');
  return Array.from(labels);
}

function missingInfoLabels(item: MemoryLibraryItem) {
  const labels: string[] = [];
  if (!item.title?.trim() || item.title === '未命名记录' || item.title === '未命名经验') labels.push('缺标题');
  if (!item.body?.trim() || item.body.trim().length < 30) labels.push('缺背景');
  if (!item.memberName?.trim()) labels.push('缺关联成员');
  if (!item.createdAt) labels.push('缺时间');
  if (!item.tags || item.tags.length === 0) labels.push('缺标签');
  if (item.sourceType === 'FAMILY_EXPERIENCE' && !metadataText(item, 'scenario')) labels.push('缺适用场景');
  if (item.sourceType === 'GROWTH_OBSERVATION' && !metadataText(item, 'followUpStatus')) labels.push('缺跟进状态');
  return labels.slice(0, 4);
}

function assetStatus(item: MemoryLibraryItem) {
  const missing = missingInfoLabels(item);
  if (metadataText(item, 'coreMemory') === 'true' || item.tags?.includes('核心记忆')) {
    return { label: '核心记忆', tone: 'bg-purple-50 text-purple-700' };
  }
  if (missing.length === 0) return { label: '可活化', tone: 'bg-green-50 text-green-700' };
  if (missing.length <= 2) return { label: '可补充', tone: 'bg-yellow-50 text-yellow-700' };
  return { label: '待完善', tone: 'bg-gray-100 text-gray-600' };
}

function activationPrompt(item: MemoryLibraryItem) {
  const content = (item.body || '').trim().slice(0, 220);
  return [
    `请基于这条家族记忆，告诉我它对我们家有什么用：${item.title}`,
    `类型：${sourceLabel(item)}`,
    `关联成员：${item.memberName || '未指定'}`,
    `可见范围：${visibilityLabel(item.visibility)}`,
    content ? `内容摘要：${content}` : '',
    '请从“可以理解什么、适合提醒谁、下一步还应该补充什么”三个角度回答。',
  ].filter(Boolean).join('\n');
}

function activationHref(item: MemoryLibraryItem, familyId?: number | null) {
  const params = new URLSearchParams();
  params.set('prompt', activationPrompt(item));
  if (familyId) params.set('familyId', String(familyId));
  return `/dashboard/agent?${params.toString()}`;
}

function memberDisplayName(member?: FamilyMember) {
  if (!member) return '家庭成员';
  return member.relationshipLabel?.trim() || member.nickname?.trim() || member.username?.trim() || `用户 ${member.userId}`;
}

function emptyPage(pageSize: number): PageResult<MemoryLibraryItem> {
  return {
    items: [],
    page: 1,
    pageSize,
    total: 0,
    totalPages: 0,
  };
}

function recallSourceTypeLabel(sourceType?: string) {
  if (sourceType === 'LIFE_RECORD') return '记录';
  if (sourceType === 'FAMILY_EXPERIENCE') return '经验';
  if (sourceType === 'GROWTH_OBSERVATION') return '观察';
  if (sourceType === 'AI_SUMMARY') return 'AI 摘要';
  return sourceType || '未知来源';
}

function recallSourceTone(sourceType?: string) {
  if (sourceType === 'LIFE_RECORD') return 'bg-rose-50 text-rose-700';
  if (sourceType === 'FAMILY_EXPERIENCE') return 'bg-amber-50 text-amber-700';
  if (sourceType === 'GROWTH_OBSERVATION') return 'bg-emerald-50 text-emerald-700';
  return 'bg-gray-100 text-gray-600';
}

function maintenanceActionMeta(action?: string) {
  if (action === 'MERGE_REVIEW') {
    return {
      label: '建议合并',
      tone: 'border-blue-100 bg-blue-50 text-blue-700',
      iconTone: 'text-blue-600',
    };
  }
  if (action === 'DELETE_REVIEW') {
    return {
      label: '待清理复核',
      tone: 'border-red-100 bg-red-50 text-red-700',
      iconTone: 'text-red-600',
    };
  }
  return {
    label: '建议归档',
    tone: 'border-amber-100 bg-amber-50 text-amber-700',
    iconTone: 'text-amber-600',
  };
}

interface MemoryLibraryPageProps {
  embedded?: boolean;
}

export default function MemoryLibraryPage({ embedded = false }: MemoryLibraryPageProps) {
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
  const [counts, setCounts] = useState<Record<LibraryItemType, number>>({
    LIFE_RECORD: 0,
    FAMILY_EXPERIENCE: 0,
    GROWTH_OBSERVATION: 0,
    AI_SUMMARY: 0,
  });
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [query, setQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [typeFilter, setTypeFilter] = useState<LibraryItemType | 'ALL'>('ALL');
  const [memberFilter, setMemberFilter] = useState('ALL');
  const [visibilityFilter, setVisibilityFilter] = useState('ALL');
  const [selectedItemId, setSelectedItemId] = useState('');
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(3);
  const [recallQuery, setRecallQuery] = useState('');
  const [recallResult, setRecallResult] = useState<AuthorizedMemoryRecallResult | null>(null);
  const [isRecalling, setIsRecalling] = useState(false);
  const [recallError, setRecallError] = useState('');
  const [maintenanceSuggestions, setMaintenanceSuggestions] = useState<MemoryMaintenanceSuggestion[]>([]);
  const [loadingMaintenance, setLoadingMaintenance] = useState(false);
  const [mergingSuggestionKey, setMergingSuggestionKey] = useState('');
  const [classicalDraftPreview, setClassicalDraftPreview] = useState<HeritageClassicalDraft | null>(null);
  const [classicalizingItemId, setClassicalizingItemId] = useState('');
  const [applyingClassicalItemId, setApplyingClassicalItemId] = useState('');
  const [votingActionKey, setVotingActionKey] = useState('');
  const [staleActionItemId, setStaleActionItemId] = useState('');
  const [archivingItemId, setArchivingItemId] = useState('');
  const [restoringItemId, setRestoringItemId] = useState('');
  const [deletingItemId, setDeletingItemId] = useState('');
  const [viewMode, setViewMode] = useState<LibraryViewMode>('ACTIVE');
  const [itemOpenNotice, setItemOpenNotice] = useState('');
  const [resolvedRequestedItemKey, setResolvedRequestedItemKey] = useState('');
  const [resetRequestedItemKey, setResetRequestedItemKey] = useState('');

  const requestedFamilyId = useMemo(() => {
    const value = Number(searchParams.get('familyId'));
    return Number.isFinite(value) && value > 0 ? value : null;
  }, [searchParams]);
  const requestedItemId = useMemo(() => {
    const value = searchParams.get('itemId');
    return typeof value === 'string' ? value.trim() : '';
  }, [searchParams]);

  const memberOptions = useMemo(
    () => members.map((member) => ({ id: member.userId, name: memberDisplayName(member) })),
    [members],
  );

  const selectedItem = useMemo(
    () => {
      const pageItem = pageData.items.find((item) => item.id === selectedItemId);
      if (pageItem) return pageItem;
      return maintenanceSuggestions
        .flatMap((suggestion) => suggestion.items || [])
        .find((item) => item.id === selectedItemId) || null;
    },
    [maintenanceSuggestions, pageData.items, selectedItemId],
  );

  const pageStart = pageData.total === 0 ? 0 : (pageData.page - 1) * pageData.pageSize + 1;
  const pageEnd = Math.min(pageData.page * pageData.pageSize, pageData.total);
  const totalPages = Math.max(1, pageData.totalPages || 1);
  const canManageLibrary = activeMembership?.role === 'OWNER' || viewerRole === 'ADMIN';
  const canUseRecallDebug = viewerRole === 'MEMBER' || viewerRole === 'ADMIN';
  const requestedItemKey = requestedItemId && activeFamilyId ? `${activeFamilyId}:${requestedItemId}` : '';
  const canClassicalizeSelectedItem = viewMode === 'ACTIVE'
    && !!selectedItem
    && selectedItem.sourceType === 'FAMILY_EXPERIENCE'
    && selectedItem.id.startsWith('memory-');

  useEffect(() => {
    const queryFamily = requestedFamilyId && families.some((family) => family.id === requestedFamilyId)
      ? requestedFamilyId
      : null;
    if (queryFamily && activeFamilyId !== queryFamily) {
      setActiveFamilyId(queryFamily);
    }
  }, [activeFamilyId, families, requestedFamilyId, setActiveFamilyId]);

  useEffect(() => {
    if (!canManageLibrary && viewMode !== 'ACTIVE') {
      setViewMode('ACTIVE');
    }
  }, [canManageLibrary, viewMode]);

  const loadCounts = useCallback(async (
    familyId: number,
    keyword: string,
    memberUserId?: number,
    visibility?: string,
  ) => {
    const nextCounts = await Promise.all(
      typeOptions
        .filter((option) => option.value !== 'ALL')
        .map(async (option) => {
          const result = await memoryLibraryApi.search({
            familyId,
            page: 1,
            pageSize: 1,
            keyword,
            type: option.value as LibraryItemType,
            memberUserId,
            visibility,
          }).catch(() => emptyPage(1));
          return [option.value, result.total] as const;
        }),
    );
    setCounts(Object.fromEntries(nextCounts) as Record<LibraryItemType, number>);
  }, []);

  const loadData = useCallback(async () => {
    if (!activeFamilyId) {
      setMembers([]);
      setPageData(emptyPage(pageSize));
      setMaintenanceSuggestions([]);
      return;
    }

    setIsLoading(true);
    setError('');
    const memberUserId = memberFilter === 'ALL' ? undefined : Number(memberFilter);
    const visibility = visibilityFilter === 'ALL' ? undefined : visibilityFilter;
    try {
      const [memberList, nextPage] = await Promise.all([
        familyApi.getMembers(activeFamilyId).catch(() => [] as FamilyMember[]),
        (viewMode === 'ARCHIVED' ? memoryLibraryApi.archived : memoryLibraryApi.search)({
          familyId: activeFamilyId,
          page: currentPage,
          pageSize,
          keyword: debouncedQuery,
          type: typeFilter,
          memberUserId,
          visibility,
        }),
      ]);
      setMembers(Array.isArray(memberList) ? memberList : []);
      setPageData(nextPage);
      setSelectedItemId((current) => (
        current && nextPage.items.some((item) => item.id === current) ? current : ''
      ));
      if (viewMode === 'ACTIVE') {
        void loadCounts(activeFamilyId, debouncedQuery, memberUserId, visibility);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '家族记忆库加载失败');
      setPageData(emptyPage(pageSize));
    } finally {
      setIsLoading(false);
    }
  }, [activeFamilyId, currentPage, debouncedQuery, loadCounts, memberFilter, pageSize, typeFilter, viewMode, visibilityFilter]);

  const loadMaintenanceSuggestions = useCallback(async () => {
    if (!activeFamilyId || viewMode !== 'ACTIVE') {
      setMaintenanceSuggestions([]);
      return;
    }
    setLoadingMaintenance(true);
    try {
      const suggestions = await memoryLibraryApi.maintenanceSuggestions(activeFamilyId);
      setMaintenanceSuggestions(Array.isArray(suggestions) ? suggestions : []);
    } catch {
      setMaintenanceSuggestions([]);
    } finally {
      setLoadingMaintenance(false);
    }
  }, [activeFamilyId, viewMode]);

  const resetFilters = () => {
    setQuery('');
    setDebouncedQuery('');
    setTypeFilter('ALL');
    setMemberFilter('ALL');
    setVisibilityFilter('ALL');
    setSelectedItemId('');
    setCurrentPage(1);
  };

  const resetFiltersForRequestedItem = useCallback(() => {
    setQuery('');
    setDebouncedQuery('');
    setTypeFilter('ALL');
    setMemberFilter('ALL');
    setVisibilityFilter('ALL');
    setViewMode('ACTIVE');
    setCurrentPage(1);
    setSelectedItemId('');
    setItemOpenNotice('');
  }, []);

  useEffect(() => {
    setItemOpenNotice('');
    setResolvedRequestedItemKey('');
    setResetRequestedItemKey('');
  }, [requestedItemKey]);

  useEffect(() => {
    if (!requestedItemKey || !requestedItemId || !activeFamilyId || isLoading) return;
    if (resolvedRequestedItemKey === requestedItemKey) return;

    if (pageData.items.some((item) => item.id === requestedItemId)) {
      setSelectedItemId(requestedItemId);
      setResolvedRequestedItemKey(requestedItemKey);
      setItemOpenNotice('');
      return;
    }

    const needsReset = viewMode !== 'ACTIVE'
      || currentPage !== 1
      || query.trim().length > 0
      || debouncedQuery.trim().length > 0
      || typeFilter !== 'ALL'
      || memberFilter !== 'ALL'
      || visibilityFilter !== 'ALL';

    if (needsReset && resetRequestedItemKey !== requestedItemKey) {
      setResetRequestedItemKey(requestedItemKey);
      resetFiltersForRequestedItem();
      return;
    }

    setSelectedItemId('');
    setResolvedRequestedItemKey(requestedItemKey);
    setItemOpenNotice('当前条目暂时无法在记忆库中打开，可以直接去原始页面补充或检查权限范围。');
  }, [
    activeFamilyId,
    currentPage,
    debouncedQuery,
    isLoading,
    memberFilter,
    pageData.items,
    query,
    requestedItemId,
    requestedItemKey,
    resetFiltersForRequestedItem,
    resetRequestedItemKey,
    resolvedRequestedItemKey,
    typeFilter,
    viewMode,
    visibilityFilter,
  ]);

  const runRecallDebug = async () => {
    if (!activeFamilyId || !recallQuery.trim()) return;
    setIsRecalling(true);
    setRecallError('');
    try {
      const result = await memoryApi.recallFamily(activeFamilyId, {
        query: recallQuery.trim(),
        limit: 8,
        diaryLimit: 8,
        memoryLimit: 8,
      });
      setRecallResult(result);
    } catch (err) {
      setRecallResult(null);
      setRecallError(err instanceof Error ? err.message : '召回调试失败');
    } finally {
      setIsRecalling(false);
    }
  };

  const handleArchiveSuggestionItem = async (item: MemoryLibraryItem) => {
    if (!activeFamilyId || !item.id) return;
    const confirmed = window.confirm('确认归档这条记忆吗？归档后不会被删除，但会退出默认展示和普通召回。');
    if (!confirmed) return;
    setArchivingItemId(item.id);
    setError('');
    try {
      await memoryLibraryApi.archiveItem(activeFamilyId, item.id);
      setSelectedItemId((current) => (current === item.id ? '' : current));
      await Promise.all([
        loadData(),
        loadMaintenanceSuggestions(),
      ]);
    } catch (err) {
      setError(err instanceof Error ? err.message : '归档失败');
    } finally {
      setArchivingItemId('');
    }
  };

  const handleRestoreItem = async (item: MemoryLibraryItem) => {
    if (!activeFamilyId || !item.id) return;
    const confirmed = window.confirm('确认恢复这条记忆吗？恢复后会重新进入默认展示，并可能被家族 Agent 按权限召回。');
    if (!confirmed) return;
    setRestoringItemId(item.id);
    setError('');
    try {
      await memoryLibraryApi.restoreItem(activeFamilyId, item.id);
      setSelectedItemId((current) => (current === item.id ? '' : current));
      await Promise.all([
        loadData(),
        loadMaintenanceSuggestions(),
      ]);
    } catch (err) {
      setError(err instanceof Error ? err.message : '恢复失败');
    } finally {
      setRestoringItemId('');
    }
  };

  const handleMergeSuggestion = async (suggestion: MemoryMaintenanceSuggestion) => {
    if (!activeFamilyId) return;
    const mergeItems = (suggestion.items || []).slice(0, 2);
    if (mergeItems.length < 2) return;
    const [primaryItem, secondaryItem] = mergeItems;
    const confirmed = window.confirm('确认将这两条经验合并为一条更凝练的家族记忆吗？合并后第二条会进入归档箱。');
    if (!confirmed) return;

    const suggestionKey = `${primaryItem.id}-${secondaryItem.id}`;
    setMergingSuggestionKey(suggestionKey);
    setError('');
    try {
      await memoryLibraryApi.mergeItems(activeFamilyId, primaryItem.id, secondaryItem.id);
      setSelectedItemId(primaryItem.id);
      await Promise.all([
        loadData(),
        loadMaintenanceSuggestions(),
      ]);
    } catch (err) {
      setError(err instanceof Error ? err.message : '合并失败');
    } finally {
      setMergingSuggestionKey('');
    }
  };

  const handleGenerateClassicalDraft = async (item: MemoryLibraryItem) => {
    if (!canClassicalizeSelectedItem || !item.body?.trim()) return;
    setClassicalizingItemId(item.id);
    setClassicalDraftPreview(null);
    setError('');
    setSuccess('');
    try {
      const result = await memoryApi.createHeritageClassicalDraft({
        content: item.body.trim(),
        memoryType: item.type,
        scenario: metadataText(item, 'scenario'),
        familyContext: activeFamily?.description || activeFamily?.name || '',
      });
      setClassicalDraftPreview(result.data);
    } catch (err) {
      setError(err instanceof Error ? err.message : '古文提炼失败');
    } finally {
      setClassicalizingItemId('');
    }
  };

  const handleApplyClassicalDraft = async (item: MemoryLibraryItem) => {
    if (!activeFamilyId || !classicalDraftPreview) return;
    setApplyingClassicalItemId(item.id);
    setError('');
    setSuccess('');
    try {
      await memoryLibraryApi.classicalizeItem(
        activeFamilyId,
        item.id,
        classicalDraftPreview.classicalText,
        classicalDraftPreview.plainSummary,
        classicalDraftPreview.styleNote,
      );
      await Promise.all([
        loadData(),
        loadMaintenanceSuggestions(),
      ]);
      setClassicalDraftPreview(null);
      setSuccess('已将古文稿覆盖回这条经验，原内容已保留在元数据中。');
    } catch (err) {
      setError(err instanceof Error ? err.message : '古文覆盖失败');
    } finally {
      setApplyingClassicalItemId('');
    }
  };

  const handleVoteExperience = async (item: MemoryLibraryItem, voteType: 'UP' | 'DOWN') => {
    const memoryId = parseLibraryItemNumericId(item.id, 'memory');
    if (!memoryId) return;
    const actionKey = `${item.id}-${voteType}`;
    setVotingActionKey(actionKey);
    setError('');
    try {
      await memoryApi.voteFamilyMemory(memoryId, voteType);
      await Promise.all([
        loadData(),
        loadMaintenanceSuggestions(),
      ]);
    } catch (err) {
      setError(err instanceof Error ? err.message : '反馈提交失败');
    } finally {
      setVotingActionKey('');
    }
  };

  const handleMarkObservationStale = async (item: MemoryLibraryItem) => {
    const recordId = parseLibraryItemNumericId(item.id, 'growth');
    if (!recordId) return;
    setStaleActionItemId(item.id);
    setError('');
    try {
      await growthGuardApi.markStale(recordId);
      await Promise.all([
        loadData(),
        loadMaintenanceSuggestions(),
      ]);
    } catch (err) {
      setError(err instanceof Error ? err.message : '标记失败');
    } finally {
      setStaleActionItemId('');
    }
  };

  const handleArchiveItem = async (item: MemoryLibraryItem) => {
    if (!canManageLibrary) {
      setError('只有家族创建者可以归档家族记录');
      return;
    }
    await handleArchiveSuggestionItem(item);
  };

  const handleDeleteArchivedItem = async (item: MemoryLibraryItem) => {
    if (!activeFamilyId || !item.id) return;
    const confirmed = window.confirm('确认永久删除这条已归档记忆吗？删除后无法恢复，也不会再进入家族 Agent 召回。');
    if (!confirmed) return;
    setDeletingItemId(item.id);
    setError('');
    try {
      await memoryLibraryApi.deleteArchivedItem(activeFamilyId, item.id);
      setSelectedItemId((current) => (current === item.id ? '' : current));
      await Promise.all([
        loadData(),
        loadMaintenanceSuggestions(),
      ]);
    } catch (err) {
      setError(err instanceof Error ? err.message : '删除失败');
    } finally {
      setDeletingItemId('');
    }
  };

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedQuery(query.trim());
      setCurrentPage(1);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [query]);

  useEffect(() => {
    setCurrentPage(1);
  }, [memberFilter, pageSize, typeFilter, viewMode, visibilityFilter]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  useEffect(() => {
    void loadMaintenanceSuggestions();
  }, [loadMaintenanceSuggestions]);

  useEffect(() => {
    if (pageData.totalPages > 0 && currentPage > pageData.totalPages) {
      setCurrentPage(pageData.totalPages);
    }
  }, [currentPage, pageData.totalPages]);

  useEffect(() => {
    setClassicalDraftPreview(null);
    setClassicalizingItemId('');
    setApplyingClassicalItemId('');
  }, [selectedItemId, viewMode]);

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
        <BookHeart className="mx-auto mb-3 h-10 w-10 text-gray-300" />
        <h1 className="text-lg font-semibold text-gray-900">先创建一个家族空间</h1>
        <p className="mt-2 text-sm text-gray-500">全部记忆会统一收纳当下记录、沉淀经验、守护观察和 AI 摘要。</p>
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
            <h1 className="text-xl font-bold text-gray-900">{embedded ? '全部记忆' : '记忆整理'}</h1>
            <p className="mt-1 max-w-2xl text-sm leading-6 text-gray-500">
              统一检索 {activeFamily?.name || '当前家族'} 的记录、经验、观察和 AI 摘要。这里是浏览与整理入口，具体编辑仍回到对应来源页面。
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
              onClick={() => loadData()}
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
                <p className="text-lg font-bold text-gray-900">{counts[option.value as LibraryItemType]}</p>
                <p className="text-xs text-gray-500">{option.label}</p>
              </button>
            );
          })}
        </div>
      </section>

      {error && (
        <div className="mb-4 rounded-lg border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">
          {error}
        </div>
      )}

      {success && (
        <div className="mb-4 rounded-lg border border-green-100 bg-green-50 px-4 py-3 text-sm text-green-700">
          {success}
        </div>
      )}

      {itemOpenNotice && (
        <div className="mb-4 rounded-lg border border-amber-100 bg-amber-50 px-4 py-3 text-sm text-amber-800">
          {itemOpenNotice}
        </div>
      )}

      {viewMode === 'ACTIVE' && canManageLibrary && (
      <section className="mb-4 rounded-xl border border-gray-200 bg-white p-4">
        <div className="mb-3 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="text-sm font-semibold text-gray-900">记忆整理建议</h2>
            <p className="mt-1 text-sm leading-6 text-gray-500">
              系统只给出合并、归档和清理复核建议，不会自动删除家族记忆。
            </p>
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
            正在评估记忆生命周期...
          </div>
        ) : maintenanceSuggestions.length === 0 ? (
          <div className="rounded-lg border border-green-100 bg-green-50 px-3 py-2 text-sm text-green-700">
            暂无明显需要整理的候选。当前记忆库状态比较干净。
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-3 lg:grid-cols-3">
            {maintenanceSuggestions.slice(0, 6).map((suggestion, index) => {
              const meta = maintenanceActionMeta(suggestion.action);
              const firstItem = suggestion.items?.[0];
              const secondItem = suggestion.items?.[1];
              const suggestionKey = firstItem && secondItem ? `${firstItem.id}-${secondItem.id}` : '';
              return (
                <article key={`${suggestion.action}-${index}-${firstItem?.id || 'item'}`} className={`rounded-lg border p-3 ${meta.tone}`}>
                  <div className="mb-2 flex items-start justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <AlertTriangle className={`h-4 w-4 ${meta.iconTone}`} />
                      <span className="text-xs font-semibold">{meta.label}</span>
                    </div>
                    <span className="rounded-full bg-white/70 px-2 py-0.5 text-[11px] font-medium">
                      {suggestion.score} 分
                    </span>
                  </div>
                  <p className="text-sm font-semibold text-gray-900">{suggestion.title}</p>
                  <p className="mt-1 line-clamp-2 text-xs leading-5 text-gray-600">{suggestion.reason}</p>
                  <div className="mt-2 space-y-1">
                    {(suggestion.items || []).slice(0, 2).map((item) => (
                      <div key={item.id} className="rounded-md bg-white/70 p-1.5">
                        <button
                          type="button"
                          onClick={() => setSelectedItemId(item.id)}
                          className="block w-full truncate text-left text-xs text-gray-700 hover:text-gray-900"
                          title={item.title}
                        >
                          {sourceLabel(item)} · {item.title}
                        </button>
                        {suggestion.action !== 'MERGE_REVIEW' && (
                          <button
                            type="button"
                            onClick={() => { void handleArchiveSuggestionItem(item); }}
                            disabled={archivingItemId === item.id}
                            className="mt-1 inline-flex h-6 items-center gap-1 rounded-md border border-gray-200 bg-white px-2 text-[11px] font-medium text-gray-600 hover:bg-gray-50 disabled:opacity-60"
                          >
                            {archivingItemId === item.id && <Loader2 className="h-3 w-3 animate-spin" />}
                            确认归档
                          </button>
                        )}
                      </div>
                    ))}
                  </div>
                  {suggestion.action === 'MERGE_REVIEW' && (
                    <button
                      type="button"
                      onClick={() => { void handleMergeSuggestion(suggestion); }}
                      disabled={!suggestionKey || mergingSuggestionKey === suggestionKey}
                      className="mt-3 inline-flex h-7 items-center gap-1 rounded-md border border-blue-200 bg-white px-2.5 text-[11px] font-medium text-blue-700 hover:bg-blue-50 disabled:opacity-60"
                    >
                      {mergingSuggestionKey === suggestionKey && <Loader2 className="h-3 w-3 animate-spin" />}
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

      {viewMode === 'ACTIVE' && canUseRecallDebug && (
      <section className="mb-4 rounded-xl border border-gray-200 bg-white p-4">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <h2 className="text-sm font-semibold text-gray-900">RAG 召回调试</h2>
            <p className="mt-1 text-sm leading-6 text-gray-500">
              输入一句家族 Agent 可能收到的问题，查看系统会按权限召回哪些记录、经验和守护观察。
            </p>
          </div>
          {recallResult && (
            <div className="flex flex-wrap gap-2 text-xs text-gray-500">
              <span className="rounded-full bg-gray-100 px-2.5 py-1">模式：{recallResult.retrievalMode || '未知'}</span>
              <span className="rounded-full bg-gray-100 px-2.5 py-1">Embedding：{recallResult.embeddingReadyCount ?? 0}</span>
              <span className="rounded-full bg-rose-50 px-2.5 py-1 text-rose-700">记录 {recallResult.diaryCount ?? recallResult.diaries?.length ?? 0}</span>
              <span className="rounded-full bg-amber-50 px-2.5 py-1 text-amber-700">经验 {recallResult.memoryCount ?? recallResult.memories?.length ?? 0}</span>
              <span className="rounded-full bg-emerald-50 px-2.5 py-1 text-emerald-700">观察 {recallResult.growthRecordCount ?? recallResult.growthRecords?.length ?? 0}</span>
            </div>
          )}
        </div>

        <div className="mt-3 flex flex-col gap-2 sm:flex-row">
          <label className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
            <input
              name="recallQuery"
              value={recallQuery}
              onChange={(event) => setRecallQuery(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') void runRecallDebug();
              }}
              placeholder="例如：孩子最近总是不愿意说学校的事，我们家以前有什么相关经验？"
              className="h-10 w-full rounded-lg border border-gray-200 pl-9 pr-3 text-sm outline-none focus:ring-2 focus:ring-blue-500"
            />
          </label>
          <button
            type="button"
            onClick={() => void runRecallDebug()}
            disabled={isRecalling || !activeFamilyId || !recallQuery.trim()}
            className="inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-gray-900 px-4 text-sm font-medium text-white hover:bg-gray-800 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {isRecalling ? <Loader2 className="h-4 w-4 animate-spin" /> : <Bot className="h-4 w-4" />}
            测试召回
          </button>
        </div>

        {recallError && (
          <div className="mt-3 rounded-lg border border-red-100 bg-red-50 px-3 py-2 text-sm text-red-600">
            {recallError}
          </div>
        )}

        {recallResult && (
          <div className="mt-3 rounded-lg border border-gray-100 bg-gray-50 p-3">
            {(recallResult.sources || []).length > 0 ? (
              <div className="grid grid-cols-1 gap-2 lg:grid-cols-2">
                {(recallResult.sources || []).map((source: RagRecallSource) => (
                  <div key={`${source.sourceType}-${source.id}`} className="rounded-lg border border-gray-100 bg-white p-3">
                    <div className="mb-1 flex flex-wrap items-center gap-2">
                      <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${recallSourceTone(source.sourceType)}`}>
                        {recallSourceTypeLabel(source.sourceType)}
                      </span>
                      {source.temporalLayer && (
                        <span className="rounded-full bg-gray-100 px-2 py-0.5 text-[11px] text-gray-500">
                          {source.temporalLayer}
                        </span>
                      )}
                    </div>
                    <p className="text-sm font-medium text-gray-900">{source.title || '未命名资料'}</p>
                    <p className="mt-1 line-clamp-2 text-xs leading-5 text-gray-500">{source.snippet || '暂无摘要'}</p>
                    {(source.topics?.length || source.scenes?.length) ? (
                      <div className="mt-2 flex flex-wrap gap-1">
                        {[...(source.topics || []), ...(source.scenes || [])].slice(0, 5).map((tag) => (
                          <span key={`${source.id}-${tag}`} className="rounded bg-gray-100 px-1.5 py-0.5 text-[11px] text-gray-500">
                            #{tag}
                          </span>
                        ))}
                      </div>
                    ) : null}
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-sm text-gray-500">没有召回到可用来源。可以尝试换一种问法，或检查相关记录是否已生成索引。</p>
            )}
          </div>
        )}
      </section>
      )}

      <section className="mb-4 rounded-xl border border-gray-200 bg-white p-4">
        <div className="grid grid-cols-1 gap-3 lg:grid-cols-[1.4fr_0.8fr_0.8fr_0.8fr]">
          <label className="relative">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
            <input
              name="query"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="搜索标题、正文、成员、标签..."
              className="h-10 w-full rounded-lg border border-gray-200 pl-9 pr-3 text-sm outline-none focus:ring-2 focus:ring-blue-500"
            />
          </label>
          <select
            name="typeFilter"
            value={typeFilter}
            onChange={(event) => setTypeFilter(event.target.value as LibraryItemType | 'ALL')}
            className="h-10 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
          >
            {typeOptions.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
          <select
            name="memberFilter"
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
            name="visibilityFilter"
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
        <div className="mt-3 flex flex-wrap items-center gap-2 text-xs text-gray-500">
          <span>共 {pageData.total} 条{viewMode === 'ARCHIVED' ? '归档结果' : '结果'}</span>
          {pageData.total > 0 && (
            <span>
              当前显示第 {pageStart}-{pageEnd} 条
            </span>
          )}
          {(query || typeFilter !== 'ALL' || memberFilter !== 'ALL' || visibilityFilter !== 'ALL') && (
            <button
              type="button"
              onClick={resetFilters}
              className="text-blue-600 hover:underline"
            >
              清空筛选
            </button>
          )}
        </div>
      </section>

      <section className="rounded-xl border border-gray-200 bg-white">
        {isLoading ? (
          <div className="flex h-64 items-center justify-center text-gray-400">
            <Loader2 className="mr-2 h-5 w-5 animate-spin" />
            正在整理家族记忆...
          </div>
        ) : pageData.items.length === 0 ? (
          <div className="flex h-64 items-center justify-center text-center">
            <div className="text-gray-400">
              <BookHeart className="mx-auto mb-3 h-10 w-10 opacity-40" />
              <p className="text-sm">{viewMode === 'ARCHIVED' ? '归档箱里暂无匹配记忆。' : '没有找到匹配的家族记忆。'}</p>
            </div>
          </div>
        ) : (
          <div className="divide-y divide-gray-100">
            {pageData.items.map((item) => {
              const meta = typeMeta[item.sourceType];
              const Icon = meta.icon;
              const status = assetStatus(item);
              const missing = missingInfoLabels(item);
              const usages = aiUsageLabels(item);
              const voteStats = familyExperienceVoteStats(item);
              const stalenessStats = growthObservationStalenessStats(item);
              return (
                <article key={item.id} className="p-4 transition-colors hover:bg-gray-50 sm:p-5">
                  <div className="flex items-start gap-3">
                    <span className={`inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-lg ${meta.tone}`}>
                      <Icon className="h-5 w-5" />
                    </span>
                    <div className="min-w-0 flex-1">
                      <div className="mb-1 flex flex-wrap items-center gap-2">
                        <h2 className="min-w-0 truncate text-sm font-semibold text-gray-900">{item.title}</h2>
                        <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${meta.badge}`}>
                          {meta.label}
                        </span>
                        <span className="inline-flex items-center gap-1 rounded-full bg-gray-100 px-2 py-0.5 text-[11px] text-gray-600">
                          <Shield className="h-3 w-3" />
                          {visibilityLabel(item.visibility)}
                        </span>
                        <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${status.tone}`}>
                          {status.label}
                        </span>
                      </div>
                      <p className="line-clamp-2 text-sm leading-6 text-gray-600">{item.body || '暂无摘要内容'}</p>
                      <div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-gray-400">
                        <span>{item.memberName}</span>
                        <span>{formatDate(item.createdAt)}</span>
                        <span className="rounded bg-gray-100 px-2 py-0.5 text-gray-500">{originLabel(item)}</span>
                        {(item.tags || []).slice(0, 4).map((tag) => (
                          <span key={`${item.id}-${tag}`} className="rounded bg-gray-100 px-2 py-0.5">#{tag}</span>
                        ))}
                      </div>
                      <div className="mt-2 flex flex-wrap gap-1.5 text-[11px]">
                        {usages.slice(0, 3).map((usage) => (
                          <span key={`${item.id}-${usage}`} className="rounded-full bg-blue-50 px-2 py-0.5 text-blue-700">
                            {usage}
                          </span>
                        ))}
                        {missing.slice(0, 2).map((label) => (
                          <span key={`${item.id}-${label}`} className="rounded-full bg-yellow-50 px-2 py-0.5 text-yellow-700">
                            {label}
                          </span>
                        ))}
                      </div>
                      {viewMode === 'ACTIVE' && (canVoteFamilyExperience(item) || canMarkGrowthObservationStale(item)) && (
                        <div className="mt-3 flex flex-wrap items-center gap-2 text-[11px]">
                          {canVoteFamilyExperience(item) && (
                            <>
                              <span className="rounded-full bg-amber-50 px-2 py-0.5 text-amber-700">
                                赞 {voteStats.upVotes} · 踩 {voteStats.downVotes}
                              </span>
                              <button
                                type="button"
                                onClick={() => { void handleVoteExperience(item, 'UP'); }}
                                disabled={votingActionKey === `${item.id}-UP`}
                                className={`inline-flex h-7 items-center gap-1 rounded-md border px-2.5 font-medium disabled:opacity-60 ${
                                  voteStats.myVote === 'UP'
                                    ? 'border-amber-200 bg-amber-100 text-amber-800'
                                    : 'border-amber-200 bg-white text-amber-700 hover:bg-amber-50'
                                }`}
                              >
                                {votingActionKey === `${item.id}-UP` ? <Loader2 className="h-3 w-3 animate-spin" /> : <ThumbsUp className="h-3 w-3" />}
                                点赞
                              </button>
                              <button
                                type="button"
                                onClick={() => { void handleVoteExperience(item, 'DOWN'); }}
                                disabled={votingActionKey === `${item.id}-DOWN`}
                                className={`inline-flex h-7 items-center gap-1 rounded-md border px-2.5 font-medium disabled:opacity-60 ${
                                  voteStats.myVote === 'DOWN'
                                    ? 'border-rose-200 bg-rose-100 text-rose-800'
                                    : 'border-rose-200 bg-white text-rose-700 hover:bg-rose-50'
                                }`}
                              >
                                {votingActionKey === `${item.id}-DOWN` ? <Loader2 className="h-3 w-3 animate-spin" /> : <ThumbsDown className="h-3 w-3" />}
                                点踩
                              </button>
                            </>
                          )}
                          {canMarkGrowthObservationStale(item) && (
                            <>
                              <span className="rounded-full bg-emerald-50 px-2 py-0.5 text-emerald-700">
                                过时票 {stalenessStats.staleVotes}
                              </span>
                              <button
                                type="button"
                                onClick={() => { void handleMarkObservationStale(item); }}
                                disabled={staleActionItemId === item.id || stalenessStats.myVoted}
                                className={`inline-flex h-7 items-center gap-1 rounded-md border px-2.5 font-medium disabled:opacity-60 ${
                                  stalenessStats.myVoted
                                    ? 'border-emerald-200 bg-emerald-100 text-emerald-800'
                                    : 'border-emerald-200 bg-white text-emerald-700 hover:bg-emerald-50'
                                }`}
                              >
                                {staleActionItemId === item.id && <Loader2 className="h-3 w-3 animate-spin" />}
                                {stalenessStats.myVoted ? '已标记可能过时' : '标记可能过时'}
                              </button>
                            </>
                          )}
                        </div>
                      )}
                    </div>
                    <div className="flex shrink-0 flex-col gap-2 sm:flex-row">
                      <button
                        type="button"
                        onClick={() => setSelectedItemId(item.id)}
                        className="inline-flex h-8 items-center gap-1.5 rounded-lg bg-blue-600 px-3 text-xs font-medium text-white hover:bg-blue-700"
                      >
                        查看详情
                      </button>
                      {viewMode === 'ARCHIVED' ? (
                        <>
                          {canManageLibrary && (
                            <>
                              <button
                                type="button"
                                onClick={() => { void handleRestoreItem(item); }}
                                disabled={restoringItemId === item.id || deletingItemId === item.id}
                                className="inline-flex h-8 items-center gap-1.5 rounded-lg border border-gray-200 bg-white px-3 text-xs font-medium text-gray-600 hover:bg-gray-50 disabled:opacity-60"
                              >
                                {restoringItemId === item.id ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <RotateCcw className="h-3.5 w-3.5" />}
                                恢复
                              </button>
                              <button
                                type="button"
                                onClick={() => { void handleDeleteArchivedItem(item); }}
                                disabled={deletingItemId === item.id || restoringItemId === item.id}
                                className="inline-flex h-8 items-center gap-1.5 rounded-lg border border-red-200 bg-white px-3 text-xs font-medium text-red-600 hover:bg-red-50 disabled:opacity-60"
                              >
                                {deletingItemId === item.id ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Trash2 className="h-3.5 w-3.5" />}
                                删除
                              </button>
                            </>
                          )}
                        </>
                      ) : (
                        <>
                          {canManageLibrary && (
                            <button
                              type="button"
                              onClick={() => { void handleArchiveItem(item); }}
                              disabled={archivingItemId === item.id}
                              className="inline-flex h-8 items-center gap-1.5 rounded-lg border border-amber-200 bg-white px-3 text-xs font-medium text-amber-700 hover:bg-amber-50 disabled:opacity-60"
                            >
                              {archivingItemId === item.id ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Archive className="h-3.5 w-3.5" />}
                              归档
                            </button>
                          )}
                        </>
                      )}
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
        )}
        {!isLoading && pageData.total > pageSizeOptions[0] && (
          <div className="flex flex-col gap-3 border-t border-gray-100 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-center gap-2 text-xs text-gray-500">
              <span>每页</span>
              <select
                name="pageSize"
                value={pageSize}
                onChange={(event) => {
                  setPageSize(Number(event.target.value));
                  setCurrentPage(1);
                }}
                className="h-8 rounded-lg border border-gray-200 bg-white px-2 text-xs text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
              >
                {pageSizeOptions.map((option) => (
                  <option key={option} value={option}>{option} 条</option>
                ))}
              </select>
              <span>第 {pageData.page || 1} / {totalPages} 页</span>
            </div>
            <div className="flex items-center gap-2">
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
                  {viewMode === 'ARCHIVED' && (
                    <span className="inline-flex items-center gap-1 rounded-full bg-gray-900 px-2 py-0.5 text-[11px] text-white">
                      <Archive className="h-3 w-3" />
                      已归档
                    </span>
                  )}
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
                    <p className="text-sm leading-6 text-amber-800">
                      家族创建者可以直接在记忆库归档这条记录，让主记忆流保持清晰，同时保留后续恢复空间。
                    </p>
                    <button
                      type="button"
                      onClick={() => { void handleArchiveItem(selectedItem); }}
                      disabled={archivingItemId === selectedItem.id}
                      className="inline-flex h-8 shrink-0 items-center justify-center gap-1.5 rounded-lg bg-white px-3 text-xs font-medium text-amber-700 ring-1 ring-amber-100 hover:bg-amber-100 disabled:opacity-60"
                    >
                      {archivingItemId === selectedItem.id ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Archive className="h-3.5 w-3.5" />}
                      归档这条记录
                    </button>
                  </div>
                </div>
              )}

              {canClassicalizeSelectedItem && selectedItem && (
                <div className="mb-4 rounded-lg border border-purple-100 bg-purple-50 p-3">
                  <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                    <div>
                      <p className="text-sm font-medium text-purple-900">古文提炼</p>
                      <p className="mt-1 text-sm text-purple-800">
                        先生成古文稿预览，确认后再覆盖原正文，原经验内容会保留在元数据里。
                      </p>
                    </div>
                    <button
                      type="button"
                      onClick={() => { void handleGenerateClassicalDraft(selectedItem); }}
                      disabled={classicalizingItemId === selectedItem.id || applyingClassicalItemId === selectedItem.id}
                      className="inline-flex h-8 shrink-0 items-center justify-center gap-1.5 rounded-lg bg-white px-3 text-xs font-medium text-purple-700 ring-1 ring-purple-100 hover:bg-purple-100 disabled:opacity-60"
                    >
                      {classicalizingItemId === selectedItem.id ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <ScrollText className="h-3.5 w-3.5" />}
                      提炼成古文
                    </button>
                  </div>
                </div>
              )}

              {viewMode === 'ACTIVE' && canVoteFamilyExperience(selectedItem) && (
                <div className="mb-4 rounded-lg border border-amber-100 bg-amber-50 p-3">
                  <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                    <div>
                      <p className="text-sm font-medium text-amber-900">家族反馈</p>
                      <p className="mt-1 text-sm text-amber-800">
                        当前赞 {familyExperienceVoteStats(selectedItem).upVotes}，踩 {familyExperienceVoteStats(selectedItem).downVotes}，
                        净支持度 {familyExperienceVoteStats(selectedItem).voteScore}
                      </p>
                    </div>
                    <div className="flex shrink-0 flex-wrap gap-2">
                      <button
                        type="button"
                        onClick={() => { void handleVoteExperience(selectedItem, 'UP'); }}
                        disabled={votingActionKey === `${selectedItem.id}-UP`}
                        className={`inline-flex h-8 items-center gap-1.5 rounded-lg px-3 text-xs font-medium disabled:opacity-60 ${
                          familyExperienceVoteStats(selectedItem).myVote === 'UP'
                            ? 'bg-amber-100 text-amber-800 ring-1 ring-amber-200'
                            : 'bg-white text-amber-700 ring-1 ring-amber-100 hover:bg-amber-100'
                        }`}
                      >
                        {votingActionKey === `${selectedItem.id}-UP` ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <ThumbsUp className="h-3.5 w-3.5" />}
                        点赞这条经验
                      </button>
                      <button
                        type="button"
                        onClick={() => { void handleVoteExperience(selectedItem, 'DOWN'); }}
                        disabled={votingActionKey === `${selectedItem.id}-DOWN`}
                        className={`inline-flex h-8 items-center gap-1.5 rounded-lg px-3 text-xs font-medium disabled:opacity-60 ${
                          familyExperienceVoteStats(selectedItem).myVote === 'DOWN'
                            ? 'bg-rose-100 text-rose-800 ring-1 ring-rose-200'
                            : 'bg-white text-rose-700 ring-1 ring-rose-100 hover:bg-rose-50'
                        }`}
                      >
                        {votingActionKey === `${selectedItem.id}-DOWN` ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <ThumbsDown className="h-3.5 w-3.5" />}
                        点踩这条经验
                      </button>
                    </div>
                  </div>
                </div>
              )}

              {viewMode === 'ACTIVE' && canMarkGrowthObservationStale(selectedItem) && (
                <div className="mb-4 rounded-lg border border-emerald-100 bg-emerald-50 p-3">
                  <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                    <div>
                      <p className="text-sm font-medium text-emerald-900">观察复核</p>
                      <p className="mt-1 text-sm text-emerald-800">
                        已有 {growthObservationStalenessStats(selectedItem).staleVotes} 人认为这条观察可能过时，需要新证据复核。
                      </p>
                    </div>
                    <button
                      type="button"
                      onClick={() => { void handleMarkObservationStale(selectedItem); }}
                      disabled={staleActionItemId === selectedItem.id || growthObservationStalenessStats(selectedItem).myVoted}
                      className={`inline-flex h-8 shrink-0 items-center justify-center gap-1.5 rounded-lg px-3 text-xs font-medium disabled:opacity-60 ${
                        growthObservationStalenessStats(selectedItem).myVoted
                          ? 'bg-emerald-100 text-emerald-800 ring-1 ring-emerald-200'
                          : 'bg-white text-emerald-700 ring-1 ring-emerald-100 hover:bg-emerald-100'
                      }`}
                    >
                      {staleActionItemId === selectedItem.id && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
                      {growthObservationStalenessStats(selectedItem).myVoted ? '你已标记可能过时' : '标记这条观察可能过时'}
                    </button>
                  </div>
                </div>
              )}

              {viewMode === 'ARCHIVED' && canManageLibrary && (
                <div className="mb-4 rounded-lg border border-amber-100 bg-amber-50 p-3">
                  <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                    <p className="text-sm leading-6 text-amber-800">
                      这条记忆已退出默认展示和普通召回。确认仍有价值时可以恢复；确认不再需要时可以永久删除。
                    </p>
                    <div className="flex shrink-0 flex-wrap gap-2">
                      <button
                        type="button"
                        onClick={() => { void handleRestoreItem(selectedItem); }}
                        disabled={restoringItemId === selectedItem.id || deletingItemId === selectedItem.id}
                        className="inline-flex h-8 items-center justify-center gap-1.5 rounded-lg bg-white px-3 text-xs font-medium text-amber-700 ring-1 ring-amber-100 hover:bg-amber-100 disabled:opacity-60"
                      >
                        {restoringItemId === selectedItem.id ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <RotateCcw className="h-3.5 w-3.5" />}
                        恢复这条记忆
                      </button>
                      <button
                        type="button"
                        onClick={() => { void handleDeleteArchivedItem(selectedItem); }}
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
                <p className="mb-2 text-xs font-medium text-gray-400">资产状态</p>
                <div className="flex flex-wrap gap-2">
                  <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${assetStatus(selectedItem).tone}`}>
                    {assetStatus(selectedItem).label}
                  </span>
                  <span className="rounded-full bg-gray-100 px-2.5 py-1 text-xs text-gray-600">
                    {originLabel(selectedItem)}
                  </span>
                </div>
                <div className="mt-3">
                  <p className="mb-1 text-xs font-medium text-gray-400">AI 回流范围</p>
                  <div className="flex flex-wrap gap-1.5">
                    {aiUsageLabels(selectedItem).map((usage) => (
                      <span key={`detail-${selectedItem.id}-${usage}`} className="rounded-full bg-blue-50 px-2 py-0.5 text-[11px] text-blue-700">
                        {usage}
                      </span>
                    ))}
                  </div>
                </div>
                <div className="mt-3">
                  <p className="mb-1 text-xs font-medium text-gray-400">可补充信息</p>
                  {missingInfoLabels(selectedItem).length > 0 ? (
                    <div className="flex flex-wrap gap-1.5">
                      {missingInfoLabels(selectedItem).map((label) => (
                        <span key={`missing-${selectedItem.id}-${label}`} className="rounded-full bg-yellow-50 px-2 py-0.5 text-[11px] text-yellow-700">
                          {label}
                        </span>
                      ))}
                    </div>
                  ) : (
                    <p className="text-xs text-green-700">基础信息较完整，可以被 AI 更稳定地召回和活化。</p>
                  )}
                </div>
              </div>

              <div className="mb-4 rounded-lg border border-blue-100 bg-blue-50 p-4">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="text-sm font-medium text-blue-900">来源依据</p>
                  <span className="rounded-full bg-white px-2.5 py-1 text-[11px] font-medium text-blue-700">
                    {sourceLabel(selectedItem)}
                  </span>
                </div>
                <p className="mt-2 text-sm leading-6 text-blue-900/90">
                  {evidenceDescription(selectedItem)}
                </p>
                <div className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
                  <div className="rounded-lg bg-white/80 p-3">
                    <p className="text-xs font-medium text-gray-400">内容类型</p>
                    <p className="mt-1 text-sm text-gray-800">{sourceLabel(selectedItem)}</p>
                  </div>
                  <div className="rounded-lg bg-white/80 p-3">
                    <p className="text-xs font-medium text-gray-400">保存来源</p>
                    <p className="mt-1 text-sm text-gray-800">{originLabel(selectedItem)}</p>
                  </div>
                  <div className="rounded-lg bg-white/80 p-3">
                    <p className="text-xs font-medium text-gray-400">关联成员</p>
                    <p className="mt-1 text-sm text-gray-800">{selectedItem.memberName}</p>
                  </div>
                  <div className="rounded-lg bg-white/80 p-3">
                    <p className="text-xs font-medium text-gray-400">记录时间</p>
                    <p className="mt-1 text-sm text-gray-800">{formatDate(selectedItem.createdAt) || '未知'}</p>
                  </div>
                  <div className="rounded-lg bg-white/80 p-3 sm:col-span-2">
                    <p className="text-xs font-medium text-gray-400">可见范围</p>
                    <p className="mt-1 text-sm text-gray-800">{visibilityLabel(selectedItem.visibility)}</p>
                  </div>
                </div>
                <div className="mt-3">
                  <p className="mb-1 text-xs font-medium text-gray-400">AI 参考范围</p>
                  <div className="flex flex-wrap gap-1.5">
                    {aiUsageLabels(selectedItem).map((usage) => (
                      <span key={`detail-${selectedItem.id}-${usage}`} className="rounded-full bg-white px-2 py-0.5 text-[11px] text-blue-700 ring-1 ring-blue-100">
                        {usage}
                      </span>
                    ))}
                  </div>
                </div>
                <div className="mt-3">
                  <p className="mb-1 text-xs font-medium text-gray-400">可补充信息</p>
                  {missingInfoLabels(selectedItem).length > 0 ? (
                    <div className="flex flex-wrap gap-1.5">
                      {missingInfoLabels(selectedItem).map((label) => (
                        <span key={`missing-${selectedItem.id}-${label}`} className="rounded-full bg-yellow-50 px-2 py-0.5 text-[11px] text-yellow-700">
                          {label}
                        </span>
                      ))}
                    </div>
                  ) : (
                    <p className="text-xs text-green-700">基础信息较完整，可以被 AI 更稳定地召回和活化。</p>
                  )}
                </div>
              </div>

              <div className="rounded-lg bg-gray-50 p-4">
                <p className="whitespace-pre-wrap text-sm leading-7 text-gray-700">
                  {selectedItem.body || '暂无正文内容。'}
                </p>
              </div>

              {canClassicalizeSelectedItem && classicalDraftPreview && selectedItem && (
                <div className="mt-4 rounded-lg border border-purple-100 bg-purple-50 p-4">
                  <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                    <div>
                      <p className="text-sm font-semibold text-gray-900">{classicalDraftPreview.title}</p>
                      <p className="mt-1 text-xs leading-5 text-purple-800">{classicalDraftPreview.styleNote}</p>
                    </div>
                    <div className="flex shrink-0 flex-wrap gap-2">
                      <button
                        type="button"
                        onClick={() => { void handleApplyClassicalDraft(selectedItem); }}
                        disabled={applyingClassicalItemId === selectedItem.id}
                        className="inline-flex h-8 items-center justify-center gap-1.5 rounded-lg bg-white px-3 text-xs font-medium text-purple-700 ring-1 ring-purple-100 hover:bg-purple-100 disabled:opacity-60"
                      >
                        {applyingClassicalItemId === selectedItem.id ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <CheckCircle className="h-3.5 w-3.5" />}
                        确认覆盖原文
                      </button>
                      <button
                        type="button"
                        onClick={() => setClassicalDraftPreview(null)}
                        disabled={applyingClassicalItemId === selectedItem.id}
                        className="inline-flex h-8 items-center justify-center rounded-lg bg-white px-3 text-xs font-medium text-gray-600 ring-1 ring-gray-200 hover:bg-gray-50 disabled:opacity-60"
                      >
                        取消
                      </button>
                    </div>
                  </div>
                  <div className="mt-3 rounded-lg border border-purple-100 bg-white px-3 py-3">
                    <p className="whitespace-pre-wrap text-sm leading-7 text-gray-800">
                      {classicalDraftPreview.classicalText}
                    </p>
                  </div>
                  <div className="mt-3 rounded-lg border border-white/70 bg-white/70 px-3 py-2 text-xs leading-6 text-gray-600">
                    {classicalDraftPreview.plainSummary}
                  </div>
                </div>
              )}

              <div className="mt-4 rounded-lg border border-blue-100 bg-blue-50 p-3">
                <p className="mb-2 text-xs font-medium text-blue-700">继续探索</p>
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

              {(selectedItem.tags || []).length > 0 && (
                <div className="mt-4">
                  <p className="mb-2 text-xs font-medium text-gray-400">标签</p>
                  <div className="flex flex-wrap gap-2">
                    {(selectedItem.tags || []).map((tag) => (
                      <span key={`${selectedItem.id}-detail-${tag}`} className="rounded-full bg-gray-100 px-2 py-1 text-xs text-gray-600">
                        #{tag}
                      </span>
                    ))}
                  </div>
                </div>
              )}
            </div>

            <div className="border-t border-gray-200 p-4">
              <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                <Link
                  href={activationHref(selectedItem, activeFamilyId)}
                  className="inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 text-sm font-medium text-white hover:bg-blue-700"
                >
                  <Bot className="h-4 w-4" />
                  问 AI 这条有什么用
                </Link>
                <Link
                  href={sourceHref(selectedItem, activeFamilyId)}
                  className="inline-flex h-10 items-center justify-center gap-2 rounded-lg border border-gray-200 bg-white px-4 text-sm font-medium text-gray-600 hover:bg-gray-50"
                >
                  <CheckCircle className="h-4 w-4" />
                  前往来源页面补充
                </Link>
              </div>
            </div>
          </aside>
        </div>
      )}
    </div>
  );
}
