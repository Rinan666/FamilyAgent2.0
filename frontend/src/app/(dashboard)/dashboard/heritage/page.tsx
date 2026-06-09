'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { heritageTaskApi, memoryApi } from '@/lib/api';
import { useViewerRole } from '@/hooks/useViewerRole';
import LegacyWorkpageNotice from '@/components/family/LegacyWorkpageNotice';
import VoiceInputButton from '@/components/voice/VoiceInputButton';
import type { FamilyMemoryCard, HeritageSaveJudge, HeritageTask, MemoryEntryType, MemoryScope } from '@/types';
import {
  CheckCircle,
  RefreshCw,
  Save,
  ScrollText,
  Shield,
  Sparkles,
  Target,
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

type EntryMode = 'INTERVIEW' | 'ATOM' | 'DIRECT';

const entryModeOptions: { value: EntryMode; label: string; description: string }[] = [
  { value: 'INTERVIEW', label: '访谈式录入', description: '先问长辈三个问题' },
  { value: 'ATOM', label: '三句话原子', description: '先写三个短句' },
  { value: 'DIRECT', label: '直接写正式稿', description: '直接编辑最终内容' },
];

const validMemoryTypes = new Set(typeOptions.map((option) => option.value));
const validMemoryScopes = new Set(scopeOptions.map((option) => option.value));

const interviewThemes: {
  id: string;
  label: string;
  description: string;
  memoryType: MemoryEntryType;
  scenario: string;
  questions: string[];
}[] = [
  {
    id: 'hard-choice',
    label: '艰难选择',
    description: '适合记录升学、职业、迁居或家庭关键决定背后的判断。',
    memoryType: 'ELDER_ADVICE',
    scenario: '人生选择',
    questions: [
      '当时发生了什么？你面对哪几个选择？',
      '你当时最看重什么？为什么最后那样决定？',
      '如果后辈遇到类似情况，你希望他们多想一步什么？',
    ],
  },
  {
    id: 'family-rule',
    label: '家族规矩',
    description: '适合沉淀家里反复强调的做人做事原则。',
    memoryType: 'VALUE',
    scenario: '家族规矩',
    questions: [
      '我们家有哪些一直不能忘的规矩或原则？',
      '这个规矩是从哪件事里来的？当时有什么教训？',
      '后辈具体在什么场景下应该想起它？',
    ],
  },
  {
    id: 'health-lesson',
    label: '健康教训',
    description: '适合记录体态、牙齿、视力、睡眠、运动等经验提醒。',
    memoryType: 'HEALTH_REMINDER',
    scenario: '健康提醒',
    questions: [
      '你见过或经历过哪件健康方面的教训？',
      '当时最容易被忽视的早期信号是什么？',
      '如果提醒孩子或家人，现在可以做哪件小事？',
    ],
  },
  {
    id: 'parent-child',
    label: '亲子沟通',
    description: '适合记录家人之间理解、冲突、陪伴和表达方式。',
    memoryType: 'ELDER_ADVICE',
    scenario: '亲子沟通',
    questions: [
      '你印象最深的一次亲子沟通或误会是什么？',
      '后来你明白了什么？如果换一种说法会怎样？',
      '你希望后辈在和家人沟通时记住什么？',
    ],
  },
];

function memoryTypeLabel(type?: string) {
  return typeOptions.find((option) => option.value === type)?.label || '家族经验';
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

function taskCompletionPrompt(task: HeritageTask) {
  const prompt = task.metadata?.completionPrompt;
  return typeof prompt === 'string' && prompt.trim()
    ? prompt
    : '写一句完成记录，例如：今天和孩子一起检查了牙齿，并约了下周复诊。';
}

interface HeritagePageProps {
  embedded?: boolean;
}

export default function HeritagePage({ embedded = false }: HeritagePageProps) {
  const searchParams = useSearchParams();
  const { families, activeFamilyId, setActiveFamilyId, viewerRole, isLoading: loadingFamilies } = useViewerRole();

  const [selectedFamilyId, setSelectedFamilyId] = useState<number | null>(null);
  const [tasks, setTasks] = useState<HeritageTask[]>([]);
  const [content, setContent] = useState('');
  const [entryMode, setEntryMode] = useState<EntryMode>('INTERVIEW');
  const [sourceMode, setSourceMode] = useState<EntryMode>('DIRECT');
  const [memoryType, setMemoryType] = useState<MemoryEntryType>('ELDER_ADVICE');
  const [scope, setScope] = useState<MemoryScope>('FAMILY_VISIBLE');
  const [scenario, setScenario] = useState('全家通用');
  const [interviewThemeId, setInterviewThemeId] = useState(interviewThemes[0].id);
  const [interviewAnswers, setInterviewAnswers] = useState<string[]>(() => interviewThemes[0].questions.map(() => ''));
  const [atomSituation, setAtomSituation] = useState('');
  const [atomThinking, setAtomThinking] = useState('');
  const [atomRedo, setAtomRedo] = useState('');
  const [draftCard, setDraftCard] = useState<FamilyMemoryCard | null>(null);
  const [organizedReason, setOrganizedReason] = useState('');
  const [saveJudge, setSaveJudge] = useState<HeritageSaveJudge | null>(null);
  const [organizingMode, setOrganizingMode] = useState<EntryMode | null>(null);
  const [loadingTasks, setLoadingTasks] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [saving, setSaving] = useState(false);
  const [completingTaskId, setCompletingTaskId] = useState<number | null>(null);
  const [completionNotes, setCompletionNotes] = useState<Record<number, string>>({});
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

  const selectedInterviewTheme = useMemo(
    () => interviewThemes.find((theme) => theme.id === interviewThemeId) || interviewThemes[0],
    [interviewThemeId],
  );

  const resetFormalDerivedState = () => {
    setDraftCard(null);
    setSaveJudge(null);
    setOrganizedReason('');
  };

  const appendVoiceTranscript = useCallback((text: string) => {
    setContent((current) => (current.trim() ? `${current.trim()}\n${text}` : text));
    resetFormalDerivedState();
    setSourceMode('DIRECT');
  }, []);

  const flashSuccess = (message: string) => {
    setSuccess(message);
    setTimeout(() => setSuccess(''), 3000);
  };

  const applyInterviewTheme = (themeId: string) => {
    const theme = interviewThemes.find((item) => item.id === themeId) || interviewThemes[0];
    setInterviewThemeId(theme.id);
    setInterviewAnswers(theme.questions.map(() => ''));
    setMemoryType(theme.memoryType);
    setScenario(theme.scenario);
    resetFormalDerivedState();
  };

  const buildInterviewDraft = () => {
    const answerLines = selectedInterviewTheme.questions
      .map((question, index) => {
        const answer = interviewAnswers[index]?.trim();
        if (!answer) return '';
        return `问题${index + 1}：${question}\n回答：${answer}`;
      })
      .filter(Boolean);
    if (answerLines.length === 0) return '';
    return [
      `访谈主题：${selectedInterviewTheme.label}`,
      `适用场景：${selectedInterviewTheme.scenario}`,
      '',
      answerLines.join('\n\n'),
    ].join('\n');
  };

  const buildAtomDraft = () => {
    const situation = atomSituation.trim();
    const thinking = atomThinking.trim();
    const redo = atomRedo.trim();
    if (!situation && !thinking && !redo) return '';
    return [
      `当时发生了什么：${situation || '未填写'}`,
      `我当时怎么想：${thinking || '未填写'}`,
      `如果重来我会怎么做：${redo || '未填写'}`,
    ].join('\n');
  };

  const organizeToFormalDraft = async (rawContent: string, mode: EntryMode) => {
    const trimmed = rawContent.trim();
    if (!trimmed) {
      setError(mode === 'INTERVIEW' ? '请先填写至少一个访谈回答。' : '请至少填写一句经验原子。');
      return;
    }
    setOrganizingMode(mode);
    setError('');
    try {
      const result = await memoryApi.organizeDraft({
        scene: 'HERITAGE',
        content: trimmed,
        currentType: mode === 'INTERVIEW' ? selectedInterviewTheme.memoryType : memoryType,
        currentVisibility: scope,
        target: mode === 'INTERVIEW' ? selectedInterviewTheme.scenario : scenario,
        familyContext: selectedFamily?.description || selectedFamily?.name || '',
      });
      const draft = result.data;
      setContent(draft.content);
      if (validMemoryTypes.has(draft.memory_type)) {
        setMemoryType(draft.memory_type as MemoryEntryType);
      } else if (mode === 'INTERVIEW') {
        setMemoryType(selectedInterviewTheme.memoryType);
      }
      if (validMemoryScopes.has(draft.memory_scope as MemoryScope)) {
        setScope(draft.memory_scope as MemoryScope);
      }
      setScenario(draft.scenario || (mode === 'INTERVIEW' ? selectedInterviewTheme.scenario : scenario || '全家通用'));
      setSourceMode(mode);
      setDraftCard(null);
      setSaveJudge(null);
      setOrganizedReason(draft.reason || 'AI 已将输入整理为正式保存草稿。');
      flashSuccess('已整理到正式保存区，可继续编辑后保存');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'AI 整理失败');
    } finally {
      setOrganizingMode(null);
    }
  };

  const loadTasks = useCallback(async (familyId: number) => {
    setLoadingTasks(true);
    try {
      const data = await heritageTaskApi.listFamilyTasks(familyId, 20);
      setTasks(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(err instanceof Error ? err.message : '家庭任务加载失败');
    } finally {
      setLoadingTasks(false);
    }
  }, []);

  useEffect(() => {
    const queryFamily = requestedFamilyId && families.some((family) => family.id === requestedFamilyId)
      ? requestedFamilyId
      : null;
    const nextFamilyId = queryFamily
      || (activeFamilyId && families.some((family) => family.id === activeFamilyId) ? activeFamilyId : null)
      || families[0]?.id
      || null;
    setSelectedFamilyId(nextFamilyId);
    if (queryFamily && activeFamilyId !== queryFamily) {
      setActiveFamilyId(queryFamily);
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
      void loadTasks(selectedFamilyId);
    }
  }, [loadTasks, selectedFamilyId]);

  const handleGenerateCard = async () => {
    const formalContent = content.trim();
    if (!formalContent) return;
    setGenerating(true);
    setError('');
    try {
      const result = await memoryApi.createFamilyMemoryCard({
        content: formalContent,
        memoryType,
        familyContext: selectedFamily?.description || selectedFamily?.name || '',
        target: scenario,
      });
      setDraftCard(result.data);
      setSaveJudge(null);
      flashSuccess('已基于正式保存内容整理为经验卡预览');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'AI 整理失败');
    } finally {
      setGenerating(false);
    }
  };

  const sourceMetadataValue = (mode: EntryMode) => {
    if (mode === 'INTERVIEW') return 'HERITAGE_INTERVIEW';
    if (mode === 'ATOM') return 'HERITAGE_ATOM';
    return 'HERITAGE_ENTRY';
  };

  const handleSave = async () => {
    const formalContent = content.trim();
    if (!selectedFamilyId || !formalContent) return;

    setSaving(true);
    setError('');
    try {
      const judgeResult = await memoryApi.judgeHeritageSave({
        content: formalContent,
        memoryType,
        scenario,
        familyContext: selectedFamily?.description || selectedFamily?.name || '',
        sourceMode,
      });
      const judge = judgeResult.data;
      setSaveJudge(judge);

      if (!judge.should_save) {
        setError(judge.reason || '这条内容暂时不适合作为家族经验沉淀保存，请补充更具体的经历和后辈可借鉴做法。');
        return;
      }

      const card = draftCard || (await memoryApi.createFamilyMemoryCard({
        content: formalContent,
        memoryType,
        familyContext: selectedFamily?.description || selectedFamily?.name || '',
        target: scenario,
      })).data;

      await memoryApi.createFamilyMemory({
        familyId: selectedFamilyId,
        content: formalContent,
        type: memoryType,
        scope,
        summary: card?.summary || formalContent.slice(0, 120),
        importance: memoryType === 'HEALTH_REMINDER' || memoryType === 'GROWTH_RISK' ? 4 : 3,
        memoryCard: card || undefined,
        metadata: {
          source: sourceMetadataValue(sourceMode),
          sourceMode,
          curationStatus: 'PENDING_CONFIRMATION',
          curationReason: card?.summary || formalContent.slice(0, 120),
          scenario,
          target: scenario,
          saveJudge: {
            shouldSave: judge.should_save,
            learningValueScore: judge.learning_value_score,
            descendantValue: judge.descendant_value,
            reason: judge.reason,
            missingElements: judge.missing_elements,
            sensitivity: judge.sensitivity,
          },
          learningValueScore: judge.learning_value_score,
          descendantValue: judge.descendant_value,
          saveJudgeReason: judge.reason,
          interviewThemeId: sourceMode === 'INTERVIEW' ? selectedInterviewTheme.id : undefined,
          interviewThemeLabel: sourceMode === 'INTERVIEW' ? selectedInterviewTheme.label : undefined,
          atomVersion: sourceMode === 'ATOM' ? 'THREE_SENTENCE_V1' : undefined,
        },
      });

      setContent('');
      setDraftCard(null);
      setSaveJudge(null);
      setOrganizedReason('');
      setSourceMode('DIRECT');
      flashSuccess('家族经验已保存，可到家族记忆库继续查看和操作');
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存失败');
    } finally {
      setSaving(false);
    }
  };

  const completeTask = async (task: HeritageTask) => {
    const note = completionNotes[task.id]?.trim();
    if (!note) {
      setError('请先写一句完成记录。');
      return;
    }
    setCompletingTaskId(task.id);
    setError('');
    try {
      await heritageTaskApi.complete(task.id, note);
      setCompletionNotes((current) => ({ ...current, [task.id]: '' }));
      flashSuccess('任务已完成，并沉淀为记录');
      if (selectedFamilyId) {
        await loadTasks(selectedFamilyId);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '任务完成失败');
    } finally {
      setCompletingTaskId(null);
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
        <p className="mt-2 text-sm text-gray-500">先创建或加入家族，再把值得长期留下的经验沉淀下来。</p>
        <Link
          href="/dashboard/family"
          className="mt-5 inline-flex h-10 items-center rounded-lg bg-blue-600 px-4 text-sm font-medium text-white hover:bg-blue-700"
        >
          前往家族空间
        </Link>
      </div>
    );
  }

  if (viewerRole === 'STUDENT') {
    return (
      <div className="mx-auto w-full max-w-4xl">
        {!embedded && <LegacyWorkpageNotice tab="heritage" label="经验沉淀" />}
        <div className="space-y-4">
          <section className="rounded-lg border border-gray-200 bg-white p-5 sm:p-6">
            <div className="mb-3 flex items-center gap-2">
              <ScrollText className="h-5 w-5 text-blue-600" />
              <h1 className="text-xl font-bold text-gray-900">经验沉淀</h1>
            </div>
            <p className="text-sm leading-6 text-gray-500">
              当前账号在这里使用只读视图。你可以查看自己有权限看到的经验沉淀结果与家庭任务进展；新增、整理和保存操作由家长或管理员处理。
            </p>
            <div className="mt-4 flex flex-col gap-2 sm:flex-row">
              <Link
                href={`/dashboard/family?tab=heritage${selectedFamilyId ? `&familyId=${selectedFamilyId}` : ''}`}
                className="inline-flex h-10 items-center justify-center rounded-lg bg-blue-600 px-4 text-sm font-medium text-white hover:bg-blue-700"
              >
                回到家族空间查看
              </Link>
              <Link
                href={`/dashboard/memory${selectedFamilyId ? `?familyId=${selectedFamilyId}` : ''}`}
                className="inline-flex h-10 items-center justify-center rounded-lg border border-gray-200 bg-white px-4 text-sm font-medium text-gray-600 hover:bg-gray-50"
              >
                去全部记忆
              </Link>
            </div>
          </section>

          <section className="rounded-lg border border-green-100 bg-green-50 p-4 sm:p-5">
            <div className="mb-3 flex items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <Target className="h-4 w-4 text-green-700" />
                <p className="text-sm font-semibold text-green-900">家庭任务结果</p>
              </div>
              <span className="text-xs text-green-700">{tasks.filter((task) => task.status === 'PENDING').length} 个待实践</span>
            </div>

            {loadingTasks ? (
              <div className="flex h-16 items-center justify-center text-green-700">
                <RefreshCw className="mr-2 h-4 w-4 animate-spin" />
                加载任务...
              </div>
            ) : tasks.length === 0 ? (
              <p className="rounded-lg border border-dashed border-green-200 bg-white/70 px-3 py-4 text-center text-xs text-green-700">
                暂时还没有你可查看的家庭任务。
              </p>
            ) : (
              <div className="space-y-2">
                {tasks.slice(0, 6).map((task) => (
                  <div key={task.id} className="rounded-lg border border-green-100 bg-white p-3">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="text-sm font-medium text-gray-900">{task.title}</span>
                      <span className={`rounded px-2 py-0.5 text-[11px] font-medium ${
                        task.status === 'DONE' ? 'bg-gray-100 text-gray-500' : 'bg-green-100 text-green-700'
                      }`}>
                        {task.status === 'DONE' ? '已完成' : '待实践'}
                      </span>
                      {task.dueDate && <span className="text-xs text-gray-400">截止 {task.dueDate}</span>}
                    </div>
                    <p className="mt-1 text-xs leading-5 text-gray-600">{task.action}</p>
                    {task.completionNote ? (
                      <p className="mt-2 rounded bg-gray-50 px-2 py-1.5 text-xs leading-5 text-gray-500">
                        完成记录：{task.completionNote}
                      </p>
                    ) : null}
                  </div>
                ))}
              </div>
            )}
          </section>
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto w-full max-w-6xl">
      {!embedded && <LegacyWorkpageNotice tab="heritage" label="经验沉淀" />}
      <div className="mb-5 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-xl font-bold text-gray-900">家族经验</h1>
          <p className="mt-1 text-sm text-gray-500">把日记、观察或长辈口述整理成长期资产。</p>
        </div>
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
            <h2 className="text-sm font-semibold text-gray-900">新增经验沉淀</h2>
          </div>

          <div className="mb-4 grid grid-cols-1 gap-2 rounded-lg bg-gray-50 p-1 sm:grid-cols-3">
            {entryModeOptions.map((option) => (
              <button
                key={option.value}
                type="button"
                onClick={() => setEntryMode(option.value)}
                className={`rounded-lg px-3 py-2 text-left transition-colors ${
                  entryMode === option.value
                    ? 'bg-white text-purple-700 shadow-sm'
                    : 'text-gray-500 hover:bg-white/70 hover:text-gray-800'
                }`}
              >
                <span className="block text-sm font-semibold">{option.label}</span>
                <span className="mt-0.5 block text-[11px]">{option.description}</span>
              </button>
            ))}
          </div>

          {entryMode === 'INTERVIEW' && (
            <div className="mb-4 rounded-lg border border-purple-100 bg-purple-50 p-3">
              <div className="mb-3 flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                <div>
                  <p className="text-sm font-semibold text-purple-900">访谈式录入</p>
                  <p className="mt-1 text-xs leading-5 text-purple-700">
                    不用先写长文，先问长辈几个问题，AI 会整理成正式稿。
                  </p>
                </div>
                <select
                  name="interviewThemeId"
                  value={interviewThemeId}
                  onChange={(event) => applyInterviewTheme(event.target.value)}
                  className="h-9 rounded-lg border border-purple-200 bg-white px-3 text-sm text-purple-900 outline-none focus:ring-2 focus:ring-purple-500"
                >
                  {interviewThemes.map((theme) => (
                    <option key={theme.id} value={theme.id}>{theme.label}</option>
                  ))}
                </select>
              </div>
              <p className="mb-3 text-xs leading-5 text-purple-700">{selectedInterviewTheme.description}</p>
              <div className="space-y-3">
                {selectedInterviewTheme.questions.map((question, index) => (
                  <label key={question} className="block text-xs font-medium text-purple-900">
                    {index + 1}. {question}
                    <textarea
                      name={`interviewAnswer-${index}`}
                      value={interviewAnswers[index] || ''}
                      onChange={(event) => {
                        const next = [...interviewAnswers];
                        next[index] = event.target.value;
                        setInterviewAnswers(next);
                      }}
                      rows={2}
                      className="mt-1 w-full resize-none rounded-lg border border-purple-100 bg-white px-3 py-2 text-sm text-gray-800 outline-none focus:ring-2 focus:ring-purple-500"
                      placeholder="可以直接粘贴访谈口述。"
                    />
                  </label>
                ))}
              </div>
              <div className="mt-3 flex flex-wrap items-center justify-between gap-2">
                <span className="text-xs text-purple-700">
                  已回答 {interviewAnswers.filter((answer) => answer.trim()).length} / {selectedInterviewTheme.questions.length}
                </span>
                <button
                  type="button"
                  onClick={() => void organizeToFormalDraft(buildInterviewDraft(), 'INTERVIEW')}
                  disabled={organizingMode === 'INTERVIEW'}
                  className="inline-flex h-9 items-center gap-2 rounded-lg bg-purple-600 px-3 text-xs font-medium text-white hover:bg-purple-700 disabled:opacity-50"
                >
                  {organizingMode === 'INTERVIEW' ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <Sparkles className="h-3.5 w-3.5" />}
                  整理到正式保存区
                </button>
              </div>
            </div>
          )}

          {entryMode === 'ATOM' && (
            <div className="mb-4 rounded-lg border border-blue-100 bg-blue-50 p-3">
              <p className="mb-3 text-sm font-semibold text-blue-900">三句话经验原子</p>
              <div className="space-y-3">
                <label className="block text-xs font-medium text-blue-900">
                  1. 当时发生了什么？
                  <textarea
                    name="atomSituation"
                    value={atomSituation}
                    onChange={(event) => setAtomSituation(event.target.value)}
                    rows={2}
                    className="mt-1 w-full resize-none rounded-lg border border-blue-100 bg-white px-3 py-2 text-sm text-gray-800 outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </label>
                <label className="block text-xs font-medium text-blue-900">
                  2. 我当时怎么想？
                  <textarea
                    name="atomThinking"
                    value={atomThinking}
                    onChange={(event) => setAtomThinking(event.target.value)}
                    rows={2}
                    className="mt-1 w-full resize-none rounded-lg border border-blue-100 bg-white px-3 py-2 text-sm text-gray-800 outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </label>
                <label className="block text-xs font-medium text-blue-900">
                  3. 如果重来，我会怎么做？
                  <textarea
                    name="atomRedo"
                    value={atomRedo}
                    onChange={(event) => setAtomRedo(event.target.value)}
                    rows={2}
                    className="mt-1 w-full resize-none rounded-lg border border-blue-100 bg-white px-3 py-2 text-sm text-gray-800 outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </label>
              </div>
              <button
                type="button"
                onClick={() => void organizeToFormalDraft(buildAtomDraft(), 'ATOM')}
                disabled={organizingMode === 'ATOM'}
                className="mt-3 inline-flex h-9 items-center gap-2 rounded-lg bg-blue-600 px-3 text-xs font-medium text-white hover:bg-blue-700 disabled:opacity-50"
              >
                {organizingMode === 'ATOM' ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <Sparkles className="h-3.5 w-3.5" />}
                整理到正式保存区
              </button>
            </div>
          )}

          {entryMode === 'DIRECT' && (
            <div className="mb-4 rounded-lg border border-amber-100 bg-amber-50 p-3 text-xs leading-5 text-amber-800">
              你可以直接在下方“正式保存内容”里写最终版本。保存时系统会先判断是否具备长期价值。
            </div>
          )}

          <div className="rounded-lg border border-gray-200 bg-white p-3 shadow-sm">
            <div className="mb-3 flex items-center gap-2">
              <Save className="h-4 w-4 text-blue-600" />
              <div>
                <p className="text-sm font-semibold text-gray-900">正式保存内容</p>
                <p className="text-xs text-gray-500">这是唯一保存入口。</p>
              </div>
            </div>

            <div className="mb-3 grid grid-cols-1 gap-3 sm:grid-cols-3">
              <label className="text-xs font-medium text-gray-500">
                类型
                <select
                  name="memoryType"
                  value={memoryType}
                  onChange={(event) => {
                    setMemoryType(event.target.value as MemoryEntryType);
                    setSaveJudge(null);
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
                  name="scope"
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
                  name="scenario"
                  value={scenario}
                  onChange={(event) => setScenario(event.target.value)}
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
                <VoiceInputButton onTranscript={appendVoiceTranscript} disabled={saving || generating} />
              </div>
              <textarea
                name="content"
                value={content}
                onChange={(event) => {
                  setContent(event.target.value);
                  resetFormalDerivedState();
                  setSourceMode('DIRECT');
                }}
                rows={9}
                className="w-full resize-none rounded-lg border border-gray-200 px-4 py-3 text-sm text-gray-800 outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            {organizedReason && (
              <p className="mb-3 rounded-lg border border-blue-100 bg-blue-50 px-3 py-2 text-xs leading-5 text-blue-700">
                {organizedReason}
              </p>
            )}

            {saveJudge && !saveJudge.should_save && (
              <div className="mb-3 rounded-lg border border-red-100 bg-red-50 p-3 text-sm text-red-700">
                <p className="font-medium">暂不建议保存为家族经验</p>
                <p className="mt-1 text-xs leading-5">{saveJudge.reason}</p>
              </div>
            )}

            <div className="flex flex-col gap-2 sm:flex-row">
              <button
                type="button"
                onClick={handleGenerateCard}
                disabled={!content.trim() || generating}
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
                {draftCard.motto && (
                  <p className="mt-3 rounded-lg border border-purple-100 bg-white px-3 py-2 text-sm font-semibold text-purple-800">
                    {draftCard.motto}
                  </p>
                )}
                <p className="mt-3 flex gap-2 text-xs text-gray-500">
                  <Shield className="h-3.5 w-3.5 shrink-0" />
                  {draftCard.safety_note}
                </p>
              </div>
            )}
          </div>
        </section>

        <section className="rounded-lg border border-gray-200 bg-white p-4 sm:p-5">
          <div className="mb-4 flex items-center gap-2">
            <ScrollText className="h-5 w-5 text-blue-600" />
            <h2 className="text-sm font-semibold text-gray-900">经验查看与管理</h2>
          </div>

          <div className="rounded-lg border border-blue-100 bg-blue-50 p-4">
            <p className="text-sm font-medium text-blue-900">经验记录和各类家族记录已统一收口到家族记忆库</p>
            <p className="mt-2 text-sm leading-6 text-blue-800">
              经验沉淀页只负责整理和保存。家族成员查看与操作记录、家族创建者管理成员记录和归档，都统一在家族记忆库里完成。
            </p>
            <div className="mt-4 flex flex-col gap-2 sm:flex-row">
              <Link
                href={`/dashboard/memory${selectedFamilyId ? `?familyId=${selectedFamilyId}` : ''}`}
                className="inline-flex h-10 items-center justify-center rounded-lg bg-blue-600 px-4 text-sm font-medium text-white hover:bg-blue-700"
              >
                前往家族记忆库
              </Link>
              <Link
                href={`/dashboard/memory${selectedFamilyId ? `?familyId=${selectedFamilyId}` : ''}`}
                className="inline-flex h-10 items-center justify-center rounded-lg border border-blue-200 bg-white px-4 text-sm font-medium text-blue-700 hover:bg-blue-100"
              >
                查看全部经验与记录
              </Link>
            </div>
          </div>

          <div className="mt-4 rounded-lg border border-green-100 bg-green-50 p-3">
            <div className="mb-3 flex items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <Target className="h-4 w-4 text-green-700" />
                <p className="text-sm font-semibold text-green-900">家庭任务</p>
              </div>
              <span className="text-xs text-green-700">{tasks.filter((task) => task.status === 'PENDING').length} 个待做</span>
            </div>

            {loadingTasks ? (
              <div className="flex h-16 items-center justify-center text-green-700">
                <RefreshCw className="mr-2 h-4 w-4 animate-spin" />
                加载任务...
              </div>
            ) : tasks.length === 0 ? (
              <p className="rounded-lg border border-dashed border-green-200 bg-white/70 px-3 py-4 text-center text-xs text-green-700">
                还没有家庭任务。后续可以在家族记忆库里围绕经验继续管理和推进。
              </p>
            ) : (
              <div className="space-y-2">
                {tasks.slice(0, 4).map((task) => (
                  <div key={task.id} className="rounded-lg border border-green-100 bg-white p-3">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="text-sm font-medium text-gray-900">{task.title}</span>
                      <span className={`rounded px-2 py-0.5 text-[11px] font-medium ${
                        task.status === 'DONE' ? 'bg-gray-100 text-gray-500' : 'bg-green-100 text-green-700'
                      }`}>
                        {task.status === 'DONE' ? '已完成' : '待实践'}
                      </span>
                      {task.dueDate && <span className="text-xs text-gray-400">截止 {task.dueDate}</span>}
                    </div>
                    <p className="mt-1 text-xs leading-5 text-gray-600">{task.action}</p>
                    {task.status === 'PENDING' ? (
                      <div className="mt-3 flex flex-col gap-2 sm:flex-row">
                        <input
                          name={`completionNote-${task.id}`}
                          value={completionNotes[task.id] || ''}
                          onChange={(event) => setCompletionNotes((current) => ({ ...current, [task.id]: event.target.value }))}
                          placeholder={taskCompletionPrompt(task)}
                          className="h-9 min-w-0 flex-1 rounded-lg border border-green-100 px-3 text-xs text-gray-700 outline-none focus:ring-2 focus:ring-green-500"
                        />
                        <button
                          type="button"
                          onClick={() => { void completeTask(task); }}
                          disabled={completingTaskId === task.id}
                          className="inline-flex h-9 items-center justify-center gap-2 rounded-lg bg-green-600 px-3 text-xs font-medium text-white hover:bg-green-700 disabled:opacity-50"
                        >
                          {completingTaskId === task.id ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <CheckCircle className="h-3.5 w-3.5" />}
                          完成并沉淀
                        </button>
                      </div>
                    ) : task.completionNote ? (
                      <p className="mt-2 rounded bg-gray-50 px-2 py-1.5 text-xs leading-5 text-gray-500">
                        完成记录：{task.completionNote}
                      </p>
                    ) : null}
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="mt-4 rounded-lg border border-dashed border-gray-200 p-4 text-sm leading-6 text-gray-500">
            {selectedFamily
              ? `当前沉淀目标：${selectedFamily.name}。保存后的经验会进入该家族的记忆库。`
              : '保存后的经验会进入当前家族的记忆库。'}
          </div>
        </section>
      </div>
    </div>
  );
}
