'use client';

import { useEffect, useState } from 'react';
import DashboardShell from '@/components/layout/DashboardShell';

function LoadingShell() {
  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <div className="rounded-full border border-white/70 bg-white/85 px-5 py-3 text-sm text-stone-500 shadow-[0_18px_48px_rgba(24,39,32,0.08)] backdrop-blur">
        正在准备工作区...
      </div>
    </div>
  );
}

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  if (!mounted) {
    return <LoadingShell />;
  }

  return <DashboardShell>{children}</DashboardShell>;
}
