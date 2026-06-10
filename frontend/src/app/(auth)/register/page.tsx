'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { NotebookPen, ShieldCheck, Sparkles, Users } from 'lucide-react';
import { userApi } from '@/lib/api';

const highlights = [
  { icon: NotebookPen, text: 'Start with a family diary and preserve real life moments that can be passed on.' },
  { icon: Users, text: 'Bring family members into one shared space instead of scattered chats.' },
  { icon: ShieldCheck, text: 'Permissions come before AI, and memory boundaries stay protected by default.' },
];

export default function RegisterPage() {
  const router = useRouter();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [inviteCode, setInviteCode] = useState('');
  const [nickname, setNickname] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (event?: React.FormEvent) => {
    event?.preventDefault();
    setError('');
    setLoading(true);

    try {
      await userApi.register({
        username: username.trim(),
        password,
        inviteCode: inviteCode.trim().toUpperCase(),
        nickname: nickname.trim() || undefined,
      });
      router.push('/login?registered=true');
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Registration failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex min-h-dvh items-center justify-center bg-[linear-gradient(135deg,#f7faf9_0%,#eef6f3_50%,#f4f7fb_100%)] px-4 py-6">
      <div className="grid w-full max-w-5xl overflow-hidden rounded-2xl border border-emerald-100 bg-white/95 shadow-xl shadow-emerald-950/5 lg:grid-cols-[0.96fr_1.04fr]">
        <section className="hidden min-h-[480px] flex-col justify-between border-r border-emerald-100 bg-emerald-50/80 p-8 text-gray-900 lg:flex">
          <div>
            <div className="mb-5 inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-white/80 px-3 py-1 text-xs font-medium text-emerald-800">
              <Sparkles className="h-3.5 w-3.5" />
              Invite code required
            </div>
            <h1 className="text-3xl font-bold leading-tight">Create a FamilyAgent account</h1>
            <p className="mt-4 max-w-md text-sm leading-6 text-gray-600">
              The account is just the doorway. The important part is the memory, experience, care, and legacy inside the family space.
            </p>
          </div>

          <div className="space-y-3">
            {highlights.map((item) => {
              const Icon = item.icon;
              return (
                <div
                  key={item.text}
                  className="flex items-center gap-3 rounded-xl border border-emerald-100 bg-white/75 px-4 py-3 text-sm text-gray-700"
                >
                  <Icon className="h-4 w-4 shrink-0 text-emerald-700" />
                  <span>{item.text}</span>
                </div>
              );
            })}
          </div>
        </section>

        <section className="p-6 sm:p-8">
          <div className="mb-7">
            <h2 className="text-2xl font-bold text-gray-900">Create account</h2>
            <p className="mt-1 text-sm text-gray-500">Enter a valid invite code to create or join a family space.</p>
          </div>

          <form action="javascript:void(0)" onSubmit={handleSubmit} className="space-y-4">
            {error && (
              <div className="rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">
                {error}
              </div>
            )}

            <div>
              <label htmlFor="register-username" className="mb-1 block text-sm font-medium text-gray-700">
                Username
              </label>
              <input
                id="register-username"
                name="username"
                type="text"
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                className="w-full rounded-xl border border-gray-200 bg-white px-4 py-2.5 outline-none transition focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
                placeholder="3-50 characters"
                required
                minLength={3}
                maxLength={50}
              />
            </div>

            <div>
              <label htmlFor="register-invite-code" className="mb-1 block text-sm font-medium text-gray-700">
                Invite code
              </label>
              <input
                id="register-invite-code"
                name="inviteCode"
                type="text"
                value={inviteCode}
                onChange={(event) => setInviteCode(event.target.value.toUpperCase())}
                className="w-full rounded-xl border border-gray-200 bg-white px-4 py-2.5 uppercase outline-none transition focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
                placeholder="Enter invite code"
                required
                maxLength={50}
              />
            </div>

            <div>
              <label htmlFor="register-nickname" className="mb-1 block text-sm font-medium text-gray-700">
                Nickname
              </label>
              <input
                id="register-nickname"
                name="nickname"
                type="text"
                value={nickname}
                onChange={(event) => setNickname(event.target.value)}
                className="w-full rounded-xl border border-gray-200 bg-white px-4 py-2.5 outline-none transition focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
                placeholder="Optional, defaults to username"
              />
            </div>

            <div>
              <label htmlFor="register-password" className="mb-1 block text-sm font-medium text-gray-700">
                Password
              </label>
              <input
                id="register-password"
                name="password"
                type="password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                className="w-full rounded-xl border border-gray-200 bg-white px-4 py-2.5 outline-none transition focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
                placeholder="At least 6 characters"
                required
                minLength={6}
              />
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full rounded-xl bg-emerald-700 py-2.5 font-medium text-white transition-colors hover:bg-emerald-800 disabled:opacity-50"
            >
              {loading ? 'Creating account...' : 'Create account'}
            </button>
          </form>

          <p className="mt-6 text-center text-sm text-gray-500">
            Already have an account?{' '}
            <Link href="/login" className="font-medium text-emerald-700 hover:underline">
              Sign in now
            </Link>
          </p>
        </section>
      </div>
    </div>
  );
}
