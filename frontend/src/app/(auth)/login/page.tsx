'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { BookHeart, Brain, LockKeyhole, Sparkles, Users } from 'lucide-react';
import { userApi } from '@/lib/api';
import { useAuthStore } from '@/stores/authStore';

const highlights = [
  { icon: BookHeart, text: '记录人生事件、家庭日记和成长观察' },
  { icon: Users, text: '把长辈经验沉淀成家族自己的知识库' },
  { icon: Brain, text: '在授权范围内，让 AI 更懂这个家庭' },
];

export default function LoginPage() {
  const router = useRouter();
  const login = useAuthStore((s) => s.login);

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (window.location.search.includes('registered=true')) {
      setNotice('注册成功，请登录后进入家族空间。');
    }
  }, []);

  const handleSubmit = async (e?: React.FormEvent) => {
    e?.preventDefault();
    setError('');
    setLoading(true);

    try {
      const result = await userApi.login({ username, password });
      login(
        {
          id: result.userId,
          username: result.username,
          nickname: result.nickname,
          avatarUrl: result.avatarUrl,
          role: result.role || 'USER',
          status: result.status || 'ACTIVE',
          birthDate: result.birthDate,
          birthYear: result.birthYear,
          metadata: result.metadata,
        },
        result.token,
      );
      router.push('/dashboard');
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : '登录失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex min-h-dvh items-center justify-center bg-[linear-gradient(135deg,#f7faf9_0%,#eef6f3_48%,#f4f7fb_100%)] px-4 py-6">
      <div className="grid w-full max-w-5xl overflow-hidden rounded-lg border border-emerald-100 bg-white/95 shadow-lg shadow-emerald-900/5 lg:grid-cols-[1.04fr_0.96fr]">
        <section className="flex min-h-[420px] flex-col justify-between border-b border-emerald-100 bg-emerald-50/80 p-7 text-gray-900 sm:p-9 lg:border-b-0 lg:border-r">
          <div>
            <div className="mb-5 inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-white/70 px-3 py-1 text-xs font-medium text-emerald-800">
              <Sparkles className="h-3.5 w-3.5" />
              家族长期记忆 · 家族经验传承
            </div>
            <h1 className="max-w-xl text-3xl font-bold leading-tight sm:text-4xl">
              FamilyAgent
            </h1>
            <p className="mt-4 max-w-lg text-sm leading-6 text-gray-600 sm:text-base">
              为家庭沉淀日记、长辈经验、成长观察和成员之间的理解，让 AI 不只是回答问题，而是逐渐理解这个家庭。
            </p>
          </div>

          <div className="space-y-3">
            {highlights.map((item) => {
              const Icon = item.icon;
              return (
                <div key={item.text} className="flex items-center gap-3 rounded-lg border border-emerald-100 bg-white/70 px-3 py-2.5 text-sm text-gray-700">
                  <Icon className="h-4 w-4 shrink-0 text-emerald-700" />
                  <span>{item.text}</span>
                </div>
              );
            })}
          </div>
        </section>

        <section className="p-6 sm:p-8">
          <div className="mb-7">
            <div className="mb-3 flex h-11 w-11 items-center justify-center rounded-lg bg-emerald-50 text-emerald-700">
              <LockKeyhole className="h-5 w-5" />
            </div>
            <h2 className="text-2xl font-bold text-gray-900">登录</h2>
            <p className="mt-1 text-sm text-gray-500">
              进入你的家族空间，继续记录和整理家族记忆。
            </p>
          </div>

          <form action="javascript:void(0)" onSubmit={handleSubmit} className="space-y-4">
            {error && (
              <div className="rounded-lg bg-red-50 px-4 py-2 text-sm text-red-600">
                {error}
              </div>
            )}
            {notice && (
              <div className="rounded-lg bg-green-50 px-4 py-2 text-sm text-green-700">
                {notice}
              </div>
            )}

            <div>
              <label className="mb-1 block text-sm font-medium text-gray-700">
                用户名
              </label>
              <input
                id="login-username"
                name="username"
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                className="w-full rounded-lg border border-gray-200 bg-white px-4 py-2.5 outline-none transition focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
                placeholder="请输入用户名"
                required
              />
            </div>

            <div>
              <label className="mb-1 block text-sm font-medium text-gray-700">
                密码
              </label>
              <input
                id="login-password"
                name="password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full rounded-lg border border-gray-200 bg-white px-4 py-2.5 outline-none transition focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
                placeholder="请输入密码"
                required
              />
            </div>

            <button
              type="button"
              onClick={() => void handleSubmit()}
              disabled={loading}
              className="w-full rounded-lg bg-emerald-700 py-2.5 font-medium text-white transition-colors hover:bg-emerald-800 disabled:opacity-50"
            >
              {loading ? '登录中...' : '登录'}
            </button>
          </form>

          <p className="mt-6 text-center text-sm text-gray-500">
            还没有账号？{' '}
            <Link href="/register" className="font-medium text-emerald-700 hover:underline">
              创建账号
            </Link>
          </p>
        </section>
      </div>
    </div>
  );
}
