'use client';

import { useEffect, useMemo, useState } from 'react';
import {
  BookOpen,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Database,
  FileJson,
  Filter,
  Layers,
  Loader2,
  Plus,
  RefreshCw,
  ShieldCheck,
  Sparkles,
  Tags,
  Trash2,
  Wand2,
  XCircle,
} from 'lucide-react';
import { questionApi, tutorApi } from '@/lib/api';
import { difficultyLabel, subjectLabel } from '@/lib/utils';
import { useAuthStore } from '@/stores/authStore';
import type { CreateQuestionRequest, DailyPracticeResult, KnowledgePoint, Question, TextbookMetadata } from '@/types';

type QuestionType = Question['type'];
type GroupBy = 'none' | 'type' | 'kp' | 'grade' | 'difficulty';

const GRADE_OPTIONS = [
  { value: 'grade1', label: '一年级' },
  { value: 'grade2', label: '二年级' },
  { value: 'grade3', label: '三年级' },
  { value: 'grade4', label: '四年级' },
  { value: 'grade5', label: '五年级' },
  { value: 'grade6', label: '六年级' },
  { value: 'grade7', label: '七年级' },
  { value: 'grade8', label: '八年级' },
  { value: 'grade9', label: '九年级' },
  { value: 'grade10', label: '高一' },
  { value: 'grade11', label: '高二' },
  { value: 'grade12', label: '高三' },
];

const MIXED_DRAFT_TYPES: QuestionType[] = ['CALCULATION', 'CHOICE', 'FILL', 'CALCULATION', 'PROOF'];

interface QualityCheck {
  label: string;
  passed: boolean;
  detail: string;
}

interface DraftQuestion {
  localId: string;
  question: CreateQuestionRequest;
  checks: QualityCheck[];
  approved: boolean;
}

interface TextbookChapterOption {
  key: string;
  textbookVersion: string;
  textbookName: string;
  volume: string;
  chapterCode: string;
  chapterName: string;
  grade: string;
  kpIds: number[];
  label: string;
}

type ImportRecord = Record<string, unknown>;

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

function gradeLabel(grade?: string) {
  if (!grade) return '未标注年级';
  const option = GRADE_OPTIONS.find((item) => item.value === grade);
  return option?.label || grade;
}

function textbookVersionLabel(version?: string) {
  const labels: Record<string, string> = {
    PEP: '人教版',
    BSD: '北师大版',
    SUKE: '苏科版',
    generic: '通用教材',
  };
  return version ? labels[version] || version : '通用教材';
}

function getTextbookMetadata(kp: KnowledgePoint): TextbookMetadata {
  return kp.metadata || {};
}

function asArray<T>(value: T[] | undefined | null): T[] {
  return Array.isArray(value) ? value : [];
}

function normalizeStem(value: string): string {
  return value.replace(/\s+/g, '').replace(/[，。,.]/g, '').toLowerCase();
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

function textValue(value: unknown): string {
  if (value == null) return '';
  if (typeof value === 'string') return value.trim();
  if (typeof value === 'number' || typeof value === 'boolean') return String(value).trim();
  if (Array.isArray(value)) return value.map(textValue).filter(Boolean).join('\n');
  if (typeof value === 'object') {
    const record = value as ImportRecord;
    return [
      record.value,
      record.text,
      record.stem,
      record.content,
      record.question,
      record.answer,
      record.explanation,
    ].map(textValue).find(Boolean) || '';
  }
  return '';
}

function getImportField(record: ImportRecord, keys: string[]): unknown {
  const normalized = new Map(
    Object.entries(record).map(([key, value]) => [key.trim().toLowerCase(), value]),
  );
  for (const key of keys) {
    const value = normalized.get(key.toLowerCase());
    if (value !== undefined && textValue(value)) return value;
  }
  return undefined;
}

function splitImportList(value: unknown): string[] {
  if (Array.isArray(value)) return value.map(textValue).filter(Boolean);
  return textValue(value)
    .split(/\r?\n|[；;]\s*|\s*\|\s*/)
    .map((item) => item.trim().replace(/^[A-D][.、．]\s*/i, ''))
    .filter(Boolean);
}

function detectImportDelimiter(text: string): ',' | '\t' | ';' {
  const firstLine = text.split(/\r?\n/).find((line) => line.trim()) || '';
  const candidates: Array<',' | '\t' | ';'> = [',', '\t', ';'];
  return candidates
    .map((delimiter) => ({
      delimiter,
      count: firstLine.split(delimiter).length,
    }))
    .sort((a, b) => b.count - a.count)[0]?.delimiter || ',';
}

function parseDelimitedRows(text: string, delimiter = detectImportDelimiter(text)): string[][] {
  const rows: string[][] = [];
  let row: string[] = [];
  let field = '';
  let inQuotes = false;

  for (let index = 0; index < text.length; index += 1) {
    const char = text[index];
    const next = text[index + 1];
    if (char === '"' && inQuotes && next === '"') {
      field += '"';
      index += 1;
    } else if (char === '"') {
      inQuotes = !inQuotes;
    } else if (char === delimiter && !inQuotes) {
      row.push(field.trim());
      field = '';
    } else if ((char === '\n' || char === '\r') && !inQuotes) {
      if (char === '\r' && next === '\n') index += 1;
      row.push(field.trim());
      if (row.some(Boolean)) rows.push(row);
      row = [];
      field = '';
    } else {
      field += char;
    }
  }

  row.push(field.trim());
  if (row.some(Boolean)) rows.push(row);
  return rows;
}

function parseCsvImport(text: string): ImportRecord[] {
  const rows = parseDelimitedRows(text.replace(/^\uFEFF/, ''));
  if (rows.length < 2) return [];
  const headers = rows[0].map((header) => header.trim());
  return rows.slice(1).map((row) => (
    Object.fromEntries(headers.map((header, index) => [header, row[index] || '']))
  ));
}

function parseQuestionImportText(text: string, fileName = ''): ImportRecord[] {
  const trimmed = text.trim();
  if (!trimmed) return [];

  const looksCsv = fileName.toLowerCase().endsWith('.csv')
    || fileName.toLowerCase().endsWith('.tsv')
    || (!trimmed.startsWith('{') && !trimmed.startsWith('[') && /[,\t;]/.test(trimmed));
  if (looksCsv) return parseCsvImport(trimmed);

  const parsed = JSON.parse(trimmed) as unknown;
  if (Array.isArray(parsed)) return parsed.filter((item): item is ImportRecord => Boolean(item) && typeof item === 'object' && !Array.isArray(item));
  if (parsed && typeof parsed === 'object') {
    const record = parsed as ImportRecord;
    const nested = record.questions || record.items || record.data || record.records;
    if (Array.isArray(nested)) return nested.filter((item): item is ImportRecord => Boolean(item) && typeof item === 'object' && !Array.isArray(item));
    return [record];
  }
  return [];
}

function normalizeImportedQuestionType(value: unknown, stem: string, options: string[]): QuestionType {
  const text = textValue(value).toLowerCase();
  if (text.includes('choice') || text.includes('选择') || text.includes('单选') || text.includes('多选')) return 'CHOICE';
  if (text.includes('fill') || text.includes('填空')) return 'FILL';
  if (text.includes('proof') || text.includes('证明')) return 'PROOF';
  if (text.includes('calc') || text.includes('计算') || text.includes('解答')) return 'CALCULATION';
  if (options.length >= 2) return 'CHOICE';
  if (/_{2,}|（\s*）|\(\s*\)/.test(stem)) return 'FILL';
  if (stem.includes('证明')) return 'PROOF';
  return 'CALCULATION';
}

function normalizeImportedDifficulty(value: unknown): number {
  const text = textValue(value);
  const number = Number(text.match(/[1-5]/)?.[0] || text);
  if (Number.isFinite(number)) return Math.min(5, Math.max(1, Math.round(number)));
  if (/难|高|拔高/.test(text)) return 4;
  if (/易|基础|简单/.test(text)) return 2;
  return 3;
}

function normalizeImportedGrade(value: unknown, fallback?: string): string {
  const text = textValue(value);
  if (!text) return fallback || 'grade7';
  const exact = GRADE_OPTIONS.find((item) => item.value === text || item.label === text);
  if (exact) return exact.value;
  const numeric = text.match(/\d+/)?.[0];
  if (numeric) {
    const option = GRADE_OPTIONS.find((item) => item.value === `grade${numeric}`);
    if (option) return option.value;
  }
  if (text.includes('七')) return 'grade7';
  if (text.includes('八')) return 'grade8';
  if (text.includes('九')) return 'grade9';
  return fallback || 'grade7';
}

function qualityScore(checks: QualityCheck[]): number {
  if (checks.length === 0) return 0;
  return Math.round((checks.filter((item) => item.passed).length / checks.length) * 100);
}

export default function KnowledgePage() {
  const user = useAuthStore((state) => state.user);
  const [questions, setQuestions] = useState<Question[]>([]);
  const [knowledgePoints, setKnowledgePoints] = useState<KnowledgePoint[]>([]);
  const [total, setTotal] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [hasLoadedQuestions, setHasLoadedQuestions] = useState(false);
  const [groupBy, setGroupBy] = useState<GroupBy>('none');
  const [filters, setFilters] = useState({
    subject: 'math',
    grade: '',
    textbookVersion: '',
    volume: '',
    chapterKey: '',
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
  const [importFileName, setImportFileName] = useState('');
  const [dailyPractice, setDailyPractice] = useState<DailyPracticeResult | null>(null);
  const [isGeneratingPractice, setIsGeneratingPractice] = useState(false);
  const [draftQuestions, setDraftQuestions] = useState<DraftQuestion[]>([]);
  const [isGeneratingDrafts, setIsGeneratingDrafts] = useState(false);
  const [isImportingDrafts, setIsImportingDrafts] = useState(false);
  const [maintenanceMode, setMaintenanceMode] = useState(false);
  const [deletingQuestionId, setDeletingQuestionId] = useState<number | null>(null);
  const canMaintainQuestions = ['ADMIN', 'OWNER'].includes((user?.role || '').toUpperCase());

  const formKnowledgePoints = useMemo(
    () => knowledgePoints.filter((kp) => !form.subject || kp.subject === form.subject),
    [form.subject, knowledgePoints],
  );

  const filterKnowledgePoints = useMemo(
    () => knowledgePoints.filter((kp) => !filters.subject || kp.subject === filters.subject),
    [filters.subject, knowledgePoints],
  );

  const textbookChapterOptions = useMemo(() => {
    const byKey = new Map<string, TextbookChapterOption>();

    filterKnowledgePoints.forEach((kp) => {
      const metadata = getTextbookMetadata(kp);
      if (!metadata.chapterCode || !metadata.chapterName) return;

      const textbookVersion = metadata.textbookVersion || 'generic';
      const textbookName = metadata.textbookName || textbookVersionLabel(textbookVersion);
      const volume = metadata.volume || gradeLabel(kp.grade);
      const key = `${textbookVersion}|${volume}|${metadata.chapterCode}`;
      const existing = byKey.get(key);
      if (existing) {
        existing.kpIds = Array.from(new Set([...existing.kpIds, kp.id]));
        return;
      }

      byKey.set(key, {
        key,
        textbookVersion,
        textbookName,
        volume,
        chapterCode: metadata.chapterCode,
        chapterName: metadata.chapterName,
        grade: kp.grade,
        kpIds: [kp.id],
        label: `${volume} · ${metadata.chapterName}`,
      });
    });

    if (byKey.size === 0) {
      filterKnowledgePoints
        .filter((kp) => kp.level === 1)
        .forEach((chapter) => {
          const childIds = filterKnowledgePoints
            .filter((kp) => kp.parentId === chapter.id)
            .map((kp) => kp.id);
          const volume = gradeLabel(chapter.grade);
          const chapterCode = `kp-${chapter.id}`;
          const key = `generic|${volume}|${chapterCode}`;
          byKey.set(key, {
            key,
            textbookVersion: 'generic',
            textbookName: '通用教材',
            volume,
            chapterCode,
            chapterName: chapter.name,
            grade: chapter.grade,
            kpIds: Array.from(new Set([chapter.id, ...childIds])),
            label: `${volume} · ${chapter.name}`,
          });
        });
    }

    return Array.from(byKey.values()).sort((a, b) => a.label.localeCompare(b.label, 'zh-Hans-CN'));
  }, [filterKnowledgePoints]);

  const textbookVersions = useMemo(() => {
    const map = new Map<string, string>();
    textbookChapterOptions.forEach((chapter) => {
      map.set(chapter.textbookVersion, chapter.textbookName || textbookVersionLabel(chapter.textbookVersion));
    });
    return Array.from(map.entries()).map(([value, label]) => ({ value, label }));
  }, [textbookChapterOptions]);

  const textbookVolumes = useMemo(() => (
    Array.from(new Set(
      textbookChapterOptions
        .filter((chapter) => !filters.textbookVersion || chapter.textbookVersion === filters.textbookVersion)
        .map((chapter) => chapter.volume),
    ))
  ), [filters.textbookVersion, textbookChapterOptions]);

  const filteredTextbookChapters = useMemo(() => (
    textbookChapterOptions.filter((chapter) => (
      (!filters.textbookVersion || chapter.textbookVersion === filters.textbookVersion)
      && (!filters.volume || chapter.volume === filters.volume)
    ))
  ), [filters.textbookVersion, filters.volume, textbookChapterOptions]);

  const selectedTextbookChapter = useMemo(() => (
    textbookChapterOptions.find((chapter) => chapter.key === filters.chapterKey)
  ), [filters.chapterKey, textbookChapterOptions]);

  const kpTextbookMap = useMemo(() => {
    const map: Record<number, string> = {};
    textbookChapterOptions.forEach((chapter) => {
      chapter.kpIds.forEach((kpId) => {
        map[kpId] = `${chapter.textbookName} · ${chapter.label}`;
      });
    });
    return map;
  }, [textbookChapterOptions]);

  const visibleKnowledgePoints = useMemo(() => {
    if (!selectedTextbookChapter) return filterKnowledgePoints;
    const chapterKpIds = new Set(selectedTextbookChapter.kpIds);
    return filterKnowledgePoints.filter((kp) => chapterKpIds.has(kp.id));
  }, [filterKnowledgePoints, selectedTextbookChapter]);

  const kpNameMap = useMemo(() => {
    const map: Record<number, string> = {};
    knowledgePoints.forEach((kp) => {
      map[kp.id] = kp.name;
    });
    return map;
  }, [knowledgePoints]);

  const groupedQuestions = useMemo(() => {
    const getGroupName = (question: Question) => {
      if (groupBy === 'type') return questionTypeLabel(question.type);
      if (groupBy === 'kp') return kpNameMap[question.kpId] || `知识点 ${question.kpId}`;
      if (groupBy === 'grade') return gradeLabel(question.grade);
      if (groupBy === 'difficulty') return difficultyLabel(question.difficulty);
      return '全部题目';
    };

    const map = new Map<string, Question[]>();
    questions.forEach((question) => {
      const key = getGroupName(question);
      map.set(key, [...(map.get(key) || []), question]);
    });
    return Array.from(map.entries()).map(([name, items]) => ({ name, items }));
  }, [groupBy, kpNameMap, questions]);

  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  const selectedPracticeKp = useMemo(() => {
    const kpId = filters.kpId ? Number(filters.kpId) : form.kpId;
    const explicitKp = knowledgePoints.find((kp) => kp.id === kpId);
    if (explicitKp) return explicitKp;
    const chapterKpId = selectedTextbookChapter?.kpIds[0];
    return knowledgePoints.find((kp) => kp.id === chapterKpId);
  }, [filters.kpId, form.kpId, knowledgePoints, selectedTextbookChapter]);

  const approvedDraftCount = draftQuestions.filter((item) => item.approved).length;

  const runQualityChecks = (question: CreateQuestionRequest): QualityCheck[] => {
    const stem = question.content.stem.trim();
    const answer = question.answer.value.trim();
    const steps = question.answer.steps || [];
    const existingStems = new Set(questions.map((item) => normalizeStem(item.content.stem)));

    return [
      {
        label: '题干完整',
        passed: stem.length >= 8 && /[？?=]/.test(stem),
        detail: '题干应包含明确问题或等式，避免只给片段。',
      },
      {
        label: '答案完整',
        passed: answer.length > 0,
        detail: '必须有标准答案，才能进入正式题库。',
      },
      {
        label: '步骤可复盘',
        passed: steps.length >= 2 || Boolean(question.answer.explanation?.trim()),
        detail: '至少提供 2 步解法或一段清晰解析。',
      },
      {
        label: '难度合法',
        passed: question.difficulty >= 1 && question.difficulty <= 5,
        detail: '难度必须落在 1-5 级。',
      },
      {
        label: '知识点绑定',
        passed: Boolean(question.kpId),
        detail: '建议绑定知识点，方便诊断、BKT 和错题追练。',
      },
      {
        label: '疑似不重复',
        passed: !existingStems.has(normalizeStem(stem)),
        detail: '与当前列表题干不应高度重复。',
      },
    ];
  };

  const loadQuestions = async (nextPage = page, nextPageSize = pageSize) => {
    setIsLoading(true);
    setMessage(null);
    setHasLoadedQuestions(true);
    try {
      const result = await questionApi.listQuestions({
        page: nextPage,
        size: nextPageSize,
        subject: filters.subject || undefined,
        grade: filters.grade || selectedTextbookChapter?.grade || undefined,
        kpId: filters.kpId ? Number(filters.kpId) : undefined,
        kpIds: !filters.kpId && selectedTextbookChapter?.kpIds.length ? selectedTextbookChapter.kpIds : undefined,
        difficulty: filters.difficulty ? Number(filters.difficulty) : undefined,
        type: filters.type || undefined,
        tag: filters.tag || undefined,
      });
      setQuestions(result.items || []);
      setTotal(result.total || 0);
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

  const applyFilters = async () => {
    setPage(1);
    await loadQuestions(1, pageSize);
  };

  const changePage = async (nextPage: number) => {
    setPage(nextPage);
    if (hasLoadedQuestions) {
      await loadQuestions(nextPage, pageSize);
    }
  };

  const changePageSize = async (nextPageSize: number) => {
    setPage(1);
    setPageSize(nextPageSize);
    if (hasLoadedQuestions) {
      await loadQuestions(1, nextPageSize);
    }
  };

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
      await loadQuestions(1, pageSize);
      setPage(1);
    } catch (err) {
      setMessage(err instanceof Error ? err.message : '题目保存失败');
    } finally {
      setIsSaving(false);
    }
  };

  const findImportedKnowledgePoint = (record: ImportRecord, stem: string): KnowledgePoint | undefined => {
    const explicitKpId = Number(textValue(getImportField(record, ['kpId', 'kp_id', 'knowledgePointId', 'knowledge_point_id', '知识点ID'])));
    if (Number.isFinite(explicitKpId)) {
      const explicit = knowledgePoints.find((kp) => kp.id === explicitKpId);
      if (explicit) return explicit;
    }

    const kpText = textValue(getImportField(record, ['knowledgePoint', 'knowledge_point', 'kp', '知识点']));
    if (kpText) {
      const exact = knowledgePoints.find((kp) => kp.name === kpText);
      if (exact) return exact;
      const fuzzy = knowledgePoints.find((kp) => kpText.includes(kp.name) || stem.includes(kp.name));
      if (fuzzy) return fuzzy;
    }

    const chapterText = textValue(getImportField(record, ['chapter', 'chapterName', 'chapter_name', '章节', '单元']));
    if (chapterText) {
      const chapter = textbookChapterOptions.find((item) => (
        item.chapterName === chapterText || chapterText.includes(item.chapterName) || item.label.includes(chapterText)
      ));
      const kp = knowledgePoints.find((item) => item.id === chapter?.kpIds[0]);
      if (kp) return kp;
    }

    if (selectedTextbookChapter?.kpIds.length) {
      return knowledgePoints.find((kp) => kp.id === selectedTextbookChapter.kpIds[0]);
    }

    return visibleKnowledgePoints.find((kp) => stem.includes(kp.name)) || formKnowledgePoints[0];
  };

  const normalizeImportRecord = (record: ImportRecord): CreateQuestionRequest | null => {
    const content = record.content && typeof record.content === 'object' && !Array.isArray(record.content)
      ? record.content as ImportRecord
      : {};
    const answerRecord = record.answer && typeof record.answer === 'object' && !Array.isArray(record.answer)
      ? record.answer as ImportRecord
      : {};
    const stem = textValue(
      content.stem
      || getImportField(record, ['stem', 'question', 'title', 'content', '题干', '题目', '问题']),
    );
    if (!stem) return null;

    const optionFields = ['A', 'B', 'C', 'D'].map((key) => textValue(getImportField(record, [key, `option${key}`, `选项${key}`]))).filter(Boolean);
    const options = splitImportList(content.options || getImportField(record, ['options', 'choices', '选项']));
    const mergedOptions = optionFields.length ? optionFields : options;
    const kp = findImportedKnowledgePoint(record, stem);
    const answerValue = textValue(
      answerRecord.value
      || answerRecord.answer
      || getImportField(record, ['answer', 'correctAnswer', 'correct_answer', 'standardAnswer', 'standard_answer', '答案', '标准答案']),
    );
    const steps = splitImportList(
      answerRecord.steps
      || getImportField(record, ['steps', 'solutionSteps', 'solution_steps', '解题步骤', '步骤']),
    );
    const explanation = textValue(
      answerRecord.explanation
      || answerRecord.analysis
      || getImportField(record, ['explanation', 'analysis', 'solution', '解析', '讲解']),
    );
    const grade = normalizeImportedGrade(
      getImportField(record, ['grade', '年级', '学段']),
      filters.grade || selectedTextbookChapter?.grade || kp?.grade || form.grade,
    );
    const type = normalizeImportedQuestionType(
      getImportField(record, ['type', 'questionType', 'question_type', '题型']),
      stem,
      mergedOptions,
    );
    const knowledgeLabel = kp?.name || selectedTextbookChapter?.chapterName;

    return {
      kpId: kp?.id,
      subject: textValue(getImportField(record, ['subject', '学科'])) || filters.subject || form.subject || 'math',
      grade,
      type,
      difficulty: normalizeImportedDifficulty(getImportField(record, ['difficulty', 'level', '难度'])),
      content: {
        stem,
        options: mergedOptions,
      },
      answer: {
        value: answerValue,
        steps,
        explanation,
      },
      tags: Array.from(new Set([
        'import-draft',
        'review-required',
        knowledgeLabel,
        selectedTextbookChapter?.textbookName,
        selectedTextbookChapter?.volume,
        selectedTextbookChapter?.chapterName,
        ...splitImportList(getImportField(record, ['tags', 'tag', '标签'])),
      ].filter(isNonEmptyString))),
      source: 'IMPORT_DRAFT',
    };
  };

  const importQuestionDrafts = async () => {
    if (!batchJson.trim()) return;
    setIsSaving(true);
    setMessage(null);
    try {
      const records = parseQuestionImportText(batchJson, importFileName);
      const drafts = records
        .map(normalizeImportRecord)
        .filter((item): item is CreateQuestionRequest => Boolean(item))
        .map((question, index) => {
          const checks = runQualityChecks(question);
          return {
            localId: `import-${Date.now()}-${index}`,
            question,
            checks,
            approved: qualityScore(checks) >= 80,
          };
        });

      if (drafts.length === 0) {
        setMessage('没有识别到可导入题目，请检查 CSV/JSON 字段');
        return;
      }

      setDraftQuestions((prev) => [...drafts, ...prev]);
      setMessage(`已清洗 ${drafts.length} 道导入草稿，请在右侧审核后入库`);
      setBatchJson('');
      setImportFileName('');
    } catch (err) {
      setMessage(err instanceof Error ? err.message : '题库导入失败，请检查 CSV/JSON 格式');
    } finally {
      setIsSaving(false);
    }
  };

  const loadImportFile = async (file?: File) => {
    if (!file) return;
    setImportFileName(file.name);
    setBatchJson(await file.text());
  };

  const deleteQuestion = async (question: Question) => {
    if (!canMaintainQuestions) {
      setMessage('只有管理员可以维护题库');
      return;
    }
    if (!window.confirm(`确认删除这道题吗？\n\n${question.content.stem.slice(0, 80)}`)) {
      return;
    }

    setDeletingQuestionId(question.id);
    setMessage(null);
    try {
      await questionApi.deleteQuestion(question.id);
      setQuestions((prev) => prev.filter((item) => item.id !== question.id));
      setTotal((prev) => Math.max(0, prev - 1));
      setMessage('题目已删除');
    } catch (err) {
      setMessage(err instanceof Error ? err.message : '题目删除失败');
    } finally {
      setDeletingQuestionId(null);
    }
  };

  const generateDailyPractice = async () => {
    const knowledgePoint = selectedTextbookChapter
      ? `${selectedTextbookChapter.chapterName}：${selectedPracticeKp?.name || '章节综合'}`
      : selectedPracticeKp?.name || filters.tag || '初中数学综合巩固';
    setIsGeneratingPractice(true);
    setMessage(null);
    try {
      const result = await tutorApi.dailyPractice({
        knowledgePoint,
        grade: selectedTextbookChapter?.grade || selectedPracticeKp?.grade || form.grade || '初中',
        subject: '数学',
        masteryLevel: '中',
        availableMinutes: 15,
        difficulty: '标准',
        questionCount: 5,
        weakPoints: selectedTextbookChapter
          ? [selectedTextbookChapter.chapterName, selectedPracticeKp?.name].filter(isNonEmptyString)
          : selectedPracticeKp ? [selectedPracticeKp.name] : [],
        scenario: '学生自练',
      });
      setDailyPractice(result.data);
    } catch (err) {
      setMessage(err instanceof Error ? err.message : '今日短练生成失败');
    } finally {
      setIsGeneratingPractice(false);
    }
  };

  const generateDraftQuestions = async () => {
    const kp = selectedPracticeKp || formKnowledgePoints[0];
    const knowledgePoint = selectedTextbookChapter
      ? `${selectedTextbookChapter.chapterName}：${kp?.name || '章节综合'}`
      : kp?.name || filters.tag || '初中数学综合';
    const selectedQuestionTypes = filters.type ? [filters.type as QuestionType] : MIXED_DRAFT_TYPES;
    const difficulty = filters.difficulty ? Number(filters.difficulty) : form.difficulty || 3;
    const draftGrade = filters.grade || selectedTextbookChapter?.grade || kp?.grade || form.grade || 'grade7';
    const textbookRequirement = selectedTextbookChapter
      ? `教材同步范围：${selectedTextbookChapter.textbookName}，${selectedTextbookChapter.volume}，${selectedTextbookChapter.chapterName}。题目只围绕该章节核心概念与常见课堂训练展开，避免超纲到后续章节。`
      : '';

    setIsGeneratingDrafts(true);
    setMessage(null);
    try {
      const generationPlan = filters.type
        ? [{ type: filters.type as QuestionType, count: 5 }]
        : selectedQuestionTypes.map((type) => ({ type, count: 1 }));
      const results = await Promise.all(generationPlan.map(({ type, count }) => (
        tutorApi.generateQuestions({
          subject: filters.subject || form.subject || 'math',
          grade: gradeLabel(draftGrade),
          knowledgePoint,
          questionType: type,
          difficulty,
          count,
          additionalRequirements: `${textbookRequirement}生成可直接进入审核的题库草稿。题型必须是${questionTypeLabel(type)}。每道题必须包含标准答案 answer.value、至少 2 条解题步骤 answer.steps，以及清晰解析 answer.explanation；答案必须能由步骤推导，不允许留空或只写“略”。`,
        })
      )));
      const generatedQuestions = results.flatMap((result, resultIndex) => (
        (result.questions || []).map((question) => ({
          question,
          requestedType: generationPlan[resultIndex]?.type || 'CALCULATION',
        }))
      )).slice(0, 5);

      const drafts: DraftQuestion[] = generatedQuestions.map(({ question, requestedType }, index) => {
        const generatedKpId = (question as Question & { kp_id?: number }).kp_id;
        const draft: CreateQuestionRequest = {
          kpId: kp?.id || question.kpId || generatedKpId || form.kpId,
          subject: filters.subject || form.subject || 'math',
          grade: draftGrade,
          type: requestedType,
          difficulty: Number(question.difficulty) || difficulty,
          content: {
            stem: question.content?.stem || '',
            options: question.content?.options || [],
            figures: question.content?.figures || [],
          },
          answer: {
            value: question.answer?.value || '',
            steps: question.answer?.steps || [],
            explanation: question.answer?.explanation || '',
          },
          tags: Array.from(new Set([
            'ai-generated',
            'review-required',
            knowledgePoint,
            selectedTextbookChapter?.textbookName,
            selectedTextbookChapter?.volume,
            selectedTextbookChapter?.chapterName,
            ...(question.tags || []),
          ].filter(isNonEmptyString))),
          source: 'AI_DRAFT',
        };
        const checks = runQualityChecks(draft);
        return {
          localId: `${Date.now()}-${index}`,
          question: draft,
          checks,
          approved: qualityScore(checks) >= 80,
        };
      });

      setDraftQuestions(drafts);
      setMessage(drafts.length > 0 ? `已生成 ${drafts.length} 道待审核草稿` : 'AI 没有生成可用草稿，请调整条件重试');
    } catch (err) {
      setMessage(err instanceof Error ? err.message : 'AI 生成草稿失败');
    } finally {
      setIsGeneratingDrafts(false);
    }
  };

  const toggleDraftApproval = (localId: string) => {
    setDraftQuestions((prev) => prev.map((item) => (
      item.localId === localId ? { ...item, approved: !item.approved } : item
    )));
  };

  const removeDraftQuestion = (localId: string) => {
    setDraftQuestions((prev) => prev.filter((item) => item.localId !== localId));
  };

  const importApprovedDrafts = async () => {
    const approved = draftQuestions.filter((item) => item.approved).map((item) => item.question);
    if (approved.length === 0) {
      setMessage('请先勾选至少一道审核通过的草稿');
      return;
    }

    setIsImportingDrafts(true);
    setMessage(null);
    try {
      await questionApi.batchCreateQuestions(approved);
      setMessage(`已入库 ${approved.length} 道 AI 审核题`);
      setDraftQuestions((prev) => prev.filter((item) => !item.approved));
      await loadQuestions(1, pageSize);
      setPage(1);
    } catch (err) {
      setMessage(err instanceof Error ? err.message : 'AI 草稿入库失败');
    } finally {
      setIsImportingDrafts(false);
    }
  };

  return (
    <div className="mx-auto flex min-h-[calc(100dvh-6rem)] max-w-7xl flex-col lg:min-h-[calc(100vh-8rem)]">
      <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-xl font-bold text-gray-900">题库与知识资源</h1>
          <p className="text-xs text-gray-500 mt-1">
            当前管理测试题库；后续扩展为多学科资料、家族技能与经验传承知识的统一资源库
          </p>
        </div>
        <button
          onClick={() => { void loadQuestions(page, pageSize); }}
          disabled={isLoading}
          className="inline-flex items-center gap-2 rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-600 hover:bg-gray-50 disabled:opacity-50"
        >
          {isLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : <RefreshCw className="w-4 h-4" />}
          刷新
        </button>
      </div>

      <div className="grid flex-1 grid-cols-1 gap-4 lg:min-h-0 xl:grid-cols-[320px_1fr]">
        <section className="overflow-hidden rounded-xl border border-gray-200 bg-white xl:overflow-y-auto">
          <details className="border-b border-gray-100">
            <summary className="flex cursor-pointer items-center justify-between gap-3 p-4 text-sm font-semibold text-gray-900 hover:bg-gray-50">
              <span className="inline-flex items-center gap-2">
              <Plus className="w-4 h-4 text-blue-600" />
                手动录入题目
              </span>
              <span className="text-xs font-normal text-gray-400">备用</span>
            </summary>
            <div className="px-4 pb-4">

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
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
                <select
                  value={form.grade || ''}
                  onChange={(e) => setForm((prev) => ({ ...prev, grade: e.target.value }))}
                  className="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-blue-500"
                >
                  {GRADE_OPTIONS.map((grade) => (
                    <option key={grade.value} value={grade.value}>{grade.label}</option>
                  ))}
                </select>
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

            <div className="grid grid-cols-1 gap-3 mt-3 sm:grid-cols-2">
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
                placeholder="数学 方程 易错"
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
          </details>

          <details>
            <summary className="flex cursor-pointer items-center justify-between gap-3 p-4 text-sm font-semibold text-gray-900 hover:bg-gray-50">
              <span className="inline-flex items-center gap-2">
                <FileJson className="w-4 h-4 text-indigo-600" />
                题库文件导入
              </span>
              <span className="text-xs font-normal text-gray-400">CSV/JSON</span>
            </summary>
            <div className="px-4 pb-4">
            <label className="block">
              <span className="text-xs font-medium text-gray-500">上传文件</span>
              <input
                type="file"
                accept=".csv,.tsv,.json,application/json,text/csv,text/tab-separated-values,text/plain"
                onChange={(event) => { void loadImportFile(event.target.files?.[0]); }}
                className="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-xs text-gray-600 file:mr-3 file:rounded-md file:border-0 file:bg-gray-100 file:px-2 file:py-1 file:text-xs file:text-gray-700"
              />
              {importFileName && (
                <span className="mt-1 block truncate text-xs text-gray-400">{importFileName}</span>
              )}
            </label>
            <textarea
              value={batchJson}
              onChange={(e) => {
                setBatchJson(e.target.value);
                if (importFileName) setImportFileName('');
              }}
              rows={8}
              placeholder={'CSV 示例：\n题干,答案,解析,年级,章节,知识点,题型,难度\n解方程：2x+5=13,x=4,移项得2x=8，再系数化为1,七年级,一元一次方程与不等式,一元一次方程,计算题,2\n\nJSON 示例：\n[{"stem":"解方程：2x+5=13","answer":"x=4","explanation":"移项得2x=8，再系数化为1","grade":"grade7","knowledgePoint":"一元一次方程"}]'}
              className="mt-3 w-full resize-none rounded-lg border border-gray-200 p-3 font-mono text-xs outline-none focus:ring-2 focus:ring-blue-500"
            />
            <button
              onClick={importQuestionDrafts}
              disabled={isSaving || !batchJson.trim()}
              className="mt-3 w-full inline-flex items-center justify-center gap-2 rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50"
            >
              {isSaving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Database className="w-4 h-4" />}
              清洗为草稿
            </button>
            <p className="mt-2 text-xs leading-5 text-gray-500">
              导入内容不会直接入库，会先进入右侧草稿审核；建议包含题干、答案、解析、年级、章节或知识点字段。
            </p>
            </div>
          </details>
        </section>

        <section className="flex min-w-0 flex-col overflow-hidden rounded-xl border border-gray-200 bg-white">
          <div className="border-b border-gray-100 p-4">
            <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between sm:gap-3">
              <div className="flex flex-wrap items-center gap-2">
                <BookOpen className="w-4 h-4 text-blue-600" />
                <h2 className="text-sm font-semibold text-gray-900">题库列表</h2>
                <span className="text-xs text-gray-400">{hasLoadedQuestions ? `共 ${total} 条` : '待加载'}</span>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                {message && <span className="text-xs text-blue-600">{message}</span>}
                <button
                  type="button"
                  onClick={() => { void generateDailyPractice(); }}
                  disabled={isGeneratingPractice}
                  className="inline-flex items-center gap-1.5 rounded-lg bg-indigo-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
                >
                  {isGeneratingPractice ? <Loader2 className="w-4 h-4 animate-spin" /> : <Sparkles className="w-4 h-4" />}
                  今日短练
                </button>
                <button
                  type="button"
                  onClick={() => { void generateDraftQuestions(); }}
                  disabled={isGeneratingDrafts}
                  className="inline-flex items-center gap-1.5 rounded-lg bg-blue-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-blue-700 disabled:opacity-50"
                >
                  {isGeneratingDrafts ? <Loader2 className="w-4 h-4 animate-spin" /> : <Wand2 className="w-4 h-4" />}
                  AI 草稿
                </button>
                <button
                  type="button"
                  onClick={() => {
                    if (!canMaintainQuestions) {
                      setMessage('只有管理员可以维护题库');
                      return;
                    }
                    setMaintenanceMode((prev) => !prev);
                  }}
                  className={`inline-flex items-center gap-1.5 rounded-lg border px-3 py-1.5 text-xs font-medium ${
                    maintenanceMode
                      ? 'border-red-200 bg-red-50 text-red-700 hover:bg-red-100'
                      : 'border-gray-200 bg-white text-gray-600 hover:bg-gray-50'
                  }`}
                >
                  <ShieldCheck className="w-4 h-4" />
                  维护模式
                </button>
              </div>
            </div>

            <div className="mt-4 grid grid-cols-1 gap-2 rounded-lg bg-gray-50 p-3 sm:grid-cols-3">
              <label className="min-w-0">
                <span className="mb-1 flex items-center gap-1.5 text-xs font-medium text-gray-600">
                  <BookOpen className="h-3.5 w-3.5" />
                  教材版本
                </span>
                <select
                  value={filters.textbookVersion}
                  onChange={(e) => {
                    setPage(1);
                    setFilters((prev) => ({
                      ...prev,
                      textbookVersion: e.target.value,
                      volume: '',
                      chapterKey: '',
                      kpId: '',
                    }));
                  }}
                  className="w-full rounded-lg border border-gray-200 bg-white px-2 py-2 text-xs outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value="">全部教材</option>
                  {textbookVersions.map((version) => (
                    <option key={version.value} value={version.value}>{version.label}</option>
                  ))}
                </select>
              </label>
              <label className="min-w-0">
                <span className="mb-1 block text-xs font-medium text-gray-600">册别</span>
                <select
                  value={filters.volume}
                  onChange={(e) => {
                    setPage(1);
                    setFilters((prev) => ({ ...prev, volume: e.target.value, chapterKey: '', kpId: '' }));
                  }}
                  className="w-full rounded-lg border border-gray-200 bg-white px-2 py-2 text-xs outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value="">全部册别</option>
                  {textbookVolumes.map((volume) => (
                    <option key={volume} value={volume}>{volume}</option>
                  ))}
                </select>
              </label>
              <label className="min-w-0">
                <span className="mb-1 block text-xs font-medium text-gray-600">章节</span>
                <select
                  value={filters.chapterKey}
                  onChange={(e) => {
                    const chapter = textbookChapterOptions.find((item) => item.key === e.target.value);
                    setPage(1);
                    setFilters((prev) => ({
                      ...prev,
                      chapterKey: e.target.value,
                      textbookVersion: chapter?.textbookVersion || prev.textbookVersion,
                      volume: chapter?.volume || prev.volume,
                      grade: chapter?.grade || prev.grade,
                      kpId: '',
                    }));
                  }}
                  className="w-full rounded-lg border border-gray-200 bg-white px-2 py-2 text-xs outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value="">全部章节</option>
                  {filteredTextbookChapters.map((chapter) => (
                    <option key={chapter.key} value={chapter.key}>
                      {chapter.label}（{chapter.kpIds.length} 知识点）
                    </option>
                  ))}
                </select>
              </label>
            </div>

            <div className="mt-4 grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-6">
              <select
                value={filters.subject}
                onChange={(e) => {
                  setPage(1);
                  setFilters((prev) => ({
                    ...prev,
                    subject: e.target.value,
                    textbookVersion: '',
                    volume: '',
                    chapterKey: '',
                    kpId: '',
                  }));
                }}
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
                value={filters.grade}
                onChange={(e) => {
                  setPage(1);
                  setFilters((prev) => ({ ...prev, grade: e.target.value }));
                }}
                className="rounded-lg border border-gray-200 px-2 py-2 text-xs outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="">全部年级</option>
                {GRADE_OPTIONS.map((grade) => (
                  <option key={grade.value} value={grade.value}>{grade.label}</option>
                ))}
              </select>
              <select
                value={filters.type}
                onChange={(e) => {
                  setPage(1);
                  setFilters((prev) => ({ ...prev, type: e.target.value }));
                }}
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
                onChange={(e) => {
                  setPage(1);
                  setFilters((prev) => ({ ...prev, difficulty: e.target.value }));
                }}
                className="rounded-lg border border-gray-200 px-2 py-2 text-xs outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="">全部难度</option>
                {[1, 2, 3, 4, 5].map((level) => (
                  <option key={level} value={level}>{difficultyLabel(level)}</option>
                ))}
              </select>
              <select
                value={filters.kpId}
                onChange={(e) => {
                  setPage(1);
                  setFilters((prev) => ({ ...prev, kpId: e.target.value }));
                }}
                className="rounded-lg border border-gray-200 px-2 py-2 text-xs outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="">全部知识点</option>
                {visibleKnowledgePoints.map((kp) => (
                  <option key={kp.id} value={kp.id}>{kp.name}</option>
                ))}
              </select>
              <div className="flex gap-2">
                <input
                  value={filters.tag}
                  onChange={(e) => {
                    setPage(1);
                    setFilters((prev) => ({ ...prev, tag: e.target.value }));
                  }}
                  placeholder="标签"
                  className="min-w-0 flex-1 rounded-lg border border-gray-200 px-2 py-2 text-xs outline-none focus:ring-2 focus:ring-blue-500"
                />
                <button
                  onClick={() => { void applyFilters(); }}
                  className="inline-flex items-center justify-center rounded-lg bg-gray-900 px-3 text-white hover:bg-gray-800"
                >
                  <Filter className="w-4 h-4" />
                </button>
              </div>
            </div>

            <div className="mt-3 flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <Layers className="w-4 h-4 text-gray-400" />
                <select
                  value={groupBy}
                  onChange={(e) => setGroupBy(e.target.value as GroupBy)}
                  className="rounded-lg border border-gray-200 px-2 py-1.5 text-xs outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value="none">不分组</option>
                  <option value="type">按题型</option>
                  <option value="kp">按知识点</option>
                  <option value="grade">按推荐年级</option>
                  <option value="difficulty">按难度</option>
                </select>
                <select
                  value={pageSize}
                  onChange={(e) => {
                    void changePageSize(Number(e.target.value));
                  }}
                  className="rounded-lg border border-gray-200 px-2 py-1.5 text-xs outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value={10}>每页 10</option>
                  <option value={30}>每页 30</option>
                  <option value={50}>每页 50</option>
                </select>
              </div>
              <div className="flex items-center gap-2 text-xs text-gray-500">
                <button
                  onClick={() => { void changePage(Math.max(1, page - 1)); }}
                  disabled={!hasLoadedQuestions || page <= 1 || isLoading}
                  className="inline-flex h-7 w-7 items-center justify-center rounded-lg border border-gray-200 bg-white disabled:opacity-40"
                >
                  <ChevronLeft className="w-4 h-4" />
                </button>
                <span>第 {page} / {totalPages} 页</span>
                <button
                  onClick={() => { void changePage(Math.min(totalPages, page + 1)); }}
                  disabled={!hasLoadedQuestions || page >= totalPages || isLoading}
                  className="inline-flex h-7 w-7 items-center justify-center rounded-lg border border-gray-200 bg-white disabled:opacity-40"
                >
                  <ChevronRight className="w-4 h-4" />
                </button>
              </div>
            </div>
          </div>

          <div className="flex-1 overflow-y-auto p-4">
            {draftQuestions.length > 0 && (
              <section className="mb-4 rounded-lg border border-blue-100 bg-blue-50 p-4">
                <div className="mb-3 flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                  <div>
                    <h3 className="text-sm font-semibold text-gray-900">AI 题目草稿审核</h3>
                    <p className="mt-1 text-xs text-gray-600">
                      先审核质量门禁，再把勾选通过的题目批量入库。
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() => { void importApprovedDrafts(); }}
                    disabled={isImportingDrafts || approvedDraftCount === 0}
                    className="inline-flex items-center gap-1.5 rounded-lg bg-gray-900 px-3 py-1.5 text-xs font-medium text-white hover:bg-gray-800 disabled:opacity-50"
                  >
                    {isImportingDrafts ? <Loader2 className="w-4 h-4 animate-spin" /> : <CheckCircle2 className="w-4 h-4" />}
                    入库 {approvedDraftCount} 题
                  </button>
                </div>

                <div className="space-y-3">
                  {draftQuestions.map((draft, index) => {
                    const score = qualityScore(draft.checks);
                    return (
                      <article key={draft.localId} className="rounded-lg border border-blue-100 bg-white p-4">
                        <div className="mb-2 flex flex-wrap items-center gap-2">
                          <label className="inline-flex items-center gap-2 text-xs font-medium text-gray-700">
                            <input
                              type="checkbox"
                              checked={draft.approved}
                              onChange={() => toggleDraftApproval(draft.localId)}
                              className="h-4 w-4 rounded border-gray-300 text-blue-600"
                            />
                            审核通过
                          </label>
                          <span className={`rounded px-2 py-0.5 text-xs ${
                            score >= 80 ? 'bg-green-50 text-green-700' : score >= 60 ? 'bg-yellow-50 text-yellow-700' : 'bg-red-50 text-red-700'
                          }`}
                          >
                            质量 {score}%
                          </span>
                          <span className="rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-600">
                            {gradeLabel(draft.question.grade)}
                          </span>
                          <span className="rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-600">
                            {questionTypeLabel(draft.question.type)}
                          </span>
                          <span className="rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-600">
                            {difficultyLabel(draft.question.difficulty)}
                          </span>
                          {draft.question.kpId && (
                            <span className="rounded bg-purple-50 px-2 py-0.5 text-xs text-purple-700">
                              {kpNameMap[draft.question.kpId] || `知识点 ${draft.question.kpId}`}
                            </span>
                          )}
                          {draft.question.kpId && kpTextbookMap[draft.question.kpId] && (
                            <span className="rounded bg-cyan-50 px-2 py-0.5 text-xs text-cyan-700">
                              {kpTextbookMap[draft.question.kpId]}
                            </span>
                          )}
                          <button
                            type="button"
                            onClick={() => removeDraftQuestion(draft.localId)}
                            className="ml-auto inline-flex items-center gap-1 rounded border border-gray-200 px-2 py-1 text-xs text-gray-500 hover:bg-gray-50"
                          >
                            <XCircle className="h-3.5 w-3.5" />
                            移除
                          </button>
                        </div>

                        <p className="text-sm text-gray-900 whitespace-pre-wrap">
                          {index + 1}. {draft.question.content.stem}
                        </p>
                        {draft.question.content.options?.length ? (
                          <div className="mt-2 grid grid-cols-1 gap-1 text-xs text-gray-500 sm:grid-cols-2">
                            {draft.question.content.options.map((option, optionIndex) => (
                              <span key={`${draft.localId}-${optionIndex}`} className="min-w-0 break-words">
                                {String.fromCharCode(65 + optionIndex)}. {option}
                              </span>
                            ))}
                          </div>
                        ) : null}

                        <details className="mt-2">
                          <summary className="cursor-pointer text-xs text-gray-500 hover:text-gray-700">答案、解析与质量检查</summary>
                          <div className="mt-2 grid grid-cols-1 gap-3 lg:grid-cols-2">
                            <div className="rounded bg-gray-50 p-3 text-xs text-gray-600">
                              <p className="font-medium text-gray-900">答案：{draft.question.answer.value || '暂无'}</p>
                              {draft.question.answer.steps?.length ? (
                                <ol className="mt-2 list-decimal list-inside space-y-1">
                                  {draft.question.answer.steps.map((step, stepIndex) => (
                                    <li key={`${draft.localId}-step-${stepIndex}`}>{step}</li>
                                  ))}
                                </ol>
                              ) : null}
                              {draft.question.answer.explanation && (
                                <p className="mt-2">{draft.question.answer.explanation}</p>
                              )}
                            </div>
                            <div className="rounded bg-gray-50 p-3">
                              <div className="grid grid-cols-1 gap-1.5 text-xs">
                                {draft.checks.map((check) => (
                                  <div key={check.label} className="flex items-start gap-2">
                                    {check.passed ? (
                                      <CheckCircle2 className="mt-0.5 h-3.5 w-3.5 flex-none text-green-600" />
                                    ) : (
                                      <XCircle className="mt-0.5 h-3.5 w-3.5 flex-none text-red-500" />
                                    )}
                                    <div>
                                      <span className={check.passed ? 'text-green-700' : 'text-red-600'}>{check.label}</span>
                                      <span className="ml-1 text-gray-500">{check.detail}</span>
                                    </div>
                                  </div>
                                ))}
                              </div>
                            </div>
                          </div>
                        </details>
                      </article>
                    );
                  })}
                </div>
              </section>
            )}

            {dailyPractice && (
              <section className="mb-4 rounded-lg border border-indigo-100 bg-indigo-50 p-4">
                <div className="mb-3 flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                  <div>
                    <h3 className="text-sm font-semibold text-gray-900">{dailyPractice.daily_goal}</h3>
                    <p className="mt-1 text-xs text-gray-600">{dailyPractice.warmup_prompt}</p>
                  </div>
                  <span className="rounded bg-white px-2 py-1 text-xs text-indigo-700">10-15 分钟</span>
                </div>
                <div className="space-y-2">
                  {asArray(dailyPractice.questions).map((item, index) => (
                    <details key={`${item.stem}-${index}`} className="rounded border border-indigo-100 bg-white p-3">
                      <summary className="cursor-pointer text-sm text-gray-900">
                        {index + 1}. {item.stem}
                      </summary>
                      <div className="mt-2 text-xs text-gray-600">
                        <p className="font-medium text-gray-800">答案：{item.answer}</p>
                        <p className="mt-1">{item.explanation}</p>
                        <div className="mt-2 flex flex-wrap gap-1">
                          {asArray(item.error_tags).map((tag) => (
                            <span key={tag} className="rounded bg-indigo-50 px-2 py-0.5 text-indigo-700">{tag}</span>
                          ))}
                        </div>
                      </div>
                    </details>
                  ))}
                </div>
                <div className="mt-3 grid grid-cols-1 gap-2 text-xs text-gray-600 lg:grid-cols-2">
                  <div className="rounded border border-indigo-100 bg-white p-2">
                    <p className="font-medium text-gray-800">自评标准</p>
                    <ul className="mt-1 space-y-1">
                      {asArray(dailyPractice.self_check).map((item) => <li key={item}>{item}</li>)}
                    </ul>
                  </div>
                  <div className="rounded border border-indigo-100 bg-white p-2">
                    <p className="font-medium text-gray-800">下次复习</p>
                    <p className="mt-1">{dailyPractice.next_review_action}</p>
                  </div>
                </div>
              </section>
            )}
            {isLoading ? (
              <div className="h-full flex items-center justify-center text-gray-400">
                <Loader2 className="w-6 h-6 animate-spin" />
              </div>
            ) : !hasLoadedQuestions ? (
              <div className="h-full flex items-center justify-center text-center text-gray-400">
                <div>
                  <BookOpen className="w-12 h-12 mx-auto mb-3 opacity-30" />
                  <p className="text-sm">选择筛选条件或点击刷新加载题库</p>
                  <p className="mt-1 text-xs">默认不展开全部题目，避免列表过长。</p>
                </div>
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
                {groupedQuestions.map((group) => (
                  <details key={group.name} open className="rounded-lg border border-gray-100 bg-white">
                    <summary className="cursor-pointer px-4 py-3 text-sm font-medium text-gray-800">
                      {group.name}
                      <span className="ml-2 text-xs font-normal text-gray-400">{group.items.length} 题</span>
                    </summary>
                    <div className="space-y-3 border-t border-gray-100 p-3">
                      {group.items.map((question) => (
                        <article key={question.id} className="rounded-lg border border-gray-100 p-4">
                          <div className="flex flex-wrap items-center gap-2 mb-2">
                            <span className="rounded bg-blue-50 px-2 py-0.5 text-xs font-medium text-blue-700">
                              {subjectLabel(question.subject)}
                            </span>
                            <span className="rounded bg-purple-50 px-2 py-0.5 text-xs text-purple-700">
                              {kpNameMap[question.kpId] || `知识点 ${question.kpId}`}
                            </span>
                            {kpTextbookMap[question.kpId] && (
                              <span className="rounded bg-cyan-50 px-2 py-0.5 text-xs text-cyan-700">
                                {kpTextbookMap[question.kpId]}
                              </span>
                            )}
                            <span className="rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-600">
                              {gradeLabel(question.grade)}
                            </span>
                            <span className="rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-600">
                              {questionTypeLabel(question.type)}
                            </span>
                            <span className="rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-600">
                              {difficultyLabel(question.difficulty)}
                            </span>
                            {question.tags?.map((tag) => (
                              <span key={tag} className="inline-flex items-center gap-1 rounded bg-green-50 px-2 py-0.5 text-xs text-green-700">
                                <Tags className="w-3 h-3" />
                                {tag}
                              </span>
                            ))}
                            {maintenanceMode && (
                              <button
                                type="button"
                                onClick={() => { void deleteQuestion(question); }}
                                disabled={deletingQuestionId === question.id}
                                className="ml-auto inline-flex items-center gap-1 rounded border border-red-100 px-2 py-0.5 text-xs text-red-600 hover:bg-red-50 disabled:opacity-50"
                              >
                                {deletingQuestionId === question.id ? (
                                  <Loader2 className="h-3 w-3 animate-spin" />
                                ) : (
                                  <Trash2 className="h-3 w-3" />
                                )}
                                删除
                              </button>
                            )}
                          </div>
                          <p className="text-sm text-gray-900 whitespace-pre-wrap">{question.content.stem}</p>
                          {question.content.options?.length ? (
                            <div className="mt-2 grid grid-cols-2 gap-1 text-xs text-gray-500">
                              {question.content.options.map((option, index) => (
                                <span key={index} className="min-w-0 break-words">{String.fromCharCode(65 + index)}. {option}</span>
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
                  </details>
                ))}
              </div>
            )}
          </div>
        </section>
      </div>
    </div>
  );
}
