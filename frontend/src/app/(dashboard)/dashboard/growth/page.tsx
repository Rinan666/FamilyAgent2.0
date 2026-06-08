'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { familyApi, growthGuardApi, memoryApi } from '@/lib/api';
import { memberAge } from '@/lib/roles';
import { useViewerRole } from '@/hooks/useViewerRole';
import VoiceInputButton from '@/components/voice/VoiceInputButton';
import type {
  FamilyMember,
  GrowthGuardCategory,
  GrowthFollowUpStatus,
  GrowthGuardRecord,
  GrowthGuardReport,
  MemoryEntry,
  MemoryScope,
  WeeklyGrowthReport,
} from '@/types';
import {
  AlertTriangle,
  CheckCircle,
  HeartPulse,
  RefreshCw,
  Save,
  Shield,
  Sparkles,
  Trash2,
  Users,
} from 'lucide-react';

const categoryOptions: { value: GrowthGuardCategory; label: string }[] = [
  { value: 'POSTURE', label: '体态' },
  { value: 'DENTAL', label: '牙齿' },
  { value: 'VISION', label: '视力' },
  { value: 'SLEEP', label: '睡眠' },
  { value: 'EXERCISE', label: '运动' },
  { value: 'SCREEN_TIME', label: '屏幕时间' },
  { value: 'EMOTION', label: '情绪' },
  { value: 'COMMUNICATION', label: '沟通' },
  { value: 'OTHER', label: '其他' },
];

const visibilityOptions: { value: MemoryScope; label: string }[] = [
  { value: 'CARE_VISIBLE', label: '照护者可见' },
  { value: 'FAMILY_VISIBLE', label: '全家可见' },
  { value: 'PRIVATE', label: '仅自己可见' },
];

const followUpOptions: { value: GrowthFollowUpStatus; label: string; className: string }[] = [
  { value: 'PENDING', label: '待观察', className: 'bg-gray-100 text-gray-600' },
  { value: 'WATCHING', label: '继续关注', className: 'bg-yellow-50 text-yellow-700' },
  { value: 'IMPROVED', label: '已有改善', className: 'bg-green-50 text-green-700' },
  { value: 'ARCHIVED', label: '暂不跟进', className: 'bg-slate-100 text-slate-600' },
];

const followUpFilters: { value: GrowthFollowUpStatus | 'ALL'; label: string }[] = [
  { value: 'ALL', label: '全部' },
  { value: 'PENDING', label: '待观察' },
  { value: 'WATCHING', label: '继续关注' },
  { value: 'IMPROVED', label: '已有改善' },
  { value: 'ARCHIVED', label: '暂不跟进' },
];

const recordPageSizeOptions = [3, 6, 9];

const observerPerspectiveOptions = [
  { value: 'CAREGIVER', label: '照护者观察' },
  { value: 'SELF', label: '本人自述' },
  { value: 'ELDER', label: '长辈观察' },
  { value: 'FAMILY_MEMBER', label: '家人补充' },
  { value: 'OTHER', label: '其他来源' },
] as const;

const evidenceTypeOptions = [
  { value: 'OBSERVED_FACT', label: '可观察事实' },
  { value: 'SELF_REPORT', label: '本人表达' },
  { value: 'FEELING', label: '观察者感受' },
  { value: 'INFERENCE', label: '初步猜测' },
] as const;

const confidenceOptions = [
  { value: 'LOW', label: '低：线索少' },
  { value: 'MEDIUM', label: '中：可继续看趋势' },
  { value: 'HIGH', label: '高：多次或多来源' },
] as const;

const selfConfirmedOptions = [
  { value: 'UNKNOWN', label: '未确认' },
  { value: 'YES', label: '本人确认' },
  { value: 'NO', label: '本人未确认' },
] as const;

const quickTemplates: Record<GrowthGuardCategory, { text: string; severity: number }[]> = {
  POSTURE: [
    { text: '写作业时身体经常前倾，提醒后能坐直一会儿，但很快又前倾。', severity: 3 },
    { text: '坐下时有明显含胸或耸肩，建议观察桌椅高度和坐姿习惯。', severity: 3 },
    { text: '背书包或走路时有一侧肩膀更高，建议下周继续观察。', severity: 4 },
  ],
  DENTAL: [
    { text: '最近刷牙比较敷衍，需要家长提醒才会认真刷。', severity: 3 },
    { text: '换牙或牙齿排列变化明显，建议记录并考虑安排常规牙科检查。', severity: 4 },
    { text: '甜食和饮料摄入变多，担心影响牙齿健康。', severity: 3 },
  ],
  VISION: [
    { text: '看书或写字时眼睛离纸面较近，提醒后会恢复距离。', severity: 3 },
    { text: '使用屏幕后容易揉眼或说眼睛累，建议观察用眼时间。', severity: 4 },
    { text: '最近户外活动偏少，想下周增加户外时间。', severity: 3 },
  ],
  SLEEP: [
    { text: '最近入睡时间偏晚，早上起床精神一般。', severity: 3 },
    { text: '睡前容易拖延或继续看屏幕，影响入睡节奏。', severity: 3 },
    { text: '连续几天睡眠不足，建议下周优先调整作息。', severity: 4 },
  ],
  EXERCISE: [
    { text: '本周运动量偏少，主要是室内活动。', severity: 2 },
    { text: '运动后容易喊累，想观察耐力和日常活动量。', severity: 3 },
    { text: '最近户外活动明显减少，建议安排固定运动时间。', severity: 3 },
  ],
  SCREEN_TIME: [
    { text: '屏幕使用时间比预期长，结束时容易拖延。', severity: 3 },
    { text: '写作业前后容易主动找电子设备，建议设置更清晰的使用边界。', severity: 3 },
    { text: '睡前使用屏幕，可能影响入睡，需要下周重点观察。', severity: 4 },
  ],
  EMOTION: [
    { text: '最近遇到学习问题时更容易烦躁，需要家长先安抚再沟通。', severity: 3 },
    { text: '这几天表达意愿减少，建议多观察情绪和压力来源。', severity: 4 },
    { text: '被提醒时反应比较强烈，可能需要调整沟通方式。', severity: 3 },
  ],
  COMMUNICATION: [
    { text: '亲子沟通时容易很快变成催促和反驳，想尝试先听完再建议。', severity: 3 },
    { text: '孩子愿意说学校里的小事，可以继续保留轻松聊天时间。', severity: 2 },
    { text: '最近对家长建议比较抗拒，建议减少说教，改成一起制定小行动。', severity: 3 },
  ],
  OTHER: [
    { text: '有一个值得记录的小变化，先保存下来，下周再观察是否持续。', severity: 2 },
    { text: '家里长辈提到一个容易忽略的提醒，建议纳入下周观察。', severity: 3 },
    { text: '这件事暂时无法判断，只作为家庭观察记录。', severity: 2 },
  ],
};

function dateAfter(days: number) {
  const date = new Date();
  date.setDate(date.getDate() + days);
  return date.toISOString().slice(0, 10);
}

function categoryLabel(category?: string) {
  return categoryOptions.find((item) => item.value === category)?.label || '其他';
}

function severityLabel(value: number) {
  if (value >= 5) return '需要尽快跟进';
  if (value >= 4) return '建议留意';
  if (value >= 3) return '持续观察';
  return '轻微记录';
}

function memberName(member?: FamilyMember) {
  if (!member) return '家庭成员';
  return member.nickname?.trim() || member.username?.trim() || `用户 ${member.userId}`;
}

function weekRange(days = 6) {
  const end = new Date();
  const start = new Date();
  start.setDate(end.getDate() - days);
  return {
    weekStart: start.toISOString().slice(0, 10),
    weekEnd: end.toISOString().slice(0, 10),
  };
}

function reportContent(savedReport?: GrowthGuardReport): WeeklyGrowthReport | null {
  return (savedReport?.report || null) as WeeklyGrowthReport | null;
}

function followUpStatus(record: GrowthGuardRecord): GrowthFollowUpStatus {
  const raw = String(record.metadata?.followUpStatus || 'PENDING').toUpperCase();
  if (raw === 'WATCHING' || raw === 'IMPROVED' || raw === 'ARCHIVED') return raw;
  return 'PENDING';
}

function followUpStyle(status: GrowthFollowUpStatus) {
  return followUpOptions.find((option) => option.value === status) || followUpOptions[0];
}

function metadataText(record: GrowthGuardRecord, key: string) {
  const value = record.metadata?.[key];
  return typeof value === 'string' ? value : '';
}

function optionLabel<T extends readonly { value: string; label: string }[]>(options: T, value?: string) {
  return options.find((option) => option.value === value)?.label || '';
}

function buildObservationContent(fact: string, concern: string, uncertainty: string) {
  return [
    fact.trim() ? `发生了什么：${fact.trim()}` : '',
    concern.trim() ? `我有什么担心：${concern.trim()}` : '',
    uncertainty.trim() ? `我还不确定什么：${uncertainty.trim()}` : '',
  ].filter(Boolean).join('\n');
}

function stalenessStats(record: GrowthGuardRecord) {
  return record.metadata?.stalenessStats || {
    recordId: record.id,
    staleVotes: 0,
    stalenessWeight: 1,
    myVoted: false,
  };
}

export default function GrowthPage() {
  const searchParams = useSearchParams();
  const { families, activeFamilyId, setActiveFamilyId, isLoading: loadingFamilies } = useViewerRole();
  const [members, setMembers] = useState<Record<number, FamilyMember[]>>({});
  const [selectedFamilyId, setSelectedFamilyId] = useState<number | null>(null);
  const [targetUserId, setTargetUserId] = useState<number | ''>('');
  const [category, setCategory] = useState<GrowthGuardCategory>('POSTURE');
  const [factText, setFactText] = useState('');
  const [concernText, setConcernText] = useState('');
  const [uncertaintyText, setUncertaintyText] = useState('');
  const [severity, setSeverity] = useState(3);
  const [observedAt, setObservedAt] = useState(() => new Date().toISOString().slice(0, 10));
  const [followUpAt, setFollowUpAt] = useState(() => dateAfter(7));
  const [visibility, setVisibility] = useState<MemoryScope>('CARE_VISIBLE');
  const [observerPerspective, setObserverPerspective] = useState('CAREGIVER');
  const [evidenceType, setEvidenceType] = useState('OBSERVED_FACT');
  const [confidenceLevel, setConfidenceLevel] = useState('MEDIUM');
  const [selfConfirmed, setSelfConfirmed] = useState('UNKNOWN');
  const [records, setRecords] = useState<GrowthGuardRecord[]>([]);
  const [memories, setMemories] = useState<MemoryEntry[]>([]);
  const [report, setReport] = useState<WeeklyGrowthReport | null>(null);
  const [savedReports, setSavedReports] = useState<GrowthGuardReport[]>([]);
  const [loadingRecords, setLoadingRecords] = useState(false);
  const [saving, setSaving] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [organizingDraft, setOrganizingDraft] = useState(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [updatingStatusId, setUpdatingStatusId] = useState<number | null>(null);
  const [markingStaleId, setMarkingStaleId] = useState<number | null>(null);
  const [statusFilter, setStatusFilter] = useState<GrowthFollowUpStatus | 'ALL'>('ALL');
  const [recordPage, setRecordPage] = useState(1);
  const [recordPageSize, setRecordPageSize] = useState(3);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const requestedFamilyId = useMemo(() => {
    const value = Number(searchParams.get('familyId'));
    return Number.isFinite(value) && value > 0 ? value : null;
  }, [searchParams]);
  const requestedTargetUserId = useMemo(() => {
    const value = Number(searchParams.get('targetUserId'));
    return Number.isFinite(value) && value > 0 ? value : null;
  }, [searchParams]);
  const requestedCategory = useMemo(() => {
    const value = searchParams.get('category') as GrowthGuardCategory | null;
    return value && categoryOptions.some((option) => option.value === value) ? value : null;
  }, [searchParams]);

  const selectedFamily = useMemo(
    () => families.find((family) => family.id === selectedFamilyId) || null,
    [families, selectedFamilyId],
  );
  const currentMembers = useMemo(
    () => (selectedFamilyId ? members[selectedFamilyId] || [] : []),
    [members, selectedFamilyId],
  );
  const targetMember = currentMembers.find((member) => member.userId === targetUserId);
  const actionableRecords = records.filter((record) => {
    const status = followUpStatus(record);
    return status !== 'IMPROVED' && status !== 'ARCHIVED';
  });
  const visibleRecords = records.filter((record) => statusFilter === 'ALL' || followUpStatus(record) === statusFilter);
  const totalRecordPages = Math.max(1, Math.ceil(visibleRecords.length / recordPageSize));
  const safeRecordPage = Math.min(recordPage, totalRecordPages);
  const recordPageStart = (safeRecordPage - 1) * recordPageSize;
  const paginatedRecords = visibleRecords.slice(recordPageStart, recordPageStart + recordPageSize);
  const observationContent = buildObservationContent(factText, concernText, uncertaintyText);

  useEffect(() => {
    setRecordPage(1);
  }, [selectedFamilyId, statusFilter, recordPageSize]);

  const loadFamilyData = useCallback(async (familyId: number) => {
    setLoadingRecords(true);
    setError('');
    try {
      const [recordList, memoryList, memberList, reportList] = await Promise.all([
        growthGuardApi.listFamilyRecords(familyId, 40),
        memoryApi.listFamilyMemories(familyId, 20),
        familyApi.getMembers(familyId),
        growthGuardApi.listFamilyReports(familyId, 5),
      ]);
      setRecords(Array.isArray(recordList) ? recordList : []);
      setMemories(Array.isArray(memoryList) ? memoryList : []);
      setMembers((prev) => ({ ...prev, [familyId]: Array.isArray(memberList) ? memberList : [] }));
      setSavedReports(Array.isArray(reportList) ? reportList : []);
      setReport((current) => current ?? reportContent(Array.isArray(reportList) ? reportList[0] : undefined));
      if (!targetUserId && Array.isArray(memberList) && memberList.length > 0) {
        const learner = memberList.find((member) => memberAge(member) < 18) || memberList[0];
        setTargetUserId(learner.userId);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '成长记录加载失败');
    } finally {
      setLoadingRecords(false);
    }
  }, [targetUserId]);

  useEffect(() => {
    const queryFamilyId = requestedFamilyId && families.some((family) => family.id === requestedFamilyId)
      ? requestedFamilyId
      : null;
    const nextFamilyId = queryFamilyId || (activeFamilyId && families.some((family) => family.id === activeFamilyId)
      ? activeFamilyId
      : families[0]?.id ?? null);
    setSelectedFamilyId((current) => {
      if (current === nextFamilyId) return current;
      setTargetUserId('');
      setReport(null);
      return nextFamilyId;
    });
    if (queryFamilyId && activeFamilyId !== queryFamilyId) {
      setActiveFamilyId(queryFamilyId);
    }
  }, [activeFamilyId, families, requestedFamilyId, setActiveFamilyId]);

  useEffect(() => {
    if (requestedCategory) {
      setCategory(requestedCategory);
      if (!factText.trim() && !concernText.trim() && !uncertaintyText.trim()) {
        const template = quickTemplates[requestedCategory][0];
        setFactText(template.text);
        setSeverity(template.severity);
        setFollowUpAt(dateAfter(7));
      }
    }
  }, [concernText, factText, requestedCategory, uncertaintyText]);

  useEffect(() => {
    if (!requestedTargetUserId || currentMembers.length === 0) return;
    if (currentMembers.some((member) => member.userId === requestedTargetUserId)) {
      setTargetUserId(requestedTargetUserId);
    }
  }, [currentMembers, requestedTargetUserId]);

  useEffect(() => {
    if (selectedFamilyId) {
      void loadFamilyData(selectedFamilyId);
    }
  }, [loadFamilyData, selectedFamilyId]);

  const flashSuccess = (message: string) => {
    setSuccess(message);
    setTimeout(() => setSuccess(''), 3000);
  };

  const handleSave = async () => {
    if (!selectedFamilyId || !observationContent.trim()) return;
    setSaving(true);
    setError('');
    try {
      await growthGuardApi.createRecord({
        familyId: selectedFamilyId,
        targetUserId: targetUserId || undefined,
        category,
        content: observationContent,
        severity,
        observedAt,
        followUpAt: followUpAt || undefined,
        visibility,
        metadata: {
          followUpStatus: 'PENDING',
          observerPerspective,
          evidenceType,
          confidenceLevel,
          selfConfirmed,
          observationPrinciple: 'FACT_FEELING_INFERENCE_SEPARATED',
          observationParts: {
            fact: factText.trim(),
            concern: concernText.trim(),
            uncertainty: uncertaintyText.trim(),
          },
        },
      });
      setFactText('');
      setConcernText('');
      setUncertaintyText('');
      setFollowUpAt(dateAfter(7));
      flashSuccess('成长观察已保存');
      await loadFamilyData(selectedFamilyId);
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存失败');
    } finally {
      setSaving(false);
    }
  };

  const handleUpdateFollowUpStatus = async (recordId: number, nextStatus: GrowthFollowUpStatus) => {
    setUpdatingStatusId(recordId);
    setError('');
    try {
      const updated = await growthGuardApi.updateFollowUpStatus(recordId, nextStatus);
      setRecords((prev) => prev.map((record) => (record.id === recordId ? updated : record)));
      flashSuccess(`已标记为${followUpStyle(nextStatus).label}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : '更新跟进状态失败');
    } finally {
      setUpdatingStatusId(null);
    }
  };

  const handleMarkStale = async (record: GrowthGuardRecord) => {
    setMarkingStaleId(record.id);
    setError('');
    try {
      const updated = await growthGuardApi.markStale(record.id);
      setRecords((prev) => prev.map((item) => (item.id === record.id ? updated : item)));
      flashSuccess('已标记为可能过时，AI 会降低它的参考权重');
    } catch (err) {
      setError(err instanceof Error ? err.message : '过时标记失败');
    } finally {
      setMarkingStaleId(null);
    }
  };

  const handleCategoryChange = (nextCategory: GrowthGuardCategory) => {
    setCategory(nextCategory);
    if (!factText.trim() && !concernText.trim() && !uncertaintyText.trim()) {
      const template = quickTemplates[nextCategory][0];
      setFactText(template.text);
      setSeverity(template.severity);
      setFollowUpAt(dateAfter(7));
    }
  };

  const applyTemplate = (template: { text: string; severity: number }) => {
    setFactText(template.text);
    setSeverity(template.severity);
    setFollowUpAt(dateAfter(7));
  };

  const appendVoiceTranscript = useCallback((text: string) => {
    setFactText((current) => (current.trim() ? `${current.trim()}\n${text}` : text));
  }, []);

  const handleOrganizeDraft = async () => {
    if (!observationContent.trim()) return;
    setOrganizingDraft(true);
    setError('');
    try {
      const result = await memoryApi.organizeDraft({
        content: observationContent,
        scene: 'GROWTH_GUARD',
        familyContext: selectedFamily?.description || selectedFamily?.name || '',
        currentType: category,
        currentVisibility: visibility,
        target: targetMember ? memberName(targetMember) : '',
      });
      const draft = result.data;
      setFactText(draft.content || observationContent);
      setConcernText('');
      setUncertaintyText('');
      if (categoryOptions.some((option) => option.value === draft.growth_category)) {
        setCategory(draft.growth_category as GrowthGuardCategory);
      }
      if (visibilityOptions.some((option) => option.value === draft.memory_scope)) {
        setVisibility(draft.memory_scope as MemoryScope);
      }
      setSeverity(Math.max(1, Math.min(5, Number(draft.growth_severity) || severity)));
      flashSuccess(`观察草稿已整理${draft.reason ? `：${draft.reason}` : ''}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : '草稿整理失败');
    } finally {
      setOrganizingDraft(false);
    }
  };

  const handleDelete = async (id: number) => {
    setDeletingId(id);
    setError('');
    try {
      await growthGuardApi.deleteRecord(id);
      setRecords((prev) => prev.filter((record) => record.id !== id));
      flashSuccess('记录已归档');
    } catch (err) {
      setError(err instanceof Error ? err.message : '归档失败');
    } finally {
      setDeletingId(null);
    }
  };

  const handleGenerateReport = async () => {
    setGenerating(true);
    setError('');
    try {
      const result = await growthGuardApi.weeklyReport({
        familyName: selectedFamily?.name,
        records: actionableRecords.length > 0 ? actionableRecords : records,
        memories,
        target: targetMember ? memberName(targetMember) : '家庭成员',
      });
      setReport(result.data);
      if (selectedFamilyId) {
        const { weekStart, weekEnd } = weekRange();
        const saved = await growthGuardApi.createReport({
          familyId: selectedFamilyId,
          targetUserId: targetUserId || undefined,
          weekStart,
          weekEnd,
          title: result.data.title,
          summary: result.data.summary,
          visibility: 'CARE_VISIBLE',
          report: result.data,
          metadata: {
            source: 'AI_WEEKLY_REPORT',
            recordCount: actionableRecords.length > 0 ? actionableRecords.length : records.length,
            memoryCount: memories.length,
          },
        });
        setSavedReports((prev) => [saved, ...prev.filter((item) => item.id !== saved.id)].slice(0, 3));
      }
      flashSuccess('已生成并保存成长观察照护摘要');
    } catch (err) {
      setError(err instanceof Error ? err.message : '生成照护摘要失败');
    } finally {
      setGenerating(false);
    }
  };

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
        <p className="mt-2 text-sm text-gray-500">先创建或加入家族，再记录可复核的成长线索。</p>
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
      <div className="mb-5 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-xl font-bold text-gray-900">成长观察</h1>
          <p className="mt-1 text-sm text-gray-500">记录可观察事实、来源视角和不确定性。它不是诊断，也不是对人的定性；系统会按复核周期降低旧观察的权重。</p>
        </div>
      </div>

      {error && <div className="mb-4 rounded-lg bg-red-50 px-4 py-2 text-sm text-red-600">{error}</div>}
      {success && (
        <div className="mb-4 flex items-center gap-2 rounded-lg bg-green-50 px-4 py-2 text-sm text-green-700">
          <CheckCircle className="h-4 w-4" />
          {success}
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[0.9fr_1.1fr]">
        <section className="rounded-lg border border-gray-200 bg-white p-4 sm:p-5">
          <div className="mb-4 flex items-start justify-between gap-3">
            <div className="flex items-center gap-2">
              <HeartPulse className="h-5 w-5 text-green-600" />
              <div>
                <h2 className="text-sm font-semibold text-gray-900">新增观察</h2>
                <p className="mt-0.5 text-xs text-gray-500">先写事实，再补充担心和不确定性，方便以后复核是否仍然存在。</p>
              </div>
            </div>
            <span className="rounded-full bg-green-50 px-2 py-1 text-[11px] font-medium text-green-700">观察线索</span>
          </div>

          <div className="mb-4 rounded-lg border border-green-100 bg-green-50 px-3 py-2 text-xs leading-5 text-green-800">
            记录的是“可复核线索”，不是对人的判断。AI 摘要会优先参考事实，并对未确认或过期观察降低权重。
          </div>

          <div className="mb-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
            <label className="text-xs font-medium text-gray-500">
              记录对象
              <select
                name="targetUserId"
                value={targetUserId}
                onChange={(event) => setTargetUserId(Number(event.target.value))}
                className="mt-1 h-10 w-full rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
              >
                {currentMembers.map((member) => (
                  <option key={member.userId} value={member.userId}>{memberName(member)}</option>
                ))}
              </select>
            </label>
            <label className="text-xs font-medium text-gray-500">
              类别
              <select
                name="category"
                value={category}
                onChange={(event) => handleCategoryChange(event.target.value as GrowthGuardCategory)}
                className="mt-1 h-10 w-full rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
              >
                {categoryOptions.map((option) => (
                  <option key={option.value} value={option.value}>{option.label}</option>
                ))}
              </select>
            </label>
          </div>

          <div className="mb-4 rounded-lg border border-gray-200 bg-white p-3 shadow-sm">
            <div className="mb-2 flex items-center justify-between gap-3">
              <span className="text-xs font-semibold text-gray-800">核心记录</span>
              <div className="flex flex-wrap justify-end gap-2">
                <VoiceInputButton onTranscript={appendVoiceTranscript} disabled={saving || organizingDraft} />
                <button
                  type="button"
                  onClick={handleOrganizeDraft}
                  disabled={!observationContent.trim() || saving || organizingDraft}
                  className="inline-flex h-9 items-center gap-2 rounded-lg border border-purple-100 bg-purple-50 px-3 text-xs font-medium text-purple-700 hover:bg-purple-100 disabled:opacity-50"
                >
                  {organizingDraft ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <Sparkles className="h-3.5 w-3.5" />}
                  整理草稿
                </button>
              </div>
            </div>
            <div className="grid grid-cols-1 gap-3">
              <label className="text-xs font-medium text-gray-500">
                发生了什么
                <textarea
                  name="factText"
                  value={factText}
                  onChange={(event) => setFactText(event.target.value)}
                  rows={3}
                  placeholder="尽量写可观察事实，例如：写作业时身体前倾，提醒后能坐直一会儿。"
                  className="mt-1 w-full resize-none rounded-lg border border-gray-200 px-4 py-3 text-sm text-gray-800 outline-none focus:ring-2 focus:ring-blue-500"
                />
              </label>
              <label className="text-xs font-medium text-gray-500">
                我有什么担心
                <textarea
                  name="concernText"
                  value={concernText}
                  onChange={(event) => setConcernText(event.target.value)}
                  rows={2}
                  placeholder="写自己的担心或感受，例如：担心长期坐姿影响体态，但还不确定是否持续。"
                  className="mt-1 w-full resize-none rounded-lg border border-gray-200 px-4 py-3 text-sm text-gray-800 outline-none focus:ring-2 focus:ring-blue-500"
                />
              </label>
              <label className="text-xs font-medium text-gray-500">
                我还不确定什么
                <textarea
                  name="uncertaintyText"
                  value={uncertaintyText}
                  onChange={(event) => setUncertaintyText(event.target.value)}
                  rows={2}
                  placeholder="写需要继续确认的部分，例如：还不确定是桌椅高度、疲劳，还是习惯问题。"
                  className="mt-1 w-full resize-none rounded-lg border border-gray-200 px-4 py-3 text-sm text-gray-800 outline-none focus:ring-2 focus:ring-blue-500"
                />
              </label>
            </div>
          </div>

          <div className="mb-4 rounded-lg bg-gray-50 p-3">
            <div className="mb-2 flex items-center justify-between gap-3">
              <p className="text-xs font-semibold text-gray-700">快捷填充</p>
              <button
                type="button"
                onClick={() => {
                  const template = quickTemplates[category][0];
                  applyTemplate(template);
                }}
                className="text-xs text-blue-600 hover:underline"
              >
                使用推荐
              </button>
            </div>
            <div className="flex flex-wrap gap-2">
              {quickTemplates[category].map((template) => (
                <button
                  key={template.text}
                  type="button"
                  onClick={() => applyTemplate(template)}
                  className="rounded-full border border-gray-200 bg-white px-3 py-1.5 text-left text-xs text-gray-600 transition-colors hover:border-blue-200 hover:bg-blue-50 hover:text-blue-700"
                >
                  {template.text}
                </button>
              ))}
            </div>
          </div>

          <div className="mb-4 rounded-lg border border-gray-100 bg-gray-50 p-3">
            <div className="mb-3">
              <p className="text-xs font-semibold text-gray-700">补充设置</p>
              <p className="mt-0.5 text-[11px] text-gray-500">用于帮助 AI 判断来源和可靠性，可按默认值保存。</p>
            </div>

            <div className="mb-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
              <label className="text-xs font-medium text-gray-500">
                来源视角
                <select
                  name="observerPerspective"
                  value={observerPerspective}
                  onChange={(event) => setObserverPerspective(event.target.value)}
                  className="mt-1 h-10 w-full rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
                >
                  {observerPerspectiveOptions.map((option) => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </select>
              </label>
              <label className="text-xs font-medium text-gray-500">
                证据类型
                <select
                  name="evidenceType"
                  value={evidenceType}
                  onChange={(event) => setEvidenceType(event.target.value)}
                  className="mt-1 h-10 w-full rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
                >
                  {evidenceTypeOptions.map((option) => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </select>
              </label>
            </div>

            <div className="mb-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
              <label className="text-xs font-medium text-gray-500">
                置信程度
                <select
                  name="confidenceLevel"
                  value={confidenceLevel}
                  onChange={(event) => setConfidenceLevel(event.target.value)}
                  className="mt-1 h-10 w-full rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
                >
                  {confidenceOptions.map((option) => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </select>
              </label>
              <label className="text-xs font-medium text-gray-500">
                本人确认
                <select
                  name="selfConfirmed"
                  value={selfConfirmed}
                  onChange={(event) => setSelfConfirmed(event.target.value)}
                  className="mt-1 h-10 w-full rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
                >
                  {selfConfirmedOptions.map((option) => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </select>
              </label>
            </div>

            <div className="mb-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
              <label className="text-xs font-medium text-gray-500">
                观察日期
                <input
                  name="observedAt"
                  type="date"
                  value={observedAt}
                  onChange={(event) => setObservedAt(event.target.value)}
                  className="mt-1 h-10 w-full rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
                />
              </label>
              <label className="text-xs font-medium text-gray-500">
                跟进日期
                <input
                  name="followUpAt"
                  type="date"
                  value={followUpAt}
                  onChange={(event) => setFollowUpAt(event.target.value)}
                  className="mt-1 h-10 w-full rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
                />
              </label>
            </div>

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <label className="text-xs font-medium text-gray-500">
                留意程度：{severityLabel(severity)}
                <input
                  name="severity"
                  type="range"
                  min={1}
                  max={5}
                  value={severity}
                  onChange={(event) => setSeverity(Number(event.target.value))}
                  className="mt-3 w-full accent-blue-600"
                />
              </label>
              <label className="text-xs font-medium text-gray-500">
                可见范围
                <select
                  name="visibility"
                  value={visibility}
                  onChange={(event) => setVisibility(event.target.value as MemoryScope)}
                  className="mt-1 h-10 w-full rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
                >
                  {visibilityOptions.map((option) => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </select>
              </label>
            </div>
          </div>

          <button
            type="button"
            onClick={handleSave}
            disabled={!selectedFamilyId || !observationContent.trim() || saving}
            className="inline-flex h-10 w-full items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {saving ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
            保存成长观察
          </button>
        </section>

        <section className="space-y-4">
          <div className="rounded-lg border border-gray-200 bg-white p-4 sm:p-5">
            <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <h2 className="text-sm font-semibold text-gray-900">成长观察照护摘要</h2>
                <p className="mt-1 text-xs text-gray-500">面向照护者，基于观察线索和来源视角生成；默认照护可见，不构成诊断。</p>
              </div>
              <button
                type="button"
                onClick={handleGenerateReport}
                disabled={generating || records.length === 0}
                className="inline-flex h-10 items-center justify-center gap-2 rounded-lg border border-purple-200 bg-purple-50 px-4 text-sm font-medium text-purple-700 hover:bg-purple-100 disabled:opacity-50"
              >
                {generating ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Sparkles className="h-4 w-4" />}
                生成照护摘要
              </button>
            </div>

            {report ? (
              <div className="rounded-lg bg-purple-50 p-4">
                <div className="mb-2 flex flex-wrap items-center gap-2">
                  <span className="text-sm font-semibold text-gray-900">{report.title}</span>
                  <span className="rounded-full bg-white px-2 py-0.5 text-[11px] font-medium text-purple-700">照护摘要</span>
                </div>
                <p className="text-sm leading-6 text-gray-700">{report.summary}</p>
                {report.affirmations && report.affirmations.length > 0 && (
                  <div className="mt-3 rounded-lg bg-white/70 p-3">
                    <p className="mb-1 text-xs font-medium text-green-700">值得肯定</p>
                    <ul className="space-y-1 text-sm text-gray-700">
                      {report.affirmations.map((item) => <li key={item}>{item}</li>)}
                    </ul>
                  </div>
                )}
                {report.signals.length > 0 && (
                  <div className="mt-3">
                    <p className="mb-1 text-xs font-medium text-gray-500">观察信号</p>
                    <ul className="space-y-1 text-sm text-gray-700">
                      {report.signals.map((item) => <li key={item}>{item}</li>)}
                    </ul>
                  </div>
                )}
                {report.concerns && report.concerns.length > 0 && (
                  <div className="mt-3 rounded-lg bg-yellow-50 p-3">
                    <p className="mb-1 text-xs font-medium text-yellow-700">温和留意</p>
                    <ul className="space-y-1 text-sm text-yellow-900">
                      {report.concerns.map((item) => <li key={item}>{item}</li>)}
                    </ul>
                  </div>
                )}
                {report.uncertainty_notes && report.uncertainty_notes.length > 0 && (
                  <div className="mt-3 rounded-lg bg-white/70 p-3">
                    <p className="mb-1 text-xs font-medium text-gray-600">不确定性说明</p>
                    <ul className="space-y-1 text-sm text-gray-700">
                      {report.uncertainty_notes.map((item) => <li key={item}>{item}</li>)}
                    </ul>
                  </div>
                )}
                {report.family_experience_refs.length > 0 && (
                  <div className="mt-3 rounded-lg bg-white/70 p-3">
                    <p className="mb-1 text-xs font-medium text-purple-700">可参考的家族经验</p>
                    <ul className="space-y-1 text-sm text-gray-700">
                      {report.family_experience_refs.map((item) => <li key={item}>{item}</li>)}
                    </ul>
                  </div>
                )}
                <div className="mt-3">
                  <p className="mb-1 text-xs font-medium text-gray-500">下周小行动</p>
                  <ul className="space-y-1 text-sm text-gray-700">
                    {report.suggested_actions.map((item) => (
                      <li key={item} className="flex gap-2">
                        <CheckCircle className="mt-0.5 h-4 w-4 shrink-0 text-green-600" />
                        <span>{item}</span>
                      </li>
                    ))}
                  </ul>
                </div>
                <p className="mt-3 flex gap-2 text-xs text-gray-500">
                  <Shield className="h-3.5 w-3.5 shrink-0" />
                  {report.safety_note}
                </p>
              </div>
            ) : (
              <div className="rounded-lg border border-dashed border-gray-200 p-8 text-center">
                <Sparkles className="mx-auto mb-2 h-8 w-8 text-gray-300" />
                <p className="text-sm text-gray-500">保存 1 条以上成长观察后，可生成照护者可见的成长观察摘要。</p>
              </div>
            )}

            {savedReports.length > 0 && (
              <div className="mt-4 border-t border-gray-100 pt-4">
                <p className="mb-2 text-xs font-medium text-gray-500">历史照护摘要</p>
                <div className="space-y-2">
                  {savedReports.map((savedReport) => {
                    const savedContent = reportContent(savedReport);
                    return (
                      <button
                        key={savedReport.id}
                        type="button"
                        onClick={() => savedContent && setReport(savedContent)}
                        className="flex w-full items-center justify-between gap-3 rounded-lg border border-gray-100 px-3 py-2 text-left transition-colors hover:border-purple-200 hover:bg-purple-50"
                      >
                        <span className="min-w-0">
                          <span className="block truncate text-sm font-medium text-gray-800">{savedReport.title}</span>
                          <span className="mt-0.5 block text-xs text-gray-400">
                            {savedReport.weekStart} 至 {savedReport.weekEnd}
                          </span>
                        </span>
                        <span className="shrink-0 text-xs text-purple-600">查看</span>
                      </button>
                    );
                  })}
                </div>
              </div>
            )}
          </div>

          <div className="rounded-lg border border-gray-200 bg-white p-4 sm:p-5">
            <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <h2 className="text-sm font-semibold text-gray-900">最近观察</h2>
                <p className="mt-1 text-xs text-gray-500">
                  待跟进 {actionableRecords.length} 条，改善后可标记沉淀为家庭经验。
                </p>
              </div>
              <div className="flex items-center gap-2">
                <select
                  name="statusFilter"
                  value={statusFilter}
                  onChange={(event) => setStatusFilter(event.target.value as GrowthFollowUpStatus | 'ALL')}
                  className="h-8 rounded-lg border border-gray-200 bg-white px-2 text-xs text-gray-600 outline-none focus:ring-2 focus:ring-blue-500"
                >
                  {followUpFilters.map((option) => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </select>
                <button
                  type="button"
                  onClick={() => selectedFamilyId && loadFamilyData(selectedFamilyId)}
                  className="inline-flex h-8 w-8 items-center justify-center rounded-lg text-gray-500 hover:bg-gray-50"
                  aria-label="刷新成长记录"
                >
                  <RefreshCw className={`h-4 w-4 ${loadingRecords ? 'animate-spin' : ''}`} />
                </button>
              </div>
            </div>

            {loadingRecords ? (
              <div className="flex h-36 items-center justify-center text-gray-400">
                <RefreshCw className="mr-2 h-5 w-5 animate-spin" />
                加载记录...
              </div>
            ) : records.length === 0 ? (
              <div className="rounded-lg border border-dashed border-gray-200 p-8 text-center">
                <HeartPulse className="mx-auto mb-2 h-8 w-8 text-gray-300" />
                <p className="text-sm text-gray-500">还没有成长观察记录。</p>
              </div>
            ) : visibleRecords.length === 0 ? (
              <div className="rounded-lg border border-dashed border-gray-200 p-8 text-center">
                <HeartPulse className="mx-auto mb-2 h-8 w-8 text-gray-300" />
                <p className="text-sm text-gray-500">当前筛选下没有记录。</p>
              </div>
            ) : (
              <div className="space-y-3">
                {paginatedRecords.map((record) => {
                  const target = currentMembers.find((member) => member.userId === record.targetUserId);
                  const status = followUpStatus(record);
                  const statusStyle = followUpStyle(status);
                  const stale = stalenessStats(record);
                  return (
                    <article key={record.id} className="rounded-lg border border-gray-200 p-4">
                      <div className="mb-2 flex flex-wrap items-center gap-2">
                        <span className="rounded-full bg-green-50 px-2 py-0.5 text-[11px] font-medium text-green-700">
                          {categoryLabel(record.category)}
                        </span>
                        <span className="rounded-full bg-gray-100 px-2 py-0.5 text-[11px] font-medium text-gray-600">
                          {memberName(target)}
                        </span>
                        <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${statusStyle.className}`}>
                          {statusStyle.label}
                        </span>
                        {record.severity >= 4 && (
                          <span className="inline-flex items-center gap-1 rounded-full bg-yellow-50 px-2 py-0.5 text-[11px] font-medium text-yellow-700">
                            <AlertTriangle className="h-3 w-3" />
                            {severityLabel(record.severity)}
                          </span>
                        )}
                        {metadataText(record, 'confidenceLevel') === 'LOW' && (
                          <span className="rounded-full bg-blue-50 px-2 py-0.5 text-[11px] font-medium text-blue-700">
                            低置信
                          </span>
                        )}
                        {stale.staleVotes > 0 && (
                          <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-medium text-slate-600">
                            {stale.staleVotes} 人认为可能过时
                          </span>
                        )}
                      </div>
                      <p className="text-sm leading-6 text-gray-700">{record.content}</p>
                      <div className="mt-2 flex flex-wrap gap-1.5 text-[11px] text-gray-500">
                        {optionLabel(observerPerspectiveOptions, metadataText(record, 'observerPerspective')) && (
                          <span className="rounded-full bg-gray-50 px-2 py-0.5">
                            {optionLabel(observerPerspectiveOptions, metadataText(record, 'observerPerspective'))}
                          </span>
                        )}
                        {optionLabel(evidenceTypeOptions, metadataText(record, 'evidenceType')) && (
                          <span className="rounded-full bg-gray-50 px-2 py-0.5">
                            {optionLabel(evidenceTypeOptions, metadataText(record, 'evidenceType'))}
                          </span>
                        )}
                        {optionLabel(selfConfirmedOptions, metadataText(record, 'selfConfirmed')) && (
                          <span className="rounded-full bg-gray-50 px-2 py-0.5">
                            {optionLabel(selfConfirmedOptions, metadataText(record, 'selfConfirmed'))}
                          </span>
                        )}
                      </div>
                      <div className="mt-3 flex flex-wrap items-center gap-2 text-xs text-gray-400">
                        <span>观察：{record.observedAt}</span>
                        {record.followUpAt && <span>跟进：{record.followUpAt}</span>}
                        <button
                          type="button"
                          onClick={() => { void handleMarkStale(record); }}
                          disabled={Boolean(stale.myVoted) || markingStaleId === record.id}
                          className={`inline-flex items-center gap-1 rounded-md border px-2 py-1 text-xs transition-colors disabled:opacity-60 ${
                            stale.myVoted
                              ? 'border-slate-200 bg-slate-100 text-slate-600'
                              : 'border-gray-200 bg-white text-gray-500 hover:border-slate-300 hover:text-slate-700'
                          }`}
                          title="标记后，这条观察仍保留，但 AI 会降低它的当前参考权重"
                        >
                          {markingStaleId === record.id
                            ? <RefreshCw className="h-3.5 w-3.5 animate-spin" />
                            : <AlertTriangle className="h-3.5 w-3.5" />}
                          {stale.myVoted ? '已标过时' : '可能过时'}
                        </button>
                        {stale.staleVotes > 0 && (
                          <span>权重 {Number(stale.stalenessWeight || 1).toFixed(2)}</span>
                        )}
                        <div className="flex w-full flex-wrap gap-1.5 pt-1 sm:ml-auto sm:w-auto sm:pt-0">
                          {(['PENDING', 'WATCHING', 'IMPROVED'] as GrowthFollowUpStatus[]).map((nextStatus) => (
                            <button
                              key={nextStatus}
                              type="button"
                              onClick={() => handleUpdateFollowUpStatus(record.id, nextStatus)}
                              disabled={status === nextStatus || updatingStatusId === record.id}
                              className="rounded-md border border-gray-200 bg-white px-2 py-1 text-xs text-gray-500 hover:border-blue-200 hover:bg-blue-50 hover:text-blue-700 disabled:opacity-50"
                            >
                              {followUpStyle(nextStatus).label}
                            </button>
                          ))}
                        </div>
                        <button
                          type="button"
                          onClick={() => handleDelete(record.id)}
                          disabled={deletingId === record.id}
                          className="inline-flex items-center gap-1 text-gray-400 hover:text-red-600 disabled:opacity-50 sm:ml-auto"
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                          归档
                        </button>
                      </div>
                    </article>
                  );
                })}
                {visibleRecords.length > recordPageSizeOptions[0] && (
                  <div className="flex flex-col gap-3 rounded-lg border border-gray-100 bg-gray-50 px-3 py-3 sm:flex-row sm:items-center sm:justify-between">
                    <div className="flex items-center gap-2 text-xs text-gray-500">
                      <span>每页</span>
                      <select
                        name="recordPageSize"
                        value={recordPageSize}
                        onChange={(event) => {
                          setRecordPageSize(Number(event.target.value));
                          setRecordPage(1);
                        }}
                        className="h-8 rounded-lg border border-gray-200 bg-white px-2 text-xs text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
                      >
                        {recordPageSizeOptions.map((option) => (
                          <option key={option} value={option}>{option} 条</option>
                        ))}
                      </select>
                      <span>
                        第 {safeRecordPage} / {totalRecordPages} 页
                        {visibleRecords.length > 0 ? `，当前显示第 ${recordPageStart + 1}-${Math.min(recordPageStart + recordPageSize, visibleRecords.length)} 条` : ''}
                      </span>
                    </div>
                    <div className="flex items-center gap-2">
                      <button
                        type="button"
                        onClick={() => setRecordPage((page) => Math.max(1, page - 1))}
                        disabled={safeRecordPage <= 1}
                        className="h-8 rounded-lg border border-gray-200 bg-white px-3 text-xs text-gray-600 hover:border-blue-200 hover:text-blue-700 disabled:opacity-50"
                      >
                        上一页
                      </button>
                      <button
                        type="button"
                        onClick={() => setRecordPage((page) => Math.min(totalRecordPages, page + 1))}
                        disabled={safeRecordPage >= totalRecordPages}
                        className="h-8 rounded-lg border border-gray-200 bg-white px-3 text-xs text-gray-600 hover:border-blue-200 hover:text-blue-700 disabled:opacity-50"
                      >
                        下一页
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        </section>
      </div>
    </div>
  );
}
