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
  { href: '/dashboard/memory-library', label: '记忆库', icon: BookHeart, roles: ['MEMBER', 'ADMIN'] },
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

function visibleNavItems(viewerRole: ViewerRole, isPlatformAdmin: boolean) {
  return navItems.filter((item) => item.roles.includes(viewerRole) && (!item.platformAdminOnly || isPlatformAdmin));
}

export default function Sidebar({ viewerRole = 'MEMBER', isPlatformAdmin = false, className }: SidebarProps) {
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
    <header className={cn('hidden shrink-0 border-b border-stone-200 bg-white lg:block', className)}>
      <div className="flex h-16 items-center gap-4 px-5">
        <Link
          href="/dashboard/agent"
          title="FamilyAgent"
          className="flex shrink-0 items-center gap-3 text-stone-950"
        >
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-stone-950 text-white shadow-sm">
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
              placeholder="搜索家族记忆库"
              className="h-10 w-full rounded-md border border-stone-200 bg-stone-50 pl-4 pr-11 text-sm text-stone-800 outline-none transition placeholder:text-stone-400 focus:border-emerald-300 focus:bg-white focus:ring-2 focus:ring-emerald-100"
            />
            <button
              type="submit"
              className="absolute right-1 top-1/2 inline-flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-md text-stone-500 transition hover:bg-stone-100 hover:text-stone-900"
              aria-label="搜索记忆库"
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
    <nav className="grid shrink-0 grid-cols-4 gap-1 border-t border-stone-200 bg-white px-2 py-1.5 lg:hidden">
      {visibleNavItems(viewerRole, isPlatformAdmin).map((item) => {
        const Icon = item.icon;
        const active = isActivePath(pathname, item.href);
        return (
          <Link
            key={item.href}
            href={item.href}
            className={cn(
              'flex min-w-0 flex-col items-center justify-center gap-0.5 rounded-md px-1 py-1.5 text-[11px] font-medium transition',
              active ? 'bg-stone-950 text-white' : 'text-stone-500 hover:bg-stone-100 hover:text-stone-950',
            )}
          >
            <Icon className={cn('h-4 w-4 shrink-0', active ? 'text-emerald-300' : 'text-stone-400')} />
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
        className="fixed right-0 top-1/2 z-40 inline-flex h-11 w-8 -translate-y-1/2 items-center justify-center rounded-l-md border border-r-0 border-stone-200 bg-white/95 text-stone-500 shadow-sm transition hover:text-stone-950 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-200 lg:hidden"
        aria-label="打开侧边菜单"
        aria-expanded={open}
      >
        <ChevronLeft className="h-6 w-6" strokeWidth={2.5} />
      </button>

      {open && (
        <div className="fixed inset-0 z-50 lg:hidden">
          <button
            type="button"
            className="absolute inset-0 bg-stone-950/24"
            aria-label="关闭侧边菜单"
            onClick={() => setOpen(false)}
          />
          <aside className="absolute inset-y-0 right-0 flex w-[min(20rem,86vw)] flex-col border-l border-stone-200 bg-white shadow-[-18px_0_48px_rgba(24,39,32,0.18)]">
            <div className="flex h-14 items-center justify-between border-b border-stone-200 px-4">
              <div className="flex min-w-0 items-center gap-2 text-sm font-semibold text-stone-900">
                <BookHeart className="h-4 w-4 shrink-0 text-emerald-700" />
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
                      active ? 'bg-stone-950 text-white' : 'text-stone-600 hover:bg-stone-100 hover:text-stone-950',
                    )}
                  >
                    <Icon className={cn('h-5 w-5 shrink-0', active ? 'text-emerald-300' : 'text-stone-400')} />
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
        className="inline-flex h-10 w-10 items-center justify-center rounded-md border border-stone-200 bg-white text-stone-700 shadow-sm transition hover:bg-stone-50 hover:text-stone-950"
        aria-label="打开功能抽屉"
        aria-expanded={open}
      >
        <Menu className="h-5 w-5" />
      </button>

      {open && (
        <div className="fixed inset-0 z-50">
          <button
            type="button"
            className="absolute inset-0 bg-stone-950/24"
            aria-label="关闭功能抽屉"
            onClick={() => setOpen(false)}
          />
          <aside className="absolute inset-y-0 left-0 flex w-[min(20rem,86vw)] flex-col border-r border-stone-200 bg-white shadow-[18px_0_48px_rgba(24,39,32,0.18)]">
            <div className="flex h-14 items-center justify-between border-b border-stone-200 px-4">
              <div className="flex min-w-0 items-center gap-2 text-sm font-semibold text-stone-950">
                <BookHeart className="h-4 w-4 shrink-0 text-emerald-700" />
                <span className="truncate">FamilyAgent</span>
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

            <div className="flex-1 space-y-4 overflow-y-auto p-4">
              {showLibrarySearch && (
                <form onSubmit={handleLibrarySearch}>
                  <label className="relative block">
                    <input
                      value={librarySearch}
                      onChange={(event) => setLibrarySearch(event.target.value)}
                      placeholder="搜索家族记忆库"
                      className="h-10 w-full rounded-md border border-stone-200 bg-stone-50 pl-3 pr-10 text-sm text-stone-800 outline-none transition placeholder:text-stone-400 focus:border-emerald-300 focus:bg-white focus:ring-2 focus:ring-emerald-100"
                    />
                    <button
                      type="submit"
                      className="absolute right-1 top-1/2 inline-flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-md text-stone-500 transition hover:bg-stone-100 hover:text-stone-900"
                      aria-label="搜索记忆库"
                    >
                      <Search className="h-4 w-4" />
                    </button>
                  </label>
                </form>
              )}

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
                        active ? 'bg-stone-950 text-white' : 'text-stone-600 hover:bg-stone-100 hover:text-stone-950',
                      )}
                    >
                      <Icon className={cn('h-5 w-5 shrink-0', active ? 'text-emerald-300' : 'text-stone-400')} />
                      <span className="truncate">{item.label}</span>
                    </Link>
                  );
                })}
              </nav>
            </div>
          </aside>
        </div>
      )}
    </div>
  );
}
