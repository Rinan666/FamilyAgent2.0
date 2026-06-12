'use client';

import { useAuth } from '@/hooks/useAuth';
import { useViewerRole } from '@/hooks/useViewerRole';
import Sidebar, { MobileBottomNav, MobileNav } from '@/components/layout/Sidebar';
import { isPlatformAdmin } from '@/lib/roles';

function LoadingShell() {
  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <div className="rounded-full border border-white/70 bg-white/85 px-5 py-3 text-sm text-stone-500 shadow-[0_18px_48px_rgba(24,39,32,0.08)] backdrop-blur">
        正在进入家庭空间...
      </div>
    </div>
  );
}

export default function DashboardShell({ children }: { children: React.ReactNode }) {
  const { user, isLoading } = useAuth(true);
  const {
    viewerRole,
    isLoading: isRoleLoading,
  } = useViewerRole();
  const platformAdmin = isPlatformAdmin(user);

  if (isLoading || isRoleLoading || !user) {
    return <LoadingShell />;
  }

  return (
    <div className="relative flex h-dvh overflow-hidden text-stone-900">
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_top_left,rgba(26,127,95,0.08),transparent_24%),radial-gradient(circle_at_top_right,rgba(181,150,83,0.08),transparent_22%),linear-gradient(180deg,#f7f5ef_0%,#f4f6f2_45%,#f7f7f4_100%)]" />
      <Sidebar viewerRole={viewerRole} isPlatformAdmin={platformAdmin} className="relative z-10" />
      <div className="relative z-10 flex min-w-0 flex-1 flex-col overflow-hidden">
        <div className="fixed left-4 top-4 z-30 lg:hidden">
          <MobileNav viewerRole={viewerRole} isPlatformAdmin={platformAdmin} />
        </div>
        <main className="min-h-0 min-w-0 flex-1 overflow-y-auto px-4 pb-28 pt-16 sm:px-5 lg:px-6 lg:pb-6 lg:pt-4">
          <div className="mx-auto w-full max-w-[1440px]">{children}</div>
        </main>
      </div>
      <MobileBottomNav viewerRole={viewerRole} />
    </div>
  );
}
