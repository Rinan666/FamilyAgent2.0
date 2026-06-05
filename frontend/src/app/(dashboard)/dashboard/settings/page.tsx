'use client';

import { useState } from 'react';
import { useAuth } from '@/hooks/useAuth';
import { useAuthStore } from '@/stores/authStore';
import { useRouter } from 'next/navigation';
import { Settings, LogOut, User, Shield } from 'lucide-react';

export default function SettingsPage() {
  const { user } = useAuth(false);
  const logout = useAuthStore((s) => s.logout);
  const router = useRouter();

  const handleLogout = () => {
    logout();
    router.push('/login');
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
              <span className="ml-4 text-gray-900">普通用户</span>
            </div>
          </div>
        </div>
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
        <p className="mt-1">第一阶段 · AI家教最小可用版</p>
      </div>
    </div>
  );
}
