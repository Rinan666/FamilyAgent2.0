'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { BookHeart, Brain, LockKeyhole, UserRound, Users } from 'lucide-react';
import AuthShell, {
  authInputIconClassName,
  authInputWithIconClassName,
  authLabelClassName,
  authPrimaryButtonClassName,
} from '@/components/auth/AuthShell';
import { submitFormOnEnter } from '@/lib/formKeyboard';
import { toAuthUser } from '@/lib/auth';
import { userApi } from '@/lib/api';
import { useAuthStore } from '@/stores/authStore';

const highlights = [
  {
    icon: BookHeart,
    title: '把日常留下来',
  },
  {
    icon: Users,
    title: '让家人共享同一语境',
  },
  {
    icon: Brain,
    title: '让 AI 真正理解家庭',
  },
] as const;

export default function LoginPage() {
  const router = useRouter();
  const login = useAuthStore((state) => state.login);

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (window.location.search.includes('registered=true')) {
      setNotice('注册完成，请登录后进入你的家庭空间。');
    }
  }, []);

  const handleSubmit = async (event?: React.FormEvent) => {
    event?.preventDefault();
    if (loading) return;

    setError('');
    setLoading(true);

    try {
      const result = await userApi.login({ username, password });
      login(toAuthUser(result), result.token);
      router.push('/dashboard');
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : '登录失败，请稍后重试。');
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthShell
      badge="家庭记忆"
      heroTitle="让每一段重要经历，都有稳定的落点。"
      highlights={highlights}
      formTitle="欢迎回来"
      footer={
        <p className="text-center">
          还没有账号？{' '}
          <Link
            href="/register"
            className="font-semibold text-sky-700 transition hover:text-sky-800"
          >
            立即注册
          </Link>
        </p>
      }
    >
      <form onSubmit={handleSubmit} onKeyDown={submitFormOnEnter} className="space-y-4">
        {error && (
          <div className="rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">
            {error}
          </div>
        )}
        {notice && (
          <div className="rounded-xl border border-sky-100 bg-sky-50 px-4 py-3 text-sm text-sky-700">
            {notice}
          </div>
        )}

        <div>
          <label htmlFor="login-username" className={authLabelClassName}>
            用户名
          </label>
          <div className="relative">
            <UserRound className={authInputIconClassName} />
            <input
              id="login-username"
              name="username"
              type="text"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              className={authInputWithIconClassName}
              placeholder="请输入用户名"
              autoComplete="username"
              required
            />
          </div>
        </div>

        <div>
          <label htmlFor="login-password" className={authLabelClassName}>
            密码
          </label>
          <div className="relative">
            <LockKeyhole className={authInputIconClassName} />
            <input
              id="login-password"
              name="password"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              className={authInputWithIconClassName}
              placeholder="请输入密码"
              autoComplete="current-password"
              required
            />
          </div>
        </div>

        <button
          type="submit"
          data-testid="login-submit"
          disabled={loading}
          className={authPrimaryButtonClassName}
        >
          {loading ? '登录中...' : '登录'}
        </button>
      </form>
    </AuthShell>
  );
}
