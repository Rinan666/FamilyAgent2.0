'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { LockKeyhole, NotebookPen, ShieldCheck, Ticket, UserRound, Users } from 'lucide-react';
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
    icon: NotebookPen,
    title: '从真实生活开始',
    description: '先把记忆、日记和观察沉淀下来，再逐步形成可协作、可延续的家庭内容。',
  },
  {
    icon: Users,
    title: '把家人放进同一空间',
    description: '邀请成员一起维护共同语境，而不是让信息继续散落在不同聊天窗口里。',
  },
  {
    icon: ShieldCheck,
    title: '先定义边界，再启用 AI',
    description: '权限和角色先行，默认保护家庭记忆的可见范围与使用方式。',
  },
] as const;

export default function RegisterPage() {
  const router = useRouter();
  const login = useAuthStore((state) => state.login);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [inviteCode, setInviteCode] = useState('');
  const [nickname, setNickname] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (event?: React.FormEvent) => {
    event?.preventDefault();
    if (loading) return;

    setError('');
    setLoading(true);

    try {
      const result = await userApi.register({
        username: username.trim(),
        password,
        inviteCode: inviteCode.trim().toUpperCase(),
        nickname: nickname.trim() || undefined,
      });
      login(toAuthUser(result), result.token);
      router.push('/dashboard');
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : '注册失败，请稍后重试。');
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthShell
      badge="邀请制注册"
      heroTitle="先建立一个可靠的家庭空间，再慢慢填满它。"
      heroDescription="注册只是入口。真正重要的是把记忆、经验、角色关系和长期协作沉淀进同一个稳定的家庭语境。"
      highlights={highlights}
      formTitle="创建账号"
      formDescription="输入有效的邀请码，创建新账号并加入家庭空间。后续的共享范围、身份角色和协作关系都可以继续完善。"
      footer={(
        <p className="text-center">
          已有账号？{' '}
          <Link href="/login" className="font-semibold text-emerald-700 transition hover:text-emerald-800">
            去登录
          </Link>
        </p>
      )}
    >
      <form onSubmit={handleSubmit} onKeyDown={submitFormOnEnter} className="space-y-4">
        {error && (
          <div className="rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-600">
            {error}
          </div>
        )}

        <div>
          <label htmlFor="register-username" className={authLabelClassName}>
            用户名
          </label>
          <div className="relative">
            <UserRound className={authInputIconClassName} />
            <input
              id="register-username"
              name="username"
              type="text"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              className={authInputWithIconClassName}
              placeholder="3 到 50 个字符"
              required
              minLength={3}
              maxLength={50}
            />
          </div>
        </div>

        <div>
          <label htmlFor="register-invite-code" className={authLabelClassName}>
            邀请码
          </label>
          <div className="relative">
            <Ticket className={authInputIconClassName} />
            <input
              id="register-invite-code"
              name="inviteCode"
              type="text"
              value={inviteCode}
              onChange={(event) => setInviteCode(event.target.value.toUpperCase())}
              className={`${authInputWithIconClassName} uppercase`}
              placeholder="请输入邀请码"
              required
              maxLength={50}
            />
          </div>
        </div>

        <div>
          <label htmlFor="register-nickname" className={authLabelClassName}>
            昵称
          </label>
          <div className="relative">
            <Users className={authInputIconClassName} />
            <input
              id="register-nickname"
              name="nickname"
              type="text"
              value={nickname}
              onChange={(event) => setNickname(event.target.value)}
              className={authInputWithIconClassName}
              placeholder="可选，默认使用用户名"
            />
          </div>
        </div>

        <div>
          <label htmlFor="register-password" className={authLabelClassName}>
            密码
          </label>
          <div className="relative">
            <LockKeyhole className={authInputIconClassName} />
            <input
              id="register-password"
              name="password"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              className={authInputWithIconClassName}
              placeholder="至少 6 个字符"
              required
              minLength={6}
            />
          </div>
        </div>

        <button type="submit" disabled={loading} className={authPrimaryButtonClassName}>
          {loading ? '创建中...' : '创建账号'}
        </button>
      </form>
    </AuthShell>
  );
}
