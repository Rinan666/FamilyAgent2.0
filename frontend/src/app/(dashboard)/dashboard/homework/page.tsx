'use client';

import { useMemo, useState } from 'react';
import {
  CheckCircle2,
  ClipboardCheck,
  Copy,
  FileUp,
  Loader2,
  RotateCcw,
  Sparkles,
  Trash2,
  XCircle,
} from 'lucide-react';
import { tutorApi } from '@/lib/api';
import type { GradeResult, MistakeReviewResult } from '@/types';

interface HomeworkQuestion {
  id: string;
  stem: string;
  referenceAnswer: string;
  explanation: string;
  studentAnswer: string;
}

interface NormalizedGrade {
  score: number;
  correct: boolean;
  feedback: string;
  errorType?: string;
  parentExplanation?: string;
  nextSuggestion?: string;
  gradingMode?: string;
}

const SAMPLE_HOMEWORK = `1. 解方程：3(x-2)=2x+5
答案：x=11
解析：去括号得3x-6=2x+5，移项合并得x=11

2. 计算：2x²+3x-5x²+4x
答案：-3x²+7x
解析：合并同类项，2x²-5x²=-3x²，3x+4x=7x`;

function makeId(index: number) {
  return `hw-${Date.now()}-${index}`;
}

function cleanLine(value: string) {
  return value.trim().replace(/^[\s\d一二三四五六七八九十]+[、.)．]\s*/, '');
}

function splitHomeworkText(text: string): HomeworkQuestion[] {
  const normalized = text.replace(/\r\n/g, '\n').replace(/\r/g, '\n').trim();
  if (!normalized) return [];

  const blocks = normalized
    .split(/\n\s*\n+/)
    .map((block) => block.trim())
    .filter(Boolean);
  const sourceBlocks = blocks.length > 1 ? blocks : normalized
    .split(/(?=\n?\s*(?:\d+|[一二三四五六七八九十]+)[、.)．]\s*)/)
    .map((block) => block.trim())
    .filter(Boolean);

  return sourceBlocks.map((block, index) => {
    const lines = block.split('\n').map((line) => line.trim()).filter(Boolean);
    const answerLine = lines.find((line) => /^(答案|参考答案|正确答案|答)[:：]/.test(line));
    const explanationLine = lines.find((line) => /^(解析|解题过程|步骤|思路)[:：]/.test(line));
    const stemLines = lines.filter((line) => line !== answerLine && line !== explanationLine);
    const stem = cleanLine(stemLines.join('\n'));

    return {
      id: makeId(index),
      stem,
      referenceAnswer: answerLine?.replace(/^(答案|参考答案|正确答案|答)[:：]\s*/, '').trim() || '',
      explanation: explanationLine?.replace(/^(解析|解题过程|步骤|思路)[:：]\s*/, '').trim() || '',
      studentAnswer: '',
    };
  }).filter((item) => item.stem);
}

function normalizeGrade(raw: { data?: GradeResult } | GradeResult): NormalizedGrade {
  const data = (raw as { data?: GradeResult }).data || raw as GradeResult;
  const legacy = data as unknown as Record<string, unknown>;
  const errorAnalysis = (legacy.error_analysis ?? legacy.errorAnalysis ?? data.errorAnalysis) as Record<string, unknown> | undefined;
  const score = Number(legacy.overall_score ?? data.overallScore ?? 0);
  const correct = Boolean(legacy.is_correct ?? data.isCorrect ?? score >= 60);
  const suggestion = String(errorAnalysis?.suggestion ?? '');

  return {
    score: Number.isFinite(score) ? score : 0,
    correct,
    feedback: String(legacy.overall_feedback ?? data.overallFeedback ?? ''),
    errorType: String(errorAnalysis?.primary_error_type ?? errorAnalysis?.primaryErrorType ?? ''),
    parentExplanation: String(errorAnalysis?.parent_explanation ?? errorAnalysis?.parentExplanation ?? ''),
    nextSuggestion: String(errorAnalysis?.next_suggestion ?? errorAnalysis?.nextSuggestion ?? suggestion),
    gradingMode: String(legacy.grading_mode ?? legacy.gradingMode ?? 'quick'),
  };
}

function asArray<T>(value: T[] | undefined | null): T[] {
  return Array.isArray(value) ? value : [];
}

export default function HomeworkPage() {
  const [homeworkText, setHomeworkText] = useState(SAMPLE_HOMEWORK);
  const [fileName, setFileName] = useState('');
  const [questions, setQuestions] = useState<HomeworkQuestion[]>([]);
  const [grades, setGrades] = useState<Record<string, NormalizedGrade>>({});
  const [reviews, setReviews] = useState<Record<string, MistakeReviewResult>>({});
  const [message, setMessage] = useState<string | null>(null);
  const [isExtracting, setIsExtracting] = useState(false);
  const [isGrading, setIsGrading] = useState(false);
  const [reviewingId, setReviewingId] = useState<string | null>(null);

  const summary = useMemo(() => {
    const gradeItems = Object.values(grades);
    const wrong = gradeItems.filter((item) => !item.correct).length;
    const average = gradeItems.length
      ? Math.round(gradeItems.reduce((sum, item) => sum + item.score, 0) / gradeItems.length)
      : 0;
    return { graded: gradeItems.length, wrong, average };
  }, [grades]);

  const parseHomework = () => {
    const parsed = splitHomeworkText(homeworkText);
    setQuestions(parsed);
    setGrades({});
    setReviews({});
    setMessage(parsed.length ? `已识别 ${parsed.length} 道题` : '没有识别到题目，请检查题号、答案或换行格式');
  };

  const handleFileUpload = async (file?: File) => {
    if (!file) return;
    setIsExtracting(true);
    setMessage(null);
    try {
      const result = await tutorApi.extractContent(file);
      if (!result.data.supported) {
        setMessage(result.data.message);
        return;
      }
      setFileName(result.data.filename);
      setHomeworkText(result.data.structuredText || result.data.text);
      setMessage(result.data.message);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '作业文件解析失败');
    } finally {
      setIsExtracting(false);
    }
  };

  const updateQuestion = (id: string, patch: Partial<HomeworkQuestion>) => {
    setQuestions((prev) => prev.map((item) => item.id === id ? { ...item, ...patch } : item));
  };

  const removeQuestion = (id: string) => {
    setQuestions((prev) => prev.filter((item) => item.id !== id));
    setGrades((prev) => {
      const next = { ...prev };
      delete next[id];
      return next;
    });
    setReviews((prev) => {
      const next = { ...prev };
      delete next[id];
      return next;
    });
  };

  const gradeHomework = async () => {
    if (questions.length === 0) return;
    setIsGrading(true);
    setMessage(null);
    try {
      const entries = await Promise.all(questions.map(async (question) => {
        const raw = await tutorApi.quickGrade({
          questionContent: question.stem,
          answer: question.referenceAnswer,
          steps: question.explanation,
          studentAnswer: question.studentAnswer,
          subject: '数学',
          grade: '初中',
        });
        return [question.id, normalizeGrade(raw)] as const;
      }));
      setGrades(Object.fromEntries(entries));
      setMessage('作业批改完成');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '作业批改失败');
    } finally {
      setIsGrading(false);
    }
  };

  const reviewMistake = async (question: HomeworkQuestion) => {
    setReviewingId(question.id);
    setMessage(null);
    try {
      const result = await tutorApi.mistakeReview({
        questionContent: question.stem,
        answer: question.referenceAnswer,
        studentAnswer: question.studentAnswer,
        steps: question.explanation,
        gradeResult: grades[question.id] as unknown as Record<string, unknown>,
        grade: '初中',
        subject: '数学',
        knowledgePoint: '作业题',
      });
      setReviews((prev) => ({ ...prev, [question.id]: result.data }));
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '错题复盘生成失败');
    } finally {
      setReviewingId(null);
    }
  };

  const copyResult = async () => {
    const lines = [
      'FamilyAgent 作业批改结果',
      `题目：${questions.length} 道，已批改：${summary.graded} 道，错题：${summary.wrong} 道，平均分：${summary.average}`,
      '',
      ...questions.flatMap((question, index) => {
        const grade = grades[question.id];
        return [
          `${index + 1}. ${question.stem}`,
          `学生答案：${question.studentAnswer || '未作答'}`,
          `参考答案：${question.referenceAnswer || '未填写'}`,
          grade ? `结果：${grade.correct ? '正确' : '待加强'}，${Math.round(grade.score)} 分` : '结果：未批改',
          grade?.feedback ? `反馈：${grade.feedback}` : '',
          '',
        ].filter(Boolean);
      }),
    ];
    await navigator.clipboard.writeText(lines.join('\n'));
    setMessage('批改结果已复制');
  };

  return (
    <div className="mx-auto flex min-h-[calc(100dvh-6rem)] max-w-7xl flex-col lg:min-h-[calc(100vh-8rem)]">
      <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-xl font-bold text-gray-900">作业批改</h1>
          <p className="mt-1 text-xs text-gray-500">上传或粘贴作业，先拆题批改，再生成错题复盘</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {message && <span className="rounded-lg bg-blue-50 px-2.5 py-1.5 text-xs text-blue-700">{message}</span>}
          <button
            type="button"
            onClick={copyResult}
            disabled={questions.length === 0}
            className="inline-flex items-center gap-1.5 rounded-lg border border-gray-200 bg-white px-3 py-1.5 text-xs text-gray-600 hover:bg-gray-50 disabled:opacity-50"
          >
            <Copy className="h-4 w-4" />
            复制结果
          </button>
          <button
            type="button"
            onClick={() => {
              setHomeworkText(SAMPLE_HOMEWORK);
              setQuestions([]);
              setGrades({});
              setReviews({});
              setFileName('');
              setMessage(null);
            }}
            className="inline-flex items-center gap-1.5 rounded-lg border border-gray-200 bg-white px-3 py-1.5 text-xs text-gray-600 hover:bg-gray-50"
          >
            <RotateCcw className="h-4 w-4" />
            重置
          </button>
        </div>
      </div>

      <div className="grid flex-1 grid-cols-1 gap-4 lg:min-h-0 xl:grid-cols-[360px_1fr]">
        <section className="overflow-hidden rounded-xl border border-gray-200 bg-white xl:overflow-y-auto">
          <div className="border-b border-gray-100 p-4">
            <div className="mb-3 flex items-center gap-2">
              <FileUp className="h-4 w-4 text-blue-600" />
              <h2 className="text-sm font-semibold text-gray-900">作业内容</h2>
            </div>
            <input
              type="file"
              accept=".txt,.md,.csv,.json,.pdf,.docx,text/plain,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
              onChange={(event) => { void handleFileUpload(event.target.files?.[0]); }}
              className="w-full rounded-lg border border-gray-200 px-3 py-2 text-xs text-gray-600 file:mr-3 file:rounded-md file:border-0 file:bg-gray-100 file:px-2 file:py-1 file:text-xs file:text-gray-700"
            />
            {fileName && <p className="mt-1 truncate text-xs text-gray-400">{fileName}</p>}
            <textarea
              value={homeworkText}
              onChange={(event) => setHomeworkText(event.target.value)}
              rows={14}
              className="mt-3 w-full resize-none rounded-lg border border-gray-200 p-3 text-xs outline-none focus:ring-2 focus:ring-blue-500"
            />
            <button
              type="button"
              onClick={parseHomework}
              disabled={isExtracting || !homeworkText.trim()}
              className="mt-3 inline-flex w-full items-center justify-center gap-2 rounded-lg bg-gray-900 px-3 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50"
            >
              {isExtracting ? <Loader2 className="h-4 w-4 animate-spin" /> : <ClipboardCheck className="h-4 w-4" />}
              拆分作业题
            </button>
          </div>

          <div className="grid grid-cols-3 gap-2 p-4 text-center">
            <div className="rounded-lg bg-gray-50 p-3">
              <p className="text-xl font-bold text-gray-900">{questions.length}</p>
              <p className="mt-1 text-xs text-gray-500">题目</p>
            </div>
            <div className="rounded-lg bg-green-50 p-3">
              <p className="text-xl font-bold text-green-700">{summary.average}</p>
              <p className="mt-1 text-xs text-green-700">均分</p>
            </div>
            <div className="rounded-lg bg-red-50 p-3">
              <p className="text-xl font-bold text-red-700">{summary.wrong}</p>
              <p className="mt-1 text-xs text-red-700">错题</p>
            </div>
          </div>
        </section>

        <section className="flex min-w-0 flex-col overflow-hidden rounded-xl border border-gray-200 bg-white">
          <div className="flex flex-col gap-3 border-b border-gray-100 p-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h2 className="text-sm font-semibold text-gray-900">作业批改单</h2>
              <p className="mt-1 text-xs text-gray-400">补齐参考答案和学生答案后可批量批改</p>
            </div>
            <button
              type="button"
              onClick={gradeHomework}
              disabled={questions.length === 0 || isGrading}
              className="inline-flex items-center justify-center gap-1.5 rounded-lg bg-green-600 px-3 py-2 text-xs font-medium text-white hover:bg-green-700 disabled:opacity-50"
            >
              {isGrading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Sparkles className="h-4 w-4" />}
              AI批改作业
            </button>
          </div>

          <div className="flex-1 overflow-y-auto p-4">
            {questions.length === 0 ? (
              <div className="flex h-full items-center justify-center text-center text-gray-400">
                <div>
                  <ClipboardCheck className="mx-auto mb-3 h-12 w-12 opacity-30" />
                  <p className="text-sm font-medium">还没有作业题</p>
                  <p className="mt-1 text-xs">上传文件或粘贴文本后点击拆分作业题</p>
                </div>
              </div>
            ) : (
              <div className="space-y-4">
                {questions.map((question, index) => {
                  const grade = grades[question.id];
                  const review = reviews[question.id];

                  return (
                    <article key={question.id} className="rounded-lg border border-gray-100 p-4">
                      <div className="mb-3 flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <div className="mb-2 flex items-center gap-2">
                            <span className="flex h-6 w-6 items-center justify-center rounded bg-blue-50 text-xs font-semibold text-blue-700">
                              {index + 1}
                            </span>
                            {grade && (
                              <span className={`inline-flex items-center gap-1 rounded px-2 py-0.5 text-xs ${
                                grade.correct ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'
                              }`}
                              >
                                {grade.correct ? <CheckCircle2 className="h-3.5 w-3.5" /> : <XCircle className="h-3.5 w-3.5" />}
                                {Math.round(grade.score)} 分
                              </span>
                            )}
                          </div>
                          <textarea
                            value={question.stem}
                            onChange={(event) => updateQuestion(question.id, { stem: event.target.value })}
                            rows={2}
                            className="w-full resize-none rounded-lg border border-gray-200 p-2 text-sm outline-none focus:ring-2 focus:ring-blue-500"
                          />
                        </div>
                        <button
                          type="button"
                          onClick={() => removeQuestion(question.id)}
                          className="inline-flex h-8 w-8 items-center justify-center rounded-lg text-gray-400 hover:bg-gray-50 hover:text-red-500"
                        >
                          <Trash2 className="h-4 w-4" />
                        </button>
                      </div>

                      <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
                        <label className="block">
                          <span className="text-xs font-medium text-gray-500">参考答案</span>
                          <textarea
                            value={question.referenceAnswer}
                            onChange={(event) => updateQuestion(question.id, { referenceAnswer: event.target.value })}
                            rows={3}
                            className="mt-1 w-full resize-none rounded-lg border border-gray-200 p-2 text-sm outline-none focus:ring-2 focus:ring-blue-500"
                          />
                        </label>
                        <label className="block">
                          <span className="text-xs font-medium text-gray-500">学生答案</span>
                          <textarea
                            value={question.studentAnswer}
                            onChange={(event) => updateQuestion(question.id, { studentAnswer: event.target.value })}
                            rows={3}
                            className="mt-1 w-full resize-none rounded-lg border border-gray-200 p-2 text-sm outline-none focus:ring-2 focus:ring-blue-500"
                          />
                        </label>
                      </div>

                      <label className="mt-3 block">
                        <span className="text-xs font-medium text-gray-500">解析/步骤</span>
                        <textarea
                          value={question.explanation}
                          onChange={(event) => updateQuestion(question.id, { explanation: event.target.value })}
                          rows={2}
                          className="mt-1 w-full resize-none rounded-lg border border-gray-200 p-2 text-sm outline-none focus:ring-2 focus:ring-blue-500"
                        />
                      </label>

                      {grade && (
                        <div className={`mt-3 rounded-lg border p-3 ${
                          grade.correct ? 'border-green-100 bg-green-50' : 'border-red-100 bg-red-50'
                        }`}
                        >
                          <p className={`text-sm font-medium ${grade.correct ? 'text-green-700' : 'text-red-700'}`}>
                            {grade.correct ? '正确' : '待加强'} · {Math.round(grade.score)} 分
                          </p>
                          {grade.errorType && grade.errorType !== '无' && (
                            <p className="mt-1 text-xs text-gray-500">错误类型：{grade.errorType}</p>
                          )}
                          {grade.feedback && <p className="mt-2 text-xs text-gray-600">{grade.feedback}</p>}
                          {(grade.parentExplanation || grade.nextSuggestion) && (
                            <div className="mt-3 grid grid-cols-1 gap-2 lg:grid-cols-2">
                              {grade.parentExplanation && (
                                <div className="rounded border border-blue-100 bg-white/80 p-2">
                                  <p className="text-xs font-medium text-blue-700">家长解释</p>
                                  <p className="mt-1 text-xs text-gray-600">{grade.parentExplanation}</p>
                                </div>
                              )}
                              {grade.nextSuggestion && (
                                <div className="rounded border border-purple-100 bg-white/80 p-2">
                                  <p className="text-xs font-medium text-purple-700">下一步</p>
                                  <p className="mt-1 text-xs text-gray-600">{grade.nextSuggestion}</p>
                                </div>
                              )}
                            </div>
                          )}
                          <button
                            type="button"
                            onClick={() => { void reviewMistake(question); }}
                            disabled={reviewingId === question.id}
                            className="mt-3 inline-flex items-center gap-1 text-xs text-purple-600 hover:text-purple-700 disabled:opacity-50"
                          >
                            {reviewingId === question.id && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
                            生成错题复盘
                          </button>
                        </div>
                      )}

                      {review && (
                        <div className="mt-3 rounded-lg border border-purple-100 bg-purple-50 p-3">
                          <div className="flex flex-wrap items-center gap-2">
                            <span className="rounded bg-white px-2 py-0.5 text-xs font-medium text-purple-700">
                              {review.error_category}
                            </span>
                            <span className="text-xs text-gray-500">{review.correction_note}</span>
                          </div>
                          <p className="mt-2 text-xs text-gray-700">{review.error_pattern}</p>
                          <div className="mt-3 grid grid-cols-1 gap-2 lg:grid-cols-2">
                            <div className="rounded border border-purple-100 bg-white p-2">
                              <p className="text-xs font-medium text-gray-800">同类题建议</p>
                              <ul className="mt-1 space-y-1 text-xs text-gray-600">
                                {asArray(review.similar_question_suggestions).map((item) => <li key={item}>{item}</li>)}
                              </ul>
                            </div>
                            <div className="rounded border border-purple-100 bg-white p-2">
                              <p className="text-xs font-medium text-gray-800">间隔复习</p>
                              <ul className="mt-1 space-y-1 text-xs text-gray-600">
                                {asArray(review.spaced_review_plan).map((item) => (
                                  <li key={`${item.day_offset}-${item.action}`}>第 {item.day_offset} 天：{item.action}</li>
                                ))}
                              </ul>
                            </div>
                          </div>
                          <p className="mt-2 text-xs text-gray-500">{review.parent_explanation}</p>
                        </div>
                      )}
                    </article>
                  );
                })}
              </div>
            )}
          </div>
        </section>
      </div>
    </div>
  );
}
