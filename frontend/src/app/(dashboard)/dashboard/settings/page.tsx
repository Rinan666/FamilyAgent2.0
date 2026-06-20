'use client';

import { useEffect, useMemo, useState, type FormEvent } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import {
  AlertTriangle,
  CalendarDays,
  CheckCircle,
  Database,
  KeyRound,
  LogOut,
  RefreshCw,
  Shield,
  Trash2,
  User,
  Users,
} from 'lucide-react';
import { WorkbenchHero, WorkbenchPage, WorkbenchSectionTitle, WorkbenchSurface } from '@/components/layout/Workbench';
import { userApi } from '@/lib/api';
import { familyApi } from '@/lib/api/family';
import { familyRoleLabel, isPlatformAdmin } from '@/lib/roles';
import { notifyViewerRoleChanged, useViewerRole } from '@/hooks/useViewerRole';
import { useAuthStore } from '@/stores/authStore';
import type { User as AppUser } from '@/types';

function platformRoleLabel(role?: string) {
  return (role || '').toUpperCase() === 'ADMIN' ? '平台管理员' : '普通用户';
}

function parseMetadata(metadata?: Record<string, unknown> | string | null) {
  if (!metadata) return {};
  let current: unknown = metadata;

  for (let depth = 0; depth < 5; depth += 1) {
    if (!current) return {};
    if (typeof current === 'string') {
      try {
        current = JSON.parse(current);
        continue;
      } catch {
        return {};
      }
    }
    if (typeof current === 'object' && !Array.isArray(current)) {
      return current as Record<string, unknown>;
    }
    return {};
  }

  return {};
}

function birthDateFromMetadata(metadata?: Record<string, unknown> | string | null) {
  const parsed = parseMetadata(metadata);
  const value = parsed.birthDate || parsed.birthday || parsed.dateOfBirth;
  return typeof value === 'string' ? value.slice(0, 10) : '';
}

function birthDateFromUser(user?: Pick<AppUser, 'birthDate' | 'metadata'> | null) {
  if (!user) return '';
  if (typeof user.birthDate === 'string' && user.birthDate.trim()) {
    return user.birthDate.slice(0, 10);
  }
  return birthDateFromMetadata(user.metadata);
}

function ageLabel(birthDate: string) {
  if (!birthDate) return '未设置';
  const date = new Date(birthDate);
  if (Number.isNaN(date.getTime())) return '未设置';
  const now = new Date();
  let age = now.getFullYear() - date.getFullYear();
  const monthDelta = now.getMonth() - date.getMonth();
  if (monthDelta < 0 || (monthDelta === 0 && now.getDate() < date.getDate())) age -= 1;
  return age >= 0 && age <= 130 ? `${age}` : '未设置';
}

export default function SettingsPage() {
  const user = useAuthStore((s) => s.user);
  const setUser = useAuthStore((s) => s.setUser);
  const logout = useAuthStore((s) => s.logout);
  const {
    families,
    activeFamilyId,
    activeFamily,
    activeMembership,
    setActiveFamilyId,
  } = useViewerRole();
  const router = useRouter();
  const [birthDate, setBirthDate] = useState(() => birthDateFromUser(user));
  const [savingProfile, setSavingProfile] = useState(false);
  const [profileError, setProfileError] = useState('');
  const [profileSuccess, setProfileSuccess] = useState('');
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [savingPassword, setSavingPassword] = useState(false);
  const [passwordError, setPasswordError] = useState('');
  const [passwordSuccess, setPasswordSuccess] = useState('');
  const [deleteConfirmationName, setDeleteConfirmationName] = useState('');
  const [deleteAllData, setDeleteAllData] = useState(false);
  const [deletingFamily, setDeletingFamily] = useState(false);
  const [deleteFamilyError, setDeleteFamilyError] = useState('');
  const [deleteFamilySuccess, setDeleteFamilySuccess] = useState('');

  const canDeleteActiveFamily = Boolean(
    activeFamily && (isPlatformAdmin(user) || activeMembership?.role === 'OWNER'),
  );
  const deleteConfirmationMatches = useMemo(() => {
    if (!activeFamily) return false;
    return deleteConfirmationName.trim() === activeFamily.name.trim();
  }, [activeFamily, deleteConfirmationName]);
  const canSubmitFamilyDelete = canDeleteActiveFamily
    && deleteAllData
    && deleteConfirmationMatches
    && !deletingFamily;

  useEffect(() => {
    setBirthDate(birthDateFromUser(user));
  }, [user]);

  useEffect(() => {
    let active = true;
    userApi.getMe()
      .then((latest) => {
        if (!active) return;
        setUser(latest);
        setBirthDate(birthDateFromUser(latest));
      })
      .catch(() => {
        // Keep the cached user if refresh fails.
      });
    return () => {
      active = false;
    };
  }, [setUser]);

  const resetFamilyDeleteState = () => {
    setDeleteConfirmationName('');
    setDeleteAllData(false);
    setDeleteFamilyError('');
    setDeleteFamilySuccess('');
  };

  const handleSelectFamily = (familyId: number) => {
    setActiveFamilyId(familyId);
    resetFamilyDeleteState();
  };

  const handleLogout = () => {
    logout();
    router.push('/login');
  };

  const handleUpdateProfile = async (event: FormEvent) => {
    event.preventDefault();
    setProfileError('');
    setProfileSuccess('');
    setSavingProfile(true);
    try {
      const updated = await userApi.updateProfile({ birthDate: birthDate || undefined });
      setUser(updated);
      setBirthDate(birthDateFromUser(updated));
      setProfileSuccess('生日已保存');
    } catch (err) {
      setProfileError(err instanceof Error ? err.message : '保存资料失败');
    } finally {
      setSavingProfile(false);
    }
  };

  const handleChangePassword = async (event: FormEvent) => {
    event.preventDefault();
    setPasswordError('');
    setPasswordSuccess('');

    if (newPassword.length < 6) {
      setPasswordError('新密码至少需要 6 个字符');
      return;
    }
    if (newPassword !== confirmPassword) {
      setPasswordError('两次输入的新密码不一致');
      return;
    }

    setSavingPassword(true);
    try {
      await userApi.changePassword({ currentPassword, newPassword });
      setPasswordSuccess('密码已更新，请重新登录');
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      setTimeout(() => {
        logout();
        router.push('/login');
      }, 900);
    } catch (err) {
      setPasswordError(err instanceof Error ? err.message : '修改密码失败');
    } finally {
      setSavingPassword(false);
    }
  };

  const handleDeleteFamily = async (event: FormEvent) => {
    event.preventDefault();
    if (!activeFamily || !canSubmitFamilyDelete) return;

    setDeletingFamily(true);
    setDeleteFamilyError('');
    setDeleteFamilySuccess('');
    try {
      await familyApi.deleteFamily(activeFamily.id, {
        confirmationName: deleteConfirmationName.trim(),
        deleteAllData,
      });
      const nextFamilyId = families.find((family) => family.id !== activeFamily.id)?.id ?? null;
      setActiveFamilyId(nextFamilyId);
      notifyViewerRoleChanged();
      resetFamilyDeleteState();
      setDeleteFamilySuccess('家族已删除');
    } catch (err) {
      setDeleteFamilyError(err instanceof Error ? err.message : '删除家族失败');
    } finally {
      setDeletingFamily(false);
    }
  };

  return (
    <WorkbenchPage className="max-w-6xl">
      <WorkbenchHero
        badge={<span className="text-xs font-medium text-stone-500">设置</span>}
        title="账户与家族"
      />

      <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_320px]">
        <div className="space-y-3">
          <WorkbenchSurface className="space-y-4">
            <WorkbenchSectionTitle title="个人资料" />

            <div className="flex items-center gap-3 border-b border-stone-100 pb-4">
              <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-stone-950 text-sm font-semibold text-white">
                {(user?.nickname || user?.username || 'U').charAt(0).toUpperCase()}
              </div>
              <div className="min-w-0">
                <h3 className="truncate text-sm font-semibold text-stone-950">{user?.nickname || user?.username}</h3>
                <p className="truncate text-xs text-stone-500">@{user?.username}</p>
              </div>
            </div>

            <form onSubmit={handleUpdateProfile} className="space-y-3">
              {profileError && <div className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-600">{profileError}</div>}
              {profileSuccess && (
                <div className="flex items-center gap-2 rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-700">
                  <CheckCircle className="h-4 w-4" />
                  {profileSuccess}
                </div>
              )}

              <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_96px] sm:items-end">
                <label className="block text-sm font-medium text-stone-700">
                  生日
                  <input
                    name="birthDate"
                    type="date"
                    value={birthDate}
                    onChange={(event) => setBirthDate(event.target.value)}
                    className="mt-1 h-11 w-full rounded-md border border-stone-200 bg-stone-50 px-3 text-sm text-stone-900 outline-none transition focus:border-emerald-500 focus:bg-white focus:ring-2 focus:ring-emerald-100"
                  />
                </label>
                <div className="rounded-md bg-stone-50 px-3 py-2 text-sm">
                  <span className="block text-xs text-stone-500">年龄</span>
                  <span className="font-medium text-stone-900">{ageLabel(birthDate)}</span>
                </div>
              </div>

              <button
                type="submit"
                disabled={savingProfile}
                className="inline-flex h-10 items-center justify-center gap-2 rounded-md bg-stone-950 px-4 text-sm font-medium text-white transition hover:bg-stone-800 disabled:opacity-50"
              >
                {savingProfile ? <RefreshCw className="h-4 w-4 animate-spin" /> : <CalendarDays className="h-4 w-4" />}
                保存
              </button>
            </form>
          </WorkbenchSurface>

          <WorkbenchSurface className="space-y-4">
            <WorkbenchSectionTitle title="安全" />

            <form onSubmit={handleChangePassword} className="space-y-3">
              {passwordError && <div className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-600">{passwordError}</div>}
              {passwordSuccess && (
                <div className="flex items-center gap-2 rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-700">
                  <CheckCircle className="h-4 w-4" />
                  {passwordSuccess}
                </div>
              )}

              <div className="grid gap-3 sm:grid-cols-3">
                <label className="block text-sm font-medium text-stone-700">
                  当前密码
                  <input
                    name="currentPassword"
                    type="password"
                    value={currentPassword}
                    onChange={(event) => setCurrentPassword(event.target.value)}
                    className="mt-1 h-10 w-full rounded-md border border-stone-200 bg-stone-50 px-3 text-sm text-stone-900 outline-none transition focus:border-emerald-500 focus:bg-white focus:ring-2 focus:ring-emerald-100"
                    autoComplete="current-password"
                    required
                  />
                </label>
                <label className="block text-sm font-medium text-stone-700">
                  新密码
                  <input
                    name="newPassword"
                    type="password"
                    value={newPassword}
                    onChange={(event) => setNewPassword(event.target.value)}
                    className="mt-1 h-10 w-full rounded-md border border-stone-200 bg-stone-50 px-3 text-sm text-stone-900 outline-none transition focus:border-emerald-500 focus:bg-white focus:ring-2 focus:ring-emerald-100"
                    autoComplete="new-password"
                    minLength={6}
                    required
                  />
                </label>
                <label className="block text-sm font-medium text-stone-700">
                  确认密码
                  <input
                    name="confirmPassword"
                    type="password"
                    value={confirmPassword}
                    onChange={(event) => setConfirmPassword(event.target.value)}
                    className="mt-1 h-10 w-full rounded-md border border-stone-200 bg-stone-50 px-3 text-sm text-stone-900 outline-none transition focus:border-emerald-500 focus:bg-white focus:ring-2 focus:ring-emerald-100"
                    autoComplete="new-password"
                    minLength={6}
                    required
                  />
                </label>
              </div>

              <button
                type="submit"
                disabled={savingPassword}
                className="inline-flex h-10 items-center justify-center gap-2 rounded-md bg-stone-950 px-4 text-sm font-medium text-white transition hover:bg-stone-800 disabled:opacity-50"
              >
                {savingPassword ? <RefreshCw className="h-4 w-4 animate-spin" /> : <KeyRound className="h-4 w-4" />}
                更新密码
              </button>
            </form>
          </WorkbenchSurface>
        </div>

        <aside className="space-y-3">
          <WorkbenchSurface className="space-y-4">
            <WorkbenchSectionTitle title="家族" />

            {families.length > 0 ? (
              <div className="space-y-2">
                {families.map((family) => {
                  const active = activeFamilyId === family.id;
                  return (
                    <button
                      key={family.id}
                      type="button"
                      onClick={() => handleSelectFamily(family.id)}
                      className={`flex h-10 w-full items-center justify-between rounded-md border px-3 text-left text-sm transition ${
                        active
                          ? 'border-stone-950 bg-stone-950 text-white'
                          : 'border-stone-200 bg-white text-stone-700 hover:border-stone-300 hover:bg-stone-50'
                      }`}
                    >
                      <span className="truncate">{family.name}</span>
                      {active ? <span className="text-xs text-stone-300">当前</span> : null}
                    </button>
                  );
                })}
              </div>
            ) : (
              <div className="rounded-md bg-stone-50 px-3 py-3 text-sm text-stone-500">暂无家族</div>
            )}

            <div className="space-y-2 border-t border-stone-100 pt-3 text-sm">
              <div className="flex items-center gap-2 text-stone-600">
                <Users className="h-4 w-4 text-stone-400" />
                <span>{activeMembership ? familyRoleLabel(activeMembership.role) : '未加入家族'}</span>
              </div>
              <div className="flex items-center gap-2 text-stone-600">
                <User className="h-4 w-4 text-stone-400" />
                <span>{user?.username}</span>
              </div>
              <div className="flex items-center gap-2 text-stone-600">
                <Shield className="h-4 w-4 text-stone-400" />
                <span>{platformRoleLabel(user?.role)}</span>
              </div>
            </div>
          </WorkbenchSurface>

          {isPlatformAdmin(user) && (
            <WorkbenchSurface className="space-y-3">
              <WorkbenchSectionTitle title="管理" />
              <Link
                href="/dashboard/admin/database"
                className="inline-flex h-10 w-full items-center justify-center gap-2 rounded-md bg-stone-950 px-4 text-sm font-medium text-white transition hover:bg-stone-800"
              >
                <Database className="h-4 w-4" />
                系统巡检
              </Link>
            </WorkbenchSurface>
          )}

          <WorkbenchSurface className="space-y-4 border-red-100">
            <WorkbenchSectionTitle title="删除家族" />

            {deleteFamilyError && <div className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-600">{deleteFamilyError}</div>}
            {deleteFamilySuccess && (
              <div className="flex items-center gap-2 rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-700">
                <CheckCircle className="h-4 w-4" />
                {deleteFamilySuccess}
              </div>
            )}

            {activeFamily ? (
              <form onSubmit={handleDeleteFamily} className="space-y-3">
                <div className="flex items-start gap-2 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
                  <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
                  <span>删除「{activeFamily.name}」和全部家族数据。</span>
                </div>

                <label className="block text-sm font-medium text-stone-700">
                  家族名称
                  <input
                    value={deleteConfirmationName}
                    onChange={(event) => setDeleteConfirmationName(event.target.value)}
                    className="mt-1 h-10 w-full rounded-md border border-stone-200 bg-white px-3 text-sm text-stone-900 outline-none transition focus:border-red-400 focus:ring-2 focus:ring-red-100"
                    placeholder={activeFamily.name}
                    disabled={!canDeleteActiveFamily || deletingFamily}
                  />
                </label>

                <label className="flex items-start gap-2 text-sm text-stone-600">
                  <input
                    type="checkbox"
                    checked={deleteAllData}
                    onChange={(event) => setDeleteAllData(event.target.checked)}
                    className="mt-1 h-4 w-4 rounded border-stone-300 text-red-600 focus:ring-red-200"
                    disabled={!canDeleteActiveFamily || deletingFamily}
                  />
                  <span>确认删除全部数据</span>
                </label>

                {!canDeleteActiveFamily && (
                  <p className="text-xs leading-5 text-stone-500">只有家族拥有者或平台管理员可以删除家族。</p>
                )}

                <button
                  type="submit"
                  disabled={!canSubmitFamilyDelete}
                  className="inline-flex h-10 w-full items-center justify-center gap-2 rounded-md bg-red-600 px-4 text-sm font-medium text-white transition hover:bg-red-700 disabled:cursor-not-allowed disabled:bg-stone-200 disabled:text-stone-500"
                >
                  {deletingFamily ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
                  删除家族
                </button>
              </form>
            ) : (
              <div className="rounded-md bg-stone-50 px-3 py-3 text-sm text-stone-500">请选择一个家族</div>
            )}
          </WorkbenchSurface>

          <WorkbenchSurface>
            <button
              type="button"
              onClick={handleLogout}
              className="inline-flex h-10 w-full items-center justify-center gap-2 rounded-md bg-stone-100 px-4 text-sm font-medium text-stone-700 transition hover:bg-stone-200"
            >
              <LogOut className="h-4 w-4" />
              退出登录
            </button>
          </WorkbenchSurface>
        </aside>
      </div>
    </WorkbenchPage>
  );
}
