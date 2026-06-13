'use client';

import { useCallback, useEffect, useMemo } from 'react';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { Loader2, Sparkles, Users } from 'lucide-react';
import FamilyMembersPanel from '@/components/family/FamilyMembersPanel';
import {
  WorkbenchEmptyState,
  WorkbenchHero,
  WorkbenchPage,
  WorkbenchSectionTitle,
  WorkbenchSurface,
} from '@/components/layout/Workbench';
import MemoryLibraryWorkbench from '@/components/memory-library/MemoryLibraryWorkbench';
import { useViewerRole } from '@/hooks/useViewerRole';
import type { FamilyTab } from '@/types';

const tabs: { value: FamilyTab; label: string }[] = [
  { value: 'library', label: '记忆库' },
  { value: 'members', label: '成员列表' },
];

const legacyTabRedirects: Record<string, FamilyTab> = {
  overview: 'members',
  heritage: 'library',
  library: 'library',
  growth: 'library',
  stream: 'library',
};

function parseFamilyTab(value: string | null): FamilyTab {
  if (tabs.some((tab) => tab.value === value)) return value as FamilyTab;
  if (value && legacyTabRedirects[value]) return legacyTabRedirects[value];
  return 'library';
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

  const selectedFamily = useMemo(
    () => families.find((family) => family.id === selectedFamilyId) || null,
    [families, selectedFamilyId],
  );

  const updateUrl = useCallback((nextTab: FamilyTab, nextFamilyId?: number | null) => {
    const params = new URLSearchParams(searchParams.toString());
    params.set('tab', nextTab);
    if (nextFamilyId) {
      params.set('familyId', String(nextFamilyId));
    } else {
      params.delete('familyId');
    }
    router.push(`${pathname}?${params.toString()}`, { scroll: false });
  }, [pathname, router, searchParams]);

  useEffect(() => {
    if (!requestedTab) {
      const params = new URLSearchParams(searchParams.toString());
      params.set('tab', 'library');
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
      <div className="flex h-60 items-center justify-center text-gray-400">
        <Loader2 className="mr-2 h-5 w-5 animate-spin" />
        正在加载家族空间...
      </div>
    );
  }

  return (
    <WorkbenchPage>
      <WorkbenchHero
        badge={(
          <span className="inline-flex items-center gap-2 rounded-full bg-emerald-50 px-3 py-1 text-xs font-medium text-emerald-700">
            <Sparkles className="h-3.5 w-3.5" />
            家族空间
          </span>
        )}
        title="成员与家族记忆"
        description="家族空间保留记忆库和成员协作两个核心入口。"
        aside={(
          <div>
            <p className="text-xs font-medium uppercase tracking-[0.2em] text-stone-400">当前家族</p>
            <div className="mt-3 space-y-2">
              {selectedFamily ? (
                <>
                  <p className="text-base font-semibold text-stone-950">{selectedFamily.name}</p>
                  <p className="text-sm leading-6 text-stone-500">
                    从记忆库统一查看、整理和维护全家可用的重要记忆资产，也可以从成员列表进入个人记忆视图。
                  </p>
                </>
              ) : (
                <p className="text-sm text-stone-500">先创建或加入一个家族空间。</p>
              )}
            </div>
          </div>
        )}
      />

      <WorkbenchSurface className="grid grid-cols-2 gap-2">
        {tabs.map((tab) => (
          <button
            key={tab.value}
            type="button"
            onClick={() => updateUrl(tab.value, selectedFamilyId)}
            className={`rounded-2xl px-4 py-3 text-left text-sm font-medium transition ${
              currentTab === tab.value
                ? 'bg-stone-950 text-white shadow-[0_16px_36px_rgba(24,39,32,0.14)]'
                : 'bg-stone-50 text-stone-600 hover:bg-stone-100 hover:text-stone-900'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </WorkbenchSurface>

      {families.length === 0 ? (
        <WorkbenchEmptyState
          icon={<Users className="h-6 w-6" />}
          title="还没有家族空间"
          description="创建或加入家族后，就可以在这里查看成员列表，并把家族记忆沉淀到统一记忆库。"
          action={(
            <div className="w-full max-w-4xl">
              <FamilyMembersPanel viewerRole={viewerRole} families={families} focusedFamilyId={selectedFamilyId} />
            </div>
          )}
        />
      ) : (
        <>
          {currentTab === 'library' && <MemoryLibraryWorkbench embedded />}

          {currentTab === 'members' && (
            <WorkbenchSurface className="space-y-4">
              <WorkbenchSectionTitle
                title="成员列表"
                description="在成员列表中查看当前家族成员，并通过“成员记忆”入口进入对应成员的记忆视图。"
              />
              <FamilyMembersPanel
                viewerRole={viewerRole}
                families={families}
                focusedFamilyId={selectedFamilyId}
              />
            </WorkbenchSurface>
          )}
        </>
      )}
    </WorkbenchPage>
  );
}
