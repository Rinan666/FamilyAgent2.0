'use client';

import { useState, useEffect, useMemo, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { questionApi, assessmentApi } from '@/lib/api';
import { useAuthStore } from '@/stores/authStore';
import { difficultyLabel, masteryColor, masteryLevel, formatDate } from '@/lib/utils';
import type { Question, AbilityProfile, TestRecord, KnowledgePoint } from '@/types';
import {
  BookX, Target, Clock, ChevronDown, ChevronUp,
  ArrowRight, FileText, AlertTriangle, CheckCircle2,
} from 'lucide-react';

type Tab = 'wrong' | 'history';

export default function NotebookPage() {
  const router = useRouter();

  // Data state
  const [wrongQuestions, setWrongQuestions] = useState<Question[]>([]);
  const [profiles, setProfiles] = useState<AbilityProfile[]>([]);
  const [testHistory, setTestHistory] = useState<TestRecord[]>([]);
  const [kpNames, setKpNames] = useState<Record<number, string>>({});
  const [loading, setLoading] = useState(true);

  // UI state
  const [activeTab, setActiveTab] = useState<Tab>('wrong');
  const [expandedAnswers, setExpandedAnswers] = useState<Set<number>>(new Set());
  const [filterDifficulty, setFilterDifficulty] = useState<number | null>(null);
  const [filterKpId, setFilterKpId] = useState<number | null>(null);

  const userId = useAuthStore((s) => s.user?.id);

  const loadAll = useCallback(async () => {
    setLoading(true);
    try {
      const [wrongData, profileData, historyData, treeData] = await Promise.all([
        questionApi.getWrongQuestions(userId!, 50),
        assessmentApi.getProfiles(userId!),
        assessmentApi.getHistory(userId!, 30),
        questionApi.getKnowledgeTree(),
      ]);
      setWrongQuestions(wrongData || []);
      setProfiles(profileData || []);
      setTestHistory(historyData || []);

      // Flatten knowledge tree
      const names: Record<number, string> = {};
      const flattenTree = (nodes: KnowledgePoint[]) => {
        for (const n of nodes) {
          names[n.id] = n.name;
          if ((n as unknown as { children?: KnowledgePoint[] }).children) {
            flattenTree((n as unknown as { children: KnowledgePoint[] }).children);
          }
        }
      };
      flattenTree(treeData || []);
      setKpNames(names);
    } catch (err) {
      console.error('Failed to load notebook data', err);
    } finally {
      setLoading(false);
    }
  }, [userId]);

  useEffect(() => {
    if (userId) {
      loadAll();
    } else {
      setLoading(false);
    }
  }, [userId, loadAll]);

  // Derived stats
  const stats = useMemo(() => {
    const weakCount = profiles.filter((p) => p.masteryProbability < 0.4).length;
    const zpdCount = profiles.filter(
      (p) => p.masteryProbability >= 0.3 && p.masteryProbability <= 0.7,
    ).length;
    const recentTests = testHistory.filter((t) => {
      const d = new Date(t.createdAt);
      const now = new Date();
      return (now.getTime() - d.getTime()) < 30 * 24 * 3600 * 1000;
    }).length;
    const avgScore =
      testHistory.length > 0
        ? Math.round(
            testHistory.reduce((sum, t) => sum + (t.totalScore || 0), 0) / testHistory.length,
          )
        : 0;

    return { weakCount, zpdCount, recentTests, avgScore, totalWrong: wrongQuestions.length };
  }, [profiles, testHistory, wrongQuestions]);

  // Filtered wrong questions
  const filteredQuestions = useMemo(() => {
    return wrongQuestions.filter((q) => {
      if (filterDifficulty !== null && q.difficulty !== filterDifficulty) return false;
      if (filterKpId !== null && q.kpId !== filterKpId) return false;
      return true;
    });
  }, [wrongQuestions, filterDifficulty, filterKpId]);

  // Unique knowledge points from wrong questions
  const availableKps = useMemo(() => {
    const kpSet = new Map<number, string>();
    for (const q of wrongQuestions) {
      if (!kpSet.has(q.kpId)) {
        kpSet.set(q.kpId, kpNames[q.kpId] || `知识点${q.kpId}`);
      }
    }
    return Array.from(kpSet.entries()).map(([id, name]) => ({ id, name }));
  }, [wrongQuestions, kpNames]);

  // Toggle answer visibility
  const toggleAnswer = (id: number) => {
    setExpandedAnswers((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  // Navigate to tutor page
  const goToTutor = () => {
    router.push('/dashboard/tutor');
  };

  // Loading state
  if (loading) {
    return (
      <div className="flex items-center justify-center h-64 text-gray-500">加载中...</div>
    );
  }

  // Not logged in
  if (!userId) {
    return (
      <div className="flex items-center justify-center h-64 text-gray-400">请先登录</div>
    );
  }

  return (
    <div className="max-w-5xl mx-auto">
      {/* Header */}
      <div className="mb-6">
        <h1 className="text-xl font-bold text-gray-900">错题本</h1>
        <p className="text-sm text-gray-500">整理错题、分析薄弱点、精准复习</p>
      </div>

      {/* Stats cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
        {[
          {
            label: '错题数量',
            value: `${stats.totalWrong}题`,
            icon: BookX,
            color: 'text-red-600 bg-red-50',
          },
          {
            label: '薄弱知识点',
            value: `${stats.weakCount}个`,
            icon: AlertTriangle,
            color: 'text-orange-600 bg-orange-50',
          },
          {
            label: '最近发展区',
            value: `${stats.zpdCount}个`,
            icon: Target,
            color: 'text-purple-600 bg-purple-50',
          },
          {
            label: '近期测试',
            value: `${stats.recentTests}次`,
            icon: Clock,
            color: 'text-blue-600 bg-blue-50',
          },
        ].map((stat) => {
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

      {/* Tab bar */}
      <div className="flex items-center gap-1 mb-4 bg-gray-100 rounded-lg p-1 w-fit">
        <button
          onClick={() => setActiveTab('wrong')}
          className={`px-4 py-1.5 text-sm rounded-md transition-colors ${
            activeTab === 'wrong'
              ? 'bg-white text-gray-900 shadow-sm font-medium'
              : 'text-gray-500 hover:text-gray-700'
          }`}
        >
          📝 错题列表
        </button>
        <button
          onClick={() => setActiveTab('history')}
          className={`px-4 py-1.5 text-sm rounded-md transition-colors ${
            activeTab === 'history'
              ? 'bg-white text-gray-900 shadow-sm font-medium'
              : 'text-gray-500 hover:text-gray-700'
          }`}
        >
          📋 测试记录
        </button>
      </div>

      {/* ========== Wrong Questions Tab ========== */}
      {activeTab === 'wrong' && (
        <>
          {/* Filters */}
          <div className="flex items-center gap-3 mb-4">
            <span className="text-xs text-gray-400">筛选：</span>
            <select
              value={filterDifficulty ?? ''}
              onChange={(e) =>
                setFilterDifficulty(e.target.value ? Number(e.target.value) : null)
              }
              className="text-xs border border-gray-200 rounded-lg px-3 py-1.5 bg-white text-gray-700 outline-none focus:ring-1 focus:ring-blue-500"
            >
              <option value="">全部难度</option>
              {[1, 2, 3, 4, 5].map((d) => (
                <option key={d} value={d}>
                  {difficultyLabel(d)}
                </option>
              ))}
            </select>
            <select
              value={filterKpId ?? ''}
              onChange={(e) =>
                setFilterKpId(e.target.value ? Number(e.target.value) : null)
              }
              className="text-xs border border-gray-200 rounded-lg px-3 py-1.5 bg-white text-gray-700 outline-none focus:ring-1 focus:ring-blue-500"
            >
              <option value="">全部知识点</option>
              {availableKps.map((kp) => (
                <option key={kp.id} value={kp.id}>
                  {kp.name}
                </option>
              ))}
            </select>
            {(filterDifficulty !== null || filterKpId !== null) && (
              <button
                onClick={() => {
                  setFilterDifficulty(null);
                  setFilterKpId(null);
                }}
                className="text-xs text-blue-600 hover:underline"
              >
                清除筛选
              </button>
            )}
            <span className="ml-auto text-xs text-gray-400">
              共 {filteredQuestions.length} 题
            </span>
          </div>

          {/* Question list */}
          {filteredQuestions.length === 0 ? (
            <div className="bg-white rounded-xl border border-gray-200 p-12 text-center">
              <CheckCircle2 className="w-12 h-12 text-green-200 mx-auto mb-3" />
              <p className="text-gray-500 font-medium">
                {wrongQuestions.length === 0 ? '太棒了！没有错题记录' : '当前筛选条件下没有错题'}
              </p>
              <p className="text-sm text-gray-400 mt-1">
                {wrongQuestions.length === 0
                  ? '继续完成测试来追踪你的学习进度'
                  : '尝试调整筛选条件'}
              </p>
            </div>
          ) : (
            <div className="space-y-3">
              {filteredQuestions.map((q) => {
                const content = q.content;
                const answer = q.answer;
                const isExpanded = expandedAnswers.has(q.id);
                const profile = profiles.find((p) => p.kpId === q.kpId);

                return (
                  <div
                    key={q.id}
                    className="bg-white rounded-xl border border-gray-200 p-5 hover:border-gray-300 transition-colors"
                  >
                    {/* Tags */}
                    <div className="flex items-center gap-2 mb-3 flex-wrap">
                      <span className="text-xs px-2 py-0.5 bg-blue-100 text-blue-700 rounded">
                        {q.subject === 'math' ? '数学' : q.subject}
                      </span>
                      <span className="text-xs px-2 py-0.5 bg-gray-100 text-gray-600 rounded">
                        {difficultyLabel(q.difficulty)}
                      </span>
                      <span className="text-xs px-2 py-0.5 bg-gray-100 text-gray-600 rounded">
                        {q.type === 'CHOICE'
                          ? '选择题'
                          : q.type === 'FILL'
                            ? '填空题'
                            : q.type === 'PROOF'
                              ? '证明题'
                              : '计算题'}
                      </span>
                      {kpNames[q.kpId] && (
                        <span className="text-xs px-2 py-0.5 bg-purple-50 text-purple-600 rounded">
                          {kpNames[q.kpId]}
                        </span>
                      )}
                      {profile && (
                        <span className={`text-xs ${masteryColor(profile.masteryProbability)}`}>
                          掌握度: {masteryLevel(profile.masteryProbability)} (
                          {Math.round(profile.masteryProbability * 100)}%)
                        </span>
                      )}
                    </div>

                    {/* Question stem */}
                    <p className="text-gray-900 mb-3 whitespace-pre-wrap leading-relaxed">
                      {content.stem}
                    </p>

                    {/* Options */}
                    {content.options && (
                      <div className="space-y-1 mb-3">
                        {content.options.map((opt, i) => (
                          <div key={i} className="text-sm text-gray-600 pl-4">
                            {String.fromCharCode(65 + i)}. {opt}
                          </div>
                        ))}
                      </div>
                    )}

                    {/* Expandable answer */}
                    <div className="border-t border-gray-100 pt-3 mt-3">
                      <button
                        onClick={() => toggleAnswer(q.id)}
                        className="flex items-center gap-1 text-xs text-gray-500 hover:text-gray-700 transition-colors"
                      >
                        {isExpanded ? (
                          <ChevronUp className="w-4 h-4" />
                        ) : (
                          <ChevronDown className="w-4 h-4" />
                        )}
                        {isExpanded ? '收起答案' : '查看答案'}
                      </button>

                      {isExpanded && (
                        <div className="mt-3 p-4 bg-green-50 rounded-lg border border-green-100">
                          <p className="text-xs font-medium text-green-700 mb-2">✅ 正确答案</p>
                          <p className="text-sm text-gray-900 font-medium">{answer.value}</p>
                          {answer.steps && answer.steps.length > 0 && (
                            <div className="mt-2">
                              <p className="text-xs text-gray-500 mb-1">解题步骤：</p>
                              <ol className="list-decimal list-inside text-sm text-gray-700 space-y-0.5">
                                {answer.steps.map((step, i) => (
                                  <li key={i}>{step}</li>
                                ))}
                              </ol>
                            </div>
                          )}
                          {answer.explanation && (
                            <p className="text-xs text-gray-500 mt-2">
                              💡 {answer.explanation}
                            </p>
                          )}
                        </div>
                      )}
                    </div>

                    {/* Action */}
                    <div className="flex items-center justify-between mt-3 pt-3 border-t border-gray-100">
                      {profile && (
                        <div className="text-xs text-gray-400">
                          答题 {profile.totalAttempts} 次 · 正确 {profile.correctAttempts} 次
                          {profile.consecutiveCorrect >= 3 && (
                            <span className="text-green-500 ml-1">· 连续正确！</span>
                          )}
                        </div>
                      )}
                      <button
                        onClick={goToTutor}
                        className="ml-auto flex items-center gap-1 px-4 py-1.5 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700 transition-colors"
                      >
                        去练习
                        <ArrowRight className="w-4 h-4" />
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </>
      )}

      {/* ========== Test History Tab ========== */}
      {activeTab === 'history' && (
        <>
          {testHistory.length === 0 ? (
            <div className="bg-white rounded-xl border border-gray-200 p-12 text-center">
              <FileText className="w-12 h-12 text-gray-200 mx-auto mb-3" />
              <p className="text-gray-500 font-medium">暂无测试记录</p>
              <p className="text-sm text-gray-400 mt-1">
                完成一次测试后这里会显示你的测试历史
              </p>
            </div>
          ) : (
            <div className="space-y-3">
              {testHistory.map((record) => {
                const questionCount = record.questionIds?.length || 0;
                const scoreColor =
                  (record.totalScore || 0) >= 80
                    ? 'text-green-600'
                    : (record.totalScore || 0) >= 60
                      ? 'text-yellow-600'
                      : 'text-red-600';
                const scoreBg =
                  (record.totalScore || 0) >= 80
                    ? 'bg-green-50'
                    : (record.totalScore || 0) >= 60
                      ? 'bg-yellow-50'
                      : 'bg-red-50';

                return (
                  <div
                    key={record.id}
                    className="bg-white rounded-xl border border-gray-200 p-5 hover:border-gray-300 transition-colors"
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-4">
                        {/* Score badge */}
                        <div
                          className={`w-16 h-16 rounded-xl flex flex-col items-center justify-center ${scoreBg}`}
                        >
                          <span className={`text-xl font-bold ${scoreColor}`}>
                            {record.totalScore ?? '-'}
                          </span>
                          <span className="text-[10px] text-gray-400">分</span>
                        </div>

                        <div>
                          <div className="flex items-center gap-2 mb-1">
                            <span className="text-sm font-medium text-gray-900">
                              测试记录 #{record.id}
                            </span>
                            <span
                              className={`text-xs px-1.5 py-0.5 rounded ${
                                record.status === 'COMPLETED'
                                  ? 'bg-green-100 text-green-600'
                                  : 'bg-yellow-100 text-yellow-600'
                              }`}
                            >
                              {record.status === 'COMPLETED' ? '已完成' : record.status}
                            </span>
                          </div>
                          <div className="text-xs text-gray-400 space-x-3">
                            <span>{formatDate(record.createdAt)}</span>
                            <span>·</span>
                            <span>{questionCount} 道题</span>
                            {record.totalTime && <span>· {record.totalTime}秒</span>}
                            {record.source && (
                              <>
                                <span>·</span>
                                <span>{record.source === 'ADAPTIVE' ? '自适应' : '手动'}</span>
                              </>
                            )}
                          </div>
                        </div>
                      </div>

                      {/* Score bar */}
                      <div className="hidden md:flex items-center gap-3">
                        <div className="w-32">
                          <div className="flex items-center justify-between text-xs text-gray-400 mb-1">
                            <span>正确率</span>
                            <span>{Math.round(record.totalScore || 0)}%</span>
                          </div>
                          <div className="w-full bg-gray-200 rounded-full h-2">
                            <div
                              className="h-2 rounded-full transition-all"
                              style={{
                                width: `${Math.round(record.totalScore || 0)}%`,
                                backgroundColor:
                                  (record.totalScore || 0) >= 80
                                    ? '#22c55e'
                                    : (record.totalScore || 0) >= 60
                                      ? '#eab308'
                                      : '#ef4444',
                              }}
                            />
                          </div>
                        </div>
                      </div>
                    </div>

                    {/* Question IDs */}
                    {record.questionIds && record.questionIds.length > 0 && (
                      <div className="mt-3 pt-3 border-t border-gray-100">
                        <p className="text-xs text-gray-400 mb-2">
                          涉及题目 ID：{record.questionIds.join(', ')}
                        </p>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </>
      )}
    </div>
  );
}
