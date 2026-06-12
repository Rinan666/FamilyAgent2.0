'use client';

import type { ReactNode } from 'react';
import type { User } from '@/types';

interface HeaderProps {
  user: User;
  mobileNav?: ReactNode;
}

export default function Header({
  user,
  mobileNav,
}: HeaderProps) {
  return (
    <header className="sticky top-0 z-20 border-b border-white/70 bg-white/72 backdrop-blur-xl">
      <div className="mx-auto flex h-[74px] w-full max-w-[1440px] items-center gap-3 px-4 md:px-6">
        <div className="flex min-w-0 flex-1 items-center gap-3">
          {mobileNav}
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold text-stone-900">
              {user.nickname || user.username}
            </p>
            <p className="mt-1 truncate text-xs text-stone-500">@{user.username}</p>
          </div>
        </div>

        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full border border-stone-200/80 bg-white text-sm font-semibold text-stone-900 shadow-sm">
          {(user.nickname || user.username).charAt(0).toUpperCase()}
        </div>
      </div>
    </header>
  );
}
