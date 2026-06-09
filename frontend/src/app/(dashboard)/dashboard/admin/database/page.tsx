'use client';

import { useCallback, useDeferredValue, useEffect, useMemo, useState } from 'react';
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
  Trash2,
  Users,
} from 'lucide-react';
import SearchPaginationControls from '@/components/family/SearchPaginationControls';
import { adminApi } from '@/lib/api';
import { isPlatformAdmin } from '@/lib/roles';
import { useAuthStore } from '@/stores/authStore';
import type {
  AdminUserSummary,
  DatabaseHealthResponse,
  DatabaseTableCount,
  EmbeddingStatusSummary,
  FamilyDatabaseSummary,
  MemoryRecallDiagnosticResponse,
  PageResult,
} from '@/types';

const USER_PAGE_SIZE = 10;
const FAMILY_PAGE_SIZE = 10;

function createEmptyPage<T>(pageSize: number): PageResult<T> {
  return {
    items: [],
    page: 1,
    pageSize,
    total: 0,
    totalPages: 1,
  };
}

function pageBounds(pageResult: PageResult<unknown>) {
  if (pageResult.total === 0) {
    return { start: 0, end: 0 };
  }
  const start = (pageResult.page - 1) * pageResult.pageSize + 1;
  const end = Math.min(pageResult.page * pageResult.pageSize, pageResult.total);
  return { start, end };
}

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
  if (sourceType === 'MEMORY' || sourceType === 'FAMILY_EXPERIENCE') return '家族经验';
  if (sourceType === 'GROWTH_OBSERVATION') return '成长观察';
  return sourceType || '未知来源';
}

function tableGroup(tables: DatabaseTableCount[], legacy: boolean) {
  return tables.filter((table) => table.legacy === legacy);
}

export default function AdminDatabaseHealthPage() {
  const user = useAuthStore((state) => state.user);
  const platformAdmin = isPlatformAdmin(user);

  const [data, setData] = useState<DatabaseHealthResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');

  const [userLookupOpen, setUserLookupOpen] = useState(false);
  const [userKeyword, setUserKeyword] = useState('');
  const [userPage, setUserPage] = useState(1);
  const [usersPageData, setUsersPageData] = useState<PageResult<AdminUserSummary>>(() =>
    createEmptyPage(USER_PAGE_SIZE),
  );
  const [isUsersLoading, setIsUsersLoading] = useState(false);
  const [usersError, setUsersError] = useState('');

  const [familyKeyword, setFamilyKeyword] = useState('');
  const [familyPage, setFamilyPage] = useState(1);
  const [familiesPageData, setFamiliesPageData] = useState<PageResult<FamilyDatabaseSummary>>(() =>
    createEmptyPage(FAMILY_PAGE_SIZE),
  );
  const [isFamiliesLoading, setIsFamiliesLoading] = useState(false);
  const [familiesError, setFamiliesError] = useState('');

  const [diagnosticForm, setDiagnosticForm] = useState({
    familyId: '',
    viewerUserId: '',
    query: '',
  });
  const [diagnosticResult, setDiagnosticResult] = useState<MemoryRecallDiagnosticResponse | null>(null);
  const [diagnosticError, setDiagnosticError] = useState('');
  const [isDiagnosing, setIsDiagnosing] = useState(false);

  const [deleteForm, setDeleteForm] = useState({
    userId: '',
    confirmText: '',
  });
  const [deleteError, setDeleteError] = useState('');
  const [deleteMessage, setDeleteMessage] = useState('');
  const [isDeleting, setIsDeleting] = useState(false);

  const deferredUserKeyword = useDeferredValue(userKeyword);
  const deferredFamilyKeyword = useDeferredValue(familyKeyword);

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

  const userBounds = useMemo(() => pageBounds(usersPageData), [usersPageData]);
  const familyBounds = useMemo(() => pageBounds(familiesPageData), [familiesPageData]);

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

  const loadUsers = useCallback(async (page: number, keyword: string) => {
    setIsUsersLoading(true);
    setUsersError('');
    try {
      setUsersPageData(await adminApi.listUsers({
        keyword,
        page,
        pageSize: USER_PAGE_SIZE,
      }));
    } catch (err) {
      setUsersError(err instanceof Error ? err.message : '用户 ID 对照表加载失败');
      setUsersPageData(createEmptyPage(USER_PAGE_SIZE));
    } finally {
      setIsUsersLoading(false);
    }
  }, []);

  const loadFamilies = useCallback(async (page: number, keyword: string) => {
    setIsFamiliesLoading(true);
    setFamiliesError('');
    try {
      setFamiliesPageData(await adminApi.listFamilies({
        keyword,
        page,
        pageSize: FAMILY_PAGE_SIZE,
      }));
    } catch (err) {
      setFamiliesError(err instanceof Error ? err.message : '家族数据概况加载失败');
      setFamiliesPageData(createEmptyPage(FAMILY_PAGE_SIZE));
    } finally {
      setIsFamiliesLoading(false);
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

  const deleteUser = useCallback(async () => {
    const userId = Number(deleteForm.userId);
    if (!Number.isFinite(userId) || userId <= 0) {
      setDeleteError('请输入有效的用户 ID');
      return;
    }
    if (deleteForm.confirmText.trim().toUpperCase() !== 'DELETE') {
      setDeleteError('请输入 DELETE 作为二次确认');
      return;
    }
    if (!window.confirm(`确认删除用户 #${userId} 吗？这会清理关联记录且不可恢复。`)) {
      return;
    }

    setIsDeleting(true);
    setDeleteError('');
    setDeleteMessage('');
    try {
      await adminApi.deleteUser(userId);
      setDeleteMessage(`用户 #${userId} 已删除。`);
      setDeleteForm({ userId: '', confirmText: '' });
      await loadHealth();
      await loadFamilies(familyPage, deferredFamilyKeyword);
      if (userLookupOpen) {
        await loadUsers(userPage, deferredUserKeyword);
      }
    } catch (err) {
      setDeleteError(err instanceof Error ? err.message : '删除用户失败');
    } finally {
      setIsDeleting(false);
    }
  }, [
    deleteForm,
    deferredFamilyKeyword,
    deferredUserKeyword,
    familyPage,
    loadFamilies,
    loadHealth,
    loadUsers,
    userLookupOpen,
    userPage,
  ]);

  useEffect(() => {
    if (platformAdmin) {
      void loadHealth();
    } else {
      setIsLoading(false);
    }
  }, [loadHealth, platformAdmin]);

  useEffect(() => {
    if (!platformAdmin) return;
    void loadFamilies(familyPage, deferredFamilyKeyword);
  }, [deferredFamilyKeyword, familyPage, loadFamilies, platformAdmin]);

  useEffect(() => {
    if (!platformAdmin || !userLookupOpen) return;
    void loadUsers(userPage, deferredUserKeyword);
  }, [deferredUserKeyword, loadUsers, platformAdmin, userLookupOpen, userPage]);

  if (!platformAdmin) {
    return (
      <div className="mx-auto max-w-2xl rounded-2xl border border-gray-200 bg-white p-8 text-center">
        <Shield className="mx-auto mb-3 h-10 w-10 text-gray-300" />
        <h1 className="text-lg font-semibold text-gray-900">需要平台管理员权限</h1>
        <p className="mt-2 text-sm text-gray-500">
          数据库健康页仅对平台管理员开放，不向普通家庭成员展示。
        </p>
      </div>
    );
  }

  return (
    <div className="mx-auto w-full max-w-7xl">
      <section className="mb-4 rounded-2xl border border-gray-200 bg-white p-5">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <h1 className="text-xl font-bold text-gray-900">数据库健康</h1>
            <p className="mt-1 text-sm text-gray-500">
              查看核心家族数据、历史兼容表和 RAG 向量状态。这里仅展示排查摘要，不展示隐私原文。
            </p>
          </div>
          <button
            type="button"
            onClick={() => void loadHealth()}
            disabled={isLoading}
            className="inline-flex h-10 items-center justify-center gap-2 rounded-xl border border-gray-200 bg-white px-4 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-60"
          >
            {isLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : <RefreshCw className="h-4 w-4" />}
            刷新概览
          </button>
        </div>
      </section>

      {error && (
        <div className="mb-4 rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">
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
            <div className="rounded-2xl border border-gray-200 bg-white p-4">
              <Users className="mb-3 h-5 w-5 text-blue-600" />
              <p className="text-2xl font-bold text-gray-900">{data.totalUsers}</p>
              <p className="text-sm text-gray-500">用户</p>
            </div>
            <div className="rounded-2xl border border-gray-200 bg-white p-4">
              <Server className="mb-3 h-5 w-5 text-indigo-600" />
              <p className="text-2xl font-bold text-gray-900">{data.totalFamilies}</p>
              <p className="text-sm text-gray-500">家族空间</p>
            </div>
            <div className="rounded-2xl border border-gray-200 bg-white p-4">
              <Database className="mb-3 h-5 w-5 text-emerald-600" />
              <p className="text-2xl font-bold text-gray-900">{data.totalCoreRecords}</p>
              <p className="text-sm text-gray-500">核心家族记录</p>
            </div>
            <div className="rounded-2xl border border-gray-200 bg-white p-4">
              <Bot className="mb-3 h-5 w-5 text-violet-600" />
              <p className="text-2xl font-bold text-gray-900">{data.totalSkillRuns || 0}</p>
              <p className="text-sm text-gray-500">技能运行审计</p>
            </div>
            <div className="rounded-2xl border border-gray-200 bg-white p-4">
              <CheckCircle className="mb-3 h-5 w-5 text-green-600" />
              <p className="text-2xl font-bold text-gray-900">{embeddingHealth}%</p>
              <p className="text-sm text-gray-500">
                向量可用率，{data.readyEmbeddings}/{data.totalEmbeddings}
              </p>
            </div>
          </section>

          <section className="mb-4 rounded-2xl border border-gray-200 bg-white p-5">
            <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <h2 className="text-sm font-semibold text-gray-900">基础状态</h2>
                <p className="mt-1 text-sm text-gray-500">
                  数据库：{data.databaseName}，pgvector：{data.pgvectorInstalled ? '已启用' : '未启用'}，生成时间：
                  {formatDate(data.generatedAt)}
                </p>
              </div>
              {data.failedEmbeddings > 0 || (data.failedSkillRuns || 0) > 0 ? (
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
                    <div key={table.tableName} className="rounded-xl bg-gray-50 px-3 py-2">
                      <p className="text-sm font-medium text-gray-900">{table.count}</p>
                      <p className="truncate text-xs text-gray-500">{table.label}</p>
                    </div>
                  ))}
                </div>
              </div>
              <div>
                <p className="mb-2 text-xs font-medium text-gray-400">历史兼容表</p>
                <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                  {tableGroup(data.tableCounts, true).map((table) => (
                    <div key={table.tableName} className="rounded-xl bg-gray-50 px-3 py-2">
                      <p className="text-sm font-medium text-gray-900">{table.count}</p>
                      <p className="truncate text-xs text-gray-500">{table.label}</p>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </section>

          <section className="mb-4 rounded-2xl border border-gray-200 bg-white p-5">
            <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
              <div>
                <h2 className="text-sm font-semibold text-gray-900">用户名 / ID 对照表</h2>
                <p className="mt-1 text-sm text-gray-500">
                  方便删除用户、排查 family member 映射，或快速确认 viewerUserId 对应的是谁。
                </p>
              </div>
              <div className="flex flex-wrap gap-2">
                {userLookupOpen ? (
                  <button
                    type="button"
                    onClick={() => void loadUsers(userPage, deferredUserKeyword)}
                    disabled={isUsersLoading}
                    className="inline-flex h-10 items-center justify-center gap-2 rounded-xl border border-gray-200 bg-white px-4 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-60"
                  >
                    {isUsersLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : <RefreshCw className="h-4 w-4" />}
                    刷新用户表
                  </button>
                ) : null}
                <button
                  type="button"
                  onClick={() => setUserLookupOpen((open) => !open)}
                  className="inline-flex h-10 items-center justify-center gap-2 rounded-xl bg-gray-900 px-4 text-sm font-medium text-white hover:bg-gray-800"
                >
                  <Users className="h-4 w-4" />
                  {userLookupOpen ? '收起对照表' : '查看用户名 / ID 对照表'}
                </button>
              </div>
            </div>

            {userLookupOpen ? (
              <div className="mt-4">
                <SearchPaginationControls
                  searchValue={userKeyword}
                  onSearchChange={(value) => {
                    setUserKeyword(value);
                    setUserPage(1);
                  }}
                  searchPlaceholder="按 ID / username / nickname / role / status 搜索"
                  itemLabel="个用户"
                  currentPage={usersPageData.page}
                  pageCount={Math.max(usersPageData.totalPages, 1)}
                  onPageChange={setUserPage}
                  startIndex={userBounds.start}
                  endIndex={userBounds.end}
                  filteredTotal={usersPageData.total}
                  total={usersPageData.total}
                  className="mb-4"
                />

                {usersError ? (
                  <div className="rounded-xl border border-red-100 bg-red-50 px-3 py-2 text-sm text-red-600">
                    {usersError}
                  </div>
                ) : isUsersLoading && usersPageData.items.length === 0 ? (
                  <div className="flex h-24 items-center justify-center text-sm text-gray-400">
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    正在加载用户名 / ID 对照表...
                  </div>
                ) : usersPageData.items.length === 0 ? (
                  <div className="rounded-xl border border-dashed border-gray-200 px-4 py-6 text-center text-sm text-gray-500">
                    {userKeyword.trim() ? '没有匹配的用户。' : '当前没有可展示的用户。'}
                  </div>
                ) : (
                  <div className="overflow-x-auto rounded-xl border border-gray-100">
                    <table className="min-w-full text-left text-sm">
                      <thead className="border-b border-gray-100 bg-gray-50 text-xs text-gray-500">
                        <tr>
                          <th className="py-2.5 pl-4 pr-3 font-medium">ID</th>
                          <th className="px-3 py-2.5 font-medium">Username</th>
                          <th className="px-3 py-2.5 font-medium">昵称</th>
                          <th className="px-3 py-2.5 font-medium">角色</th>
                          <th className="px-3 py-2.5 font-medium">状态</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-gray-100 bg-white">
                        {usersPageData.items.map((item) => (
                          <tr key={item.id}>
                            <td className="py-3 pl-4 pr-3 font-mono text-gray-900">{item.id}</td>
                            <td className="px-3 py-3 font-medium text-gray-900">{item.username}</td>
                            <td className="px-3 py-3 text-gray-600">{item.nickname || '-'}</td>
                            <td className="px-3 py-3 text-gray-600">{item.role || '-'}</td>
                            <td className="px-3 py-3 text-gray-600">{item.status || '-'}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            ) : null}
          </section>

          <section className="mb-4 rounded-2xl border border-red-200 bg-white p-5">
            <div className="mb-4 flex items-start gap-3">
              <div className="rounded-xl bg-red-50 p-2 text-red-600">
                <Trash2 className="h-5 w-5" />
              </div>
              <div>
                <h2 className="text-sm font-semibold text-gray-900">删除用户</h2>
                <p className="mt-1 text-sm text-gray-500">
                  仅平台管理员可用。删除会清理该用户的会话、记录、成长观察、向量索引和家庭成员映射，且不可恢复。
                </p>
                <p className="mt-2 text-xs leading-5 text-red-700">
                  Family dissolve 只会在这次显式删除流程中按“最后成员”规则触发；启动时的 family lifecycle
                  现在仅做审计告警，不会自动删除 ownerless family。
                </p>
              </div>
            </div>

            <div className="grid grid-cols-1 gap-3 lg:grid-cols-[180px_180px_auto]">
              <input
                name="deleteUserId"
                type="number"
                min="1"
                value={deleteForm.userId}
                onChange={(event) => setDeleteForm((prev) => ({ ...prev, userId: event.target.value }))}
                placeholder="用户 ID"
                className="h-10 rounded-xl border border-gray-200 px-3 text-sm outline-none focus:border-red-300"
              />
              <input
                name="deleteConfirm"
                type="text"
                value={deleteForm.confirmText}
                onChange={(event) => setDeleteForm((prev) => ({ ...prev, confirmText: event.target.value }))}
                placeholder="输入 DELETE 确认"
                className="h-10 rounded-xl border border-gray-200 px-3 text-sm uppercase outline-none focus:border-red-300"
              />
              <button
                type="button"
                onClick={() => void deleteUser()}
                disabled={isDeleting}
                className="inline-flex h-10 items-center justify-center gap-2 rounded-xl bg-red-600 px-4 text-sm font-medium text-white hover:bg-red-700 disabled:opacity-60"
              >
                {isDeleting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
                删除用户
              </button>
            </div>

            {deleteError && (
              <div className="mt-3 rounded-xl border border-red-100 bg-red-50 px-3 py-2 text-sm text-red-600">
                {deleteError}
              </div>
            )}

            {deleteMessage && (
              <div className="mt-3 rounded-xl border border-green-100 bg-green-50 px-3 py-2 text-sm text-green-700">
                {deleteMessage}
              </div>
            )}
          </section>

          <section className="mb-4 rounded-2xl border border-gray-200 bg-white p-5">
            <div className="mb-4 flex flex-col gap-2 lg:flex-row lg:items-start lg:justify-between">
              <div>
                <h2 className="text-sm font-semibold text-gray-900">RAG 召回诊断</h2>
                <p className="mt-1 text-sm text-gray-500">
                  以指定家庭成员的权限视角模拟召回，帮助排查 Agent 为什么参考了某类记忆。
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
                className="h-10 rounded-xl border border-gray-200 px-3 text-sm outline-none focus:border-gray-400"
              />
              <input
                name="diagnosticViewerUserId"
                type="number"
                min="1"
                value={diagnosticForm.viewerUserId}
                onChange={(event) => setDiagnosticForm((prev) => ({ ...prev, viewerUserId: event.target.value }))}
                placeholder="viewerUserId"
                className="h-10 rounded-xl border border-gray-200 px-3 text-sm outline-none focus:border-gray-400"
              />
              <input
                name="diagnosticQuery"
                type="text"
                value={diagnosticForm.query}
                onChange={(event) => setDiagnosticForm((prev) => ({ ...prev, query: event.target.value }))}
                placeholder="例如：刷牙、升学、家庭沟通"
                className="h-10 rounded-xl border border-gray-200 px-3 text-sm outline-none focus:border-gray-400"
              />
              <button
                type="button"
                onClick={() => void runDiagnostic()}
                disabled={isDiagnosing}
                className="inline-flex h-10 items-center justify-center gap-2 rounded-xl bg-gray-900 px-4 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-60"
              >
                {isDiagnosing ? <Loader2 className="h-4 w-4 animate-spin" /> : <Search className="h-4 w-4" />}
                诊断
              </button>
            </div>

            {diagnosticError && (
              <div className="mt-3 rounded-xl border border-red-100 bg-red-50 px-3 py-2 text-sm text-red-600">
                {diagnosticError}
              </div>
            )}

            {diagnosticResult && (
              <div className="mt-4 rounded-xl border border-gray-100 bg-gray-50 p-4">
                <div className="mb-3 flex flex-wrap gap-2 text-xs font-medium text-gray-600">
                  <span className="rounded-full bg-white px-2.5 py-1">Family #{diagnosticResult.familyId}</span>
                  <span className="rounded-full bg-white px-2.5 py-1">Viewer #{diagnosticResult.viewerUserId}</span>
                  <span className="rounded-full bg-white px-2.5 py-1">{diagnosticResult.retrievalMode}</span>
                  <span className="rounded-full bg-white px-2.5 py-1">
                    READY embeddings: {diagnosticResult.embeddingReadyCount}
                  </span>
                </div>

                <div className="mb-4 grid grid-cols-3 gap-2 text-center text-sm">
                  <div className="rounded-xl bg-white px-3 py-2">
                    <p className="font-semibold text-gray-900">{diagnosticResult.diaryCount}</p>
                    <p className="text-xs text-gray-500">人生记录</p>
                  </div>
                  <div className="rounded-xl bg-white px-3 py-2">
                    <p className="font-semibold text-gray-900">{diagnosticResult.memoryCount}</p>
                    <p className="text-xs text-gray-500">家族经验</p>
                  </div>
                  <div className="rounded-xl bg-white px-3 py-2">
                    <p className="font-semibold text-gray-900">{diagnosticResult.growthRecordCount}</p>
                    <p className="text-xs text-gray-500">成长观察</p>
                  </div>
                </div>

                {diagnosticResult.sources.length === 0 ? (
                  <p className="text-sm text-gray-500">没有召回到可见来源。</p>
                ) : (
                  <div className="space-y-2">
                    {diagnosticResult.sources.map((source) => (
                      <div key={source.id} className="rounded-xl bg-white p-3">
                        <div className="mb-1 flex flex-wrap items-center gap-2 text-xs text-gray-500">
                          <span className="font-medium text-gray-700">{sourceLabel(source.sourceType)}</span>
                          <span>{source.id}</span>
                          {source.visibility && <span>{source.visibility}</span>}
                          {source.temporalLayer && <span>{source.temporalLayer}</span>}
                        </div>
                        <p className="text-sm font-medium text-gray-900">{source.title}</p>
                        <p className="mt-1 line-clamp-2 text-xs leading-5 text-gray-600">{source.snippet}</p>
                        {source.topics?.length || source.scenes?.length ? (
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

          <section className="mb-4 rounded-2xl border border-gray-200 bg-white p-5">
            <div className="mb-4 flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
              <div>
                <h2 className="text-sm font-semibold text-gray-900">家族数据概况</h2>
                <p className="mt-1 text-sm text-gray-500">
                  支持按家族 ID 或家族名搜索，并按页查看成员数、记录数和向量健康情况。
                </p>
              </div>
              <button
                type="button"
                onClick={() => void loadFamilies(familyPage, deferredFamilyKeyword)}
                disabled={isFamiliesLoading}
                className="inline-flex h-10 items-center justify-center gap-2 rounded-xl border border-gray-200 bg-white px-4 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-60"
              >
                {isFamiliesLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : <RefreshCw className="h-4 w-4" />}
                刷新家族概况
              </button>
            </div>

            <SearchPaginationControls
              searchValue={familyKeyword}
              onSearchChange={(value) => {
                setFamilyKeyword(value);
                setFamilyPage(1);
              }}
              searchPlaceholder="按 familyId 或家族名搜索"
              itemLabel="个家族"
              currentPage={familiesPageData.page}
              pageCount={Math.max(familiesPageData.totalPages, 1)}
              onPageChange={setFamilyPage}
              startIndex={familyBounds.start}
              endIndex={familyBounds.end}
              filteredTotal={familiesPageData.total}
              total={familiesPageData.total}
              className="mb-4"
            />

            {familiesError ? (
              <div className="rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">
                {familiesError}
              </div>
            ) : isFamiliesLoading && familiesPageData.items.length === 0 ? (
              <div className="flex h-32 items-center justify-center text-sm text-gray-400">
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                正在加载家族数据概况...
              </div>
            ) : familiesPageData.items.length === 0 ? (
              <div className="rounded-xl border border-dashed border-gray-200 px-4 py-8 text-center text-sm text-gray-500">
                {familyKeyword.trim() ? '没有匹配的家族。' : '当前没有可展示的家族数据。'}
              </div>
            ) : (
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
                    {familiesPageData.items.map((family) => (
                      <tr key={family.familyId}>
                        <td className="py-3 pr-4">
                          <div className="font-medium text-gray-900">{family.familyName}</div>
                          <div className="text-xs text-gray-400">Family #{family.familyId}</div>
                        </td>
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
            )}
          </section>

          <section className="mb-4 grid grid-cols-1 gap-4 xl:grid-cols-[1.2fr_0.8fr]">
            <div className="rounded-2xl border border-gray-200 bg-white p-5">
              <h2 className="mb-3 text-sm font-semibold text-gray-900">Embedding 状态</h2>
              {groupedEmbeddingStatuses.length === 0 ? (
                <p className="text-sm text-gray-500">暂无向量索引数据。</p>
              ) : (
                <div className="space-y-3">
                  {groupedEmbeddingStatuses.map(([familyId, statuses]) => (
                    <div key={familyId} className="rounded-xl border border-gray-100 p-3">
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

            <div className="rounded-2xl border border-gray-200 bg-white p-5">
              <h2 className="mb-3 text-sm font-semibold text-gray-900">最近失败向量</h2>
              {data.recentFailedEmbeddings.length === 0 ? (
                <p className="text-sm text-gray-500">暂无失败项。</p>
              ) : (
                <div className="space-y-2">
                  {data.recentFailedEmbeddings.map((item) => (
                    <div key={item.id} className="rounded-xl bg-red-50 p-3">
                      <div className="mb-1 flex flex-wrap items-center gap-2 text-xs font-medium text-red-700">
                        <span>#{item.id}</span>
                        <span>Family {item.familyId}</span>
                        <span>
                          {sourceLabel(item.sourceType)} {item.sourceId}
                        </span>
                      </div>
                      <p className="line-clamp-2 text-xs leading-5 text-red-700">{item.error || '未知错误'}</p>
                      <p className="mt-1 text-[11px] text-red-500">{formatDate(item.updatedAt)}</p>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </section>

          <section className="mb-4 rounded-2xl border border-gray-200 bg-white p-5">
            <h2 className="mb-3 text-sm font-semibold text-gray-900">最近失败技能运行</h2>
            {!data.recentFailedSkillRuns || data.recentFailedSkillRuns.length === 0 ? (
              <p className="text-sm text-gray-500">暂无失败项。</p>
            ) : (
              <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
                {data.recentFailedSkillRuns.map((item) => (
                  <div key={item.id} className="rounded-xl bg-red-50 p-3">
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
