'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { KeyRound, NotebookPen, ShieldCheck, Users } from 'lucide-react';
import { userApi } from '@/lib/api';

const principles = [
  { icon: NotebookPen, text: '从一条人生记录开始沉淀家族记忆' },
  { icon: Users, text: '通过家族空间连接多位家庭成员' },
  { icon: ShieldCheck, text: '按可见范围共享，不默认公开私密记录' },
];

export default function RegisterPage() {
  const router = useRouter();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [nickname, setNickname] = useState('');
  const [inviteCode, setInviteCode] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e?: React.FormEvent) => {
    e?.preventDefault();
    setError('');
    setLoading(true);

    try {
      await userApi.register({ username, password, nickname, inviteCode: inviteCode.trim() });
      router.push('/login?registered=true');
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : '注册失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex min-h-dvh items-center justify-center bg-[linear-gradient(135deg,#f7faf9_0%,#eef6f3_48%,#f4f7fb_100%)] px-4 py-6">
      <div className="grid w-full max-w-5xl overflow-hidden rounded-lg border border-emerald-100 bg-white/95 shadow-lg shadow-emerald-900/5 lg:grid-cols-[0.96fr_1.04fr]">
        <section className="hidden min-h-[460px] flex-col justify-between border-r border-emerald-100 bg-emerald-50/80 p-8 text-gray-900 lg:flex">
          <div>
            <div className="mb-5 inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-white/70 px-3 py-1 text-xs font-medium text-emerald-800">
              <KeyRound className="h-3.5 w-3.5" />
              内测邀请制
            </div>
            <h1 className="text-3xl font-bold leading-tight">
              创建 FamilyAgent 账号
            </h1>
            <p className="mt-4 max-w-md text-sm leading-6 text-gray-600">
              账号只是入口，真正的核心是家族空间。你可以在里面记录家庭日记、整理长辈经验，并授权 AI 参考这些记忆。
            </p>
          </div>

          <div className="space-y-3">
            {principles.map((item) => {
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
            <h2 className="text-2xl font-bold text-gray-900">创建账号</h2>
            <p className="mt-1 text-sm text-gray-500">
              注册后可创建或加入家族空间，开始记录属于这个家庭的软资产。
            </p>
          </div>

          <form action="javascript:void(0)" onSubmit={handleSubmit} className="space-y-4">
            {error && (
              <div className="rounded-lg bg-red-50 px-4 py-2 text-sm text-red-600">
                {error}
              </div>
            )}

            <div>
              <label className="mb-1 block text-sm font-medium text-gray-700">
                用户名
              </label>
              <input
                id="register-username"
                name="username"
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                className="w-full rounded-lg border border-gray-200 bg-white px-4 py-2.5 outline-none transition focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
                placeholder="3-50 个字符"
                required
                minLength={3}
                maxLength={50}
              />
            </div>

            <div>
              <label className="mb-1 block text-sm font-medium text-gray-700">
                昵称
              </label>
              <input
                id="register-nickname"
                name="nickname"
                type="text"
                value={nickname}
                onChange={(e) => setNickname(e.target.value)}
                className="w-full rounded-lg border border-gray-200 bg-white px-4 py-2.5 outline-none transition focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
                placeholder="选填，默认使用用户名"
              />
            </div>

            <div>
              <label className="mb-1 block text-sm font-medium text-gray-700">
                密码
              </label>
              <input
                id="register-password"
                name="password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full rounded-lg border border-gray-200 bg-white px-4 py-2.5 outline-none transition focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
                placeholder="至少 6 个字符"
                required
                minLength={6}
              />
            </div>

            <div>
              <label className="mb-1 block text-sm font-medium text-gray-700">
                邀请码
              </label>
              <input
                id="register-invite-code"
                name="inviteCode"
                type="text"
                value={inviteCode}
                onChange={(e) => setInviteCode(e.target.value.toUpperCase())}
                className="w-full rounded-lg border border-gray-200 bg-white px-4 py-2.5 uppercase outline-none transition focus:border-emerald-500 focus:ring-2 focus:ring-emerald-100"
                placeholder="请输入内测邀请码"
                required
                maxLength={50}
              />
            </div>

            <button
              type="button"
              onClick={() => void handleSubmit()}
              disabled={loading}
              className="w-full rounded-lg bg-emerald-700 py-2.5 font-medium text-white transition-colors hover:bg-emerald-800 disabled:opacity-50"
            >
              {loading ? '注册中...' : '创建账号'}
            </button>
          </form>

          <p className="mt-6 text-center text-sm text-gray-500">
            已有账号？{' '}
            <Link href="/login" className="font-medium text-emerald-700 hover:underline">
              立即登录
            </Link>
          </p>
        </section>
      </div>
    </div>
  );
}
