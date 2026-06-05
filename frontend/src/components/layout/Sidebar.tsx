'use client';

import { useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { cn } from '@/lib/utils';
import {
  LayoutDashboard,
  GraduationCap,
  ClipboardList,
  BarChart3,
  Users,
  BookOpen,
  Settings,
  BookX,
  Lock,
  Menu,
  X,
} from 'lucide-react';

const navItems = [
  { href: '/dashboard', label: '首页', icon: LayoutDashboard, requiresDiagnosis: true },
  { href: '/dashboard/tutor', label: 'AI家教', icon: GraduationCap, requiresDiagnosis: true },
  { href: '/dashboard/test', label: '数学诊断', icon: ClipboardList },
  { href: '/dashboard/notebook', label: '错题本', icon: BookX, requiresDiagnosis: true },
  { href: '/dashboard/assessment', label: '学力评估', icon: BarChart3, requiresDiagnosis: true },
  { href: '/dashboard/family', label: '家族空间', icon: Users },
  { href: '/dashboard/knowledge', label: '题库/知识库', icon: BookOpen },
  { href: '/dashboard/settings', label: '设置', icon: Settings },
];

const mobilePrimaryNav = [
  { href: '/dashboard', label: '首页', icon: LayoutDashboard, requiresDiagnosis: true },
  { href: '/dashboard/tutor', label: '家教', icon: GraduationCap, requiresDiagnosis: true },
  { href: '/dashboard/test', label: '诊断', icon: ClipboardList },
  { href: '/dashboard/notebook', label: '错题', icon: BookX, requiresDiagnosis: true },
  { href: '/dashboard/assessment', label: '评估', icon: BarChart3, requiresDiagnosis: true },
];

interface SidebarProps {
  diagnosisLocked?: boolean;
  className?: string;
}

function isActivePath(pathname: string, href: string) {
  return href === '/dashboard' ? pathname === href : pathname.startsWith(href);
}

function NavigationLinks({
  diagnosisLocked = false,
  onNavigate,
}: {
  diagnosisLocked?: boolean;
  onNavigate?: () => void;
}) {
  const pathname = usePathname();

  return (
    <>
      {navItems.map((item) => {
        const isActive = isActivePath(pathname, item.href);
        const Icon = item.icon;
        const isLocked = diagnosisLocked && item.requiresDiagnosis;
        const href = isLocked ? '/dashboard/test?required=1' : item.href;

        return (
          <Link
            key={item.href}
            href={href}
            onClick={onNavigate}
            title={isLocked ? '完成首次数学诊断后开放' : item.label}
            className={cn(
              'flex items-center gap-3 rounded-lg px-3 py-3 text-sm font-medium transition-colors lg:py-2.5',
              isActive
                ? 'bg-blue-50 text-blue-700'
                : isLocked
                  ? 'text-gray-300 hover:bg-gray-50'
                  : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900',
            )}
          >
            <Icon className="h-5 w-5 shrink-0" />
            <span className="flex-1">{item.label}</span>
            {isLocked && <Lock className="h-3.5 w-3.5" />}
          </Link>
        );
      })}
    </>
  );
}

export function MobileBottomNav({ diagnosisLocked = false }: { diagnosisLocked?: boolean }) {
  const pathname = usePathname();

  return (
    <nav className="fixed inset-x-0 bottom-0 z-40 border-t border-gray-200 bg-white/95 px-2 pb-[max(env(safe-area-inset-bottom),0.5rem)] pt-2 shadow-[0_-8px_24px_rgba(15,23,42,0.08)] backdrop-blur lg:hidden">
      <div className="mx-auto grid max-w-md grid-cols-5 gap-1">
        {mobilePrimaryNav.map((item) => {
          const Icon = item.icon;
          const isLocked = diagnosisLocked && item.requiresDiagnosis;
          const href = isLocked ? '/dashboard/test?required=1' : item.href;
          const isActive = isActivePath(pathname, item.href);

          return (
            <Link
              key={item.href}
              href={href}
              aria-label={item.label}
              className={cn(
                'flex min-w-0 flex-col items-center justify-center gap-1 rounded-xl px-1 py-1.5 text-[11px] font-medium transition-colors',
                isActive
                  ? 'bg-blue-50 text-blue-700'
                  : isLocked
                    ? 'text-gray-300'
                    : 'text-gray-500 hover:bg-gray-50 hover:text-gray-800',
              )}
            >
              <span className="relative">
                <Icon className="h-5 w-5" />
                {isLocked && <Lock className="absolute -right-1 -top-1 h-3 w-3 rounded-full bg-white" />}
              </span>
              <span className="truncate">{item.label}</span>
            </Link>
          );
        })}
      </div>
    </nav>
  );
}

export default function Sidebar({ diagnosisLocked = false, className }: SidebarProps) {
  return (
    <aside className={cn('hidden w-60 shrink-0 flex-col border-r border-gray-200 bg-white xl:w-64 lg:flex', className)}>
      {/* Logo */}
      <div className="h-16 flex items-center px-6 border-b border-gray-200">
        <Link href="/dashboard" className="flex items-center gap-2">
          <GraduationCap className="w-6 h-6 text-blue-600" />
          <span className="font-bold text-lg text-gray-900">家族教育</span>
        </Link>
      </div>

      {/* Nav */}
      <nav className="flex-1 space-y-1 px-3 py-4">
        <NavigationLinks diagnosisLocked={diagnosisLocked} />
      </nav>

      {/* Footer */}
      <div className="p-4 border-t border-gray-200">
        <p className="text-xs text-gray-400">v0.1.0 · 内测版</p>
      </div>
    </aside>
  );
}

export function MobileNav({ diagnosisLocked = false }: { diagnosisLocked?: boolean }) {
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
              <Link
                href="/dashboard"
                onClick={() => setOpen(false)}
                className="flex items-center gap-2"
              >
                <GraduationCap className="h-6 w-6 text-blue-600" />
                <span className="text-lg font-bold text-gray-900">家族教育</span>
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
              <NavigationLinks diagnosisLocked={diagnosisLocked} onNavigate={() => setOpen(false)} />
            </nav>

            <div className="border-t border-gray-200 p-4">
              <p className="text-xs text-gray-400">v0.1.0 · 内测版</p>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
