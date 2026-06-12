'use client';

import type { ReactNode } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { LogOut } from 'lucide-react';
import { useAuthStore } from '@/stores/authStore';
import type { Family, FamilyMember, User } from '@/types';
import { familyRoleLabel, isPlatformAdmin, viewerRoleLabel, type ViewerRole } from '@/lib/roles';

interface HeaderProps {
  user: User;
  viewerRole?: ViewerRole;
  families?: Family[];
  activeFamilyId?: number | null;
  activeMembership?: FamilyMember | null;
  setActiveFamilyId?: (familyId: number | null) => void;
  mobileNav?: ReactNode;
}

export default function Header({
  user,
  viewerRole = 'MEMBER',
  families = [],
  activeFamilyId,
  activeMembership,
  setActiveFamilyId,
  mobileNav,
}: HeaderProps) {
  const router = useRouter();
  const logout = useAuthStore((state) => state.logout);
  const platformAdmin = isPlatformAdmin(user);

  const handleLogout = () => {
    logout();
    router.push('/login');
  };

  return (
    <header className="sticky top-0 z-20 border-b border-white/70 bg-white/72 backdrop-blur-xl">
      <div className="mx-auto flex h-[74px] w-full max-w-[1440px] items-center justify-between gap-3 px-4 md:px-6">
        <div className="flex min-w-0 items-center gap-3">
          {mobileNav}
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold text-stone-900">
              {user.nickname || user.username}
            </p>
            <div className="mt-1 flex min-w-0 items-center gap-2 text-xs text-stone-500">
              <span className="truncate">已进入家庭工作区</span>
              <span className="hidden shrink-0 rounded-full bg-stone-100 px-2.5 py-1 font-medium text-stone-700 sm:inline-flex">
                {viewerRoleLabel(viewerRole)}
              </span>
              {platformAdmin && (
                <span className="hidden shrink-0 rounded-full bg-amber-100 px-2.5 py-1 font-medium text-amber-800 md:inline-flex">
                  平台管理员
                </span>
              )}
            </div>
          </div>
        </div>

        <div className="flex shrink-0 items-center gap-2 sm:gap-3">
          {families.length > 0 && (
            <label className="hidden items-center gap-2 rounded-full border border-stone-200/80 bg-stone-50/85 px-3 py-1.5 text-xs text-stone-500 md:flex">
              <span>当前家庭</span>
              <select
                name="activeFamilyId"
                value={activeFamilyId ?? ''}
                onChange={(event) => setActiveFamilyId?.(Number(event.target.value) || null)}
                className="h-8 max-w-40 bg-transparent pr-1 text-sm font-medium text-stone-900 outline-none lg:max-w-52"
              >
                {families.map((family) => (
                  <option key={family.id} value={family.id}>{family.name}</option>
                ))}
              </select>
              {activeMembership && (
                <span className="hidden rounded-full bg-white px-2 py-1 text-[11px] font-medium text-stone-500 lg:inline-flex">
                  {familyRoleLabel(activeMembership.role)}
                </span>
              )}
            </label>
          )}

          <Link
            href="/dashboard/settings"
            title="设置"
            className="flex h-10 min-w-10 items-center justify-center rounded-full border border-stone-200/80 bg-white text-sm font-semibold text-stone-900 shadow-sm transition hover:border-stone-300 hover:bg-stone-50"
          >
            {(user.nickname || user.username).charAt(0).toUpperCase()}
          </Link>

          <button
            onClick={handleLogout}
            className="inline-flex h-10 items-center gap-2 rounded-full px-3 text-sm text-stone-500 transition hover:bg-stone-100 hover:text-stone-900"
          >
            <LogOut className="h-4 w-4" />
            <span className="hidden sm:inline">退出</span>
          </button>
        </div>
      </div>
    </header>
  );
}
