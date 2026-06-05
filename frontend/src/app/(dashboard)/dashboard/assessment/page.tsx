'use client';

import { useState, useEffect, useCallback, useMemo } from 'react';
import { assessmentApi, questionApi, tutorApi } from '@/lib/api';
import { useAuthStore } from '@/stores/authStore';
import { formatDate, masteryColor, masteryLevel } from '@/lib/utils';
import type { AbilityProfile, ExamReviewResult, KnowledgePoint, Question, StudyPlanResult, TestRecord, TestRecordDetail } from '@/types';
import {
  RadarChart, PolarGrid, PolarAngleAxis, PolarRadiusAxis,
  Radar, ResponsiveContainer, Tooltip,
} from 'recharts';
import { TrendingUp, Target, Zap, AlertTriangle, ClipboardCheck, BookX, ArrowRight, Loader2, Sparkles } from 'lucide-react';

function safeScore(value: unknown): number {
  const score = Number(value);
  return Number.isFinite(score) ? score : 0;
}

function scoreLabel(value: unknown): string {
  return `${Math.round(safeScore(value))}`;
}

function clampScore(value: number): number {
  return Math.max(0, Math.min(100, Math.round(value)));
}

function flattenKnowledgePoints(nodes: KnowledgePoint[]): KnowledgePoint[] {
  const result: KnowledgePoint[] = [];
  const walk = (items: KnowledgePoint[]) => {
    for (const item of items) {
      result.push(item);
      if (item.children?.length) walk(item.children);
    }
  };
  walk(nodes);
  return result;
}

function asArray<T>(value: T[] | undefined | null): T[] {
  return Array.isArray(value) ? value : [];
}

export default function AssessmentPage() {
  const [profiles, setProfiles] = useState<AbilityProfile[]>([]);
  const [testHistory, setTestHistory] = useState<TestRecord[]>([]);
  const [latestDetail, setLatestDetail] = useState<TestRecordDetail | null>(null);
  const [wrongQuestions, setWrongQuestions] = useState<Question[]>([]);
  const [kpNames, setKpNames] = useState<Record<number, string>>({});
  const [knowledgePoints, setKnowledgePoints] = useState<KnowledgePoint[]>([]);
  const [examReview, setExamReview] = useState<ExamReviewResult | null>(null);
  const [studyPlan, setStudyPlan] = useState<StudyPlanResult | null>(null);
  const [isGeneratingExamReview, setIsGeneratingExamReview] = useState(false);
  const [isGeneratingStudyPlan, setIsGeneratingStudyPlan] = useState(false);
  const [aiActionError, setAiActionError] = useState('');
  const [loading, setLoading] = useState(true);

  const userId = useAuthStore((s) => s.user?.id);

  const loadAll = useCallback(async () => {
    setLoading(true);
    try {
      const [profileData, historyData] = await Promise.all([
        assessmentApi.getProfiles(userId!),
        assessmentApi.getHistory(userId!, 10),
      ]);
      setProfiles(profileData || []);
      setTestHistory(historyData || []);
      setLoading(false);

      void questionApi.getWrongQuestions(userId!, 20)
        .then((wrongData) => setWrongQuestions(wrongData || []))
        .catch(() => setWrongQuestions([]));

      if (historyData?.[0]?.id) {
        void assessmentApi.getTestDetail(historyData[0].id)
          .then(setLatestDetail)
          .catch(() => setLatestDetail(null));
      } else {
        setLatestDetail(null);
      }
      void questionApi.getKnowledgeTree()
        .then((treeData) => {
          const points = flattenKnowledgePoints(treeData || []);
          const names: Record<number, string> = {};
          for (const kp of points) {
            names[kp.id] = kp.name;
          }
          setKpNames(names);
          setKnowledgePoints(points);
        })
        .catch(() => {
          setKpNames({});
          setKnowledgePoints([]);
        });
    } catch (err) {
      console.error('Failed to load assessment data', err);
      setLoading(false);
    } finally {
      // Auxiliary data is intentionally loaded in the background above.
    }
  }, [userId]);

  useEffect(() => {
    if (userId) {
      loadAll();
    } else {
      setLoading(false);
    }
  }, [userId, loadAll]);

  // Statistics
  const mastered = profiles.filter((p) => p.masteryProbability >= 0.85).length;
  const weak = profiles.filter((p) => p.masteryProbability < 0.4).length;
  const zpd = profiles.filter((p) => p.masteryProbability >= 0.3 && p.masteryProbability <= 0.7).length;
  const avgMastery = profiles.length > 0
    ? Math.round((profiles.reduce((sum, p) => sum + p.masteryProbability, 0) / profiles.length) * 100)
    : 0;
  const latestTest = testHistory[0];

  const topAbility = [
    { name: '德', label: '品格与责任', value: null, status: '待接入' },
    { name: '智', label: '学科学习与思维', value: avgMastery, status: profiles.length > 0 ? '已接入' : '待诊断' },
    { name: '体', label: '身体与习惯', value: null, status: '待接入' },
    { name: '美', label: '审美与表达', value: null, status: '待接入' },
    { name: '劳', label: '实践与生活能力', value: null, status: '待接入' },
  ];

  const mathAbilityRadarData = useMemo(() => {
    const latestScore = latestTest ? safeScore(latestTest.totalScore) : avgMastery;
    const base = profiles.length > 0
      ? avgMastery
      : latestTest
        ? latestScore
        : 0;
    const abilityScores: Record<string, number> = {
      概念理解: base,
      计算能力: base,
      审题能力: base,
      推理能力: base,
      应用迁移: base,
      细心规范: base,
    };

    const wrongItems = latestDetail?.items.filter((item) => item.wrong) || [];
    for (const item of wrongItems) {
      const type = item.errorType || '答案不符';
      if (/概念|理解/.test(type)) {
        abilityScores.概念理解 -= 18;
        abilityScores.审题能力 -= 8;
      }
      if (/计算|答案不符/.test(type)) {
        abilityScores.计算能力 -= 16;
        abilityScores.细心规范 -= 8;
      }
      if (/符号/.test(type)) {
        abilityScores.计算能力 -= 10;
        abilityScores.细心规范 -= 16;
      }
      if (/步骤遗漏/.test(type)) {
        abilityScores.推理能力 -= 8;
        abilityScores.细心规范 -= 14;
      }
      if (/公式误用/.test(type)) {
        abilityScores.概念理解 -= 12;
        abilityScores.应用迁移 -= 14;
      }
      if (/逻辑/.test(type)) {
        abilityScores.推理能力 -= 18;
      }
    }

    if (wrongQuestions.length > 0 && wrongItems.length === 0) {
      abilityScores.细心规范 -= Math.min(12, wrongQuestions.length * 2);
      abilityScores.计算能力 -= Math.min(10, wrongQuestions.length * 2);
    }

    return Object.entries(abilityScores).map(([subject, value]) => ({
      subject,
      value: clampScore(value),
      fullMark: 100,
    }));
  }, [avgMastery, latestDetail, latestTest, profiles.length, wrongQuestions.length]);

  const hasAssessmentData = profiles.length > 0 || Boolean(latestTest);

  const subjectAbilityData = useMemo(() => {
    const mathProfiles = profiles.filter((profile) => {
      const kp = knowledgePoints.find((item) => item.id === profile.kpId);
      return !kp || kp.subject === 'math';
    });
    const mathAvg = mathProfiles.length > 0
      ? Math.round((mathProfiles.reduce((sum, p) => sum + p.masteryProbability, 0) / mathProfiles.length) * 100)
      : 0;

    return [
      { name: '数学', value: mathAvg, status: mathProfiles.length > 0 ? '已接入' : '待诊断' },
      { name: '语文', value: null, status: '待接入' },
      { name: '英语', value: null, status: '待接入' },
      { name: '科学', value: null, status: '待接入' },
      { name: '课外阅读', value: null, status: '待接入' },
      { name: '问题解决', value: null, status: '待接入' },
    ];
  }, [knowledgePoints, profiles]);

  const stats = [
    { label: '平均掌握度', value: `${avgMastery}%`, icon: TrendingUp, color: 'text-blue-600 bg-blue-50' },
    { label: '已掌握', value: `${mastered}个`, icon: Zap, color: 'text-green-600 bg-green-50' },
    { label: '需要加强', value: `${weak}个`, icon: AlertTriangle, color: 'text-orange-600 bg-orange-50' },
    { label: '最近发展区', value: `${zpd}个`, icon: Target, color: 'text-purple-600 bg-purple-50' },
  ];

  const parentSummary = useMemo(() => {
    const sortedWeak = profiles
      .slice()
      .sort((a, b) => a.masteryProbability - b.masteryProbability)
      .slice(0, 3);
    const weakNames = sortedWeak.map((profile) => kpNames[profile.kpId] || `知识点${profile.kpId}`);
    const latestScore = latestTest ? safeScore(latestTest.totalScore) : null;
    const wrongCount = wrongQuestions.length;
    const diagnosticItems = latestDetail?.items.filter((item) => item.wrong) || [];
    const errorTypes = Array.from(new Set(
      diagnosticItems
        .map((item) => item.errorType)
        .filter((item): item is string => Boolean(item && item !== '无')),
    )).slice(0, 3);
    const savedSuggestion = diagnosticItems.find((item) => item.nextSuggestion)?.nextSuggestion;

    let level = '暂无诊断数据';
    if (latestScore != null) {
      if (latestScore >= 85) level = '本次表现较稳';
      else if (latestScore >= 60) level = '基础已建立，仍有薄弱点';
      else level = '需要优先补基础';
    }

    const suggestions: string[] = [];
    if (!latestTest) {
      suggestions.push('先完成一次数学诊断，建立初始学力档案。');
    } else {
      if (weakNames.length > 0) {
        suggestions.push(`优先复习 ${weakNames.join('、')}。`);
      }
      if (wrongCount > 0) {
        suggestions.push(`从错题本选择 ${Math.min(3, wrongCount)} 道题，让孩子先复述思路再看 AI 讲解。`);
      }
      if (savedSuggestion) {
        suggestions.push(savedSuggestion);
      }
      if (latestScore != null && latestScore < 60) {
        suggestions.push('下一次测试减少题量，先保证基础题正确率。');
      } else {
        suggestions.push('下一次练习加入 1-2 道中等难度变式题，观察是否真正掌握。');
      }
    }

    return {
      level,
      latestScore,
      latestDate: latestTest?.createdAt,
      weakNames,
      wrongCount,
      errorTypes,
      suggestions,
    };
  }, [kpNames, latestDetail, latestTest, profiles, wrongQuestions.length]);

  const weakPointNames = useMemo(
    () => profiles
      .slice()
      .sort((a, b) => a.masteryProbability - b.masteryProbability)
      .slice(0, 5)
      .map((profile) => kpNames[profile.kpId] || `知识点${profile.kpId}`),
    [kpNames, profiles],
  );

  const profileMap = useMemo(() => Object.fromEntries(
    profiles.map((profile) => [
      String(profile.kpId),
      {
        name: kpNames[profile.kpId] || `知识点${profile.kpId}`,
        mastery: profile.masteryProbability,
        attempts: profile.totalAttempts,
        correct: profile.correctAttempts,
      },
    ]),
  ), [kpNames, profiles]);

  const generateExamReview = async () => {
    setIsGeneratingExamReview(true);
    setAiActionError('');
    try {
      const recentMistakes = (latestDetail?.items || [])
        .filter((item) => item.wrong)
        .slice(0, 5)
        .map((item) => ({
          questionId: item.questionId,
          knowledgePoint: item.kpId ? kpNames[item.kpId] : '',
          studentAnswer: item.studentAnswer,
          score: item.score,
          errorType: item.errorType,
          feedback: item.feedback,
        }));
      const result = await tutorApi.examReview({
        examGoal: '数学阶段诊断后提升',
        scoreSummary: latestTest ? `最近一次测试 ${scoreLabel(latestTest.totalScore)} 分` : '尚未完成正式测试',
        grade: '初中',
        subject: '数学',
        profiles: profileMap,
        weakPoints: weakPointNames,
        recentMistakes,
        availableMinutes: 30,
        reviewDays: 7,
      });
      setExamReview(result.data);
    } catch (err) {
      setAiActionError(err instanceof Error ? err.message : '生成复习建议失败');
    } finally {
      setIsGeneratingExamReview(false);
    }
  };

  const generateStudyPlan = async () => {
    setIsGeneratingStudyPlan(true);
    setAiActionError('');
    try {
      const result = await tutorApi.studyPlan({
        learningGoal: weakPointNames.length > 0
          ? `优先提升 ${weakPointNames.slice(0, 3).join('、')}`
          : '建立稳定的数学每日练习节奏',
        grade: '初中',
        subject: '数学',
        profiles: profileMap,
        weakPoints: weakPointNames,
        availableMinutes: 30,
        planDays: 7,
        constraints: '每天任务控制在 30 分钟以内，优先少量高质量练习和错题复盘。',
      });
      setStudyPlan(result.data);
    } catch (err) {
      setAiActionError(err instanceof Error ? err.message : '生成学习计划失败');
    } finally {
      setIsGeneratingStudyPlan(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64 text-gray-500">
        加载中...
      </div>
    );
  }

  if (!userId) {
    return (
      <div className="flex items-center justify-center h-64 text-gray-400">
        请先登录以查看学力评估
      </div>
    );
  }

  return (
    <div className="max-w-5xl mx-auto">
      <div className="mb-6">
        <h1 className="text-xl font-bold text-gray-900">能力评估</h1>
        <p className="text-sm text-gray-500">顶层能力树 + 智育学科诊断；当前仅数学诊断数据已接入</p>
      </div>

      {/* Stats cards */}
      <div className="mb-6 grid grid-cols-2 gap-3 lg:grid-cols-4 lg:gap-4">
        {stats.map((stat) => {
          const Icon = stat.icon;
          return (
            <div key={stat.label} className="bg-white rounded-xl border border-gray-200 p-4">
              <div className="flex items-center gap-2 mb-2">
                <div className={`p-1.5 rounded-lg ${stat.color}`}>
                  <Icon className="w-4 h-4" />
                </div>
                <span className="text-xs text-gray-500">{stat.label}</span>
              </div>
              <div className="text-2xl font-bold text-gray-900">{stat.value}</div>
            </div>
          );
        })}
      </div>

      <section className="mb-6 rounded-xl border border-blue-100 bg-blue-50/60 p-5">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <div className="mb-2 flex items-center gap-2">
              <ClipboardCheck className="h-5 w-5 text-blue-600" />
              <h2 className="font-semibold text-gray-900">家长反馈摘要</h2>
            </div>
            <p className="text-sm text-gray-700">{parentSummary.level}</p>
            <div className="mt-3 flex flex-wrap gap-2 text-xs">
              <span className="rounded border border-blue-100 bg-white px-2 py-1 text-gray-600">
                最近测试：{parentSummary.latestScore == null ? '未完成' : `${scoreLabel(parentSummary.latestScore)} 分`}
              </span>
              {parentSummary.latestDate && (
                <span className="rounded border border-blue-100 bg-white px-2 py-1 text-gray-600">
                  时间：{formatDate(parentSummary.latestDate)}
                </span>
              )}
              <span className="rounded border border-blue-100 bg-white px-2 py-1 text-gray-600">
                错题：{parentSummary.wrongCount} 道
              </span>
              <span className="rounded border border-blue-100 bg-white px-2 py-1 text-gray-600">
                薄弱点：{parentSummary.weakNames.length > 0 ? parentSummary.weakNames.join('、') : '暂无'}
              </span>
              {parentSummary.errorTypes.length > 0 && (
                <span className="rounded border border-blue-100 bg-white px-2 py-1 text-gray-600">
                  主要错误：{parentSummary.errorTypes.join('、')}
                </span>
              )}
            </div>
          </div>

          <div className="min-w-0 rounded-lg border border-blue-100 bg-white p-3 lg:w-80">
            <div className="mb-2 flex items-center gap-2 text-sm font-medium text-gray-900">
              <Target className="h-4 w-4 text-blue-600" />
              下一步建议
            </div>
            <ul className="space-y-1.5 text-sm text-gray-600">
              {parentSummary.suggestions.map((item) => (
                <li key={item} className="flex gap-2">
                  <ArrowRight className="mt-0.5 h-3.5 w-3.5 flex-none text-blue-500" />
                  <span>{item}</span>
                </li>
              ))}
            </ul>
            <div className="mt-3 flex flex-wrap gap-2">
              <button
                type="button"
                onClick={() => { void generateExamReview(); }}
                disabled={isGeneratingExamReview}
                className="inline-flex items-center gap-1.5 rounded-lg bg-blue-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-blue-700 disabled:opacity-50"
              >
                {isGeneratingExamReview ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Sparkles className="h-3.5 w-3.5" />}
                生成复习建议
              </button>
              <button
                type="button"
                onClick={() => { void generateStudyPlan(); }}
                disabled={isGeneratingStudyPlan}
                className="inline-flex items-center gap-1.5 rounded-lg border border-blue-100 bg-white px-3 py-1.5 text-xs font-medium text-blue-700 hover:bg-blue-50 disabled:opacity-50"
              >
                {isGeneratingStudyPlan ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Target className="h-3.5 w-3.5" />}
                生成学习计划
              </button>
            </div>
            {aiActionError && <p className="mt-2 text-xs text-red-500">{aiActionError}</p>}
          </div>
        </div>
      </section>

      {(examReview || studyPlan) && (
        <section className="mb-6 grid grid-cols-1 gap-4 lg:grid-cols-2">
          {examReview && (
            <div className="rounded-xl border border-indigo-100 bg-indigo-50 p-4">
              <h3 className="text-sm font-semibold text-gray-900">AI 测评复习建议</h3>
              <p className="mt-2 text-sm text-gray-700">{examReview.diagnosis}</p>
              <div className="mt-3 space-y-2">
                {asArray(examReview.priority_weak_points).slice(0, 4).map((item) => (
                  <div key={item.knowledge_point} className="rounded border border-indigo-100 bg-white p-2">
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-xs font-medium text-gray-900">{item.knowledge_point}</span>
                      <span className="rounded bg-indigo-50 px-2 py-0.5 text-xs text-indigo-700">{item.priority}</span>
                    </div>
                    <p className="mt-1 text-xs text-gray-600">{item.reason}</p>
                  </div>
                ))}
              </div>
              <div className="mt-3 rounded border border-indigo-100 bg-white p-2">
                <p className="text-xs font-medium text-gray-900">下次复测</p>
                <p className="mt-1 text-xs text-gray-600">{examReview.next_retest}</p>
              </div>
            </div>
          )}

          {studyPlan && (
            <div className="rounded-xl border border-green-100 bg-green-50 p-4">
              <h3 className="text-sm font-semibold text-gray-900">AI 学习计划</h3>
              <p className="mt-2 text-sm text-gray-700">{studyPlan.plan_goal}</p>
              <div className="mt-3 space-y-2">
                {asArray(studyPlan.daily_tasks).slice(0, 4).map((item) => (
                  <div key={`${item.day}-${item.focus}`} className="rounded border border-green-100 bg-white p-2">
                    <div className="text-xs font-medium text-gray-900">第 {item.day} 天：{item.focus}</div>
                    <ul className="mt-1 space-y-1 text-xs text-gray-600">
                      {asArray(item.tasks).map((task) => <li key={task}>{task}</li>)}
                    </ul>
                    <p className="mt-1 text-xs text-green-700">检查：{item.check_method}</p>
                  </div>
                ))}
              </div>
            </div>
          )}
        </section>
      )}

      <div className="mb-6 grid grid-cols-1 gap-4 lg:grid-cols-3">
        <div className="rounded-xl border border-gray-200 bg-white p-4">
          <div className="mb-2 flex items-center gap-2 text-sm font-medium text-gray-900">
            <TrendingUp className="h-4 w-4 text-blue-600" />
            最近测试
          </div>
          {latestTest ? (
            <>
              <p className="text-2xl font-bold text-gray-900">{scoreLabel(latestTest.totalScore)} 分</p>
              <p className="mt-1 text-xs text-gray-400">{formatDate(latestTest.createdAt)}</p>
            </>
          ) : (
            <p className="text-sm text-gray-400">完成一次诊断后显示</p>
          )}
        </div>
        <div className="rounded-xl border border-gray-200 bg-white p-4">
          <div className="mb-2 flex items-center gap-2 text-sm font-medium text-gray-900">
            <AlertTriangle className="h-4 w-4 text-orange-600" />
            重点薄弱点
          </div>
          {parentSummary.weakNames.length > 0 ? (
            <div className="flex flex-wrap gap-2">
              {parentSummary.weakNames.map((name) => (
                <span key={name} className="rounded bg-orange-50 px-2 py-1 text-xs text-orange-700">{name}</span>
              ))}
            </div>
          ) : (
            <p className="text-sm text-gray-400">暂无明显薄弱点</p>
          )}
        </div>
        <div className="rounded-xl border border-gray-200 bg-white p-4">
          <div className="mb-2 flex items-center gap-2 text-sm font-medium text-gray-900">
            <BookX className="h-4 w-4 text-red-600" />
            错题积累
          </div>
          <p className="text-2xl font-bold text-gray-900">{parentSummary.wrongCount} 道</p>
          <p className="mt-1 text-xs text-gray-400">建议优先复盘最近 3 道</p>
        </div>
      </div>

      <section className="mb-6 bg-white rounded-xl border border-gray-200 p-6">
        <h3 className="font-semibold text-gray-900 mb-4">顶层能力框架</h3>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-5">
          {topAbility.map((item) => (
            <div key={item.name} className="rounded-lg border border-gray-100 bg-gray-50 p-4">
              <div className="flex items-center justify-between">
                <span className="text-lg font-bold text-gray-900">{item.name}</span>
                <span className={`rounded px-2 py-0.5 text-xs ${
                  item.value == null ? 'bg-gray-100 text-gray-500' : 'bg-blue-50 text-blue-700'
                }`}
                >
                  {item.status}
                </span>
              </div>
              <p className="mt-1 text-xs text-gray-500">{item.label}</p>
              {item.value == null ? (
                <div className="mt-4 h-2 rounded-full bg-gray-200" />
              ) : (
                <>
                  <div className="mt-4 h-2 rounded-full bg-gray-200">
                    <div className="h-2 rounded-full bg-blue-500" style={{ width: `${item.value}%` }} />
                  </div>
                  <p className="mt-1 text-right text-xs text-gray-500">{item.value}%</p>
                </>
              )}
            </div>
          ))}
        </div>
      </section>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3 lg:gap-6">
        <div className="rounded-xl border border-gray-200 bg-white p-4 sm:p-6 lg:col-span-1">
          <h3 className="font-semibold text-gray-900 mb-4">智育维度</h3>
          <div className="space-y-3">
            {subjectAbilityData.map((item) => (
              <div key={item.name}>
                <div className="mb-1 flex items-center justify-between text-xs">
                  <span className="font-medium text-gray-700">{item.name}</span>
                  <span className="text-gray-400">{item.value == null ? item.status : `${item.value}%`}</span>
                </div>
                <div className="h-2 rounded-full bg-gray-200">
                  <div
                    className={`h-2 rounded-full ${item.value == null ? 'bg-gray-300' : 'bg-blue-500'}`}
                    style={{ width: `${item.value ?? 0}%` }}
                  />
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Knowledge point detail list */}
        <div className="rounded-xl border border-gray-200 bg-white p-4 sm:p-6 lg:col-span-2">
          <h3 className="font-semibold text-gray-900 mb-4">数学能力画像</h3>
          {hasAssessmentData && (
            <div className="mb-6 h-72">
              <ResponsiveContainer width="100%" height="100%">
                <RadarChart data={mathAbilityRadarData}>
                  <PolarGrid />
                  <PolarAngleAxis dataKey="subject" tick={{ fontSize: 12 }} />
                  <PolarRadiusAxis angle={30} domain={[0, 100]} />
                  <Radar name="掌握度" dataKey="value" stroke="#3b82f6" fill="#3b82f6" fillOpacity={0.2} />
                  <Tooltip />
                </RadarChart>
              </ResponsiveContainer>
            </div>
          )}
          <h3 className="font-semibold text-gray-900 mb-4">数学知识点掌握</h3>
          {profiles.length === 0 ? (
            <div className="text-center py-8 text-gray-400">
              <p>还没有评估数据</p>
              <p className="text-sm mt-1">完成一次测试后这里会显示你的学力档案</p>
            </div>
          ) : (
            <div className="space-y-2">
              {profiles.map((profile) => (
                <div key={profile.kpId} className="flex flex-col gap-3 p-3 bg-gray-50 rounded-lg sm:flex-row sm:items-center sm:justify-between">
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="text-sm font-medium text-gray-900">
                        {kpNames[profile.kpId] || `知识点${profile.kpId}`}
                      </span>
                      <span className={`text-xs ${masteryColor(profile.masteryProbability)}`}>
                        {masteryLevel(profile.masteryProbability)}
                      </span>
                    </div>
                    <div className="text-xs text-gray-400 mt-0.5">
                      答题 {profile.totalAttempts} 次 · 正确 {profile.correctAttempts} 次
                      {profile.consecutiveCorrect >= 3 && ' · 连续正确！'}
                    </div>
                  </div>
                  <div className="w-full sm:ml-4 sm:w-32">
                    <div className="w-full bg-gray-200 rounded-full h-2">
                      <div
                        className="h-2 rounded-full transition-all"
                        style={{
                          width: `${Math.round(profile.masteryProbability * 100)}%`,
                          backgroundColor:
                            profile.masteryProbability < 0.3 ? '#ef4444'
                            : profile.masteryProbability < 0.6 ? '#eab308'
                            : profile.masteryProbability < 0.85 ? '#3b82f6'
                            : '#22c55e',
                        }}
                      />
                    </div>
                    <div className="text-right text-xs text-gray-400 mt-0.5">
                      {Math.round(profile.masteryProbability * 100)}%
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
