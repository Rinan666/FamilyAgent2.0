'use client';

import type { ReactNode } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
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
  viewerRole = 'STUDENT',
  families = [],
  activeFamilyId,
  activeMembership,
  setActiveFamilyId,
  mobileNav,
}: HeaderProps) {
  const router = useRouter();
  const logout = useAuthStore((s) => s.logout);
  const platformAdmin = isPlatformAdmin(user);

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
        <span className="hidden shrink-0 rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-medium text-emerald-700 sm:inline-flex">
          {viewerRoleLabel(viewerRole)}
        </span>
        {platformAdmin && (
          <span className="hidden shrink-0 rounded-full bg-orange-50 px-2.5 py-1 text-xs font-medium text-orange-700 md:inline-flex">
            平台管理员
          </span>
        )}
      </div>

      <div className="flex shrink-0 items-center gap-2 md:gap-4">
        {families.length > 0 && (
          <label className="hidden items-center gap-2 text-xs text-gray-500 sm:flex">
            <span className="hidden md:inline">当前家族</span>
            <select
              value={activeFamilyId ?? ''}
              onChange={(event) => setActiveFamilyId?.(Number(event.target.value) || null)}
              className="h-9 max-w-40 rounded-lg border border-gray-200 bg-white px-2 text-sm text-gray-700 outline-none focus:ring-2 focus:ring-blue-500 lg:max-w-52"
            >
              {families.map((family) => (
                <option key={family.id} value={family.id}>{family.name}</option>
              ))}
            </select>
            {activeMembership && (
              <span className="hidden rounded-full bg-gray-100 px-2 py-1 font-medium text-gray-600 lg:inline-flex">
                {familyRoleLabel(activeMembership.role)}
              </span>
            )}
          </label>
        )}

        <Link
          href="/dashboard/settings"
          title="设置"
          className="flex h-8 w-8 items-center justify-center rounded-full bg-blue-600 text-sm font-medium text-white transition-colors hover:bg-blue-700"
        >
          {(user.nickname || user.username).charAt(0).toUpperCase()}
        </Link>

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
