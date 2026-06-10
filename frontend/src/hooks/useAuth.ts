/**
 * Authentication hook.
 */
'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/stores/authStore';
import { userApi } from '@/lib/api/user';

export function useAuth(requireAuth: boolean = true) {
  const router = useRouter();
  const { user, isAuthenticated, isLoading, hasHydrated, hydrateFromStorage, setUser, setLoading, logout } =
    useAuthStore();

  useEffect(() => {
    hydrateFromStorage();
  }, [hydrateFromStorage]);

  useEffect(() => {
    if (!hasHydrated) return;

    if (!isAuthenticated) {
      setLoading(false);
      if (requireAuth) router.push('/login');
      return;
    }

    if (user && user.role) {
      setLoading(false);
      return;
    }

    let cancelled = false;
    setLoading(true);

    userApi
      .getMe()
      .then((u) => {
        if (!cancelled) setUser(u);
      })
      .catch(() => {
        if (!cancelled) {
          logout();
          if (requireAuth) router.push('/login');
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [hasHydrated, isAuthenticated, user, requireAuth, router, setUser, setLoading, logout]);

  return {
    user,
    isAuthenticated,
    isLoading,
    hasHydrated,
    logout,
  };
}
