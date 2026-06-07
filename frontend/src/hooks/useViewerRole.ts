'use client';

import { useEffect, useMemo, useState } from 'react';
import { familyApi } from '@/lib/api';
import { deriveViewerRole, type ViewerRole } from '@/lib/roles';
import { useFamilyContextStore } from '@/stores/familyContextStore';
import { useAuthStore } from '@/stores/authStore';
import type { Family, FamilyMember } from '@/types';

const ROLE_EVENT = 'familyagent:family-membership-changed';

let cachedUserId: number | null = null;
let cachedFamilies: Family[] = [];
let cachedMemberships: FamilyMember[] = [];

export function notifyViewerRoleChanged() {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new Event(ROLE_EVENT));
  }
}

export function useViewerRole() {
  const user = useAuthStore((state) => state.user);
  const { activeFamilyId, hasHydrated, hydrate, setActiveFamilyId } = useFamilyContextStore();
  const userId = user?.id ?? null;
  const hasCachedUser = userId != null && cachedUserId === userId;
  const [families, setFamilies] = useState<Family[]>(() => (hasCachedUser ? cachedFamilies : []));
  const [memberships, setMemberships] = useState<FamilyMember[]>(() => (hasCachedUser ? cachedMemberships : []));
  const [isLoading, setIsLoading] = useState(!hasCachedUser);

  useEffect(() => {
    hydrate();
  }, [hydrate]);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      if (!user) return;
      setIsLoading(true);
      try {
        const familyList = await familyApi.getMyFamilies();
        if (cancelled) return;
        const nextFamilies = Array.isArray(familyList) ? familyList : [];
        setFamilies(nextFamilies);

        const memberLists = await Promise.all(
          nextFamilies.map((family) => familyApi.getMembers(family.id).catch(() => [] as FamilyMember[])),
        );
        if (!cancelled) {
          const currentUserId = user?.id;
          const nextMemberships = memberLists
            .flat()
            .filter((member) => !currentUserId || member.userId === currentUserId);
          const selectedFamilyExists = nextFamilies.some((family) => family.id === activeFamilyId);
          const nextActiveFamilyId = selectedFamilyExists ? activeFamilyId : nextFamilies[0]?.id ?? null;
          if (nextActiveFamilyId !== activeFamilyId) {
            setActiveFamilyId(nextActiveFamilyId);
          }
          cachedUserId = currentUserId ?? null;
          cachedFamilies = nextFamilies;
          cachedMemberships = nextMemberships;
          setMemberships(nextMemberships);
        }
      } catch {
        if (!cancelled) {
          cachedUserId = user.id;
          cachedFamilies = [];
          cachedMemberships = [];
          setFamilies([]);
          setMemberships([]);
        }
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    }

    if (!user) {
      cachedUserId = null;
      cachedFamilies = [];
      cachedMemberships = [];
      setFamilies([]);
      setMemberships([]);
      setIsLoading(false);
      return;
    }

    void load();
    window.addEventListener(ROLE_EVENT, load);
    return () => {
      cancelled = true;
      window.removeEventListener(ROLE_EVENT, load);
    };
  }, [activeFamilyId, setActiveFamilyId, user]);

  const viewerRole: ViewerRole = useMemo(
    () => deriveViewerRole(user, memberships, activeFamilyId),
    [activeFamilyId, memberships, user],
  );

  const activeFamily = useMemo(
    () => families.find((family) => family.id === activeFamilyId) || null,
    [activeFamilyId, families],
  );
  const activeMembership = useMemo(
    () => memberships.find((member) => member.familyId === activeFamilyId) || null,
    [activeFamilyId, memberships],
  );

  const isRoleLoading = userId != null && (!hasHydrated || isLoading || cachedUserId !== userId);

  return {
    viewerRole,
    families,
    memberships,
    activeFamilyId,
    activeFamily,
    activeMembership,
    setActiveFamilyId,
    isLoading: isRoleLoading,
  };
}
