'use client';

import { usePathname } from 'next/navigation';
import { useAuth } from '@/hooks/useAuth';
import { useViewerRole } from '@/hooks/useViewerRole';
import Sidebar, { MobileNavDrawer } from '@/components/layout/Sidebar';
import { isPlatformAdmin } from '@/lib/roles';
import { cn } from '@/lib/utils';

function LoadingShell() {
  return (
    <div className="app-shell flex min-h-screen items-center justify-center px-4">
      <div className="glass-panel-strong rounded-2xl px-5 py-3 text-sm text-stone-500">
        正在进入家庭空间...
      </div>
    </div>
  );
}

export default function DashboardShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const { user, isLoading } = useAuth(true);
  const {
    viewerRole,
    isLoading: isRoleLoading,
  } = useViewerRole();
  const platformAdmin = isPlatformAdmin(user);
  const isAgentChat = pathname.startsWith('/dashboard/agent');

  if (isLoading || isRoleLoading || !user) {
    return <LoadingShell />;
  }

  return (
    <div className="app-shell relative flex h-dvh flex-col overflow-hidden text-stone-900">
      <Sidebar viewerRole={viewerRole} isPlatformAdmin={platformAdmin} className="relative z-20" />
      <div className="relative flex min-w-0 flex-1 flex-col overflow-hidden">
        <main className={cn(
          'min-h-0 min-w-0 flex-1 overflow-y-auto px-3 pt-3 sm:px-4 lg:px-4 lg:pb-4 lg:pt-4',
          isAgentChat ? 'pb-0' : 'pb-4',
        )}>
          <div className="mx-auto w-full max-w-[1600px]">{children}</div>
        </main>
        <MobileNavDrawer viewerRole={viewerRole} isPlatformAdmin={platformAdmin} />
      </div>
    </div>
  );
}
