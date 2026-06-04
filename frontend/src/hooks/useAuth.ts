/**
 * 认证 Hook
 */
'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/stores/authStore';
import { userApi } from '@/lib/api';

export function useAuth(requireAuth: boolean = true) {
  const router = useRouter();
  const { user, isAuthenticated, isLoading, setUser, setLoading, logout } =
    useAuthStore();

  useEffect(() => {
    if (isAuthenticated && !user) {
      // 有token但没用户信息，拉取
      setLoading(true);
      userApi
        .getMe()
        .then((u) => {
          setUser(u);
        })
        .catch(() => {
          logout();
          if (requireAuth) router.push('/login');
        })
        .finally(() => setLoading(false));
    }

    if (!isAuthenticated && requireAuth) {
      router.push('/login');
    }
  }, [isAuthenticated, user, requireAuth, router, setUser, setLoading, logout]);

  return {
    user,
    isAuthenticated,
    isLoading,
    logout,
  };
}
