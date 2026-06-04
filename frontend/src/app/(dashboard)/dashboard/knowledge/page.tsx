'use client';

import { useEffect, useMemo, useState } from 'react';
import {
  BookOpen,
  Database,
  FileJson,
  Filter,
  Loader2,
  Plus,
  RefreshCw,
  Tags,
} from 'lucide-react';
import { questionApi } from '@/lib/api';
import { difficultyLabel } from '@/lib/utils';
import type { CreateQuestionRequest, KnowledgePoint, Question } from '@/types';

type QuestionType = Question['type'];

const EMPTY_FORM: CreateQuestionRequest = {
  subject: 'math',
  grade: 'grade7',
  type: 'CALCULATION',
  difficulty: 3,
  content: { stem: '' },
  answer: { value: '', steps: [] },
  tags: ['math'],
  source: 'MANUAL',
};

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

function splitLines(value: string): string[] {
  return value
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function splitTags(value: string): string[] {
  return value
    .split(/[,，\s]+/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function questionTypeLabel(type: QuestionType) {
  const labels: Record<QuestionType, string> = {
    CHOICE: '选择题',
    FILL: '填空题',
    CALCULATION: '计算题',
    PROOF: '证明题',
  };
  return labels[type] || type;
}

export default function KnowledgePage() {
  const [questions, setQuestions] = useState<Question[]>([]);
  const [knowledgePoints, setKnowledgePoints] = useState<KnowledgePoint[]>([]);
  const [total, setTotal] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [filters, setFilters] = useState({
    subject: 'math',
    kpId: '',
    difficulty: '',
    type: '',
    tag: '',
  });
  const [form, setForm] = useState<CreateQuestionRequest>(EMPTY_FORM);
  const [optionsText, setOptionsText] = useState('');
  const [stepsText, setStepsText] = useState('');
  const [tagsText, setTagsText] = useState('math');
  const [batchJson, setBatchJson] = useState('');

  const formKnowledgePoints = useMemo(
    () => knowledgePoints.filter((kp) => !form.subject || kp.subject === form.subject),
    [form.subject, knowledgePoints],
  );

  const filterKnowledgePoints = useMemo(
    () => knowledgePoints.filter((kp) => !filters.subject || kp.subject === filters.subject),
    [filters.subject, knowledgePoints],
  );

  const loadQuestions = async () => {
    setIsLoading(true);
    setMessage(null);
    try {
      const page = await questionApi.listQuestions({
        page: 1,
        size: 30,
        subject: filters.subject || undefined,
        kpId: filters.kpId ? Number(filters.kpId) : undefined,
        difficulty: filters.difficulty ? Number(filters.difficulty) : undefined,
        type: filters.type || undefined,
        tag: filters.tag || undefined,
      });
      setQuestions(page.items || []);
      setTotal(page.total || 0);
    } catch (err) {
      setMessage(err instanceof Error ? err.message : '题库加载失败');
      setQuestions([]);
      setTotal(0);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    questionApi.getKnowledgeTree()
      .then((tree) => setKnowledgePoints(flattenKnowledgePoints(tree || [])))
      .catch(() => setKnowledgePoints([]));
  }, []);

  useEffect(() => {
    loadQuestions();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const saveQuestion = async () => {
    if (!form.content.stem.trim() || !form.answer.value.trim()) {
      setMessage('题干和答案不能为空');
      return;
    }

    setIsSaving(true);
    setMessage(null);
    try {
      await questionApi.createQuestion({
        ...form,
        kpId: form.kpId || undefined,
        content: {
          stem: form.content.stem.trim(),
          options: splitLines(optionsText),
        },
        answer: {
          value: form.answer.value.trim(),
          steps: splitLines(stepsText),
          explanation: form.answer.explanation?.trim() || undefined,
        },
        tags: splitTags(tagsText),
      });
      setMessage('题目已保存');
      setForm(EMPTY_FORM);
      setOptionsText('');
      setStepsText('');
      setTagsText('math');
      await loadQuestions();
    } catch (err) {
      setMessage(err instanceof Error ? err.message : '题目保存失败');
    } finally {
      setIsSaving(false);
    }
  };

  const importJson = async () => {
    if (!batchJson.trim()) return;
    setIsSaving(true);
    setMessage(null);
    try {
      const parsed = JSON.parse(batchJson) as CreateQuestionRequest | CreateQuestionRequest[];
      const items = Array.isArray(parsed) ? parsed : [parsed];
      await questionApi.batchCreateQuestions(items);
      setMessage(`已导入 ${items.length} 道题`);
      setBatchJson('');
      await loadQuestions();
    } catch (err) {
      setMessage(err instanceof Error ? err.message : 'JSON 导入失败，请检查格式');
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="max-w-7xl mx-auto min-h-[calc(100vh-8rem)] flex flex-col">
      <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-xl font-bold text-gray-900">题库与知识资源</h1>
          <p className="text-xs text-gray-500 mt-1">
            当前管理测试题库；后续扩展为多学科资料、家族技能与经验传承知识的统一资源库
          </p>
        </div>
        <button
          onClick={loadQuestions}
          disabled={isLoading}
          className="inline-flex items-center gap-2 rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-600 hover:bg-gray-50 disabled:opacity-50"
        >
          {isLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : <RefreshCw className="w-4 h-4" />}
          刷新
        </button>
      </div>

      <div className="grid grid-cols-1 gap-4 flex-1 min-h-0 xl:grid-cols-[360px_1fr]">
        <section className="bg-white border border-gray-200 rounded-xl overflow-y-auto">
          <div className="p-4 border-b border-gray-100">
            <div className="flex items-center gap-2 mb-3">
              <Plus className="w-4 h-4 text-blue-600" />
              <h2 className="text-sm font-semibold text-gray-900">录入题目</h2>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <label className="block">
                <span className="text-xs font-medium text-gray-500">学科</span>
                <select
                  value={form.subject}
                  onChange={(e) => setForm((prev) => ({ ...prev, subject: e.target.value, kpId: undefined }))}
                  className="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value="math">数学</option>
                  <option value="chinese">语文</option>
                  <option value="english">英语</option>
                  <option value="science">科学</option>
                  <option value="family_wisdom">家族传承</option>
                </select>
              </label>
              <label className="block">
                <span className="text-xs font-medium text-gray-500">年级/阶段</span>
                <input
                  value={form.grade || ''}
                  onChange={(e) => setForm((prev) => ({ ...prev, grade: e.target.value }))}
                  className="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-500"
                />
              </label>
            </div>

            <label className="block mt-3">
              <span className="text-xs font-medium text-gray-500">知识点</span>
              <select
                value={form.kpId ?? ''}
                onChange={(e) => setForm((prev) => ({ ...prev, kpId: e.target.value ? Number(e.target.value) : undefined }))}
                className="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="">暂不绑定</option>
                {formKnowledgePoints.map((kp) => (
                  <option key={kp.id} value={kp.id}>{kp.name}</option>
                ))}
              </select>
            </label>

            <div className="grid grid-cols-2 gap-3 mt-3">
              <label className="block">
                <span className="text-xs font-medium text-gray-500">题型</span>
                <select
                  value={form.type}
                  onChange={(e) => setForm((prev) => ({ ...prev, type: e.target.value as QuestionType }))}
                  className="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value="CALCULATION">计算题</option>
                  <option value="CHOICE">选择题</option>
                  <option value="FILL">填空题</option>
                  <option value="PROOF">证明题</option>
                </select>
              </label>
              <label className="block">
                <span className="text-xs font-medium text-gray-500">难度</span>
                <select
                  value={form.difficulty}
                  onChange={(e) => setForm((prev) => ({ ...prev, difficulty: Number(e.target.value) }))}
                  className="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-500"
                >
                  {[1, 2, 3, 4, 5].map((level) => (
                    <option key={level} value={level}>{difficultyLabel(level)}</option>
                  ))}
                </select>
              </label>
            </div>

            <label className="block mt-3">
              <span className="text-xs font-medium text-gray-500">题干</span>
              <textarea
                value={form.content.stem}
                onChange={(e) => setForm((prev) => ({ ...prev, content: { ...prev.content, stem: e.target.value } }))}
                rows={4}
                className="mt-1 w-full resize-none rounded-lg border border-gray-200 p-3 text-sm outline-none focus:ring-2 focus:ring-blue-500"
              />
            </label>

            <label className="block mt-3">
              <span className="text-xs font-medium text-gray-500">选项（每行一个，非选择题可空）</span>
              <textarea
                value={optionsText}
                onChange={(e) => setOptionsText(e.target.value)}
                rows={3}
                className="mt-1 w-full resize-none rounded-lg border border-gray-200 p-3 text-sm outline-none focus:ring-2 focus:ring-blue-500"
              />
            </label>

            <label className="block mt-3">
              <span className="text-xs font-medium text-gray-500">答案</span>
              <input
                value={form.answer.value}
                onChange={(e) => setForm((prev) => ({ ...prev, answer: { ...prev.answer, value: e.target.value } }))}
                className="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-500"
              />
            </label>

            <label className="block mt-3">
              <span className="text-xs font-medium text-gray-500">步骤（每行一步）</span>
              <textarea
                value={stepsText}
                onChange={(e) => setStepsText(e.target.value)}
                rows={3}
                className="mt-1 w-full resize-none rounded-lg border border-gray-200 p-3 text-sm outline-none focus:ring-2 focus:ring-blue-500"
              />
            </label>

            <label className="block mt-3">
              <span className="text-xs font-medium text-gray-500">标签</span>
              <input
                value={tagsText}
                onChange={(e) => setTagsText(e.target.value)}
                placeholder="math 方程 易错"
                className="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-500"
              />
            </label>

            <button
              onClick={saveQuestion}
              disabled={isSaving}
              className="mt-4 w-full inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
            >
              {isSaving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />}
              保存题目
            </button>
          </div>

          <div className="p-4">
            <div className="flex items-center gap-2 mb-3">
              <FileJson className="w-4 h-4 text-indigo-600" />
              <h2 className="text-sm font-semibold text-gray-900">JSON 批量导入</h2>
            </div>
            <textarea
              value={batchJson}
              onChange={(e) => setBatchJson(e.target.value)}
              rows={7}
              placeholder='[{"subject":"math","grade":"grade7","type":"CALCULATION","difficulty":2,"content":{"stem":"解方程：2x+5=13"},"answer":{"value":"x=4","steps":["2x=8","x=4"]},"tags":["math","方程"]}]'
              className="w-full resize-none rounded-lg border border-gray-200 p-3 font-mono text-xs outline-none focus:ring-2 focus:ring-blue-500"
            />
            <button
              onClick={importJson}
              disabled={isSaving || !batchJson.trim()}
              className="mt-3 w-full inline-flex items-center justify-center gap-2 rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50"
            >
              <Database className="w-4 h-4" />
              导入 JSON
            </button>
          </div>
        </section>

        <section className="bg-white border border-gray-200 rounded-xl overflow-hidden flex flex-col">
          <div className="border-b border-gray-100 p-4">
            <div className="flex items-center justify-between gap-3">
              <div className="flex items-center gap-2">
                <BookOpen className="w-4 h-4 text-blue-600" />
                <h2 className="text-sm font-semibold text-gray-900">题库列表</h2>
                <span className="text-xs text-gray-400">共 {total} 条</span>
              </div>
              {message && <span className="text-xs text-blue-600">{message}</span>}
            </div>

            <div className="mt-4 grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-5">
              <select
                value={filters.subject}
                onChange={(e) => setFilters((prev) => ({ ...prev, subject: e.target.value, kpId: '' }))}
                className="rounded-lg border border-gray-200 px-2 py-2 text-xs outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="">全部学科</option>
                <option value="math">数学</option>
                <option value="chinese">语文</option>
                <option value="english">英语</option>
                <option value="science">科学</option>
                <option value="family_wisdom">家族传承</option>
              </select>
              <select
                value={filters.type}
                onChange={(e) => setFilters((prev) => ({ ...prev, type: e.target.value }))}
                className="rounded-lg border border-gray-200 px-2 py-2 text-xs outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="">全部题型</option>
                <option value="CALCULATION">计算题</option>
                <option value="CHOICE">选择题</option>
                <option value="FILL">填空题</option>
                <option value="PROOF">证明题</option>
              </select>
              <select
                value={filters.difficulty}
                onChange={(e) => setFilters((prev) => ({ ...prev, difficulty: e.target.value }))}
                className="rounded-lg border border-gray-200 px-2 py-2 text-xs outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="">全部难度</option>
                {[1, 2, 3, 4, 5].map((level) => (
                  <option key={level} value={level}>{difficultyLabel(level)}</option>
                ))}
              </select>
              <select
                value={filters.kpId}
                onChange={(e) => setFilters((prev) => ({ ...prev, kpId: e.target.value }))}
                className="rounded-lg border border-gray-200 px-2 py-2 text-xs outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="">全部知识点</option>
                {filterKnowledgePoints.map((kp) => (
                  <option key={kp.id} value={kp.id}>{kp.name}</option>
                ))}
              </select>
              <div className="flex gap-2">
                <input
                  value={filters.tag}
                  onChange={(e) => setFilters((prev) => ({ ...prev, tag: e.target.value }))}
                  placeholder="标签"
                  className="min-w-0 flex-1 rounded-lg border border-gray-200 px-2 py-2 text-xs outline-none focus:ring-2 focus:ring-blue-500"
                />
                <button
                  onClick={loadQuestions}
                  className="inline-flex items-center justify-center rounded-lg bg-gray-900 px-3 text-white hover:bg-gray-800"
                >
                  <Filter className="w-4 h-4" />
                </button>
              </div>
            </div>
          </div>

          <div className="flex-1 overflow-y-auto p-4">
            {isLoading ? (
              <div className="h-full flex items-center justify-center text-gray-400">
                <Loader2 className="w-6 h-6 animate-spin" />
              </div>
            ) : questions.length === 0 ? (
              <div className="h-full flex items-center justify-center text-center text-gray-400">
                <div>
                  <BookOpen className="w-12 h-12 mx-auto mb-3 opacity-30" />
                  <p className="text-sm">暂无匹配题目</p>
                </div>
              </div>
            ) : (
              <div className="space-y-3">
                {questions.map((question) => (
                  <article key={question.id} className="rounded-lg border border-gray-100 p-4">
                    <div className="flex items-center gap-2 mb-2">
                      <span className="rounded bg-blue-50 px-2 py-0.5 text-xs font-medium text-blue-700">
                        {question.subject}
                      </span>
                      <span className="rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-600">
                        {questionTypeLabel(question.type)}
                      </span>
                      <span className="rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-600">
                        {difficultyLabel(question.difficulty)}
                      </span>
                      {question.tags?.slice(0, 4).map((tag) => (
                        <span key={tag} className="inline-flex items-center gap-1 rounded bg-green-50 px-2 py-0.5 text-xs text-green-700">
                          <Tags className="w-3 h-3" />
                          {tag}
                        </span>
                      ))}
                    </div>
                    <p className="text-sm text-gray-900 whitespace-pre-wrap">{question.content.stem}</p>
                    {question.content.options?.length ? (
                      <div className="mt-2 grid grid-cols-2 gap-1 text-xs text-gray-500">
                        {question.content.options.map((option, index) => (
                          <span key={index}>{String.fromCharCode(65 + index)}. {option}</span>
                        ))}
                      </div>
                    ) : null}
                    <details className="mt-2">
                      <summary className="cursor-pointer text-xs text-gray-400 hover:text-gray-600">答案与步骤</summary>
                      <div className="mt-2 rounded-lg bg-gray-50 p-3 text-xs text-gray-600">
                        <p className="font-medium text-gray-800">{question.answer.value}</p>
                        {question.answer.steps?.length ? (
                          <ol className="mt-2 list-decimal list-inside space-y-1">
                            {question.answer.steps.map((step, index) => <li key={index}>{step}</li>)}
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
