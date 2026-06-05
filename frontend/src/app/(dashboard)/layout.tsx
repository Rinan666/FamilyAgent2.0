'use client';

import { useEffect, useMemo, useState } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { useAuth } from '@/hooks/useAuth';
import Sidebar, { MobileBottomNav, MobileNav } from '@/components/layout/Sidebar';
import Header from '@/components/layout/Header';
import { assessmentApi } from '@/lib/api';
import { isDiagnosisCompletedLocally, markDiagnosisCompletedLocally } from '@/lib/diagnosis';

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { user, isLoading } = useAuth(true);
  const userId = user?.id;
  const pathname = usePathname();
  const router = useRouter();
  const [hasCompletedDiagnosis, setHasCompletedDiagnosis] = useState(false);
  const [isCheckingDiagnosis, setIsCheckingDiagnosis] = useState(true);

  const isDiagnosisRequiredRoute = useMemo(() => {
    const allowedPrefixes = [
      '/dashboard/test',
    ];
    return pathname === '/dashboard' || !allowedPrefixes.some((prefix) => pathname.startsWith(prefix));
  }, [pathname]);

  useEffect(() => {
    if (!userId) return;

    let cancelled = false;
    const locallyCompleted = isDiagnosisCompletedLocally(userId);
    setHasCompletedDiagnosis(locallyCompleted);
    setIsCheckingDiagnosis(!locallyCompleted);

    assessmentApi.getHistory(undefined, 1)
      .then((records) => {
        if (!cancelled) {
          const completed = (records || []).length > 0;
          setHasCompletedDiagnosis(completed);
          if (completed) markDiagnosisCompletedLocally(userId);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setHasCompletedDiagnosis(isDiagnosisCompletedLocally(userId));
        }
      })
      .finally(() => {
        if (!cancelled) setIsCheckingDiagnosis(false);
      });

    return () => {
      cancelled = true;
    };
  }, [userId]);

  useEffect(() => {
    const unlockDiagnosis = (event: Event) => {
      const detail = (event as CustomEvent<{ userId?: number }>).detail;
      if (!detail?.userId || detail.userId === userId) {
        setHasCompletedDiagnosis(true);
      }
    };
    window.addEventListener('familyagent:diagnosis-completed', unlockDiagnosis);
    return () => window.removeEventListener('familyagent:diagnosis-completed', unlockDiagnosis);
  }, [userId]);

  useEffect(() => {
    if (isLoading || isCheckingDiagnosis || !user) return;
    if (!hasCompletedDiagnosis && isDiagnosisRequiredRoute) {
      router.replace('/dashboard/test?required=1');
    }
  }, [hasCompletedDiagnosis, isCheckingDiagnosis, isDiagnosisRequiredRoute, isLoading, router, user]);

  if (isLoading || !user) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-gray-500">加载中...</div>
      </div>
    );
  }

  return (
    <div className="flex min-h-dvh bg-gray-50">
      <Sidebar diagnosisLocked={!isCheckingDiagnosis && !hasCompletedDiagnosis} />
      <div className="flex min-w-0 flex-1 flex-col">
        <Header
          user={user}
          mobileNav={<MobileNav diagnosisLocked={!isCheckingDiagnosis && !hasCompletedDiagnosis} />}
        />
        <main className="min-w-0 flex-1 bg-gray-50 px-4 pb-28 pt-5 sm:px-5 lg:p-6">{children}</main>
      </div>
      <MobileBottomNav diagnosisLocked={!isCheckingDiagnosis && !hasCompletedDiagnosis} />
    </div>
  );
}
