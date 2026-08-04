'use client';

import { useEffect, useMemo } from 'react';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { Loader2, Users } from 'lucide-react';
import FamilyMembersPanel from '@/components/family/FamilyMembersPanel';
import { useViewerRole } from '@/hooks/useViewerRole';

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

  const requestedFamilyId = useMemo(() => {
    const value = Number(searchParams.get('familyId'));
    return Number.isFinite(value) && value > 0 ? value : null;
  }, [searchParams]);

  const selectedFamilyId = useMemo(() => {
    if (requestedFamilyId && families.some((family) => family.id === requestedFamilyId))
      return requestedFamilyId;
    if (activeFamilyId && families.some((family) => family.id === activeFamilyId))
      return activeFamilyId;
    return families[0]?.id || null;
  }, [activeFamilyId, families, requestedFamilyId]);

  useEffect(() => {
    if (selectedFamilyId && selectedFamilyId !== activeFamilyId)
      setActiveFamilyId(selectedFamilyId);
  }, [activeFamilyId, selectedFamilyId, setActiveFamilyId]);

  useEffect(() => {
    if (!selectedFamilyId || requestedFamilyId === selectedFamilyId) return;
    const params = new URLSearchParams(searchParams.toString());
    params.set('familyId', String(selectedFamilyId));
    params.delete('tab');
    router.replace(`${pathname}?${params.toString()}`, { scroll: false });
  }, [pathname, requestedFamilyId, router, searchParams, selectedFamilyId]);

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
      {families.length === 0 ? (
        <div className="glass-panel rounded-[24px] border-dashed border-stone-300/80 px-6 py-16 text-center">
          <Users className="mx-auto mb-3 h-10 w-10 text-stone-300" />
          <h2 className="text-lg font-semibold text-stone-950">还没有家庭空间</h2>
          <div className="mx-auto mt-6 max-w-4xl">
            <FamilyMembersPanel
              viewerRole={viewerRole}
              families={families}
              focusedFamilyId={selectedFamilyId}
            />
          </div>
        </div>
      ) : (
        <FamilyMembersPanel
          viewerRole={viewerRole}
          families={families}
          focusedFamilyId={selectedFamilyId}
          onFocusedFamilyChange={setActiveFamilyId}
        />
      )}
    </div>
  );
}
