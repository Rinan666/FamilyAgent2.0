'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { BookHeart, Brain, LockKeyhole, Sparkles, Users } from 'lucide-react';
import { userApi } from '@/lib/api';
import { useAuthStore } from '@/stores/authStore';

const highlights = [
  { icon: BookHeart, text: 'Capture life events, family diaries, and growth observations.' },
  { icon: Users, text: 'Turn elder experience into a family knowledge base.' },
  { icon: Brain, text: 'Let AI understand the family better within the granted scope.' },
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
      setNotice('Registration completed. Sign in to enter your family space.');
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
      setError(err instanceof Error ? err.message : 'Login failed');
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
              Long-term family memory · Shared family wisdom
            </div>
            <h1 className="max-w-xl text-3xl font-bold leading-tight sm:text-4xl">
              FamilyAgent
            </h1>
            <p className="mt-4 max-w-lg text-sm leading-6 text-gray-600 sm:text-base">
              Build diaries, elder experience, growth observations, and mutual understanding into one shared family context so AI does more than answer questions.
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
            <h2 className="text-2xl font-bold text-gray-900">Sign in</h2>
            <p className="mt-1 text-sm text-gray-500">
              Enter your family space and continue organizing family memory.
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
                Username
              </label>
              <input
                id="login-username"
                name="username"
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                className="w-full rounded-lg border border-gray-200 bg-white px-4 py-2.5 outline-none transition focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
                placeholder="Enter username"
                required
              />
            </div>

            <div>
              <label className="mb-1 block text-sm font-medium text-gray-700">
                Password
              </label>
              <input
                id="login-password"
                name="password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full rounded-lg border border-gray-200 bg-white px-4 py-2.5 outline-none transition focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
                placeholder="Enter password"
                required
              />
            </div>

            <button
              type="button"
              onClick={() => void handleSubmit()}
              disabled={loading}
              className="w-full rounded-lg bg-emerald-700 py-2.5 font-medium text-white transition-colors hover:bg-emerald-800 disabled:opacity-50"
            >
              {loading ? 'Signing in...' : 'Sign in'}
            </button>
          </form>

          <p className="mt-6 text-center text-sm text-gray-500">
            No account yet?{' '}
            <Link href="/register" className="font-medium text-emerald-700 hover:underline">
              Create one
            </Link>
          </p>
        </section>
      </div>
    </div>
  );
}
