'use client';

import { useAuth } from '@/hooks/useAuth';
import { useViewerRole } from '@/hooks/useViewerRole';
import Sidebar, { MobileBottomNav, MobileNav } from '@/components/layout/Sidebar';
import Header from '@/components/layout/Header';
import { isPlatformAdmin } from '@/lib/roles';

function LoadingShell() {
  return (
    <div className="min-h-screen flex items-center justify-center">
      <div className="text-gray-500">加载中...</div>
    </div>
  );
}

export default function DashboardShell({ children }: { children: React.ReactNode }) {
  const { user, isLoading } = useAuth(true);
  const {
    viewerRole,
    families,
    activeFamilyId,
    activeMembership,
    setActiveFamilyId,
    isLoading: isRoleLoading,
  } = useViewerRole();
  const platformAdmin = isPlatformAdmin(user);

  if (isLoading || isRoleLoading || !user) {
    return <LoadingShell />;
  }

  return (
    <div className="flex min-h-dvh bg-gray-50">
      <Sidebar viewerRole={viewerRole} isPlatformAdmin={platformAdmin} />
      <div className="flex min-w-0 flex-1 flex-col">
        <Header
          user={user}
          viewerRole={viewerRole}
          families={families}
          activeFamilyId={activeFamilyId}
          activeMembership={activeMembership}
          setActiveFamilyId={setActiveFamilyId}
          mobileNav={<MobileNav viewerRole={viewerRole} isPlatformAdmin={platformAdmin} />}
        />
        <main className="min-w-0 flex-1 bg-gray-50 px-4 pb-28 pt-5 sm:px-5 lg:p-6">{children}</main>
      </div>
      <MobileBottomNav viewerRole={viewerRole} />
    </div>
  );
}
