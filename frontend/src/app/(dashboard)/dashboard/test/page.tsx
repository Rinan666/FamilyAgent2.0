'use client';

import { useEffect, useMemo, useState } from 'react';
import {
  ClipboardList,
  CheckCircle2,
  Download,
  FileText,
  Loader2,
  RefreshCw,
  Send,
  SlidersHorizontal,
  Trash2,
} from 'lucide-react';
import { assessmentApi, questionApi, tutorApi } from '@/lib/api';
import { difficultyLabel } from '@/lib/utils';
import type { KnowledgePoint, Question } from '@/types';
import { useAuthStore } from '@/stores/authStore';

type OutputFormat = 'online' | 'pdf' | 'json';
type QuestionType = 'ALL' | Question['type'];

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
  const [isGenerating, setIsGenerating] = useState(false);
  const [isGrading, setIsGrading] = useState(false);
  const [analyzingQuestionId, setAnalyzingQuestionId] = useState<number | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [savedRecordId, setSavedRecordId] = useState<number | null>(null);
  const [hasLoadedDraft, setHasLoadedDraft] = useState(false);

  useEffect(() => {
    questionApi.getKnowledgeTree()
      .then((tree) => setKnowledgePoints(flattenKnowledgePoints(tree || [])))
      .catch(() => setKnowledgePoints([]));
  }, []);

  useEffect(() => {
    try {
      const raw = localStorage.getItem(TEST_DRAFT_STORAGE_KEY);
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
      localStorage.removeItem(TEST_DRAFT_STORAGE_KEY);
    } finally {
      setHasLoadedDraft(true);
    }
  }, []);

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
    localStorage.setItem(TEST_DRAFT_STORAGE_KEY, JSON.stringify(draft));
  }, [answers, gradeResults, hasLoadedDraft, questions, requestText, savedRecordId, testRequest]);

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
      setSavedRecordId(null);
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
      '参考答案',
      ...questions.flatMap((q, index) => [
        `${index + 1}. ${q.answer.value}`,
        ...(q.answer.steps || []).map((step) => `   - ${step}`),
      ]),
    ];
    navigator.clipboard?.writeText(lines.join('\n'));
  };

  const copyJson = () => {
    navigator.clipboard?.writeText(JSON.stringify(structuredOutput, null, 2));
  };

  const clearDraft = () => {
    localStorage.removeItem(TEST_DRAFT_STORAGE_KEY);
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

    return {
      score,
      correct,
      feedback,
      errorType,
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
          timeSpent: 0,
        })),
      });
      setSavedRecordId(record.id);
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存测试记录失败');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="max-w-6xl mx-auto h-[calc(100vh-8rem)] flex flex-col">
      <div className="flex items-center justify-between mb-4 shrink-0">
        <div>
          <h1 className="text-xl font-bold text-gray-900">数学诊断</h1>
          <p className="text-xs text-gray-500">先完成一组诊断题，再进入 AI 家教讲解</p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={exportText}
            disabled={questions.length === 0}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs rounded-lg border border-gray-200 bg-white text-gray-600 hover:bg-gray-50 disabled:opacity-50"
          >
            <Download className="w-4 h-4" />
            复制文本
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

      <div className="flex-1 grid grid-cols-[340px_1fr] gap-4 overflow-hidden">
        <section className="bg-white border border-gray-200 rounded-xl overflow-y-auto">
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

        <section className="bg-white border border-gray-200 rounded-xl overflow-hidden flex flex-col">
          <div className="border-b border-gray-100 px-5 py-4 flex items-center justify-between">
            <div>
              <h2 className="text-sm font-semibold text-gray-900">诊断题预览</h2>
              <p className="text-xs text-gray-400 mt-1">
                范围：{testRequest.knowledgeScope.join('、') || selectedKnowledgeName} ·
                难度：{testRequest.difficulty ? difficultyLabel(testRequest.difficulty) : '混合'} ·
                {questions.length} 题
              </p>
            </div>
            <ClipboardList className="w-5 h-5 text-blue-500" />
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
                    {gradeResults[question.id] && (
                      <div className={`mt-3 rounded-lg border p-3 text-sm ${
                        gradeResults[question.id].correct
                          ? 'border-green-100 bg-green-50'
                          : 'border-red-100 bg-red-50'
                      }`}
                      >
                        <div className="flex items-center justify-between gap-3">
                          <span className={`font-medium ${
                            gradeResults[question.id].correct ? 'text-green-700' : 'text-red-700'
                          }`}
                          >
                            {gradeResults[question.id].correct ? '正确' : '待加强'}
                          </span>
                          <div className="flex items-center gap-2">
                            <span className="text-[10px] text-gray-400">
                              {gradeResults[question.id].gradingMode === 'ai' ? 'AI 详细分析' : '快速判分'}
                            </span>
                            <span className="text-xs text-gray-500">
                              {Math.round(gradeResults[question.id].score)} 分
                            </span>
                          </div>
                        </div>
                        {gradeResults[question.id].errorType && gradeResults[question.id].errorType !== '无' && (
                          <p className="mt-1 text-xs text-gray-500">
                            错误类型：{gradeResults[question.id].errorType}
                          </p>
                        )}
                        {gradeResults[question.id].feedback && (
                          <p className="mt-2 text-xs text-gray-600">
                            {gradeResults[question.id].feedback}
                          </p>
                        )}
                        {gradeResults[question.id].gradingMode !== 'ai' && (
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
                      </div>
                    )}
                    <details className="mt-3">
                      <summary className="cursor-pointer text-xs text-gray-400 hover:text-gray-600">参考答案与步骤</summary>
                      <div className="mt-2 rounded-lg bg-green-50 border border-green-100 p-3 text-sm">
                        <p className="font-medium text-green-700">{question.answer.value}</p>
                        {question.answer.steps?.length ? (
                          <ol className="mt-2 list-decimal list-inside space-y-1 text-gray-700">
                            {question.answer.steps.map((step, stepIndex) => (
                              <li key={stepIndex}>{step}</li>
                            ))}
                          </ol>
                        ) : null}
                      </div>
                    </details>
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
