'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import { useAuthStore } from '@/stores/authStore';
import { familyApi, questionApi } from '@/lib/api';
import {
  GraduationCap, BarChart3, Users, BookOpen, ArrowRight,
  Layers, Clock, ClipboardList, BookX, Sparkles,
} from 'lucide-react';

export default function DashboardPage() {
  const user = useAuthStore((s) => s.user);
  const [stats, setStats] = useState({ families: 0, questions: 0, sessions: 0 });
  const [families, setFamilies] = useState<{ id: number; name: string; inviteCode?: string }[]>([]);

  useEffect(() => {
    // 加载家族数据
    familyApi.getMyFamilies()
      .then((data) => {
        const list = Array.isArray(data) ? data : [];
        setFamilies(list);
        setStats((s) => ({ ...s, families: list.length }));
      })
      .catch(() => {});

    // 加载题库数据
    questionApi.listQuestions({ page: 1, size: 1 })
      .then((data) => {
        setStats((s) => ({ ...s, questions: data?.total || 0 }));
      })
      .catch(() => {});
  }, []);

  const quickLinks = [
    { href: '/dashboard/tutor', label: 'AI家教', icon: GraduationCap, color: 'bg-blue-50 text-blue-700 hover:bg-blue-100' },
    { href: '/dashboard/test', label: '数学诊断', icon: ClipboardList, color: 'bg-indigo-50 text-indigo-700 hover:bg-indigo-100' },
    { href: '/dashboard/notebook', label: '错题复盘', icon: BookX, color: 'bg-red-50 text-red-700 hover:bg-red-100' },
    { href: '/dashboard/assessment', label: '能力评估', icon: BarChart3, color: 'bg-green-50 text-green-700 hover:bg-green-100' },
    { href: '/dashboard/knowledge', label: '题库资源', icon: BookOpen, color: 'bg-orange-50 text-orange-700 hover:bg-orange-100' },
    { href: '/dashboard/family', label: '家族空间', icon: Users, color: 'bg-purple-50 text-purple-700 hover:bg-purple-100' },
  ];

  const mainActions = [
    {
      href: '/dashboard/tutor',
      title: 'AI家教',
      desc: '讲题、追问、整理思路',
      icon: GraduationCap,
      color: 'bg-blue-600 text-white',
    },
    {
      href: '/dashboard/test',
      title: '数学诊断',
      desc: '生成一组练习题',
      icon: ClipboardList,
      color: 'bg-indigo-50 text-indigo-700',
    },
    {
      href: '/dashboard/notebook',
      title: '错题复盘',
      desc: '回看最近薄弱点',
      icon: BookX,
      color: 'bg-red-50 text-red-700',
    },
  ];

  return (
    <div className="mx-auto w-full max-w-6xl">
      <section className="mb-4 rounded-xl border border-gray-200 bg-white p-4 sm:p-5">
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0">
            <div className="mb-2 inline-flex items-center gap-1.5 rounded-full bg-blue-50 px-2.5 py-1 text-xs font-medium text-blue-700">
              <Sparkles className="h-3.5 w-3.5" />
              今日学习
            </div>
            <h1 className="text-2xl font-bold text-gray-900">
              你好，{user?.nickname || user?.username || '用户'}
            </h1>
            <p className="mt-1 text-sm text-gray-500">
              从一道题开始，把卡点讲清楚。
            </p>
          </div>
          <div className="hidden rounded-xl bg-gray-50 px-3 py-2 text-right sm:block">
            <p className="text-xs text-gray-400">今日可用</p>
            <p className="text-xl font-bold text-green-600">∞</p>
          </div>
        </div>

        <Link
          href="/dashboard/tutor"
          className="mt-4 flex h-12 items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 text-sm font-semibold text-white transition-colors hover:bg-blue-700"
        >
          <GraduationCap className="h-5 w-5" />
          进入 AI 家教
        </Link>
      </section>

      <div className="mb-4 grid grid-cols-3 gap-2">
        {mainActions.map((action) => {
          const Icon = action.icon;
          return (
            <Link
              key={action.href}
              href={action.href}
              className={`flex min-h-24 flex-col justify-between rounded-xl p-3 transition-colors ${action.color}`}
            >
              <Icon className="h-5 w-5" />
              <span>
                <span className="block text-sm font-semibold">{action.title}</span>
                <span className="mt-1 hidden text-xs opacity-75 sm:block">{action.desc}</span>
              </span>
            </Link>
          );
        })}
      </div>

      <div className="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-3">
        <div className="rounded-xl border border-gray-200 bg-white p-3 sm:p-4">
          <div className="mb-2 flex items-center gap-2">
            <Users className="h-4 w-4 text-purple-500" />
            <span className="text-xs text-gray-500">我的家族</span>
          </div>
          <div className="text-xl font-bold text-gray-900 sm:text-2xl">{stats.families}</div>
        </div>
        <div className="rounded-xl border border-gray-200 bg-white p-3 sm:p-4">
          <div className="mb-2 flex items-center gap-2">
            <Layers className="h-4 w-4 text-blue-500" />
            <span className="text-xs text-gray-500">题库数量</span>
          </div>
          <div className="text-xl font-bold text-gray-900 sm:text-2xl">{stats.questions}</div>
        </div>
        <div className="col-span-2 rounded-xl border border-gray-200 bg-white p-3 sm:p-4 lg:col-span-1">
          <div className="mb-2 flex items-center gap-2">
            <Clock className="h-4 w-4 text-green-500" />
            <span className="text-xs text-gray-500">今日可用</span>
          </div>
          <div className="text-xl font-bold text-green-600 sm:text-2xl">∞</div>
        </div>
      </div>

      <section className="mb-4 rounded-xl border border-gray-200 bg-white p-4 sm:p-5">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-gray-900">常用入口</h2>
          <Link href="/dashboard/settings" className="text-xs text-gray-400 hover:text-gray-600">
            设置
          </Link>
        </div>
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 xl:grid-cols-6">
          {quickLinks.map((link) => {
            const Icon = link.icon;
            return (
              <Link
                key={link.href}
                href={link.href}
                className={`flex min-h-14 items-center gap-2 rounded-lg px-3 py-2 text-sm transition-colors sm:flex-col sm:justify-center sm:gap-1 sm:text-center ${link.color}`}
              >
                <Icon className="h-4 w-4 shrink-0" />
                <span className="truncate">{link.label}</span>
              </Link>
            );
          })}
        </div>
      </section>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[1.25fr_0.75fr]">
        {/* 核心功能 */}
        <div className="rounded-xl border border-gray-200 bg-white p-4 sm:p-5">
          <h3 className="font-semibold text-gray-900 mb-3 text-sm">核心功能</h3>
          <div className="space-y-2">
            {[
              { icon: GraduationCap, title: 'AI家教', desc: '苏格拉底式讲题 · 自适应学习', href: '/dashboard/tutor', color: 'bg-blue-500' },
              { icon: ClipboardList, title: '测试生成', desc: '按范围 · 难度 · 数量生成测试', href: '/dashboard/test', color: 'bg-indigo-500' },
              { icon: BarChart3, title: '学力评估', desc: '知识图谱 · 掌握概率可视化', href: '/dashboard/assessment', color: 'bg-green-500' },
              { icon: BookOpen, title: '题库/知识库', desc: '测试题源 · 多学科与家族经验资源', href: '/dashboard/knowledge', color: 'bg-orange-500' },
            ].map((f) => (
              <Link key={f.href} href={f.href}
                className="flex items-center gap-3 p-3 rounded-lg hover:bg-gray-50 transition-colors group">
                <div className={`${f.color} p-2 rounded-lg`}>
                  <f.icon className="w-4 h-4 text-white" />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="text-sm font-medium text-gray-900">{f.title}</div>
                  <div className="text-xs text-gray-400">{f.desc}</div>
                </div>
                <ArrowRight className="w-4 h-4 text-gray-300 group-hover:text-blue-500 shrink-0" />
              </Link>
            ))}
          </div>
        </div>

        {/* 我的家族 */}
        <div className="rounded-xl border border-gray-200 bg-white p-4 sm:p-5">
          <div className="flex items-center justify-between mb-3">
            <h3 className="font-semibold text-gray-900 text-sm">我的家族</h3>
            <Link href="/dashboard/family" className="text-xs text-blue-600 hover:underline">
              管理
            </Link>
          </div>
          {families.length === 0 ? (
            <div className="text-center py-6">
              <Users className="w-8 h-8 text-gray-200 mx-auto mb-2" />
              <p className="text-sm text-gray-400 mb-3">还没有加入家族</p>
              <Link href="/dashboard/family"
                className="px-4 py-2 bg-blue-600 text-white text-xs rounded-lg hover:bg-blue-700">
                创建第一个家族
              </Link>
            </div>
          ) : (
            <div className="space-y-2">
              {families.map((f) => (
                <div key={f.id} className="flex flex-col gap-2 p-3 bg-gray-50 rounded-lg sm:flex-row sm:items-center sm:justify-between">
                  <div className="flex min-w-0 items-center gap-2">
                    <div className="w-8 h-8 bg-purple-100 text-purple-600 rounded-lg flex items-center justify-center text-sm font-bold">
                      {f.name.charAt(0)}
                    </div>
                    <span className="truncate text-sm font-medium text-gray-900">{f.name}</span>
                  </div>
                  {f.inviteCode && (
                    <span className="text-[10px] text-gray-400 font-mono">{f.inviteCode}</span>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
