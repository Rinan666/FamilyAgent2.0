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
  { href: '/dashboard/diary', label: '写下', icon: BookHeart, roles: ['MEMBER', 'ADMIN'] },
  { href: '/album', label: '相册', icon: Images, roles: ['MEMBER', 'ADMIN'] },
  { href: '/dashboard/family', label: '家庭空间', icon: Users, roles: ['MEMBER', 'ADMIN'] },
  { href: '/dashboard/settings', label: '设置', icon: Settings, roles: ['MEMBER', 'ADMIN'] },
] as const;

const mobilePrimaryNav: readonly NavItem[] = [
  { href: '/dashboard/agent', label: '助手', icon: Sparkles, roles: ['MEMBER', 'ADMIN'] },
  { href: '/dashboard/diary', label: '写下', icon: BookHeart, roles: ['MEMBER', 'ADMIN'] },
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
                'group flex items-center gap-3 rounded-2xl px-3 py-3 text-sm font-medium transition-all',
                isActive
                  ? 'bg-stone-950 text-white shadow-[0_16px_36px_rgba(24,39,32,0.14)]'
                  : 'text-stone-600 hover:bg-white/82 hover:text-stone-950',
              )}
            >
              <Icon
                className={cn(
                  'h-5 w-5 shrink-0 transition-colors',
                  isActive ? 'text-emerald-300' : 'text-stone-400 group-hover:text-emerald-700',
                )}
              />
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
    <nav className="fixed inset-x-0 bottom-0 z-40 px-4 pb-[max(env(safe-area-inset-bottom),0.75rem)] pt-2 lg:hidden">
      <div className="mx-auto grid max-w-lg grid-cols-5 gap-1 rounded-[24px] border border-white/70 bg-white/92 p-2 shadow-[0_20px_48px_rgba(24,39,32,0.12)] backdrop-blur-xl">
        {items.map((item) => {
          const Icon = item.icon;
          const isActive = isActivePath(pathname, item.href);

          return (
            <Link
              key={item.href}
              href={item.href}
              aria-label={item.label}
              className={cn(
                'flex min-w-0 flex-col items-center justify-center gap-1 rounded-2xl px-1 py-2 text-[11px] font-medium transition-colors',
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
    <aside className={cn('hidden w-72 shrink-0 border-r border-white/70 bg-[#f5f4ee]/86 backdrop-blur xl:flex', className)}>
      <div className="flex h-full flex-col px-5 pb-5 pt-6">
        <Link
          href="/dashboard/agent"
          className="rounded-[28px] border border-white/80 bg-white/78 px-4 py-4 shadow-[0_16px_36px_rgba(24,39,32,0.08)]"
        >
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-stone-950 text-white">
              <BookHeart className="h-5 w-5" />
            </div>
            <div>
              <p className="text-base font-semibold text-stone-950">FamilyAgent</p>
              <p className="text-sm text-stone-500">家庭记忆工作台</p>
            </div>
          </div>
        </Link>

        <div className="mt-8 px-2">
          <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-stone-400">Navigation</p>
        </div>

        <nav className="mt-3 flex-1 space-y-1.5">
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
        className="inline-flex h-10 w-10 items-center justify-center rounded-2xl border border-stone-200/80 bg-white text-stone-600 shadow-sm lg:hidden"
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
          <div className="absolute inset-y-0 left-0 flex w-[min(22rem,88vw)] flex-col border-r border-white/70 bg-[#f5f4ee]/95 shadow-[0_20px_60px_rgba(24,39,32,0.18)] backdrop-blur-xl">
            <div className="flex h-20 items-center justify-between px-4">
              <Link href="/dashboard/agent" onClick={() => setOpen(false)} className="flex items-center gap-2">
                <div className="flex h-10 w-10 items-center justify-center rounded-2xl bg-stone-950 text-white">
                  <BookHeart className="h-5 w-5" />
                </div>
                <div>
                  <p className="text-base font-semibold text-stone-950">FamilyAgent</p>
                  <p className="text-xs text-stone-500">家庭记忆工作台</p>
                </div>
              </Link>
              <button
                type="button"
                onClick={() => setOpen(false)}
                className="inline-flex h-10 w-10 items-center justify-center rounded-2xl text-stone-500 transition hover:bg-white/70"
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

            <div className="p-4 pt-2">
              <div className="rounded-[22px] border border-white/80 bg-white/78 p-4 text-sm text-stone-500">
                选择一个模块继续。
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
