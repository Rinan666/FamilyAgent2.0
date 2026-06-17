'use client';

import { useAuth } from '@/hooks/useAuth';
import { useViewerRole } from '@/hooks/useViewerRole';
import Sidebar, { MobileBottomNav, MobileNav } from '@/components/layout/Sidebar';
import { isPlatformAdmin } from '@/lib/roles';

function LoadingShell() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-stone-50 px-4">
      <div className="rounded-md border border-stone-200 bg-white px-4 py-2 text-sm text-stone-500">
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
    <div className="relative flex h-dvh overflow-hidden bg-stone-50 text-stone-900">
      <Sidebar viewerRole={viewerRole} isPlatformAdmin={platformAdmin} className="relative z-10" />
      <div className="relative z-10 flex min-w-0 flex-1 flex-col overflow-hidden">
        <div className="fixed left-4 top-4 z-30 lg:hidden">
          <MobileNav viewerRole={viewerRole} isPlatformAdmin={platformAdmin} />
        </div>
        <main className="min-h-0 min-w-0 flex-1 overflow-y-auto px-3 pb-24 pt-14 sm:px-4 lg:px-4 lg:pb-4 lg:pt-4">
          <div className="mx-auto w-full max-w-[1600px]">{children}</div>
        </main>
      </div>
      <MobileBottomNav viewerRole={viewerRole} />
    </div>
  );
}
