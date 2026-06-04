'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { cn } from '@/lib/utils';
import {
  LayoutDashboard,
  GraduationCap,
  BarChart3,
  Users,
  BookOpen,
  Settings,
} from 'lucide-react';

const navItems = [
  { href: '/dashboard', label: '首页', icon: LayoutDashboard },
  { href: '/dashboard/tutor', label: 'AI家教', icon: GraduationCap },
  { href: '/dashboard/assessment', label: '学力评估', icon: BarChart3 },
  { href: '/dashboard/family', label: '家族空间', icon: Users },
  { href: '/dashboard/knowledge', label: '知识库', icon: BookOpen },
  { href: '/dashboard/settings', label: '设置', icon: Settings },
];

export default function Sidebar() {
  const pathname = usePathname();

  return (
    <aside className="w-64 bg-white border-r border-gray-200 flex flex-col">
      {/* Logo */}
      <div className="h-16 flex items-center px-6 border-b border-gray-200">
        <Link href="/dashboard" className="flex items-center gap-2">
          <GraduationCap className="w-6 h-6 text-blue-600" />
          <span className="font-bold text-lg text-gray-900">家族教育</span>
        </Link>
      </div>

      {/* Nav */}
      <nav className="flex-1 px-3 py-4 space-y-1">
        {navItems.map((item) => {
          const isActive = pathname === item.href;
          const Icon = item.icon;

          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                'flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors',
                isActive
                  ? 'bg-blue-50 text-blue-700'
                  : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900',
              )}
            >
              <Icon className="w-5 h-5" />
              {item.label}
            </Link>
          );
        })}
      </nav>

      {/* Footer */}
      <div className="p-4 border-t border-gray-200">
        <p className="text-xs text-gray-400">v0.1.0 — Alpha</p>
      </div>
    </aside>
  );
}
