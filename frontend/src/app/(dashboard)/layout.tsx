'use client';

import { useEffect, useState } from 'react';
import DashboardShell from '@/components/layout/DashboardShell';

function LoadingShell() {
  return (
    <div className="min-h-screen flex items-center justify-center">
      <div className="text-gray-500">加载中...</div>
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
