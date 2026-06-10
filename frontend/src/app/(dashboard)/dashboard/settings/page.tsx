'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { CalendarDays, CheckCircle, Database, KeyRound, LogOut, RefreshCw, Shield, User } from 'lucide-react';
import { userApi } from '@/lib/api';
import { isPlatformAdmin } from '@/lib/roles';
import { useAuthStore } from '@/stores/authStore';
import type { User as AppUser } from '@/types';

function platformRoleLabel(role?: string) {
  return (role || '').toUpperCase() === 'ADMIN' ? 'Platform admin' : 'Standard user';
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
  if (!birthDate) return 'Not set';
  const date = new Date(birthDate);
  if (Number.isNaN(date.getTime())) return 'Not set';
  const now = new Date();
  let age = now.getFullYear() - date.getFullYear();
  const monthDelta = now.getMonth() - date.getMonth();
  if (monthDelta < 0 || (monthDelta === 0 && now.getDate() < date.getDate())) age -= 1;
  return age >= 0 && age <= 130 ? `${age}` : 'Not set';
}

export default function SettingsPage() {
  const user = useAuthStore((s) => s.user);
  const setUser = useAuthStore((s) => s.setUser);
  const logout = useAuthStore((s) => s.logout);
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
      setProfileSuccess('Birthday saved. Family member pages will now show birthday and age.');
    } catch (err) {
      setProfileError(err instanceof Error ? err.message : 'Failed to save profile');
    } finally {
      setSavingProfile(false);
    }
  };

  const handleChangePassword = async (event: React.FormEvent) => {
    event.preventDefault();
    setPasswordError('');
    setPasswordSuccess('');

    if (newPassword.length < 6) {
      setPasswordError('New password must be at least 6 characters.');
      return;
    }
    if (newPassword !== confirmPassword) {
      setPasswordError('The new passwords do not match.');
      return;
    }

    setSavingPassword(true);
    try {
      await userApi.changePassword({ currentPassword, newPassword });
      setPasswordSuccess('Password updated. Please sign in again.');
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      setTimeout(() => {
        logout();
        router.push('/login');
      }, 900);
    } catch (err) {
      setPasswordError(err instanceof Error ? err.message : 'Failed to change password');
    } finally {
      setSavingPassword(false);
    }
  };

  return (
    <div className="mx-auto max-w-2xl">
      <div className="mb-6">
        <h1 className="text-xl font-bold text-gray-900">Settings</h1>
        <p className="text-sm text-gray-500">Manage account details, security, and platform maintenance access</p>
      </div>

      <div className="mb-4 rounded-xl border border-gray-200 bg-white p-6">
        <div className="mb-4 flex items-center gap-2">
          <CalendarDays className="h-5 w-5 text-blue-600" />
          <h2 className="text-sm font-semibold text-gray-900">Profile</h2>
        </div>

        <form onSubmit={handleUpdateProfile} className="space-y-4">
          {profileError && (
            <div className="rounded-lg bg-red-50 px-4 py-2 text-sm text-red-600">
              {profileError}
            </div>
          )}
          {profileSuccess && (
            <div className="flex items-center gap-2 rounded-lg bg-green-50 px-4 py-2 text-sm text-green-700">
              <CheckCircle className="h-4 w-4" />
              {profileSuccess}
            </div>
          )}

          <label className="block text-sm font-medium text-gray-700">
            Birth date
            <input
              name="birthDate"
              type="date"
              value={birthDate}
              onChange={(event) => setBirthDate(event.target.value)}
              className="mt-1 h-10 w-full rounded-lg border border-gray-200 px-3 text-sm text-gray-900 outline-none focus:ring-2 focus:ring-blue-500"
            />
          </label>
          <p className="text-xs leading-5 text-gray-500">
            Current age used for Agent tone and boundary decisions: {ageLabel(birthDate)}. Nothing is filled automatically when birth date is empty.
          </p>

          <button
            type="submit"
            disabled={savingProfile}
            className="inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 text-sm font-medium text-white transition-colors hover:bg-blue-700 disabled:opacity-50"
          >
            {savingProfile ? <RefreshCw className="h-4 w-4 animate-spin" /> : <CalendarDays className="h-4 w-4" />}
            Save birthday
          </button>
        </form>
      </div>

      <div className="mb-4 rounded-xl border border-gray-200 bg-white p-6">
        <div className="mb-4 flex items-center gap-3">
          <div className="flex h-12 w-12 items-center justify-center rounded-full bg-blue-600 text-lg font-medium text-white">
            {(user?.nickname || user?.username || 'U').charAt(0).toUpperCase()}
          </div>
          <div>
            <h3 className="font-semibold text-gray-900">{user?.nickname || user?.username}</h3>
            <p className="text-sm text-gray-500">@{user?.username}</p>
          </div>
        </div>

        <div className="space-y-3">
          <div className="flex items-center gap-3 rounded-lg bg-gray-50 p-3">
            <User className="h-4 w-4 text-gray-400" />
            <div className="flex-1 text-sm">
              <span className="text-gray-500">Username</span>
              <span className="ml-4 text-gray-900">{user?.username}</span>
            </div>
          </div>
          <div className="flex items-center gap-3 rounded-lg bg-gray-50 p-3">
            <Shield className="h-4 w-4 text-gray-400" />
            <div className="flex-1 text-sm">
              <span className="text-gray-500">Platform role</span>
              <span className="ml-4 text-gray-900">{platformRoleLabel(user?.role)}</span>
            </div>
          </div>
        </div>
      </div>

      <div className="mb-4 rounded-xl border border-gray-200 bg-white p-6">
        <div className="mb-4 flex items-center gap-2">
          <KeyRound className="h-5 w-5 text-blue-600" />
          <h2 className="text-sm font-semibold text-gray-900">Change password</h2>
        </div>

        <form onSubmit={handleChangePassword} className="space-y-4">
          {passwordError && (
            <div className="rounded-lg bg-red-50 px-4 py-2 text-sm text-red-600">
              {passwordError}
            </div>
          )}
          {passwordSuccess && (
            <div className="flex items-center gap-2 rounded-lg bg-green-50 px-4 py-2 text-sm text-green-700">
              <CheckCircle className="h-4 w-4" />
              {passwordSuccess}
            </div>
          )}

          <label className="block text-sm font-medium text-gray-700">
            Current password
            <input
              name="currentPassword"
              type="password"
              value={currentPassword}
              onChange={(event) => setCurrentPassword(event.target.value)}
              className="mt-1 h-10 w-full rounded-lg border border-gray-200 px-3 text-sm text-gray-900 outline-none focus:ring-2 focus:ring-blue-500"
              autoComplete="current-password"
              required
            />
          </label>

          <label className="block text-sm font-medium text-gray-700">
            New password
            <input
              name="newPassword"
              type="password"
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
              className="mt-1 h-10 w-full rounded-lg border border-gray-200 px-3 text-sm text-gray-900 outline-none focus:ring-2 focus:ring-blue-500"
              autoComplete="new-password"
              minLength={6}
              required
            />
          </label>

          <label className="block text-sm font-medium text-gray-700">
            Confirm new password
            <input
              name="confirmPassword"
              type="password"
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
              className="mt-1 h-10 w-full rounded-lg border border-gray-200 px-3 text-sm text-gray-900 outline-none focus:ring-2 focus:ring-blue-500"
              autoComplete="new-password"
              minLength={6}
              required
            />
          </label>

          <button
            type="submit"
            disabled={savingPassword}
            className="inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 text-sm font-medium text-white transition-colors hover:bg-blue-700 disabled:opacity-50"
          >
            {savingPassword ? <RefreshCw className="h-4 w-4 animate-spin" /> : <KeyRound className="h-4 w-4" />}
            Save new password
          </button>
        </form>
      </div>

      {isPlatformAdmin(user) && (
        <div className="mb-4 rounded-xl border border-gray-200 bg-white p-6">
          <div className="mb-3 flex items-center gap-2">
            <Database className="h-5 w-5 text-purple-600" />
            <h2 className="text-sm font-semibold text-gray-900">Admin tools</h2>
          </div>
          <p className="mb-4 text-sm leading-6 text-gray-500">
            Inspect database health, record counts, and vector index status. This page only shows diagnostic summaries and does not expose private family source text.
          </p>
          <Link
            href="/dashboard/admin/database"
            className="inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-purple-600 px-4 text-sm font-medium text-white transition-colors hover:bg-purple-700"
          >
            <Database className="h-4 w-4" />
            Open database health
          </Link>
        </div>
      )}

      <div className="rounded-xl border border-gray-200 bg-white p-6">
        <button
          type="button"
          onClick={handleLogout}
          className="flex items-center gap-2 rounded-lg bg-red-50 px-4 py-2 text-sm text-red-600 transition-colors hover:bg-red-100"
        >
          <LogOut className="h-4 w-4" />
          Sign out
        </button>
      </div>

      <div className="mt-8 text-center text-xs text-gray-400">
        <p>FamilyAgent v0.1.0</p>
        <p className="mt-1">Minimum viable release for long-term family memory and the Family Agent</p>
      </div>
    </div>
  );
}
