'use client';

import { type FormEvent, useEffect, useState } from 'react';
import Link from 'next/link';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { BookHeart, Images, Menu, Search, Settings, Sparkles, Users, X } from 'lucide-react';
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

export default function Sidebar({ viewerRole = 'MEMBER', isPlatformAdmin = false, className }: SidebarProps) {
  const pathname = usePathname();
  const router = useRouter();
  const searchParams = useSearchParams();
  const [librarySearch, setLibrarySearch] = useState('');

  useEffect(() => {
    if (!pathname.startsWith('/dashboard/family')) return;
    setLibrarySearch(searchParams.get('q') || '');
  }, [pathname, searchParams]);

  const handleLibrarySearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const params = new URLSearchParams();
    params.set('tab', 'library');
    const familyId = searchParams.get('familyId');
    if (familyId) params.set('familyId', familyId);
    if (librarySearch.trim()) params.set('q', librarySearch.trim());
    router.push(`/dashboard/family?${params.toString()}`);
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
