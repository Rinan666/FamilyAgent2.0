'use client';

import { useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { BookHeart, Database, Images, Menu, Settings, Sparkles, Users, X } from 'lucide-react';
import type { ViewerRole } from '@/lib/roles';
import { cn } from '@/lib/utils';

type NavItem = {
  href: string;
  label: string;
  icon: typeof Sparkles;
  roles: readonly ViewerRole[];
  platformAdminOnly?: boolean;
};

const navItems: readonly NavItem[] = [
  { href: '/dashboard/agent', label: '家庭助手', icon: Sparkles, roles: ['MEMBER', 'ADMIN'] },
  { href: '/dashboard/diary', label: '日记', icon: BookHeart, roles: ['MEMBER', 'ADMIN'] },
  { href: '/album', label: '相册', icon: Images, roles: ['MEMBER', 'ADMIN'] },
  { href: '/dashboard/family', label: '家庭空间', icon: Users, roles: ['MEMBER', 'ADMIN'] },
  { href: '/dashboard/admin/database', label: '数据库健康', icon: Database, roles: ['ADMIN'], platformAdminOnly: true },
  { href: '/dashboard/settings', label: '设置', icon: Settings, roles: ['MEMBER', 'ADMIN'] },
] as const;

const mobilePrimaryNav: readonly NavItem[] = [
  { href: '/dashboard/agent', label: '助手', icon: Sparkles, roles: ['MEMBER', 'ADMIN'] },
  { href: '/dashboard/diary', label: '日记', icon: BookHeart, roles: ['MEMBER', 'ADMIN'] },
  { href: '/album', label: '相册', icon: Images, roles: ['MEMBER', 'ADMIN'] },
  { href: '/dashboard/family', label: '家庭', icon: Users, roles: ['MEMBER', 'ADMIN'] },
  { href: '/dashboard/settings', label: '设置', icon: Settings, roles: ['MEMBER', 'ADMIN'] },
] as const;

interface SidebarProps {
  viewerRole?: ViewerRole;
  isPlatformAdmin?: boolean;
  className?: string;
}

function isActivePath(pathname: string, href: string) {
  return href === '/dashboard' ? pathname === href : pathname.startsWith(href);
}

function NavigationLinks({
  viewerRole = 'MEMBER',
  isPlatformAdmin = false,
  onNavigate,
}: {
  viewerRole?: ViewerRole;
  isPlatformAdmin?: boolean;
  onNavigate?: () => void;
}) {
  const pathname = usePathname();

  return (
    <>
      {navItems
        .filter((item) => item.roles.includes(viewerRole) && (!item.platformAdminOnly || isPlatformAdmin))
        .map((item) => {
          const Icon = item.icon;
          const isActive = isActivePath(pathname, item.href);

          return (
            <Link
              key={item.href}
              href={item.href}
              onClick={onNavigate}
              title={item.label}
              className={cn(
                'flex items-center gap-3 rounded-lg px-3 py-3 text-sm font-medium transition-colors lg:py-2.5',
                isActive ? 'bg-blue-50 text-blue-700' : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900',
              )}
            >
              <Icon className="h-5 w-5 shrink-0" />
              <span className="flex-1">{item.label}</span>
            </Link>
          );
        })}
    </>
  );
}

export function MobileBottomNav({
  viewerRole = 'MEMBER',
}: {
  viewerRole?: ViewerRole;
}) {
  const pathname = usePathname();
  const items = mobilePrimaryNav.filter((item) => item.roles.includes(viewerRole)).slice(0, 5);

  return (
    <nav className="fixed inset-x-0 bottom-0 z-40 border-t border-gray-200 bg-white/95 px-2 pb-[max(env(safe-area-inset-bottom),0.5rem)] pt-2 shadow-[0_-8px_24px_rgba(15,23,42,0.08)] backdrop-blur lg:hidden">
      <div className="mx-auto grid max-w-md grid-cols-5 gap-1">
        {items.map((item) => {
          const Icon = item.icon;
          const isActive = isActivePath(pathname, item.href);

          return (
            <Link
              key={item.href}
              href={item.href}
              aria-label={item.label}
              className={cn(
                'flex min-w-0 flex-col items-center justify-center gap-1 rounded-xl px-1 py-1.5 text-[11px] font-medium transition-colors',
                isActive ? 'bg-blue-50 text-blue-700' : 'text-gray-500 hover:bg-gray-50 hover:text-gray-800',
              )}
            >
              <Icon className="h-5 w-5" />
              <span className="truncate">{item.label}</span>
            </Link>
          );
        })}
      </div>
    </nav>
  );
}

export default function Sidebar({ viewerRole = 'MEMBER', isPlatformAdmin = false, className }: SidebarProps) {
  return (
    <aside className={cn('hidden w-60 shrink-0 flex-col border-r border-gray-200 bg-white lg:flex xl:w-64', className)}>
      <div className="flex h-16 items-center border-b border-gray-200 px-6">
        <Link href="/dashboard/agent" className="flex items-center gap-2">
          <BookHeart className="h-6 w-6 text-blue-600" />
          <span className="text-lg font-bold text-gray-900">FamilyAgent</span>
        </Link>
      </div>

      <nav className="flex-1 space-y-1 px-3 py-4">
        <NavigationLinks viewerRole={viewerRole} isPlatformAdmin={isPlatformAdmin} />
      </nav>

      <div className="border-t border-gray-200 p-4">
        <p className="text-xs text-gray-400">v0.1.0 测试版</p>
      </div>
    </aside>
  );
}

export function MobileNav({
  viewerRole = 'MEMBER',
  isPlatformAdmin = false,
}: {
  viewerRole?: ViewerRole;
  isPlatformAdmin?: boolean;
}) {
  const [open, setOpen] = useState(false);

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="inline-flex h-10 w-10 items-center justify-center rounded-lg border border-gray-200 bg-white text-gray-600 lg:hidden"
        aria-label="打开导航"
      >
        <Menu className="h-5 w-5" />
      </button>

      {open && (
        <div className="fixed inset-0 z-50 lg:hidden">
          <button
            type="button"
            className="absolute inset-0 bg-gray-900/35"
            aria-label="关闭导航"
            onClick={() => setOpen(false)}
          />
          <div className="absolute inset-y-0 left-0 flex w-[min(20rem,86vw)] flex-col bg-white shadow-xl">
            <div className="flex h-16 items-center justify-between border-b border-gray-200 px-4">
              <Link href="/dashboard/agent" onClick={() => setOpen(false)} className="flex items-center gap-2">
                <BookHeart className="h-6 w-6 text-blue-600" />
                <span className="text-lg font-bold text-gray-900">FamilyAgent</span>
              </Link>
              <button
                type="button"
                onClick={() => setOpen(false)}
                className="inline-flex h-9 w-9 items-center justify-center rounded-lg text-gray-500 hover:bg-gray-50"
                aria-label="关闭导航"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <nav className="flex-1 space-y-1.5 overflow-y-auto px-3 py-4">
              <NavigationLinks
                viewerRole={viewerRole}
                isPlatformAdmin={isPlatformAdmin}
                onNavigate={() => setOpen(false)}
              />
            </nav>

            <div className="border-t border-gray-200 p-4">
              <p className="text-xs text-gray-400">v0.1.0 测试版</p>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
