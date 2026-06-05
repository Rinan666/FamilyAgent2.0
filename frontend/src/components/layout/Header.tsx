'use client';

import type { ReactNode } from 'react';
import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/stores/authStore';
import type { User } from '@/types';

interface HeaderProps {
  user: User;
  mobileNav?: ReactNode;
}

export default function Header({ user, mobileNav }: HeaderProps) {
  const router = useRouter();
  const logout = useAuthStore((s) => s.logout);

  const handleLogout = () => {
    logout();
    router.push('/login');
  };

  return (
    <header className="flex h-16 items-center justify-between gap-3 border-b border-gray-200 bg-white px-4 md:px-6">
      <div className="flex min-w-0 items-center gap-3">
        {mobileNav}
        <h2 className="truncate text-sm text-gray-500">
          欢迎回来，{user.nickname || user.username}
        </h2>
      </div>

      <div className="flex shrink-0 items-center gap-3 md:gap-4">
        {/* 用户头像 */}
        <div className="w-8 h-8 bg-blue-600 text-white rounded-full flex items-center justify-center text-sm font-medium">
          {(user.nickname || user.username).charAt(0).toUpperCase()}
        </div>

        <button
          onClick={handleLogout}
          className="text-sm text-gray-500 hover:text-gray-700"
        >
          退出
        </button>
      </div>
    </header>
  );
}
