'use client';

import { type FormEvent, useEffect, useState } from 'react';
import Link from 'next/link';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { BookHeart, ChevronLeft, Menu, Search, Settings, Sparkles, Users, X } from 'lucide-react';
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
  {
    href: '/dashboard/memory-library',
    label: '记忆库',
    icon: BookHeart,
    roles: ['MEMBER', 'ADMIN'],
  },
  { href: '/dashboard/family', label: '家庭空间', icon: Users, roles: ['MEMBER', 'ADMIN'] },
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
        .filter(
          (item) => item.roles.includes(viewerRole) && (!item.platformAdminOnly || isPlatformAdmin),
        )
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
                  isActive ? 'text-sky-300' : 'text-stone-400 group-hover:text-sky-700',
                )}
              />
              <span className="hidden flex-1 2xl:block">{item.label}</span>
            </Link>
          );
        })}
    </>
  );
}

function visibleNavItems(viewerRole: ViewerRole, isPlatformAdmin: boolean) {
  return navItems.filter(
    (item) => item.roles.includes(viewerRole) && (!item.platformAdminOnly || isPlatformAdmin),
  );
}

export default function Sidebar({
  viewerRole = 'MEMBER',
  isPlatformAdmin = false,
  className,
}: SidebarProps) {
  const pathname = usePathname();
  const router = useRouter();
  const searchParams = useSearchParams();
  const [librarySearch, setLibrarySearch] = useState('');

  useEffect(() => {
    if (!pathname.startsWith('/dashboard/memory-library')) return;
    setLibrarySearch(searchParams.get('q') || '');
  }, [pathname, searchParams]);

  const handleLibrarySearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const params = new URLSearchParams();
    const familyId = searchParams.get('familyId');
    if (familyId) params.set('familyId', familyId);
    if (librarySearch.trim()) params.set('q', librarySearch.trim());
    router.push(`/dashboard/memory-library?${params.toString()}`);
  };

  return (
    <header
      className={cn(
        'glass-panel-strong z-20 hidden shrink-0 border-x-0 border-t-0 lg:block',
        className,
      )}
    >
      <div className="flex h-16 items-center gap-4 px-5">
        <Link
          href="/dashboard/agent"
          title="FamilyAgent"
          className="group flex shrink-0 items-center gap-3 text-stone-950"
        >
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-sky-700 to-blue-600 text-white shadow-[0_10px_22px_rgba(14,165,233,0.22)] transition group-hover:-translate-y-0.5">
            <BookHeart className="h-5 w-5" />
          </div>
          <p className="text-base font-semibold">FamilyAgent</p>
        </Link>

        <nav className="flex shrink-0 items-center gap-1">
          <NavigationLinks viewerRole={viewerRole} isPlatformAdmin={isPlatformAdmin} />
        </nav>

        <form onSubmit={handleLibrarySearch} className="mx-auto w-full max-w-xl">
          <label className="relative block">
            <input
              value={librarySearch}
              onChange={(event) => setLibrarySearch(event.target.value)}
              placeholder="搜索全部记忆"
              className="glass-control h-10 w-full rounded-2xl pl-4 pr-11 text-sm text-stone-800 outline-none transition placeholder:text-stone-400 focus:border-sky-300 focus:bg-white/90 focus:ring-2 focus:ring-sky-100"
            />
            <button
              type="submit"
              className="absolute right-1 top-1/2 inline-flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-md text-stone-500 transition hover:bg-stone-100 hover:text-stone-900"
              aria-label="搜索全部记忆"
            >
              <Search className="h-4 w-4" />
            </button>
          </label>
        </form>
      </div>
    </header>
  );
}

export function MobileNav({
  viewerRole = 'MEMBER',
  isPlatformAdmin = false,
}: {
  viewerRole?: ViewerRole;
  isPlatformAdmin?: boolean;
}) {
  const pathname = usePathname();

  return (
    <nav className="glass-panel-strong grid shrink-0 grid-cols-4 gap-1 border-x-0 border-b-0 px-2 py-1.5 lg:hidden">
      {visibleNavItems(viewerRole, isPlatformAdmin).map((item) => {
        const Icon = item.icon;
        const active = isActivePath(pathname, item.href);
        return (
          <Link
            key={item.href}
            href={item.href}
            className={cn(
              'flex min-w-0 flex-col items-center justify-center gap-0.5 rounded-md px-1 py-1.5 text-[11px] font-medium transition',
              active
                ? 'bg-stone-950 text-white'
                : 'text-stone-500 hover:bg-stone-100 hover:text-stone-950',
            )}
          >
            <Icon className={cn('h-4 w-4 shrink-0', active ? 'text-sky-300' : 'text-stone-400')} />
            <span className="max-w-full truncate">{item.label}</span>
          </Link>
        );
      })}
    </nav>
  );
}

export function MobileNavDrawer({
  viewerRole = 'MEMBER',
  isPlatformAdmin = false,
}: {
  viewerRole?: ViewerRole;
  isPlatformAdmin?: boolean;
}) {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  const items = visibleNavItems(viewerRole, isPlatformAdmin);

  useEffect(() => {
    setOpen(false);
  }, [pathname]);

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="glass-control fixed right-0 top-1/2 z-40 inline-flex h-11 w-8 -translate-y-1/2 items-center justify-center rounded-l-xl border-r-0 text-stone-500 shadow-[0_10px_30px_rgba(15,23,42,0.12)] transition hover:text-stone-950 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sky-200 lg:hidden"
        aria-label="打开侧边菜单"
        aria-expanded={open}
      >
        <ChevronLeft className="h-6 w-6" strokeWidth={2.5} />
      </button>

      {open && (
        <div className="fixed inset-0 z-50 lg:hidden">
          <button
            type="button"
            className="absolute inset-0 bg-stone-950/20 backdrop-blur-[2px]"
            aria-label="关闭侧边菜单"
            onClick={() => setOpen(false)}
          />
          <aside className="glass-panel-strong absolute inset-y-3 right-3 flex w-[min(20rem,86vw)] flex-col overflow-hidden rounded-[24px] shadow-[-18px_0_48px_rgba(15,23,42,0.16)]">
            <div className="flex h-14 items-center justify-between border-b border-white/70 px-4">
              <div className="flex min-w-0 items-center gap-2 text-sm font-semibold text-stone-900">
                <BookHeart className="h-4 w-4 shrink-0 text-sky-700" />
                快捷入口
              </div>
              <button
                type="button"
                onClick={() => setOpen(false)}
                className="inline-flex h-8 w-8 items-center justify-center rounded-md text-stone-500 transition hover:bg-stone-100 hover:text-stone-950"
                aria-label="关闭侧边菜单"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <nav className="flex-1 space-y-1 overflow-y-auto p-4">
              {items.map((item) => {
                const Icon = item.icon;
                const active = isActivePath(pathname, item.href);
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    onClick={() => setOpen(false)}
                    className={cn(
                      'flex h-11 min-w-0 items-center gap-3 rounded-md px-3 text-sm font-medium transition',
                      active
                        ? 'bg-stone-950 text-white'
                        : 'text-stone-600 hover:bg-white/70 hover:text-stone-950',
                    )}
                  >
                    <Icon
                      className={cn('h-5 w-5 shrink-0', active ? 'text-sky-300' : 'text-stone-400')}
                    />
                    <span className="truncate">{item.label}</span>
                  </Link>
                );
              })}
            </nav>
          </aside>
        </div>
      )}
    </>
  );
}

export function MobilePageDrawer({
  viewerRole = 'MEMBER',
  isPlatformAdmin = false,
  showLibrarySearch = false,
}: {
  viewerRole?: ViewerRole;
  isPlatformAdmin?: boolean;
  showLibrarySearch?: boolean;
}) {
  const pathname = usePathname();
  const router = useRouter();
  const searchParams = useSearchParams();
  const [open, setOpen] = useState(false);
  const [librarySearch, setLibrarySearch] = useState('');
  const items = visibleNavItems(viewerRole, isPlatformAdmin);

  useEffect(() => {
    setOpen(false);
  }, [pathname]);

  useEffect(() => {
    if (!showLibrarySearch) return;
    setLibrarySearch(searchParams.get('q') || '');
  }, [searchParams, showLibrarySearch]);

  const handleLibrarySearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const params = new URLSearchParams();
    const familyId = searchParams.get('familyId');
    if (familyId) params.set('familyId', familyId);
    if (librarySearch.trim()) params.set('q', librarySearch.trim());
    setOpen(false);
    router.push(`/dashboard/memory-library?${params.toString()}`);
  };

  return (
    <div className="lg:hidden">
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="glass-control inline-flex h-10 w-10 items-center justify-center rounded-2xl text-stone-700 shadow-[0_10px_28px_rgba(15,23,42,0.1)] transition hover:text-sky-700"
        aria-label={showLibrarySearch ? '打开记忆搜索' : '打开功能抽屉'}
        aria-expanded={open}
      >
        {showLibrarySearch ? <Search className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
      </button>

      {open && (
        <div className="fixed inset-0 z-50">
          <button
            type="button"
            className="absolute inset-0 bg-stone-950/20 backdrop-blur-[2px]"
            aria-label="关闭功能抽屉"
            onClick={() => setOpen(false)}
          />
          <aside
            className={cn(
              'glass-panel-strong absolute overflow-hidden rounded-[24px]',
              showLibrarySearch
                ? 'left-3 right-3 top-3 shadow-[0_24px_70px_rgba(15,23,42,0.2)]'
                : 'inset-y-3 left-3 flex w-[min(20rem,86vw)] flex-col shadow-[18px_0_48px_rgba(15,23,42,0.16)]',
            )}
          >
            <div className="flex h-14 items-center justify-between border-b border-white/70 px-4">
              <div className="flex min-w-0 items-center gap-2 text-sm font-semibold text-stone-950">
                <BookHeart className="h-4 w-4 shrink-0 text-sky-700" />
                <span className="truncate">{showLibrarySearch ? '搜索记忆' : 'FamilyAgent'}</span>
              </div>
              <button
                type="button"
                onClick={() => setOpen(false)}
                className="inline-flex h-8 w-8 items-center justify-center rounded-md text-stone-500 transition hover:bg-stone-100 hover:text-stone-950"
                aria-label="关闭功能抽屉"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <div className={cn('space-y-4 p-4', !showLibrarySearch && 'flex-1 overflow-y-auto')}>
              {showLibrarySearch && (
                <form onSubmit={handleLibrarySearch}>
                  <label className="relative block">
                    <input
                      value={librarySearch}
                      onChange={(event) => setLibrarySearch(event.target.value)}
                      placeholder="搜索全部记忆"
                      autoFocus
                      className="glass-control h-12 w-full rounded-2xl pl-4 pr-12 text-sm text-stone-800 outline-none transition placeholder:text-stone-400 focus:border-sky-300 focus:bg-white/90 focus:ring-2 focus:ring-sky-100"
                    />
                    <button
                      type="submit"
                      className="absolute right-1 top-1/2 inline-flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-md text-stone-500 transition hover:bg-stone-100 hover:text-stone-900"
                      aria-label="搜索全部记忆"
                    >
                      <Search className="h-4 w-4" />
                    </button>
                  </label>
                </form>
              )}

              {!showLibrarySearch && (
                <nav className="space-y-1">
                  {items.map((item) => {
                    const Icon = item.icon;
                    const active = isActivePath(pathname, item.href);
                    return (
                      <Link
                        key={item.href}
                        href={item.href}
                        onClick={() => setOpen(false)}
                        className={cn(
                          'flex h-11 min-w-0 items-center gap-3 rounded-md px-3 text-sm font-medium transition',
                          active
                            ? 'bg-stone-950 text-white'
                            : 'text-stone-600 hover:bg-white/70 hover:text-stone-950',
                        )}
                      >
                        <Icon
                          className={cn(
                            'h-5 w-5 shrink-0',
                            active ? 'text-sky-300' : 'text-stone-400',
                          )}
                        />
                        <span className="truncate">{item.label}</span>
                      </Link>
                    );
                  })}
                </nav>
              )}
            </div>
          </aside>
        </div>
      )}
    </div>
  );
}
