'use client';

import { useEffect, useMemo, useState } from 'react';
import {
  ClipboardList,
  CheckCircle2,
  Download,
  Eye,
  FileText,
  Loader2,
  RefreshCw,
  Send,
  SlidersHorizontal,
  Trash2,
} from 'lucide-react';
import { assessmentApi, questionApi, tutorApi } from '@/lib/api';
import { markDiagnosisCompletedLocally } from '@/lib/diagnosis';
import { difficultyLabel } from '@/lib/utils';
import type { KnowledgePoint, MistakeReviewResult, Question } from '@/types';
import { useAuthStore } from '@/stores/authStore';

type OutputFormat = 'online' | 'pdf' | 'json';
type QuestionType = 'ALL' | Question['type'];
type TestView = 'paper' | 'answers' | 'results';

interface TestRequest {
  subject: string;
  grade: string;
  knowledgeScope: string[];
  kpId?: number;
  difficulty?: number;
  questionCount: number;
  questionType: QuestionType;
  outputFormat: OutputFormat;
}

interface NormalizedGrade {
  score: number;
  correct: boolean;
  feedback: string;
  errorType?: string;
  parentExplanation?: string;
  nextSuggestion?: string;
  gradingMode?: string;
  needsAiReview?: boolean;
}

interface TestDraft {
  requestText: string;
  testRequest: TestRequest;
  questions: Question[];
  answers: Record<number, string>;
  gradeResults: Record<number, NormalizedGrade>;
  savedRecordId: number | null;
}

const TEST_DRAFT_STORAGE_KEY = 'familyagent:test-generator:draft:v1';

const DEFAULT_REQUEST_TEXT = '生成一份初中数学诊断，范围是一元一次方程和一元一次不等式，难度中等，8道题，在线文本';

function flattenKnowledgePoints(nodes: KnowledgePoint[]): KnowledgePoint[] {
  const result: KnowledgePoint[] = [];
  const walk = (items: KnowledgePoint[]) => {
    for (const item of items) {
      result.push(item);
      const children = (item as unknown as { children?: KnowledgePoint[] }).children;
      if (children?.length) walk(children);
    }
  };
  walk(nodes);
  return result;
}

function inferDifficulty(text: string): number | undefined {
  if (/基础|入门|简单|容易/.test(text)) return 1;
  if (/中等|适中|普通/.test(text)) return 3;
  if (/较难|拔高|困难|难题/.test(text)) return 4;
  return undefined;
}

function inferQuestionCount(text: string): number {
  const match = text.match(/(\d+)\s*(道|题|个)/);
  if (!match) return 8;
  const count = Number(match[1]);
  return Math.max(1, Math.min(20, count));
}

function inferQuestionType(text: string): QuestionType {
  if (/选择/.test(text)) return 'CHOICE';
  if (/填空/.test(text)) return 'FILL';
  if (/证明/.test(text)) return 'PROOF';
  if (/计算|解答|解方程|化简/.test(text)) return 'CALCULATION';
  return 'ALL';
}

function inferOutputFormat(text: string): OutputFormat {
  if (/pdf|PDF|打印|试卷/.test(text)) return 'pdf';
  if (/json|JSON|结构化/.test(text)) return 'json';
  return 'online';
}

function asArray<T>(value: T[] | undefined | null): T[] {
  return Array.isArray(value) ? value : [];
}

export default function TestGeneratorPage() {
  const userId = useAuthStore((s) => s.user?.id);
  const [requestText, setRequestText] = useState(DEFAULT_REQUEST_TEXT);
  const [knowledgePoints, setKnowledgePoints] = useState<KnowledgePoint[]>([]);
  const [testRequest, setTestRequest] = useState<TestRequest>({
    subject: 'math',
    grade: 'grade7',
    knowledgeScope: ['一元一次方程', '一元一次不等式'],
    difficulty: 3,
    questionCount: 8,
    questionType: 'ALL',
    outputFormat: 'online',
  });
  const [questions, setQuestions] = useState<Question[]>([]);
  const [answers, setAnswers] = useState<Record<number, string>>({});
  const [gradeResults, setGradeResults] = useState<Record<number, NormalizedGrade>>({});
  const [mistakeReviews, setMistakeReviews] = useState<Record<number, MistakeReviewResult>>({});
  const [isGenerating, setIsGenerating] = useState(false);
  const [isGrading, setIsGrading] = useState(false);
  const [analyzingQuestionId, setAnalyzingQuestionId] = useState<number | null>(null);
  const [reviewingQuestionId, setReviewingQuestionId] = useState<number | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [savedRecordId, setSavedRecordId] = useState<number | null>(null);
  const [hasLoadedDraft, setHasLoadedDraft] = useState(false);
  const [activeView, setActiveView] = useState<TestView>('paper');
  const [copyMessage, setCopyMessage] = useState<string | null>(null);
  const testDraftStorageKey = useMemo(
    () => userId ? `${TEST_DRAFT_STORAGE_KEY}:${userId}` : TEST_DRAFT_STORAGE_KEY,
    [userId],
  );

  useEffect(() => {
    questionApi.getKnowledgeTree()
      .then((tree) => setKnowledgePoints(flattenKnowledgePoints(tree || [])))
      .catch(() => setKnowledgePoints([]));
  }, []);

  useEffect(() => {
    setHasLoadedDraft(false);
    try {
      const raw = localStorage.getItem(testDraftStorageKey);
      if (!raw) {
        setHasLoadedDraft(true);
        return;
      }

      const draft = JSON.parse(raw) as Partial<TestDraft>;
      if (typeof draft.requestText === 'string') setRequestText(draft.requestText);
      if (draft.testRequest) setTestRequest(draft.testRequest);
      if (Array.isArray(draft.questions)) setQuestions(draft.questions);
      if (draft.answers) setAnswers(draft.answers);
      if (draft.gradeResults) setGradeResults(draft.gradeResults);
      if (typeof draft.savedRecordId === 'number') setSavedRecordId(draft.savedRecordId);
    } catch {
      localStorage.removeItem(testDraftStorageKey);
    } finally {
      setHasLoadedDraft(true);
    }
  }, [testDraftStorageKey]);

  useEffect(() => {
    if (!hasLoadedDraft) return;

    const draft: TestDraft = {
      requestText,
      testRequest,
      questions,
      answers,
      gradeResults,
      savedRecordId,
    };
    localStorage.setItem(testDraftStorageKey, JSON.stringify(draft));
  }, [answers, gradeResults, hasLoadedDraft, questions, requestText, savedRecordId, testDraftStorageKey, testRequest]);

  const availableKnowledgePoints = useMemo(
    () => knowledgePoints.filter((kp) => kp.subject === 'math' && kp.level > 1),
    [knowledgePoints],
  );

  const selectedKnowledgeName = useMemo(() => {
    if (!testRequest.kpId) return '智能匹配';
    return knowledgePoints.find((kp) => kp.id === testRequest.kpId)?.name || `知识点 ${testRequest.kpId}`;
  }, [knowledgePoints, testRequest.kpId]);

  const structuredOutput = useMemo(() => ({
    request: {
      subject: testRequest.subject,
      grade: testRequest.grade,
      knowledge_scope: testRequest.knowledgeScope,
      kp_id: testRequest.kpId,
      difficulty: testRequest.difficulty,
      question_count: testRequest.questionCount,
      question_type: testRequest.questionType,
      output_format: testRequest.outputFormat,
    },
    questions: questions.map((q, index) => ({
      order: index + 1,
      id: q.id,
      kp_id: q.kpId,
      type: q.type,
      difficulty: q.difficulty,
      content: q.content,
      answer: q.answer,
    })),
  }), [questions, testRequest]);

  const parseRequest = () => {
    const matchedKps = availableKnowledgePoints.filter((kp) => requestText.includes(kp.name));
    const primaryKp = matchedKps[0];
    const scope = matchedKps.length > 0
      ? matchedKps.map((kp) => kp.name)
      : testRequest.knowledgeScope;

    setTestRequest((prev) => ({
      ...prev,
      kpId: primaryKp?.id ?? prev.kpId,
      knowledgeScope: scope,
      difficulty: inferDifficulty(requestText) ?? prev.difficulty,
      questionCount: inferQuestionCount(requestText),
      questionType: inferQuestionType(requestText),
      outputFormat: inferOutputFormat(requestText),
    }));
  };

  const generateTest = async () => {
    setIsGenerating(true);
    setError(null);
    try {
      const selected = await questionApi.selectForTest({
        kpId: testRequest.kpId,
        subject: testRequest.subject,
        difficulty: testRequest.difficulty,
        type: testRequest.questionType === 'ALL' ? undefined : testRequest.questionType,
        limit: testRequest.questionCount,
      });
      setQuestions(selected || []);
      setAnswers({});
      setGradeResults({});
      setMistakeReviews({});
      setSavedRecordId(null);
      setActiveView('paper');
      if (!selected?.length) {
        setError('没有匹配到题目，请降低筛选条件或先补充题库。');
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '生成测试失败');
      setQuestions([]);
    } finally {
      setIsGenerating(false);
    }
  };

  const copyToClipboard = async (text: string, message: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopyMessage(message);
    } catch {
      setCopyMessage('复制失败，请检查浏览器权限');
    }
    window.setTimeout(() => setCopyMessage(null), 1800);
  };

  const exportText = () => {
    const lines = [
      'FamilyAgent 数学测试',
      `范围：${testRequest.knowledgeScope.join('、') || selectedKnowledgeName}`,
      `难度：${testRequest.difficulty ? difficultyLabel(testRequest.difficulty) : '混合'}`,
      `题目数量：${questions.length}`,
      '',
      ...questions.flatMap((q, index) => [
        `${index + 1}. ${q.content.stem}`,
        ...(q.content.options || []).map((option, optionIndex) => `   ${String.fromCharCode(65 + optionIndex)}. ${option}`),
        '',
      ]),
    ];
    void copyToClipboard(lines.join('\n'), '试题已复制');
  };

  const copyAnswerSheet = () => {
    const lines = [
      '参考答案',
      ...questions.flatMap((q, index) => [
        `${index + 1}. ${q.answer.value}`,
        ...(q.answer.steps || []).map((step) => `   - ${step}`),
        '',
      ]),
    ];
    void copyToClipboard(lines.join('\n'), '答案已复制');
  };

  const copyJson = () => {
    void copyToClipboard(JSON.stringify(structuredOutput, null, 2), 'JSON 已复制');
  };

  const clearDraft = () => {
    localStorage.removeItem(testDraftStorageKey);
    setRequestText(DEFAULT_REQUEST_TEXT);
    setTestRequest({
      subject: 'math',
      grade: 'grade7',
      knowledgeScope: [],
      difficulty: 3,
      questionCount: 8,
      questionType: 'ALL',
      outputFormat: 'online',
    });
    setQuestions([]);
    setAnswers({});
    setGradeResults({});
    setMistakeReviews({});
    setSavedRecordId(null);
    setError(null);
  };

  const normalizeGrade = (raw: unknown): NormalizedGrade => {
    const result = (raw as { data?: unknown })?.data || raw;
    const data = result as Record<string, unknown>;
    const score = Number(data.overall_score ?? data.overallScore ?? 0);
    const correct = Boolean(data.is_correct ?? data.isCorrect ?? score >= 60);
    const errorAnalysis = (data.error_analysis ?? data.errorAnalysis) as Record<string, unknown> | undefined;
    const feedback = String(data.overall_feedback ?? data.overallFeedback ?? '');
    const gradingMode = String(data.grading_mode ?? data.gradingMode ?? '');
    const needsAiReview = Boolean(data.needs_ai_review ?? data.needsAiReview ?? false);
    const errorType = errorAnalysis
      ? String(errorAnalysis.primary_error_type ?? errorAnalysis.primaryErrorType ?? '')
      : undefined;
    const suggestion = errorAnalysis
      ? String(errorAnalysis.suggestion ?? '')
      : '';
    const parentExplanation = errorAnalysis
      ? String(errorAnalysis.parent_explanation ?? errorAnalysis.parentExplanation ?? '')
      : '';
    const nextSuggestion = errorAnalysis
      ? String(errorAnalysis.next_suggestion ?? errorAnalysis.nextSuggestion ?? suggestion)
      : suggestion;

    return {
      score,
      correct,
      feedback,
      errorType,
      parentExplanation,
      nextSuggestion,
      gradingMode,
      needsAiReview,
    };
  };

  const gradeAnswers = async () => {
    if (questions.length === 0) return;
    setIsGrading(true);
    setError(null);
    setSavedRecordId(null);
    try {
      const gradedEntries = await Promise.all(questions.map(async (question) => {
        const studentAnswer = answers[question.id]?.trim() || '';
        const raw = await tutorApi.quickGrade({
          questionContent: question.content.stem,
          answer: question.answer.value,
          steps: (question.answer.steps || []).join('\n'),
          studentAnswer,
          subject: '数学',
          grade: '',
        });
        return [question.id, normalizeGrade(raw)] as const;
      }));
      const nextResults = Object.fromEntries(gradedEntries);
      setGradeResults(nextResults);
      setActiveView('results');
    } catch (err) {
      setError(err instanceof Error ? err.message : '快速批改失败');
    } finally {
      setIsGrading(false);
    }
  };

  const analyzeQuestion = async (question: Question) => {
    const studentAnswer = answers[question.id]?.trim() || '';
    if (!studentAnswer) {
      setError('请先填写答案，再查看详细分析。');
      return;
    }

    setAnalyzingQuestionId(question.id);
    setError(null);
    try {
      const raw = await tutorApi.grade({
        questionContent: question.content.stem,
        answer: question.answer.value,
        steps: (question.answer.steps || []).join('\n'),
        studentAnswer,
        subject: '数学',
        grade: '',
      });
      setGradeResults((prev) => ({
        ...prev,
        [question.id]: {
          ...normalizeGrade(raw),
          gradingMode: 'ai',
          needsAiReview: false,
        },
      }));
    } catch (err) {
      setError(err instanceof Error ? err.message : '详细 AI 分析失败');
    } finally {
      setAnalyzingQuestionId(null);
    }
  };

  const reviewMistake = async (question: Question) => {
    const studentAnswer = answers[question.id]?.trim() || '';
    if (!studentAnswer) {
      setError('请先填写答案，再生成错题复盘。');
      return;
    }

    setReviewingQuestionId(question.id);
    setError(null);
    try {
      const result = await tutorApi.mistakeReview({
        questionContent: question.content.stem,
        answer: question.answer.value,
        steps: (question.answer.steps || []).join('\n'),
        studentAnswer,
        gradeResult: gradeResults[question.id] as unknown as Record<string, unknown> | undefined,
        grade: testRequest.grade,
        subject: '数学',
        knowledgePoint: knowledgePoints.find((kp) => kp.id === question.kpId)?.name || selectedKnowledgeName,
        weakPoints: testRequest.knowledgeScope,
      });
      setMistakeReviews((prev) => ({
        ...prev,
        [question.id]: result.data,
      }));
    } catch (err) {
      setError(err instanceof Error ? err.message : '错题复盘生成失败');
    } finally {
      setReviewingQuestionId(null);
    }
  };

  const submitRecord = async () => {
    if (!userId) {
      setError('请先登录后再保存测试记录。');
      return;
    }
    if (questions.length === 0) return;
    const missingGrades = questions.some((question) => !gradeResults[question.id]);
    if (missingGrades) {
      setError('请先完成 AI 批改，再保存测试记录。');
      return;
    }

    setIsSubmitting(true);
    setError(null);
    try {
      const record = await assessmentApi.submitTest({
        userId,
        source: 'GENERATED_TEST',
        results: questions.map((question) => ({
          questionId: question.id,
          kpId: question.kpId,
          answer: answers[question.id] || '',
          score: gradeResults[question.id]?.score ?? 0,
          correct: gradeResults[question.id]?.correct ?? false,
          errorType: gradeResults[question.id]?.errorType,
          feedback: gradeResults[question.id]?.feedback,
          parentExplanation: gradeResults[question.id]?.parentExplanation,
          nextSuggestion: gradeResults[question.id]?.nextSuggestion,
          timeSpent: 0,
        })),
      });
      setSavedRecordId(record.id);
      setActiveView('results');
      markDiagnosisCompletedLocally(userId);
      window.dispatchEvent(new CustomEvent('familyagent:diagnosis-completed', { detail: { userId } }));
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存测试记录失败');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="mx-auto flex min-h-[calc(100dvh-13rem)] max-w-6xl flex-col lg:h-[calc(100vh-8rem)] lg:min-h-0">
      <div className="mb-4 flex shrink-0 flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div className="min-w-0">
          <h1 className="text-lg font-bold text-gray-900 sm:text-xl">数学诊断</h1>
          <p className="text-xs text-gray-500">先完成一组诊断题，再进入 AI 家教讲解</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <button
            onClick={exportText}
            disabled={questions.length === 0}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs rounded-lg border border-gray-200 bg-white text-gray-600 hover:bg-gray-50 disabled:opacity-50"
          >
            <Download className="w-4 h-4" />
            复制试题
          </button>
          <button
            onClick={copyAnswerSheet}
            disabled={questions.length === 0}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs rounded-lg border border-gray-200 bg-white text-gray-600 hover:bg-gray-50 disabled:opacity-50"
          >
            <Eye className="w-4 h-4" />
            复制答案
          </button>
          <button
            onClick={copyJson}
            disabled={questions.length === 0}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs rounded-lg border border-gray-200 bg-white text-gray-600 hover:bg-gray-50 disabled:opacity-50"
          >
            <FileText className="w-4 h-4" />
            复制 JSON
          </button>
          <button
            onClick={clearDraft}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs rounded-lg border border-gray-200 bg-white text-gray-600 hover:bg-gray-50"
          >
            <Trash2 className="w-4 h-4" />
            清空草稿
          </button>
          {copyMessage && (
            <span className="rounded-lg bg-blue-50 px-2.5 py-1.5 text-xs text-blue-700">
              {copyMessage}
            </span>
          )}
          <button
            onClick={gradeAnswers}
            disabled={questions.length === 0 || isGrading || isSubmitting}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs rounded-lg bg-green-600 text-white hover:bg-green-700 disabled:opacity-50"
          >
            {isGrading ? <Loader2 className="w-4 h-4 animate-spin" /> : <CheckCircle2 className="w-4 h-4" />}
            AI批改
          </button>
          <button
            onClick={submitRecord}
            disabled={questions.length === 0 || isGrading || isSubmitting}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs rounded-lg bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {isSubmitting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
            保存记录
          </button>
        </div>
      </div>

      <div className="grid flex-1 grid-cols-1 gap-4 overflow-visible lg:min-h-0 lg:grid-cols-[340px_1fr] lg:overflow-hidden">
        <section className="overflow-hidden rounded-xl border border-gray-200 bg-white lg:overflow-y-auto">
          <div className="p-4 border-b border-gray-100">
            <div className="flex items-center gap-2 mb-3">
              <SlidersHorizontal className="w-4 h-4 text-blue-600" />
              <h2 className="text-sm font-semibold text-gray-900">测试要求</h2>
            </div>
            <textarea
              value={requestText}
              onChange={(e) => setRequestText(e.target.value)}
              rows={5}
              className="w-full resize-none rounded-lg border border-gray-200 p-3 text-sm outline-none focus:ring-2 focus:ring-blue-500"
            />
            <button
              onClick={parseRequest}
              className="mt-3 w-full rounded-lg bg-gray-900 px-3 py-2 text-sm font-medium text-white hover:bg-gray-800"
            >
              解析要求
            </button>
          </div>

          <div className="p-4 space-y-4">
            <label className="block">
              <span className="text-xs font-medium text-gray-500">知识点范围</span>
              <select
                value={testRequest.kpId ?? ''}
                onChange={(e) => {
                  const kpId = e.target.value ? Number(e.target.value) : undefined;
                  const kp = knowledgePoints.find((item) => item.id === kpId);
                  setTestRequest((prev) => ({
                    ...prev,
                    kpId,
                    knowledgeScope: kp ? [kp.name] : prev.knowledgeScope,
                  }));
                }}
                className="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="">智能匹配 / 全部知识点</option>
                {availableKnowledgePoints.map((kp) => (
                  <option key={kp.id} value={kp.id}>{kp.name}</option>
                ))}
              </select>
            </label>

            <div className="grid grid-cols-2 gap-3">
              <label className="block">
                <span className="text-xs font-medium text-gray-500">难度</span>
                <select
                  value={testRequest.difficulty ?? ''}
                  onChange={(e) => setTestRequest((prev) => ({
                    ...prev,
                    difficulty: e.target.value ? Number(e.target.value) : undefined,
                  }))}
                  className="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value="">混合</option>
                  {[1, 2, 3, 4, 5].map((difficulty) => (
                    <option key={difficulty} value={difficulty}>{difficultyLabel(difficulty)}</option>
                  ))}
                </select>
              </label>

              <label className="block">
                <span className="text-xs font-medium text-gray-500">题目数量</span>
                <input
                  type="number"
                  min={1}
                  max={20}
                  value={testRequest.questionCount}
                  onChange={(e) => setTestRequest((prev) => ({
                    ...prev,
                    questionCount: Math.max(1, Math.min(20, Number(e.target.value) || 1)),
                  }))}
                  className="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-500"
                />
              </label>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <label className="block">
                <span className="text-xs font-medium text-gray-500">题型</span>
                <select
                  value={testRequest.questionType}
                  onChange={(e) => setTestRequest((prev) => ({
                    ...prev,
                    questionType: e.target.value as QuestionType,
                  }))}
                  className="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value="ALL">混合</option>
                  <option value="CHOICE">选择题</option>
                  <option value="FILL">填空题</option>
                  <option value="CALCULATION">计算题</option>
                  <option value="PROOF">证明题</option>
                </select>
              </label>

              <label className="block">
                <span className="text-xs font-medium text-gray-500">输出格式</span>
                <select
                  value={testRequest.outputFormat}
                  onChange={(e) => setTestRequest((prev) => ({
                    ...prev,
                    outputFormat: e.target.value as OutputFormat,
                  }))}
                  className="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value="online">在线文本</option>
                  <option value="pdf">PDF 草稿</option>
                  <option value="json">结构化 JSON</option>
                </select>
              </label>
            </div>

            <button
              onClick={generateTest}
              disabled={isGenerating}
              className="w-full inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
            >
              {isGenerating ? <Loader2 className="w-4 h-4 animate-spin" /> : <RefreshCw className="w-4 h-4" />}
              生成诊断题
            </button>

            {error && (
              <p className="rounded-lg bg-red-50 px-3 py-2 text-xs text-red-600">{error}</p>
            )}
            {savedRecordId && (
              <p className="rounded-lg bg-green-50 px-3 py-2 text-xs text-green-700">
                诊断记录已保存：#{savedRecordId}
              </p>
            )}
          </div>
        </section>

        <section className="flex min-w-0 flex-col overflow-hidden rounded-xl border border-gray-200 bg-white">
          <div className="flex flex-col gap-3 border-b border-gray-100 px-4 py-4 sm:px-5 lg:flex-row lg:items-center lg:justify-between">
            <div className="min-w-0">
              <h2 className="text-sm font-semibold text-gray-900">
                {activeView === 'paper' ? '诊断试题' : activeView === 'answers' ? '答案页' : '批改结果'}
              </h2>
              <p className="text-xs text-gray-400 mt-1">
                范围：{testRequest.knowledgeScope.join('、') || selectedKnowledgeName} ·
                难度：{testRequest.difficulty ? difficultyLabel(testRequest.difficulty) : '混合'} ·
                {questions.length} 题
              </p>
            </div>
            <div className="flex w-full items-center gap-1 rounded-lg bg-gray-100 p-1 lg:w-auto">
              {[
                { key: 'paper' as const, label: '试题' },
                { key: 'answers' as const, label: '答案' },
                { key: 'results' as const, label: '结果' },
              ].map((item) => (
                <button
                  key={item.key}
                  onClick={() => setActiveView(item.key)}
                  className={`h-8 flex-1 rounded-md px-3 text-xs font-medium transition-colors lg:flex-none ${
                    activeView === item.key
                      ? 'bg-white text-gray-900 shadow-sm'
                      : 'text-gray-500 hover:text-gray-700'
                  }`}
                >
                  {item.label}
                </button>
              ))}
            </div>
          </div>

          <div className="flex-1 overflow-y-auto p-5">
            {questions.length === 0 ? (
              <div className="h-full flex items-center justify-center text-center text-gray-400">
                <div>
                  <ClipboardList className="w-12 h-12 mx-auto mb-3 opacity-30" />
                  <p className="text-sm font-medium">还没有生成诊断题</p>
                  <p className="text-xs mt-1">填写或解析诊断要求后，点击生成诊断题</p>
                </div>
              </div>
            ) : activeView === 'answers' ? (
              <div className="space-y-4">
                {questions.map((question, index) => (
                  <article key={`${question.id}-answer-${index}`} className="rounded-lg border border-green-100 bg-green-50 p-4">
                    <div className="mb-2 flex items-center gap-2">
                      <span className="w-6 h-6 rounded bg-white text-green-700 text-xs font-semibold flex items-center justify-center">
                        {index + 1}
                      </span>
                      <span className="text-xs text-green-700">参考答案</span>
                    </div>
                    <p className="text-sm font-medium text-gray-900">{question.answer.value}</p>
                    {question.answer.steps?.length ? (
                      <ol className="mt-3 list-decimal list-inside space-y-1 text-sm text-gray-700">
                        {question.answer.steps.map((step, stepIndex) => (
                          <li key={stepIndex}>{step}</li>
                        ))}
                      </ol>
                    ) : null}
                    {question.answer.explanation && (
                      <p className="mt-3 text-xs text-gray-500">{question.answer.explanation}</p>
                    )}
                  </article>
                ))}
              </div>
            ) : activeView === 'results' ? (
              <div className="space-y-5">
                {questions.map((question, index) => {
                  const grade = gradeResults[question.id];

                  return (
                    <article key={`${question.id}-result-${index}`} className="border-b border-gray-100 pb-5 last:border-0">
                      <div className="flex items-start justify-between gap-4">
                        <div>
                          <div className="mb-2 flex items-center gap-2">
                            <span className="w-6 h-6 rounded bg-blue-50 text-blue-700 text-xs font-semibold flex items-center justify-center">
                              {index + 1}
                            </span>
                            <span className="text-xs px-2 py-0.5 rounded bg-gray-100 text-gray-600">
                              {difficultyLabel(question.difficulty)}
                            </span>
                          </div>
                          <p className="text-sm text-gray-900 whitespace-pre-wrap leading-relaxed">{question.content.stem}</p>
                        </div>
                        {grade && (
                          <span className={`shrink-0 rounded px-2 py-1 text-xs font-medium ${
                            grade.correct ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'
                          }`}
                          >
                            {Math.round(grade.score)} 分
                          </span>
                        )}
                      </div>
                      <div className="mt-3 grid grid-cols-1 gap-3 lg:grid-cols-2">
                        <div className="rounded-lg border border-gray-100 bg-gray-50 p-3">
                          <p className="text-xs font-medium text-gray-500">学生答案</p>
                          <p className="mt-2 whitespace-pre-wrap text-sm text-gray-900">
                            {answers[question.id]?.trim() || '未作答'}
                          </p>
                        </div>
                        <div className="rounded-lg border border-green-100 bg-green-50 p-3">
                          <p className="text-xs font-medium text-green-700">参考答案</p>
                          <p className="mt-2 whitespace-pre-wrap text-sm font-medium text-gray-900">
                            {question.answer.value}
                          </p>
                        </div>
                      </div>
                      {grade ? (
                        <div className={`mt-3 rounded-lg border p-3 text-sm ${
                          grade.correct ? 'border-green-100 bg-green-50' : 'border-red-100 bg-red-50'
                        }`}
                        >
                          <div className="flex items-center justify-between gap-3">
                            <span className={`font-medium ${grade.correct ? 'text-green-700' : 'text-red-700'}`}>
                              {grade.correct ? '正确' : '待加强'}
                            </span>
                            <span className="text-[10px] text-gray-400">
                              {grade.gradingMode === 'ai' ? 'AI 详细分析' : '快速判分'}
                            </span>
                          </div>
                          {grade.errorType && grade.errorType !== '无' && (
                            <p className="mt-1 text-xs text-gray-500">错误类型：{grade.errorType}</p>
                          )}
                          {grade.feedback && <p className="mt-2 text-xs text-gray-600">{grade.feedback}</p>}
                          {(grade.parentExplanation || grade.nextSuggestion) && (
                            <div className="mt-3 grid grid-cols-1 gap-2 lg:grid-cols-2">
                              {grade.parentExplanation && (
                                <div className="rounded border border-blue-100 bg-white/80 p-2">
                                  <p className="text-xs font-medium text-blue-700">给家长看的解释</p>
                                  <p className="mt-1 text-xs text-gray-600">{grade.parentExplanation}</p>
                                </div>
                              )}
                              {grade.nextSuggestion && (
                                <div className="rounded border border-purple-100 bg-white/80 p-2">
                                  <p className="text-xs font-medium text-purple-700">下一步练习建议</p>
                                  <p className="mt-1 text-xs text-gray-600">{grade.nextSuggestion}</p>
                                </div>
                              )}
                            </div>
                          )}
                          {grade.gradingMode !== 'ai' && (
                            <button
                              type="button"
                              onClick={() => { void analyzeQuestion(question); }}
                              disabled={analyzingQuestionId === question.id}
                              className="mt-2 inline-flex items-center gap-1 text-xs text-blue-600 hover:text-blue-700 disabled:opacity-50"
                            >
                              {analyzingQuestionId === question.id && <Loader2 className="w-3 h-3 animate-spin" />}
                              查看详细 AI 分析
                            </button>
                          )}
                          <button
                            type="button"
                            onClick={() => { void reviewMistake(question); }}
                            disabled={reviewingQuestionId === question.id}
                            className="ml-3 mt-2 inline-flex items-center gap-1 text-xs text-purple-600 hover:text-purple-700 disabled:opacity-50"
                          >
                            {reviewingQuestionId === question.id && <Loader2 className="w-3 h-3 animate-spin" />}
                            生成错题复盘
                          </button>
                        </div>
                      ) : (
                        <p className="mt-3 rounded-lg bg-yellow-50 px-3 py-2 text-xs text-yellow-700">
                          尚未批改。完成作答后点击 AI批改。
                        </p>
                      )}
                      {mistakeReviews[question.id] && (
                        <div className="mt-3 rounded-lg border border-purple-100 bg-purple-50 p-3">
                          <div className="flex flex-wrap items-center gap-2">
                            <span className="rounded bg-white px-2 py-0.5 text-xs font-medium text-purple-700">
                              {mistakeReviews[question.id].error_category}
                            </span>
                            <span className="text-xs text-gray-500">{mistakeReviews[question.id].correction_note}</span>
                          </div>
                          <p className="mt-2 text-xs text-gray-700">{mistakeReviews[question.id].error_pattern}</p>
                          <div className="mt-3 grid grid-cols-1 gap-2 lg:grid-cols-2">
                            <div className="rounded border border-purple-100 bg-white p-2">
                              <p className="text-xs font-medium text-gray-800">同类题建议</p>
                              <ul className="mt-1 space-y-1 text-xs text-gray-600">
                                {asArray(mistakeReviews[question.id].similar_question_suggestions).map((item) => (
                                  <li key={item}>{item}</li>
                                ))}
                              </ul>
                            </div>
                            <div className="rounded border border-purple-100 bg-white p-2">
                              <p className="text-xs font-medium text-gray-800">间隔复习</p>
                              <ul className="mt-1 space-y-1 text-xs text-gray-600">
                                {asArray(mistakeReviews[question.id].spaced_review_plan).map((item) => (
                                  <li key={`${item.day_offset}-${item.action}`}>第 {item.day_offset} 天：{item.action}</li>
                                ))}
                              </ul>
                            </div>
                          </div>
                          <p className="mt-2 text-xs text-gray-500">{mistakeReviews[question.id].parent_explanation}</p>
                        </div>
                      )}
                    </article>
                  );
                })}
              </div>
            ) : (
              <div className="space-y-5">
                {questions.map((question, index) => (
                  <article key={`${question.id}-${index}`} className="border-b border-gray-100 pb-5 last:border-0">
                    <div className="flex items-center gap-2 mb-2">
                      <span className="w-6 h-6 rounded bg-blue-50 text-blue-700 text-xs font-semibold flex items-center justify-center">
                        {index + 1}
                      </span>
                      <span className="text-xs px-2 py-0.5 rounded bg-gray-100 text-gray-600">
                        {difficultyLabel(question.difficulty)}
                      </span>
                      <span className="text-xs px-2 py-0.5 rounded bg-gray-100 text-gray-600">
                        {question.type === 'CHOICE' ? '选择题' : question.type === 'FILL' ? '填空题' : question.type === 'PROOF' ? '证明题' : '计算题'}
                      </span>
                    </div>
                    <p className="text-sm text-gray-900 whitespace-pre-wrap leading-relaxed">{question.content.stem}</p>
                    {question.content.options && (
                      <div className="mt-2 grid grid-cols-2 gap-2">
                        {question.content.options.map((option, optionIndex) => (
                          <div key={optionIndex} className="text-sm text-gray-600">
                            {String.fromCharCode(65 + optionIndex)}. {option}
                          </div>
                        ))}
                      </div>
                    )}
                    <textarea
                      value={answers[question.id] || ''}
                      onChange={(e) => setAnswers((prev) => ({
                        ...prev,
                        [question.id]: e.target.value,
                      }))}
                      rows={2}
                      placeholder="输入学生答案..."
                      className="mt-3 w-full resize-none rounded-lg border border-gray-200 p-3 text-sm outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </article>
                ))}
              </div>
            )}
          </div>
        </section>
      </div>
    </div>
  );
}
