'use client';

import { useCallback, useEffect, useMemo } from 'react';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { Loader2, UserCheck, Users } from 'lucide-react';
import FamilyMembersPanel from '@/components/family/FamilyMembersPanel';
import PersonaMembersPanel from '@/components/family/PersonaMembersPanel';
import { MobilePageDrawer } from '@/components/layout/Sidebar';
import { useViewerRole } from '@/hooks/useViewerRole';
import { cn } from '@/lib/utils';

type FamilySpaceTab = 'members' | 'personas';

const tabs: { value: FamilySpaceTab; label: string; icon: typeof Users }[] = [
  { value: 'members', label: '成员列表', icon: Users },
  { value: 'personas', label: '精神成员', icon: UserCheck },
];

const legacyTabRedirects: Record<string, FamilySpaceTab> = {
  overview: 'members',
  heritage: 'members',
  library: 'members',
  archive: 'members',
  growth: 'members',
  stream: 'members',
};

function parseFamilyTab(value: string | null): FamilySpaceTab {
  if (tabs.some((tab) => tab.value === value)) return value as FamilySpaceTab;
  if (value && legacyTabRedirects[value]) return legacyTabRedirects[value];
  return 'members';
}

export default function FamilyPage() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const {
    families,
    activeFamilyId,
    setActiveFamilyId,
    viewerRole,
    activeMembership,
    isLoading: loadingFamilies,
  } = useViewerRole();

  const requestedTab = searchParams.get('tab');
  const currentTab = useMemo(() => parseFamilyTab(requestedTab), [requestedTab]);

  const requestedFamilyId = useMemo(() => {
    const value = Number(searchParams.get('familyId'));
    return Number.isFinite(value) && value > 0 ? value : null;
  }, [searchParams]);

  const selectedFamilyId = useMemo(() => {
    const fromQuery = requestedFamilyId && families.some((family) => family.id === requestedFamilyId)
      ? requestedFamilyId
      : null;
    return fromQuery
      || (activeFamilyId && families.some((family) => family.id === activeFamilyId) ? activeFamilyId : null)
      || families[0]?.id
      || null;
  }, [activeFamilyId, families, requestedFamilyId]);

  const updateUrl = useCallback((nextTab: FamilySpaceTab, nextFamilyId?: number | null) => {
    const params = new URLSearchParams(searchParams.toString());
    params.set('tab', nextTab);
    if (nextFamilyId) {
      params.set('familyId', String(nextFamilyId));
    } else {
      params.delete('familyId');
    }
    router.push(`${pathname}?${params.toString()}`, { scroll: false });
  }, [pathname, router, searchParams]);

  const handleTabChange = useCallback((nextTab: FamilySpaceTab) => {
    updateUrl(nextTab, selectedFamilyId);
  }, [selectedFamilyId, updateUrl]);

  useEffect(() => {
    if (!requestedTab) {
      const params = new URLSearchParams(searchParams.toString());
      params.set('tab', 'members');
      router.replace(`${pathname}?${params.toString()}`, { scroll: false });
      return;
    }

    const normalizedTab = parseFamilyTab(requestedTab);
    if (requestedTab === normalizedTab) return;

    const params = new URLSearchParams(searchParams.toString());
    params.set('tab', normalizedTab);
    router.replace(`${pathname}?${params.toString()}`, { scroll: false });
  }, [pathname, requestedTab, router, searchParams]);

  useEffect(() => {
    if (selectedFamilyId && selectedFamilyId !== activeFamilyId) {
      setActiveFamilyId(selectedFamilyId);
    }
  }, [activeFamilyId, selectedFamilyId, setActiveFamilyId]);

  if (loadingFamilies) {
    return (
      <div className="flex h-60 items-center justify-center text-stone-400">
        <Loader2 className="mr-2 h-5 w-5 animate-spin" />
        正在加载家庭空间...
      </div>
    );
  }

  return (
    <div className="mx-auto w-full max-w-[1500px]">
      <div className="mb-3 rounded-md border border-stone-200 bg-white p-2">
        <div className="flex min-w-0 items-center gap-2">
          <MobilePageDrawer viewerRole={viewerRole} />
          <nav className="flex min-w-0 gap-1 overflow-x-auto">
            {tabs.map((tab) => {
              const Icon = tab.icon;
              const active = currentTab === tab.value;
              return (
                <button
                  key={tab.value}
                  type="button"
                  onClick={() => handleTabChange(tab.value)}
                  className={cn(
                    'inline-flex h-9 shrink-0 items-center gap-2 rounded-md px-3 text-sm font-medium transition',
                    active ? 'bg-stone-950 text-white' : 'text-stone-600 hover:bg-stone-100 hover:text-stone-950',
                  )}
                >
                  <Icon className={cn('h-4 w-4', active ? 'text-emerald-300' : 'text-stone-400')} />
                  {tab.label}
                </button>
              );
            })}
          </nav>
        </div>
      </div>

      {families.length === 0 ? (
        <div className="rounded-md border border-dashed border-stone-300 bg-white px-6 py-16 text-center">
          <Users className="mx-auto mb-3 h-10 w-10 text-stone-300" />
          <h2 className="text-lg font-semibold text-stone-950">还没有家庭空间</h2>
          <p className="mx-auto mt-2 max-w-xl text-sm leading-6 text-stone-500">
            创建或加入家庭后，就可以在这里管理成员和精神成员。
          </p>
          <div className="mx-auto mt-6 w-full max-w-4xl">
            <FamilyMembersPanel viewerRole={viewerRole} families={families} focusedFamilyId={selectedFamilyId} />
          </div>
        </div>
      ) : (
        <>
          {currentTab === 'members' && (
            <FamilyMembersPanel
              viewerRole={viewerRole}
              families={families}
              focusedFamilyId={selectedFamilyId}
            />
          )}

          {currentTab === 'personas' && (
            <div className="rounded-md border border-stone-200 bg-white p-4">
              <PersonaMembersPanel
                familyId={selectedFamilyId}
                isOwner={activeMembership?.role === 'OWNER'}
              />
            </div>
          )}
        </>
      )}
    </div>
  );
}
