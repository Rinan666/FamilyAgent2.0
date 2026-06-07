'use client';

import { useState } from 'react';
import { useAuthStore } from '@/stores/authStore';
import { useRouter } from 'next/navigation';
import { userApi } from '@/lib/api';
import { CheckCircle, KeyRound, LogOut, RefreshCw, Shield, User } from 'lucide-react';

function platformRoleLabel(role?: string) {
  return (role || '').toUpperCase() === 'ADMIN' ? '平台管理员' : '普通用户';
}

export default function SettingsPage() {
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const router = useRouter();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [savingPassword, setSavingPassword] = useState(false);
  const [passwordError, setPasswordError] = useState('');
  const [passwordSuccess, setPasswordSuccess] = useState('');

  const handleLogout = () => {
    logout();
    router.push('/login');
  };

  const handleChangePassword = async (event: React.FormEvent) => {
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
      setPasswordSuccess('密码已修改，请重新登录。');
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
    <div className="max-w-2xl mx-auto">
      <div className="mb-6">
        <h1 className="text-xl font-bold text-gray-900">设置</h1>
        <p className="text-sm text-gray-500">账户与偏好</p>
      </div>

      {/* 用户信息 */}
      <div className="bg-white border border-gray-200 rounded-xl p-6 mb-4">
        <div className="flex items-center gap-3 mb-4">
          <div className="w-12 h-12 bg-blue-600 text-white rounded-full flex items-center justify-center text-lg font-medium">
            {(user?.nickname || user?.username || 'U').charAt(0).toUpperCase()}
          </div>
          <div>
            <h3 className="font-semibold text-gray-900">
              {user?.nickname || user?.username}
            </h3>
            <p className="text-sm text-gray-500">@{user?.username}</p>
          </div>
        </div>

        <div className="space-y-3">
          <div className="flex items-center gap-3 p-3 bg-gray-50 rounded-lg">
            <User className="w-4 h-4 text-gray-400" />
            <div className="flex-1 text-sm">
              <span className="text-gray-500">用户名</span>
              <span className="ml-4 text-gray-900">{user?.username}</span>
            </div>
          </div>
          <div className="flex items-center gap-3 p-3 bg-gray-50 rounded-lg">
            <Shield className="w-4 h-4 text-gray-400" />
            <div className="flex-1 text-sm">
              <span className="text-gray-500">角色</span>
              <span className="ml-4 text-gray-900">{platformRoleLabel(user?.role)}</span>
            </div>
          </div>
        </div>
      </div>

      {/* 修改密码 */}
      <div className="mb-4 rounded-xl border border-gray-200 bg-white p-6">
        <div className="mb-4 flex items-center gap-2">
          <KeyRound className="h-5 w-5 text-blue-600" />
          <h2 className="text-sm font-semibold text-gray-900">修改密码</h2>
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
            当前密码
            <input
              type="password"
              value={currentPassword}
              onChange={(event) => setCurrentPassword(event.target.value)}
              className="mt-1 h-10 w-full rounded-lg border border-gray-200 px-3 text-sm text-gray-900 outline-none focus:ring-2 focus:ring-blue-500"
              autoComplete="current-password"
              required
            />
          </label>

          <label className="block text-sm font-medium text-gray-700">
            新密码
            <input
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
            确认新密码
            <input
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
            保存新密码
          </button>
        </form>
      </div>

      {/* 退出 */}
      <div className="bg-white border border-gray-200 rounded-xl p-6">
        <button
          onClick={handleLogout}
          className="flex items-center gap-2 px-4 py-2 text-red-600 bg-red-50 rounded-lg hover:bg-red-100 transition-colors text-sm"
        >
          <LogOut className="w-4 h-4" />
          退出登录
        </button>
      </div>

      {/* 版本信息 */}
      <div className="mt-8 text-center text-xs text-gray-400">
        <p>家族教育Agent v0.1.0</p>
        <p className="mt-1">第一阶段 · 家庭陪伴 AI 最小可用版</p>
      </div>
    </div>
  );
}
