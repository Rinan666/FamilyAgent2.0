'use client';

import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/stores/authStore';
import type { User } from '@/types';

interface HeaderProps {
  user: User;
}

export default function Header({ user }: HeaderProps) {
  const router = useRouter();
  const logout = useAuthStore((s) => s.logout);

  const handleLogout = () => {
    logout();
    router.push('/login');
  };

  return (
    <header className="h-16 bg-white border-b border-gray-200 flex items-center justify-between px-6">
      <div>
        <h2 className="text-sm text-gray-500">
          欢迎回来，{user.nickname || user.username}
        </h2>
      </div>

      <div className="flex items-center gap-4">
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
