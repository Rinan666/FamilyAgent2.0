'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { NotebookPen, ShieldCheck, Sparkles, Users } from 'lucide-react';
import { userApi } from '@/lib/api';

const highlights = [
  { icon: NotebookPen, text: '从家庭日记开始，把真实生活片段沉淀成可以传下去的内容。' },
  { icon: Users, text: '让家人进入同一个共享空间，而不是散落在各处聊天记录里。' },
  { icon: ShieldCheck, text: '先有权限边界，再有 AI 使用，记忆默认受到保护。' },
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
      setError(err instanceof Error ? err.message : '注册失败，请稍后重试');
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
              需要邀请码
            </div>
            <h1 className="text-3xl font-bold leading-tight">注册 FamilyAgent 账号</h1>
            <p className="mt-4 max-w-md text-sm leading-6 text-gray-600">
              账号只是入口，真正重要的是家庭空间里沉淀下来的记忆、经验、照护与传承。
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
            <h2 className="text-2xl font-bold text-gray-900">创建账号</h2>
            <p className="mt-1 text-sm text-gray-500">输入有效的邀请码，创建或加入一个家庭空间。</p>
          </div>

          <form action="javascript:void(0)" onSubmit={handleSubmit} className="space-y-4">
            {error && (
              <div className="rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">
                {error}
              </div>
            )}

            <div>
              <label htmlFor="register-username" className="mb-1 block text-sm font-medium text-gray-700">
                用户名
              </label>
              <input
                id="register-username"
                name="username"
                type="text"
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                className="w-full rounded-xl border border-gray-200 bg-white px-4 py-2.5 outline-none transition focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
                placeholder="3-50 个字符"
                required
                minLength={3}
                maxLength={50}
              />
            </div>

            <div>
              <label htmlFor="register-invite-code" className="mb-1 block text-sm font-medium text-gray-700">
                邀请码
              </label>
              <input
                id="register-invite-code"
                name="inviteCode"
                type="text"
                value={inviteCode}
                onChange={(event) => setInviteCode(event.target.value.toUpperCase())}
                className="w-full rounded-xl border border-gray-200 bg-white px-4 py-2.5 uppercase outline-none transition focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
                placeholder="请输入邀请码"
                required
                maxLength={50}
              />
            </div>

            <div>
              <label htmlFor="register-nickname" className="mb-1 block text-sm font-medium text-gray-700">
                昵称
              </label>
              <input
                id="register-nickname"
                name="nickname"
                type="text"
                value={nickname}
                onChange={(event) => setNickname(event.target.value)}
                className="w-full rounded-xl border border-gray-200 bg-white px-4 py-2.5 outline-none transition focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
                placeholder="可选，默认使用用户名"
              />
            </div>

            <div>
              <label htmlFor="register-password" className="mb-1 block text-sm font-medium text-gray-700">
                密码
              </label>
              <input
                id="register-password"
                name="password"
                type="password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                className="w-full rounded-xl border border-gray-200 bg-white px-4 py-2.5 outline-none transition focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
                placeholder="至少 6 个字符"
                required
                minLength={6}
              />
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full rounded-xl bg-emerald-700 py-2.5 font-medium text-white transition-colors hover:bg-emerald-800 disabled:opacity-50"
            >
              {loading ? '创建中...' : '创建账号'}
            </button>
          </form>

          <p className="mt-6 text-center text-sm text-gray-500">
            已有账号？{' '}
            <Link href="/login" className="font-medium text-emerald-700 hover:underline">
              去登录
            </Link>
          </p>
        </section>
      </div>
    </div>
  );
}
