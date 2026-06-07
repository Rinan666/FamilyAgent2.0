'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { memoryApi } from '@/lib/api';
import { useViewerRole } from '@/hooks/useViewerRole';
import VoiceInputButton from '@/components/voice/VoiceInputButton';
import type { FamilyMemoryCard, MemoryEntry, MemoryEntryType, MemoryScope } from '@/types';
import {
  AlertTriangle,
  BookHeart,
  CheckCircle,
  HeartPulse,
  RefreshCw,
  Save,
  ScrollText,
  Shield,
  Sparkles,
  Users,
} from 'lucide-react';

const typeOptions: { value: MemoryEntryType; label: string }[] = [
  { value: 'ELDER_ADVICE', label: '长者建议' },
  { value: 'FAMILY_STORY', label: '家族故事' },
  { value: 'HEALTH_REMINDER', label: '健康提醒' },
  { value: 'GROWTH_RISK', label: '成长风险' },
  { value: 'VALUE', label: '价值观' },
];

const scopeOptions: { value: MemoryScope; label: string }[] = [
  { value: 'FAMILY_VISIBLE', label: '全家可见' },
  { value: 'CARE_VISIBLE', label: '照护者可见' },
  { value: 'PRIVATE', label: '仅自己可见' },
];

const scenarioSuggestions = [
  '全家通用',
  '换牙期',
  '视力保护',
  '体态管理',
  '低年级学习习惯',
  '青春期沟通',
  '屏幕时间',
  '睡眠作息',
];

function memoryTypeLabel(type?: string) {
  return typeOptions.find((option) => option.value === type)?.label || '家族经验';
}

function scopeLabel(scope?: string) {
  return scopeOptions.find((option) => option.value === scope)?.label || '家庭可见';
}

function getMemoryCard(memory: MemoryEntry): FamilyMemoryCard | null {
  const card = memory.metadata?.memoryCard;
  if (!card || typeof card !== 'object' || Array.isArray(card)) return null;
  return card as unknown as FamilyMemoryCard;
}

function sensitivityStyle(value?: string) {
  switch ((value || '').toUpperCase()) {
    case 'HIGH':
      return 'bg-red-50 text-red-700';
    case 'MEDIUM':
      return 'bg-yellow-50 text-yellow-700';
    default:
      return 'bg-green-50 text-green-700';
  }
}

function memorySourceLabel(memory: MemoryEntry) {
  if (memory.metadata?.source === 'DIARY_PROMOTION') {
    return `来自家族日记 #${memory.metadata.sourceDiaryId || ''}`.trim();
  }
  if (memory.metadata?.source === 'HERITAGE_ENTRY') {
    return '手动录入';
  }
  return '';
}

function sourceVisibilityLabel(value?: unknown) {
  switch (value) {
    case 'PRIVATE':
      return '原日记仅自己可见';
    case 'CARE_VISIBLE':
    case 'PARENT_VISIBLE':
      return '原日记照护者可见';
    case 'FAMILY_VISIBLE':
    case 'FAMILY':
      return '原日记全家可见';
    default:
      return '';
  }
}

export default function HeritagePage() {
  const searchParams = useSearchParams();
  const { families, activeFamilyId, setActiveFamilyId, isLoading: loadingFamilies } = useViewerRole();
  const [selectedFamilyId, setSelectedFamilyId] = useState<number | null>(null);
  const [memories, setMemories] = useState<MemoryEntry[]>([]);
  const [content, setContent] = useState('');
  const [memoryType, setMemoryType] = useState<MemoryEntryType>('ELDER_ADVICE');
  const [scope, setScope] = useState<MemoryScope>('FAMILY_VISIBLE');
  const [scenario, setScenario] = useState('全家通用');
  const [draftCard, setDraftCard] = useState<FamilyMemoryCard | null>(null);
  const [loadingMemories, setLoadingMemories] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [organizingDraft, setOrganizingDraft] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const requestedFamilyId = useMemo(() => {
    const value = Number(searchParams.get('familyId'));
    return Number.isFinite(value) && value > 0 ? value : null;
  }, [searchParams]);

  const selectedFamily = useMemo(
    () => families.find((family) => family.id === selectedFamilyId) || null,
    [families, selectedFamilyId],
  );

  const appendVoiceTranscript = useCallback((text: string) => {
    setContent((current) => (current.trim() ? `${current.trim()}\n${text}` : text));
    setDraftCard(null);
  }, []);

  const loadMemories = useCallback(async (familyId: number) => {
    setLoadingMemories(true);
    try {
      const data = await memoryApi.listFamilyMemories(familyId, 30);
      setMemories(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(err instanceof Error ? err.message : '家族经验加载失败');
    } finally {
      setLoadingMemories(false);
    }
  }, []);

  useEffect(() => {
    const queryFamilyId = requestedFamilyId && families.some((family) => family.id === requestedFamilyId)
      ? requestedFamilyId
      : null;
    const nextFamilyId = queryFamilyId || (activeFamilyId && families.some((family) => family.id === activeFamilyId)
      ? activeFamilyId
      : families[0]?.id ?? null);
    setSelectedFamilyId((current) => {
      if (current === nextFamilyId) return current;
      setDraftCard(null);
      return nextFamilyId;
    });
    if (queryFamilyId && activeFamilyId !== queryFamilyId) {
      setActiveFamilyId(queryFamilyId);
    }
  }, [activeFamilyId, families, requestedFamilyId, setActiveFamilyId]);

  useEffect(() => {
    const type = searchParams.get('type') as MemoryEntryType | null;
    const scenarioParam = searchParams.get('scenario');
    if (type && typeOptions.some((option) => option.value === type)) {
      setMemoryType(type);
      setDraftCard(null);
    }
    if (scenarioParam?.trim()) {
      setScenario(scenarioParam.trim());
    }
    if (!content.trim() && type === 'ELDER_ADVICE') {
      setContent('这条经验来自：\n\n当时踩过的坑或关键教训是：\n\n如果后辈遇到类似情况，我建议：');
    }
  }, [content, searchParams]);

  useEffect(() => {
    if (selectedFamilyId) {
      void loadMemories(selectedFamilyId);
    }
  }, [loadMemories, selectedFamilyId]);

  const flashSuccess = (message: string) => {
    setSuccess(message);
    setTimeout(() => setSuccess(''), 3000);
  };

  const handleGenerateCard = async () => {
    if (!content.trim()) return;
    setGenerating(true);
    setError('');
    try {
      const result = await memoryApi.createFamilyMemoryCard({
        content,
        memoryType,
        familyContext: selectedFamily?.description || selectedFamily?.name || '',
        target: scenario,
      });
      setDraftCard(result.data);
      flashSuccess('已整理为经验卡');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'AI 整理失败');
    } finally {
      setGenerating(false);
    }
  };

  const handleOrganizeDraft = async () => {
    if (!content.trim()) return;
    setOrganizingDraft(true);
    setError('');
    try {
      const result = await memoryApi.organizeDraft({
        content,
        scene: 'HERITAGE',
        familyContext: selectedFamily?.description || selectedFamily?.name || '',
        currentType: memoryType,
        currentVisibility: scope,
        target: scenario,
      });
      const draft = result.data;
      setContent(draft.content || content);
      if (typeOptions.some((option) => option.value === draft.memory_type)) {
        setMemoryType(draft.memory_type as MemoryEntryType);
      }
      if (scopeOptions.some((option) => option.value === draft.memory_scope)) {
        setScope(draft.memory_scope as MemoryScope);
      }
      if (draft.scenario) {
        setScenario(draft.scenario);
      }
      setDraftCard(null);
      flashSuccess(`草稿已整理${draft.reason ? `：${draft.reason}` : ''}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : '草稿整理失败');
    } finally {
      setOrganizingDraft(false);
    }
  };

  const handleSave = async () => {
    if (!selectedFamilyId || !content.trim()) return;
    setSaving(true);
    setError('');
    try {
      const card = draftCard;
      await memoryApi.createFamilyMemory({
        familyId: selectedFamilyId,
        content,
        type: memoryType,
        scope,
        summary: card?.summary || content.slice(0, 120),
        importance: memoryType === 'HEALTH_REMINDER' || memoryType === 'GROWTH_RISK' ? 4 : 3,
        memoryCard: card || undefined,
        metadata: { scenario, target: scenario },
      });
      setContent('');
      setDraftCard(null);
      flashSuccess('家族经验已保存');
      await loadMemories(selectedFamilyId);
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存失败');
    } finally {
      setSaving(false);
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
        <p className="mt-2 text-sm text-gray-500">先创建或加入家族，再沉淀家族经验。</p>
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
          <h1 className="text-xl font-bold text-gray-900">家族经验</h1>
          <p className="mt-1 text-sm text-gray-500">沉淀故事、提醒和长者建议，让家族经验能被后代继续使用。</p>
        </div>
        <select
          value={selectedFamilyId ?? ''}
          onChange={(event) => {
            const familyId = Number(event.target.value);
            setSelectedFamilyId(familyId);
            setActiveFamilyId(familyId);
            setDraftCard(null);
          }}
          className="h-10 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
        >
          {families.map((family) => (
            <option key={family.id} value={family.id}>{family.name}</option>
          ))}
        </select>
      </div>

      {error && <div className="mb-4 rounded-lg bg-red-50 px-4 py-2 text-sm text-red-600">{error}</div>}
      {success && (
        <div className="mb-4 flex items-center gap-2 rounded-lg bg-green-50 px-4 py-2 text-sm text-green-700">
          <CheckCircle className="h-4 w-4" />
          {success}
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[0.95fr_1.05fr]">
        <section className="rounded-lg border border-gray-200 bg-white p-4 sm:p-5">
          <div className="mb-4 flex items-center gap-2">
            <ScrollText className="h-5 w-5 text-purple-600" />
            <h2 className="text-sm font-semibold text-gray-900">新增经验</h2>
          </div>

          <div className="mb-3 grid grid-cols-1 gap-3 sm:grid-cols-3">
            <label className="text-xs font-medium text-gray-500">
              类型
              <select
                value={memoryType}
                onChange={(event) => {
                  setMemoryType(event.target.value as MemoryEntryType);
                  setDraftCard(null);
                }}
                className="mt-1 h-10 w-full rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
              >
                {typeOptions.map((option) => (
                  <option key={option.value} value={option.value}>{option.label}</option>
                ))}
              </select>
            </label>
            <label className="text-xs font-medium text-gray-500">
              可见范围
              <select
                value={scope}
                onChange={(event) => setScope(event.target.value as MemoryScope)}
                className="mt-1 h-10 w-full rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
              >
                {scopeOptions.map((option) => (
                  <option key={option.value} value={option.value}>{option.label}</option>
                ))}
              </select>
            </label>
            <label className="text-xs font-medium text-gray-500">
              适用场景
              <input
                value={scenario}
                onChange={(event) => setScenario(event.target.value)}
                placeholder="例如：换牙期、亲子沟通、视力保护"
                className="mt-1 h-10 w-full rounded-lg border border-gray-200 px-3 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
              />
            </label>
          </div>

          <div className="mb-3 flex flex-wrap gap-2">
            {scenarioSuggestions.map((item) => (
              <button
                key={item}
                type="button"
                onClick={() => setScenario(item)}
                className={`rounded-full border px-3 py-1.5 text-xs transition-colors ${
                  scenario === item
                    ? 'border-purple-200 bg-purple-50 text-purple-700'
                    : 'border-gray-200 bg-white text-gray-500 hover:border-purple-200 hover:bg-purple-50 hover:text-purple-700'
                }`}
              >
                {item}
              </button>
            ))}
          </div>

          <div className="mb-3">
            <div className="mb-2 flex items-center justify-between gap-3">
              <span className="text-xs font-medium text-gray-500">经验内容</span>
              <div className="flex flex-wrap justify-end gap-2">
                <VoiceInputButton onTranscript={appendVoiceTranscript} disabled={saving || generating || organizingDraft} />
                <button
                  type="button"
                  onClick={handleOrganizeDraft}
                  disabled={!content.trim() || saving || generating || organizingDraft}
                  className="inline-flex h-9 items-center gap-2 rounded-lg border border-purple-100 bg-purple-50 px-3 text-xs font-medium text-purple-700 hover:bg-purple-100 disabled:opacity-50"
                >
                  {organizingDraft ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <Sparkles className="h-3.5 w-3.5" />}
                  整理草稿
                </button>
              </div>
            </div>
            <textarea
              value={content}
              onChange={(event) => {
                setContent(event.target.value);
                setDraftCard(null);
              }}
              rows={9}
              placeholder="例如：爷爷说，孩子换牙期和初中前后要特别注意牙齿、坐姿和用眼距离，很多问题小时候不明显，长大后再调整会更费劲。"
              className="w-full resize-none rounded-lg border border-gray-200 px-4 py-3 text-sm text-gray-800 outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div className="flex flex-col gap-2 sm:flex-row">
            <button
              type="button"
              onClick={handleGenerateCard}
              disabled={!content.trim() || generating || organizingDraft}
              className="inline-flex h-10 items-center justify-center gap-2 rounded-lg border border-purple-200 bg-purple-50 px-4 text-sm font-medium text-purple-700 hover:bg-purple-100 disabled:opacity-50"
            >
              {generating ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Sparkles className="h-4 w-4" />}
              AI 整理
            </button>
            <button
              type="button"
              onClick={handleSave}
              disabled={!selectedFamilyId || !content.trim() || saving}
              className="inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
            >
              {saving ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
              保存经验
            </button>
          </div>

          {draftCard && (
            <div className="mt-4 rounded-lg border border-purple-100 bg-purple-50 p-4">
              <div className="mb-2 flex flex-wrap items-center gap-2">
                <span className="text-sm font-semibold text-gray-900">{draftCard.title}</span>
                <span className="rounded-full bg-white px-2 py-0.5 text-[11px] font-medium text-purple-700">
                  {draftCard.theme}
                </span>
                <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${sensitivityStyle(draftCard.sensitivity)}`}>
                  {draftCard.sensitivity}
                </span>
              </div>
              <p className="text-sm leading-6 text-gray-700">{draftCard.summary}</p>
              {draftCard.action_suggestions.length > 0 && (
                <div className="mt-3">
                  <p className="mb-1 text-xs font-medium text-gray-500">建议行动</p>
                  <ul className="space-y-1 text-sm text-gray-700">
                    {draftCard.action_suggestions.map((item) => (
                      <li key={item} className="flex gap-2">
                        <CheckCircle className="mt-0.5 h-4 w-4 shrink-0 text-green-600" />
                        <span>{item}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
              <p className="mt-3 flex gap-2 text-xs text-gray-500">
                <Shield className="h-3.5 w-3.5 shrink-0" />
                {draftCard.safety_note}
              </p>
            </div>
          )}
        </section>

        <section className="rounded-lg border border-gray-200 bg-white p-4 sm:p-5">
          <div className="mb-4 flex items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <HeartPulse className="h-5 w-5 text-green-600" />
              <h2 className="text-sm font-semibold text-gray-900">经验卡片</h2>
            </div>
            <button
              type="button"
              onClick={() => selectedFamilyId && loadMemories(selectedFamilyId)}
              className="inline-flex h-8 w-8 items-center justify-center rounded-lg text-gray-500 hover:bg-gray-50"
              aria-label="刷新家族经验"
            >
              <RefreshCw className={`h-4 w-4 ${loadingMemories ? 'animate-spin' : ''}`} />
            </button>
          </div>

          {loadingMemories ? (
            <div className="flex h-48 items-center justify-center text-gray-400">
              <RefreshCw className="mr-2 h-5 w-5 animate-spin" />
              加载经验卡...
            </div>
          ) : memories.length === 0 ? (
            <div className="rounded-lg border border-dashed border-gray-200 p-8 text-center">
              <ScrollText className="mx-auto mb-2 h-8 w-8 text-gray-300" />
              <p className="text-sm text-gray-500">这个家族还没有经验卡。</p>
            </div>
          ) : (
            <div className="space-y-3">
              {memories.map((memory) => {
                const card = getMemoryCard(memory);
                const sourceLabel = memorySourceLabel(memory);
                const originalVisibility = sourceVisibilityLabel(memory.metadata?.sourceVisibility);
                const sourceDiaryId = String(memory.metadata?.sourceDiaryId || '');
                return (
                  <article key={memory.id} className="rounded-lg border border-gray-200 p-4">
                    <div className="mb-2 flex flex-wrap items-center gap-2">
                      <span className="text-sm font-semibold text-gray-900">
                        {card?.title || memory.summary || memoryTypeLabel(memory.type)}
                      </span>
                      <span className="rounded-full bg-gray-100 px-2 py-0.5 text-[11px] font-medium text-gray-600">
                        {memoryTypeLabel(memory.type)}
                      </span>
                      <span className="rounded-full bg-blue-50 px-2 py-0.5 text-[11px] font-medium text-blue-700">
                        {scopeLabel(memory.scope)}
                      </span>
                      {card?.sensitivity && (
                        <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${sensitivityStyle(card.sensitivity)}`}>
                          {card.sensitivity}
                        </span>
                      )}
                    </div>
                    <p className="text-sm leading-6 text-gray-700">{card?.summary || memory.content}</p>
                    {card?.risk_points && card.risk_points.length > 0 && (
                      <div className="mt-3 rounded-lg bg-yellow-50 p-3">
                        <p className="mb-1 flex items-center gap-1.5 text-xs font-medium text-yellow-700">
                          <AlertTriangle className="h-3.5 w-3.5" />
                          需要留意
                        </p>
                        <ul className="space-y-1 text-sm text-yellow-800">
                          {card.risk_points.map((item) => <li key={item}>{item}</li>)}
                        </ul>
                      </div>
                    )}
                    {card?.action_suggestions && card.action_suggestions.length > 0 && (
                      <div className="mt-3">
                        <p className="mb-1 text-xs font-medium text-gray-500">建议行动</p>
                        <ul className="space-y-1 text-sm text-gray-700">
                          {card.action_suggestions.map((item) => (
                            <li key={item} className="flex gap-2">
                              <CheckCircle className="mt-0.5 h-4 w-4 shrink-0 text-green-600" />
                              <span>{item}</span>
                            </li>
                          ))}
                        </ul>
                      </div>
                    )}
                    <div className="mt-3 flex flex-wrap items-center gap-2 text-xs text-gray-400">
                      <span>{new Date(memory.createdAt).toLocaleDateString()}</span>
                      {sourceLabel && (
                        <span className="inline-flex items-center gap-1 rounded bg-rose-50 px-2 py-0.5 text-rose-600">
                          <BookHeart className="h-3.5 w-3.5" />
                          {sourceLabel}
                        </span>
                      )}
                      {originalVisibility && (
                        <span className="rounded bg-gray-50 px-2 py-0.5">{originalVisibility}</span>
                      )}
                      {typeof memory.metadata?.scenario === 'string' && memory.metadata.scenario && (
                        <span>场景：{memory.metadata.scenario}</span>
                      )}
                      {card?.suitable_for?.length ? <span>适合：{card.suitable_for.join('、')}</span> : null}
                      {sourceDiaryId && (
                        <Link
                          href="/dashboard/diary"
                          className="ml-auto text-blue-600 hover:underline"
                        >
                          查看日记
                        </Link>
                      )}
                    </div>
                  </article>
                );
              })}
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
