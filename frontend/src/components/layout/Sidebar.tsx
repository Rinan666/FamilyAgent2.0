'use client';

import { useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { BookHeart, Images, Menu, Settings, Sparkles, Users, X } from 'lucide-react';
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
                'group flex items-center justify-center rounded-md p-3 text-sm font-medium transition-colors 2xl:justify-start 2xl:gap-3',
                isActive
                  ? 'bg-stone-950 text-white shadow-sm'
                  : 'text-stone-600 hover:bg-stone-100 hover:text-stone-950',
              )}
            >
              <Icon
                className={cn(
                  'h-5 w-5 shrink-0 transition-colors',
                  isActive ? 'text-emerald-300' : 'text-stone-400 group-hover:text-emerald-700',
                )}
              />
              <span className="hidden flex-1 2xl:block">{item.label}</span>
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
    <nav className="fixed inset-x-0 bottom-0 z-40 border-t border-stone-200 bg-white/96 px-2 pb-[max(env(safe-area-inset-bottom),0.35rem)] pt-1 shadow-[0_-12px_30px_rgba(24,39,32,0.08)] backdrop-blur lg:hidden">
      <div className="mx-auto grid max-w-lg grid-cols-5 gap-1">
        {items.map((item) => {
          const Icon = item.icon;
          const isActive = isActivePath(pathname, item.href);

          return (
            <Link
              key={item.href}
              href={item.href}
              aria-label={item.label}
              className={cn(
                'flex min-w-0 flex-col items-center justify-center gap-1 rounded-md px-1 py-2 text-[11px] font-medium transition-colors',
                isActive ? 'bg-stone-950 text-white' : 'text-stone-500 hover:bg-stone-100 hover:text-stone-800',
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
    <aside className={cn('hidden w-16 shrink-0 border-r border-stone-200 bg-white lg:flex 2xl:w-56', className)}>
      <div className="flex h-full w-full flex-col px-2 pb-4 pt-4 2xl:px-3">
        <Link
          href="/dashboard/agent"
          title="FamilyAgent"
          className="flex h-11 items-center justify-center rounded-md text-stone-950 transition hover:bg-stone-100 2xl:justify-start 2xl:gap-3 2xl:px-3"
        >
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-stone-950 text-white shadow-sm">
            <BookHeart className="h-5 w-5" />
          </div>
          <p className="hidden text-sm font-semibold 2xl:block">FamilyAgent</p>
        </Link>

        <nav className="mt-4 flex-1 space-y-1">
          <NavigationLinks viewerRole={viewerRole} isPlatformAdmin={isPlatformAdmin} />
        </nav>
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
        className="inline-flex h-10 w-10 items-center justify-center rounded-md border border-stone-200 bg-white text-stone-600 shadow-sm lg:hidden"
        aria-label="打开导航"
      >
        <Menu className="h-5 w-5" />
      </button>

      {open && (
        <div className="fixed inset-0 z-50 lg:hidden">
          <button
            type="button"
            className="absolute inset-0 bg-stone-950/30 backdrop-blur-sm"
            aria-label="关闭导航"
            onClick={() => setOpen(false)}
          />
          <div className="absolute inset-y-0 left-0 flex w-[min(20rem,86vw)] flex-col border-r border-stone-200 bg-white shadow-xl">
            <div className="flex h-16 items-center justify-between px-4">
              <Link href="/dashboard/agent" onClick={() => setOpen(false)} className="flex items-center gap-2">
                <div className="flex h-10 w-10 items-center justify-center rounded-md bg-stone-950 text-white shadow-sm">
                  <BookHeart className="h-5 w-5" />
                </div>
                <div>
                  <p className="text-base font-semibold text-stone-950">FamilyAgent</p>
                </div>
              </Link>
              <button
                type="button"
                onClick={() => setOpen(false)}
                className="inline-flex h-10 w-10 items-center justify-center rounded-md text-stone-500 transition hover:bg-stone-100"
                aria-label="关闭导航"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <nav className="flex-1 space-y-1.5 overflow-y-auto px-4 py-2">
              <NavigationLinks
                viewerRole={viewerRole}
                isPlatformAdmin={isPlatformAdmin}
                onNavigate={() => setOpen(false)}
              />
            </nav>
          </div>
        </div>
      )}
    </>
  );
}
