'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { CalendarDays, CheckCircle, Database, KeyRound, LogOut, RefreshCw, Shield, User, Users } from 'lucide-react';
import { WorkbenchHero, WorkbenchPage, WorkbenchSectionTitle, WorkbenchSurface } from '@/components/layout/Workbench';
import { userApi } from '@/lib/api';
import { familyRoleLabel, isPlatformAdmin } from '@/lib/roles';
import { useViewerRole } from '@/hooks/useViewerRole';
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

  const handleLogout = () => {
    logout();
    router.push('/login');
  };

  const handleUpdateProfile = async (event: React.FormEvent) => {
    event.preventDefault();
    setProfileError('');
    setProfileSuccess('');
    setSavingProfile(true);
    try {
      const updated = await userApi.updateProfile({ birthDate: birthDate || undefined });
      setUser(updated);
      setBirthDate(birthDateFromUser(updated));
      setProfileSuccess('生日已保存，家庭成员页面现在会显示生日和年龄。');
    } catch (err) {
      setProfileError(err instanceof Error ? err.message : '保存资料失败');
    } finally {
      setSavingProfile(false);
    }
  };

  const handleChangePassword = async (event: React.FormEvent) => {
    event.preventDefault();
    setPasswordError('');
    setPasswordSuccess('');

    if (newPassword.length < 6) {
      setPasswordError('新密码至少需要 6 个字符。');
      return;
    }
    if (newPassword !== confirmPassword) {
      setPasswordError('两次输入的新密码不一致。');
      return;
    }

    setSavingPassword(true);
    try {
      await userApi.changePassword({ currentPassword, newPassword });
      setPasswordSuccess('密码已更新，请重新登录。');
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

  return (
    <WorkbenchPage className="max-w-4xl">
      <WorkbenchHero
        badge={<span className="inline-flex rounded-full bg-stone-100 px-3 py-1 text-xs font-medium text-stone-700">设置</span>}
        title="账户与安全"
      />

      {families.length > 0 && (
        <WorkbenchSurface className="space-y-5">
          <WorkbenchSectionTitle title="当前家族" />

          <div className="flex flex-wrap gap-2">
            {families.map((family) => {
              const active = activeFamilyId === family.id;
              return (
                <button
                  key={family.id}
                  type="button"
                  onClick={() => setActiveFamilyId(family.id)}
                  className={`rounded-full px-4 py-2 text-sm font-medium transition ${
                    active
                      ? 'bg-stone-950 text-white'
                      : 'border border-stone-200 bg-white text-stone-600 hover:border-stone-300 hover:bg-stone-50'
                  }`}
                >
                  {family.name}
                </button>
              );
            })}
          </div>

          {activeMembership && (
            <div className="flex items-center gap-3 rounded-2xl bg-stone-50 p-3 text-sm text-stone-600">
              <Users className="h-4 w-4 text-stone-400" />
              <span>当前身份：{familyRoleLabel(activeMembership.role)}</span>
            </div>
          )}
        </WorkbenchSurface>
      )}

      <WorkbenchSurface className="space-y-5">
        <WorkbenchSectionTitle title="个人资料" />

        <form onSubmit={handleUpdateProfile} className="space-y-4">
          {profileError && (
            <div className="rounded-2xl bg-red-50 px-4 py-3 text-sm text-red-600">
              {profileError}
            </div>
          )}
          {profileSuccess && (
            <div className="flex items-center gap-2 rounded-2xl bg-green-50 px-4 py-3 text-sm text-green-700">
              <CheckCircle className="h-4 w-4" />
              {profileSuccess}
            </div>
          )}

          <label className="block text-sm font-medium text-stone-700">
            生日
            <input
              name="birthDate"
              type="date"
              value={birthDate}
              onChange={(event) => setBirthDate(event.target.value)}
              className="mt-2 h-11 w-full rounded-2xl border border-stone-200 bg-stone-50/70 px-4 text-sm text-stone-900 outline-none transition focus:border-emerald-500 focus:bg-white focus:ring-4 focus:ring-emerald-100"
            />
          </label>
          <p className="text-xs leading-5 text-stone-500">
            当前年龄：{ageLabel(birthDate)}
          </p>

          <button
            type="submit"
            disabled={savingProfile}
            className="inline-flex h-11 items-center justify-center gap-2 rounded-full bg-stone-950 px-5 text-sm font-medium text-white transition hover:bg-stone-800 disabled:opacity-50"
          >
            {savingProfile ? <RefreshCw className="h-4 w-4 animate-spin" /> : <CalendarDays className="h-4 w-4" />}
            保存生日
          </button>
        </form>
      </WorkbenchSurface>

      <WorkbenchSurface>
        <div className="mb-4 flex items-center gap-3">
          <div className="flex h-12 w-12 items-center justify-center rounded-full bg-stone-950 text-lg font-medium text-white">
            {(user?.nickname || user?.username || 'U').charAt(0).toUpperCase()}
          </div>
          <div>
            <h3 className="font-semibold text-stone-900">{user?.nickname || user?.username}</h3>
            <p className="text-sm text-stone-500">@{user?.username}</p>
          </div>
        </div>

        <div className="space-y-3">
          <div className="flex items-center gap-3 rounded-2xl bg-stone-50 p-3">
            <User className="h-4 w-4 text-stone-400" />
            <div className="flex-1 text-sm">
              <span className="text-stone-500">用户名</span>
              <span className="ml-4 text-stone-900">{user?.username}</span>
            </div>
          </div>
          <div className="flex items-center gap-3 rounded-2xl bg-stone-50 p-3">
            <Shield className="h-4 w-4 text-stone-400" />
            <div className="flex-1 text-sm">
              <span className="text-stone-500">平台角色</span>
              <span className="ml-4 text-stone-900">{platformRoleLabel(user?.role)}</span>
            </div>
          </div>
        </div>
      </WorkbenchSurface>

      <WorkbenchSurface className="space-y-5">
        <WorkbenchSectionTitle title="修改密码" />

        <form onSubmit={handleChangePassword} className="space-y-4">
          {passwordError && (
            <div className="rounded-2xl bg-red-50 px-4 py-3 text-sm text-red-600">
              {passwordError}
            </div>
          )}
          {passwordSuccess && (
            <div className="flex items-center gap-2 rounded-2xl bg-green-50 px-4 py-3 text-sm text-green-700">
              <CheckCircle className="h-4 w-4" />
              {passwordSuccess}
            </div>
          )}

          <label className="block text-sm font-medium text-stone-700">
            当前密码
            <input
              name="currentPassword"
              type="password"
              value={currentPassword}
              onChange={(event) => setCurrentPassword(event.target.value)}
              className="mt-2 h-11 w-full rounded-2xl border border-stone-200 bg-stone-50/70 px-4 text-sm text-stone-900 outline-none transition focus:border-emerald-500 focus:bg-white focus:ring-4 focus:ring-emerald-100"
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
              className="mt-2 h-11 w-full rounded-2xl border border-stone-200 bg-stone-50/70 px-4 text-sm text-stone-900 outline-none transition focus:border-emerald-500 focus:bg-white focus:ring-4 focus:ring-emerald-100"
              autoComplete="new-password"
              minLength={6}
              required
            />
          </label>

          <label className="block text-sm font-medium text-stone-700">
            确认新密码
            <input
              name="confirmPassword"
              type="password"
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
              className="mt-2 h-11 w-full rounded-2xl border border-stone-200 bg-stone-50/70 px-4 text-sm text-stone-900 outline-none transition focus:border-emerald-500 focus:bg-white focus:ring-4 focus:ring-emerald-100"
              autoComplete="new-password"
              minLength={6}
              required
            />
          </label>

          <button
            type="submit"
            disabled={savingPassword}
            className="inline-flex h-11 items-center justify-center gap-2 rounded-full bg-stone-950 px-5 text-sm font-medium text-white transition hover:bg-stone-800 disabled:opacity-50"
          >
            {savingPassword ? <RefreshCw className="h-4 w-4 animate-spin" /> : <KeyRound className="h-4 w-4" />}
            保存新密码
          </button>
        </form>
      </WorkbenchSurface>

      {isPlatformAdmin(user) && (
        <WorkbenchSurface className="space-y-4">
          <WorkbenchSectionTitle title="管理员工具" />
          <Link
            href="/dashboard/admin/database"
            className="inline-flex h-11 items-center justify-center gap-2 rounded-full bg-stone-950 px-5 text-sm font-medium text-white transition hover:bg-stone-800"
          >
            <Database className="h-4 w-4" />
            打开系统巡检
          </Link>
        </WorkbenchSurface>
      )}

      <WorkbenchSurface>
        <button
          type="button"
          onClick={handleLogout}
          className="inline-flex h-11 items-center gap-2 rounded-full bg-red-50 px-5 text-sm font-medium text-red-600 transition hover:bg-red-100"
        >
          <LogOut className="h-4 w-4" />
          退出登录
        </button>
      </WorkbenchSurface>
    </WorkbenchPage>
  );
}
