'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import { useAuthStore } from '@/stores/authStore';
import { familyApi, questionApi } from '@/lib/api';
import {
  GraduationCap, BarChart3, Users, BookOpen, ArrowRight,
  TrendingUp, Layers, Clock,
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
    { href: '/dashboard/tutor', label: '开始学习', icon: '📝', color: 'bg-blue-50 text-blue-700 hover:bg-blue-100' },
    { href: '/dashboard/notebook', label: '错题本', icon: '📒', color: 'bg-red-50 text-red-700 hover:bg-red-100' },
    { href: '/dashboard/assessment', label: '查看评估', icon: '📊', color: 'bg-green-50 text-green-700 hover:bg-green-100' },
    { href: '/dashboard/family', label: '管理家族', icon: '👨‍👩‍👧‍👦', color: 'bg-purple-50 text-purple-700 hover:bg-purple-100' },
    { href: '/dashboard/knowledge', label: '知识库', icon: '📚', color: 'bg-orange-50 text-orange-700 hover:bg-orange-100' },
  ];

  return (
    <div className="max-w-5xl mx-auto">
      {/* 欢迎 */}
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">
          你好，{user?.nickname || user?.username || '用户'}
        </h1>
        <p className="text-gray-500 mt-1">
          让 AI 家教陪伴每个家庭的成长
        </p>
      </div>

      {/* 统计卡片 */}
      <div className="grid grid-cols-3 gap-4 mb-6">
        <div className="bg-white rounded-xl border border-gray-200 p-4">
          <div className="flex items-center gap-2 mb-2">
            <Users className="w-4 h-4 text-purple-500" />
            <span className="text-xs text-gray-500">我的家族</span>
          </div>
          <div className="text-2xl font-bold text-gray-900">{stats.families}</div>
        </div>
        <div className="bg-white rounded-xl border border-gray-200 p-4">
          <div className="flex items-center gap-2 mb-2">
            <Layers className="w-4 h-4 text-blue-500" />
            <span className="text-xs text-gray-500">题库数量</span>
          </div>
          <div className="text-2xl font-bold text-gray-900">{stats.questions}</div>
        </div>
        <div className="bg-white rounded-xl border border-gray-200 p-4">
          <div className="flex items-center gap-2 mb-2">
            <Clock className="w-4 h-4 text-green-500" />
            <span className="text-xs text-gray-500">今日可用</span>
          </div>
          <div className="text-2xl font-bold text-green-600">∞</div>
        </div>
      </div>

      {/* 快速操作 */}
      <div className="bg-white rounded-xl border border-gray-200 p-5 mb-6">
        <h3 className="font-semibold text-gray-900 mb-3 text-sm">快速开始</h3>
        <div className="grid grid-cols-2 md:grid-cols-5 gap-3">
          {quickLinks.map((link) => (
            <Link key={link.href} href={link.href}
              className={`p-3 rounded-lg text-sm text-center transition-colors ${link.color}`}>
              <span className="block text-lg mb-1">{link.icon}</span>
              {link.label}
            </Link>
          ))}
        </div>
      </div>

      {/* 功能卡片 + 家族列表 */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* 核心功能 */}
        <div className="bg-white rounded-xl border border-gray-200 p-5">
          <h3 className="font-semibold text-gray-900 mb-3 text-sm">核心功能</h3>
          <div className="space-y-2">
            {[
              { icon: GraduationCap, title: 'AI家教', desc: '苏格拉底式讲题 · 自适应学习', href: '/dashboard/tutor', color: 'bg-blue-500' },
              { icon: BarChart3, title: '学力评估', desc: '知识图谱 · 掌握概率可视化', href: '/dashboard/assessment', color: 'bg-green-500' },
              { icon: BookOpen, title: '知识库', desc: '家族智慧沉淀与传承', href: '/dashboard/knowledge', color: 'bg-orange-500' },
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
        <div className="bg-white rounded-xl border border-gray-200 p-5">
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
                <div key={f.id} className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                  <div className="flex items-center gap-2">
                    <div className="w-8 h-8 bg-purple-100 text-purple-600 rounded-lg flex items-center justify-center text-sm font-bold">
                      {f.name.charAt(0)}
                    </div>
                    <span className="text-sm font-medium text-gray-900">{f.name}</span>
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
