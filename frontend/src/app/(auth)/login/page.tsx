'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { BookHeart, Brain, Users } from 'lucide-react';
import AuthShell, {
  authInputClassName,
  authLabelClassName,
  authPrimaryButtonClassName,
} from '@/components/auth/AuthShell';
import { toAuthUser } from '@/lib/auth';
import { userApi } from '@/lib/api';
import { useAuthStore } from '@/stores/authStore';

const highlights = [
  {
    icon: BookHeart,
    title: '把日常留住',
    description: '用日记、片段和观察，慢慢积累属于这个家庭的长期记忆。',
  },
  {
    icon: Users,
    title: '让家人共享同一语境',
    description: '把经验、角色和共同经历沉淀下来，减少信息散落和重复沟通。',
  },
  {
    icon: Brain,
    title: '让 AI 真正理解家庭',
    description: '在授权范围内调用上下文，让每一次协助都更贴近真实关系。',
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
      heroDescription="FamilyAgent 帮你整理家庭日记、成员角色、成长观察与共享知识，让协作和陪伴都回到同一个空间里。"
      highlights={highlights}
      formTitle="欢迎回来"
      formDescription="登录后继续整理家庭记忆、查看协作内容，并在授权范围内获得更懂家庭语境的 AI 协助。"
      footer={(
        <p className="text-center">
          还没有账号？{' '}
          <Link href="/register" className="font-semibold text-emerald-700 transition hover:text-emerald-800">
            立即注册
          </Link>
        </p>
      )}
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        {error && (
          <div className="rounded-2xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">
            {error}
          </div>
        )}
        {notice && (
          <div className="rounded-2xl border border-emerald-100 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
            {notice}
          </div>
        )}

        <div>
          <label htmlFor="login-username" className={authLabelClassName}>
            用户名
          </label>
          <input
            id="login-username"
            name="username"
            type="text"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            className={authInputClassName}
            placeholder="请输入用户名"
            autoComplete="username"
            required
          />
        </div>

        <div>
          <label htmlFor="login-password" className={authLabelClassName}>
            密码
          </label>
          <input
            id="login-password"
            name="password"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            className={authInputClassName}
            placeholder="请输入密码"
            autoComplete="current-password"
            required
          />
        </div>

        <button type="submit" disabled={loading} className={authPrimaryButtonClassName}>
          {loading ? '登录中...' : '登录'}
        </button>
      </form>
    </AuthShell>
  );
}
