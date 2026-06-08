'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AlertTriangle,
  Bot,
  CheckCircle,
  Database,
  Loader2,
  RefreshCw,
  Search,
  Server,
  Shield,
  Users,
} from 'lucide-react';
import { adminApi } from '@/lib/api';
import { isPlatformAdmin } from '@/lib/roles';
import { useAuthStore } from '@/stores/authStore';
import type {
  DatabaseHealthResponse,
  DatabaseTableCount,
  EmbeddingStatusSummary,
  MemoryRecallDiagnosticResponse,
} from '@/types';

function formatDate(value?: string) {
  if (!value) return '暂无';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '暂无';
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function statusTone(status?: string) {
  const value = String(status || '').toUpperCase();
  if (value === 'READY') return 'bg-green-50 text-green-700';
  if (value === 'FAILED') return 'bg-red-50 text-red-700';
  if (value === 'PENDING') return 'bg-yellow-50 text-yellow-700';
  return 'bg-gray-100 text-gray-600';
}

function sourceLabel(sourceType?: string) {
  if (sourceType === 'DIARY' || sourceType === 'LIFE_RECORD') return '人生记录';
  if (sourceType === 'MEMORY' || sourceType === 'FAMILY_EXPERIENCE') return '经验沉淀';
  if (sourceType === 'GROWTH_OBSERVATION') return '成长观察';
  return sourceType || '未知来源';
}

function tableGroup(tables: DatabaseTableCount[], legacy: boolean) {
  return tables.filter((table) => table.legacy === legacy);
}

export default function AdminDatabaseHealthPage() {
  const user = useAuthStore((state) => state.user);
  const [data, setData] = useState<DatabaseHealthResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [diagnosticForm, setDiagnosticForm] = useState({
    familyId: '',
    viewerUserId: '',
    query: '',
  });
  const [diagnosticResult, setDiagnosticResult] = useState<MemoryRecallDiagnosticResponse | null>(null);
  const [diagnosticError, setDiagnosticError] = useState('');
  const [isDiagnosing, setIsDiagnosing] = useState(false);

  const platformAdmin = isPlatformAdmin(user);

  const embeddingHealth = useMemo(() => {
    if (!data || data.totalEmbeddings === 0) return 0;
    return Math.round((data.readyEmbeddings / data.totalEmbeddings) * 100);
  }, [data]);

  const groupedEmbeddingStatuses = useMemo(() => {
    const groups = new Map<string, EmbeddingStatusSummary[]>();
    for (const item of data?.embeddingStatuses || []) {
      const key = String(item.familyId);
      groups.set(key, [...(groups.get(key) || []), item]);
    }
    return Array.from(groups.entries());
  }, [data]);

  const loadHealth = useCallback(async () => {
    setIsLoading(true);
    setError('');
    try {
      setData(await adminApi.getDatabaseHealth());
    } catch (err) {
      setError(err instanceof Error ? err.message : '数据库健康状态加载失败');
      setData(null);
    } finally {
      setIsLoading(false);
    }
  }, []);

  const runDiagnostic = useCallback(async () => {
    const familyId = Number(diagnosticForm.familyId);
    const viewerUserId = Number(diagnosticForm.viewerUserId);
    if (!Number.isFinite(familyId) || familyId <= 0 || !Number.isFinite(viewerUserId) || viewerUserId <= 0) {
      setDiagnosticError('请输入有效的 familyId 和 viewerUserId');
      return;
    }

    setIsDiagnosing(true);
    setDiagnosticError('');
    setDiagnosticResult(null);
    try {
      setDiagnosticResult(await adminApi.runMemoryRecallDiagnostic({
        familyId,
        viewerUserId,
        query: diagnosticForm.query.trim(),
        diaryLimit: 3,
        memoryLimit: 3,
      }));
    } catch (err) {
      setDiagnosticError(err instanceof Error ? err.message : 'RAG 召回诊断失败');
    } finally {
      setIsDiagnosing(false);
    }
  }, [diagnosticForm]);

  useEffect(() => {
    if (platformAdmin) {
      void loadHealth();
    } else {
      setIsLoading(false);
    }
  }, [loadHealth, platformAdmin]);

  if (!platformAdmin) {
    return (
      <div className="mx-auto max-w-2xl rounded-xl border border-gray-200 bg-white p-8 text-center">
        <Shield className="mx-auto mb-3 h-10 w-10 text-gray-300" />
        <h1 className="text-lg font-semibold text-gray-900">需要平台管理员权限</h1>
        <p className="mt-2 text-sm text-gray-500">
          数据库健康页只展示给平台管理员，不对家族创建者开放。
        </p>
      </div>
    );
  }

  return (
    <div className="mx-auto w-full max-w-7xl">
      <section className="mb-4 rounded-xl border border-gray-200 bg-white p-5">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <h1 className="text-xl font-bold text-gray-900">数据库健康</h1>
            <p className="mt-1 text-sm text-gray-500">
              查看核心家族数据、历史教育表和 RAG 向量索引状态。诊断工具只展示安全摘要，不展示日记或经验原文。
            </p>
          </div>
          <button
            type="button"
            onClick={loadHealth}
            disabled={isLoading}
            className="inline-flex h-10 items-center justify-center gap-2 rounded-lg border border-gray-200 bg-white px-4 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-60"
          >
            {isLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : <RefreshCw className="h-4 w-4" />}
            刷新
          </button>
        </div>
      </section>

      {error && (
        <div className="mb-4 rounded-lg border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">
          {error}
        </div>
      )}

      {isLoading ? (
        <div className="flex h-72 items-center justify-center text-gray-400">
          <Loader2 className="mr-2 h-5 w-5 animate-spin" />
          正在读取数据库状态...
        </div>
      ) : data ? (
        <>
          <section className="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-5">
            <div className="rounded-xl border border-gray-200 bg-white p-4">
              <Users className="mb-3 h-5 w-5 text-blue-600" />
              <p className="text-2xl font-bold text-gray-900">{data.totalUsers}</p>
              <p className="text-sm text-gray-500">用户</p>
            </div>
            <div className="rounded-xl border border-gray-200 bg-white p-4">
              <Server className="mb-3 h-5 w-5 text-indigo-600" />
              <p className="text-2xl font-bold text-gray-900">{data.totalFamilies}</p>
              <p className="text-sm text-gray-500">家族空间</p>
            </div>
            <div className="rounded-xl border border-gray-200 bg-white p-4">
              <Database className="mb-3 h-5 w-5 text-emerald-600" />
              <p className="text-2xl font-bold text-gray-900">{data.totalCoreRecords}</p>
              <p className="text-sm text-gray-500">核心家族记录</p>
            </div>
            <div className="rounded-xl border border-gray-200 bg-white p-4">
              <Bot className="mb-3 h-5 w-5 text-violet-600" />
              <p className="text-2xl font-bold text-gray-900">{data.totalSkillRuns || 0}</p>
              <p className="text-sm text-gray-500">技能运行审计</p>
            </div>
            <div className="rounded-xl border border-gray-200 bg-white p-4">
              <CheckCircle className="mb-3 h-5 w-5 text-green-600" />
              <p className="text-2xl font-bold text-gray-900">{embeddingHealth}%</p>
              <p className="text-sm text-gray-500">
                向量可用率，{data.readyEmbeddings}/{data.totalEmbeddings}
              </p>
            </div>
          </section>

          <section className="mb-4 rounded-xl border border-gray-200 bg-white p-5">
            <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <h2 className="text-sm font-semibold text-gray-900">基础状态</h2>
                <p className="mt-1 text-sm text-gray-500">
                  数据库：{data.databaseName}，pgvector：{data.pgvectorInstalled ? '已启用' : '未启用'}，
                  生成时间：{formatDate(data.generatedAt)}
                </p>
              </div>
              {(data.failedEmbeddings > 0 || (data.failedSkillRuns || 0) > 0) ? (
                <span className="inline-flex items-center gap-1 rounded-full bg-red-50 px-3 py-1 text-xs font-medium text-red-700">
                  <AlertTriangle className="h-3.5 w-3.5" />
                  {data.failedEmbeddings} 条向量失败 / {data.failedSkillRuns || 0} 条技能失败
                </span>
              ) : (
                <span className="inline-flex items-center gap-1 rounded-full bg-green-50 px-3 py-1 text-xs font-medium text-green-700">
                  <CheckCircle className="h-3.5 w-3.5" />
                  暂无失败向量或技能运行
                </span>
              )}
            </div>

            <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
              <div>
                <p className="mb-2 text-xs font-medium text-gray-400">核心表</p>
                <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                  {tableGroup(data.tableCounts, false).map((table) => (
                    <div key={table.tableName} className="rounded-lg bg-gray-50 px-3 py-2">
                      <p className="text-sm font-medium text-gray-900">{table.count}</p>
                      <p className="truncate text-xs text-gray-500">{table.label}</p>
                    </div>
                  ))}
                </div>
              </div>
              <div>
                <p className="mb-2 text-xs font-medium text-gray-400">历史教育表</p>
                <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                  {tableGroup(data.tableCounts, true).map((table) => (
                    <div key={table.tableName} className="rounded-lg bg-gray-50 px-3 py-2">
                      <p className="text-sm font-medium text-gray-900">{table.count}</p>
                      <p className="truncate text-xs text-gray-500">{table.label}</p>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </section>

          <section className="mb-4 rounded-xl border border-gray-200 bg-white p-5">
            <div className="mb-4 flex flex-col gap-2 lg:flex-row lg:items-start lg:justify-between">
              <div>
                <h2 className="text-sm font-semibold text-gray-900">RAG 召回诊断</h2>
                <p className="mt-1 text-sm text-gray-500">
                  以指定家族成员的权限视角模拟召回，便于排查 AI 为什么参考了某类记忆。
                </p>
              </div>
              <span className="rounded-full bg-gray-100 px-3 py-1 text-xs font-medium text-gray-600">
                只返回安全摘要
              </span>
            </div>

            <div className="grid grid-cols-1 gap-3 lg:grid-cols-[160px_180px_1fr_auto]">
              <input
                name="diagnosticFamilyId"
                type="number"
                min="1"
                value={diagnosticForm.familyId}
                onChange={(event) => setDiagnosticForm((prev) => ({ ...prev, familyId: event.target.value }))}
                placeholder="familyId"
                className="h-10 rounded-lg border border-gray-200 px-3 text-sm outline-none focus:border-gray-400"
              />
              <input
                name="diagnosticViewerUserId"
                type="number"
                min="1"
                value={diagnosticForm.viewerUserId}
                onChange={(event) => setDiagnosticForm((prev) => ({ ...prev, viewerUserId: event.target.value }))}
                placeholder="viewerUserId"
                className="h-10 rounded-lg border border-gray-200 px-3 text-sm outline-none focus:border-gray-400"
              />
              <input
                name="diagnosticQuery"
                type="text"
                value={diagnosticForm.query}
                onChange={(event) => setDiagnosticForm((prev) => ({ ...prev, query: event.target.value }))}
                placeholder="输入问题或关键词，例如：牙齿、升学、家庭沟通"
                className="h-10 rounded-lg border border-gray-200 px-3 text-sm outline-none focus:border-gray-400"
              />
              <button
                type="button"
                onClick={runDiagnostic}
                disabled={isDiagnosing}
                className="inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-gray-900 px-4 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-60"
              >
                {isDiagnosing ? <Loader2 className="h-4 w-4 animate-spin" /> : <Search className="h-4 w-4" />}
                诊断
              </button>
            </div>

            {diagnosticError && (
              <div className="mt-3 rounded-lg border border-red-100 bg-red-50 px-3 py-2 text-sm text-red-600">
                {diagnosticError}
              </div>
            )}

            {diagnosticResult && (
              <div className="mt-4 rounded-lg border border-gray-100 bg-gray-50 p-4">
                <div className="mb-3 flex flex-wrap gap-2 text-xs font-medium text-gray-600">
                  <span className="rounded-full bg-white px-2.5 py-1">Family #{diagnosticResult.familyId}</span>
                  <span className="rounded-full bg-white px-2.5 py-1">Viewer #{diagnosticResult.viewerUserId}</span>
                  <span className="rounded-full bg-white px-2.5 py-1">{diagnosticResult.retrievalMode}</span>
                  <span className="rounded-full bg-white px-2.5 py-1">
                    READY embeddings: {diagnosticResult.embeddingReadyCount}
                  </span>
                </div>
                <div className="mb-4 grid grid-cols-3 gap-2 text-center text-sm">
                  <div className="rounded-lg bg-white px-3 py-2">
                    <p className="font-semibold text-gray-900">{diagnosticResult.diaryCount}</p>
                    <p className="text-xs text-gray-500">人生记录</p>
                  </div>
                  <div className="rounded-lg bg-white px-3 py-2">
                    <p className="font-semibold text-gray-900">{diagnosticResult.memoryCount}</p>
                    <p className="text-xs text-gray-500">经验沉淀</p>
                  </div>
                  <div className="rounded-lg bg-white px-3 py-2">
                    <p className="font-semibold text-gray-900">{diagnosticResult.growthRecordCount}</p>
                    <p className="text-xs text-gray-500">成长观察</p>
                  </div>
                </div>
                {diagnosticResult.sources.length === 0 ? (
                  <p className="text-sm text-gray-500">没有召回到可见来源。</p>
                ) : (
                  <div className="space-y-2">
                    {diagnosticResult.sources.map((source) => (
                      <div key={source.id} className="rounded-lg bg-white p-3">
                        <div className="mb-1 flex flex-wrap items-center gap-2 text-xs text-gray-500">
                          <span className="font-medium text-gray-700">{sourceLabel(source.sourceType)}</span>
                          <span>{source.id}</span>
                          {source.visibility && <span>{source.visibility}</span>}
                          {source.temporalLayer && <span>{source.temporalLayer}</span>}
                        </div>
                        <p className="text-sm font-medium text-gray-900">{source.title}</p>
                        <p className="mt-1 line-clamp-2 text-xs leading-5 text-gray-600">{source.snippet}</p>
                        {(source.topics?.length || source.scenes?.length) ? (
                          <div className="mt-2 flex flex-wrap gap-1.5">
                            {[...(source.topics || []), ...(source.scenes || [])].slice(0, 6).map((item) => (
                              <span key={item} className="rounded-full bg-gray-100 px-2 py-0.5 text-[11px] text-gray-500">
                                {item}
                              </span>
                            ))}
                          </div>
                        ) : null}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </section>

          <section className="mb-4 rounded-xl border border-gray-200 bg-white p-5">
            <h2 className="mb-3 text-sm font-semibold text-gray-900">家族数据概况</h2>
            <div className="overflow-x-auto">
              <table className="min-w-full text-left text-sm">
                <thead className="border-b border-gray-100 text-xs text-gray-400">
                  <tr>
                    <th className="py-2 pr-4 font-medium">家族</th>
                    <th className="py-2 pr-4 font-medium">成员</th>
                    <th className="py-2 pr-4 font-medium">人生记录</th>
                    <th className="py-2 pr-4 font-medium">经验</th>
                    <th className="py-2 pr-4 font-medium">观察</th>
                    <th className="py-2 pr-4 font-medium">技能运行</th>
                    <th className="py-2 pr-4 font-medium">技能失败</th>
                    <th className="py-2 pr-4 font-medium">READY</th>
                    <th className="py-2 pr-4 font-medium">FAILED</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {data.families.map((family) => (
                    <tr key={family.familyId}>
                      <td className="py-3 pr-4 font-medium text-gray-900">{family.familyName}</td>
                      <td className="py-3 pr-4 text-gray-600">{family.memberCount}</td>
                      <td className="py-3 pr-4 text-gray-600">{family.diaryCount}</td>
                      <td className="py-3 pr-4 text-gray-600">{family.memoryCount}</td>
                      <td className="py-3 pr-4 text-gray-600">{family.growthRecordCount}</td>
                      <td className="py-3 pr-4 text-violet-700">{family.skillRunCount || 0}</td>
                      <td className="py-3 pr-4 text-red-700">{family.failedSkillRunCount || 0}</td>
                      <td className="py-3 pr-4 text-green-700">{family.readyEmbeddingCount}</td>
                      <td className="py-3 pr-4 text-red-700">{family.failedEmbeddingCount}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>

          <section className="mb-4 grid grid-cols-1 gap-4 xl:grid-cols-[1.2fr_0.8fr]">
            <div className="rounded-xl border border-gray-200 bg-white p-5">
              <h2 className="mb-3 text-sm font-semibold text-gray-900">Embedding 状态</h2>
              {groupedEmbeddingStatuses.length === 0 ? (
                <p className="text-sm text-gray-500">暂无向量索引数据。</p>
              ) : (
                <div className="space-y-3">
                  {groupedEmbeddingStatuses.map(([familyId, statuses]) => (
                    <div key={familyId} className="rounded-lg border border-gray-100 p-3">
                      <p className="mb-2 text-xs font-medium text-gray-400">Family #{familyId}</p>
                      <div className="flex flex-wrap gap-2">
                        {statuses.map((item) => (
                          <span
                            key={`${item.familyId}-${item.sourceType}-${item.status}`}
                            className={`rounded-full px-2.5 py-1 text-xs font-medium ${statusTone(item.status)}`}
                            title={`最近更新：${formatDate(item.lastUpdatedAt)}`}
                          >
                            {sourceLabel(item.sourceType)} / {item.status}: {item.count}
                          </span>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="rounded-xl border border-gray-200 bg-white p-5">
              <h2 className="mb-3 text-sm font-semibold text-gray-900">最近失败向量</h2>
              {data.recentFailedEmbeddings.length === 0 ? (
                <p className="text-sm text-gray-500">暂无失败项。</p>
              ) : (
                <div className="space-y-2">
                  {data.recentFailedEmbeddings.map((item) => (
                    <div key={item.id} className="rounded-lg bg-red-50 p-3">
                      <div className="mb-1 flex flex-wrap items-center gap-2 text-xs font-medium text-red-700">
                        <span>#{item.id}</span>
                        <span>Family {item.familyId}</span>
                        <span>{sourceLabel(item.sourceType)} {item.sourceId}</span>
                      </div>
                      <p className="line-clamp-2 text-xs leading-5 text-red-700">{item.error || '未知错误'}</p>
                      <p className="mt-1 text-[11px] text-red-500">{formatDate(item.updatedAt)}</p>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </section>

          <section className="mb-4 rounded-xl border border-gray-200 bg-white p-5">
            <h2 className="mb-3 text-sm font-semibold text-gray-900">最近失败技能运行</h2>
            {!data.recentFailedSkillRuns || data.recentFailedSkillRuns.length === 0 ? (
              <p className="text-sm text-gray-500">暂无失败项。</p>
            ) : (
              <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
                {data.recentFailedSkillRuns.map((item) => (
                  <div key={item.id} className="rounded-lg bg-red-50 p-3">
                    <div className="mb-1 flex flex-wrap items-center gap-2 text-xs font-medium text-red-700">
                      <span>#{item.id}</span>
                      <span>Family {item.familyId}</span>
                      <span>User {item.triggeredBy}</span>
                      <span>{item.skillName}</span>
                      <span>{item.source}</span>
                    </div>
                    <p className="line-clamp-2 text-xs leading-5 text-red-700">
                      {item.outputSummary || '未知失败原因'}
                    </p>
                    {item.inputSummary && (
                      <p className="mt-1 line-clamp-2 text-[11px] leading-5 text-red-500">
                        输入：{item.inputSummary}
                      </p>
                    )}
                    <p className="mt-1 text-[11px] text-red-500">{formatDate(item.updatedAt)}</p>
                  </div>
                ))}
              </div>
            )}
          </section>
        </>
      ) : null}
    </div>
  );
}
