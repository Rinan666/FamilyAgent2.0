'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import { familyApi } from '@/lib/api/family';
import { deriveViewerRole, type ViewerRole } from '@/lib/roles';
import { useFamilyContextStore } from '@/stores/familyContextStore';
import { useAuthStore } from '@/stores/authStore';
import type { Family, FamilyMember } from '@/types';

const ROLE_EVENT = 'familyagent:family-membership-changed';

let cachedUserId: number | null = null;
let cachedFamilies: Family[] = [];
let cachedMembershipByFamilyId: Record<number, FamilyMember | null> = {};

function resetViewerRoleCache() {
  cachedUserId = null;
  cachedFamilies = [];
  cachedMembershipByFamilyId = {};
}

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
  const activeFamilyIdRef = useRef(activeFamilyId);

  const [families, setFamilies] = useState<Family[]>(() => (hasCachedUser ? cachedFamilies : []));
  const [activeMembership, setActiveMembership] = useState<FamilyMember | null>(() => {
    if (!hasCachedUser || activeFamilyId == null) return null;
    return cachedMembershipByFamilyId[activeFamilyId] ?? null;
  });
  const [isFamiliesLoading, setIsFamiliesLoading] = useState(!hasCachedUser);
  const [isMembershipLoading, setIsMembershipLoading] = useState(false);

  useEffect(() => {
    hydrate();
  }, [hydrate]);

  useEffect(() => {
    activeFamilyIdRef.current = activeFamilyId;
  }, [activeFamilyId]);

  useEffect(() => {
    let cancelled = false;

    async function loadFamilies() {
      if (!userId) return;

      if (!hasCachedUser) {
        setIsFamiliesLoading(true);
      }

      try {
        const familyList = await familyApi.getMyFamilies();
        if (cancelled) return;

        const nextFamilies = Array.isArray(familyList) ? familyList : [];
        const currentActiveFamilyId = activeFamilyIdRef.current;
        const validFamilyIds = new Set(nextFamilies.map((family) => family.id));

        cachedUserId = userId;
        cachedFamilies = nextFamilies;
        cachedMembershipByFamilyId = Object.fromEntries(
          Object.entries(cachedMembershipByFamilyId)
            .filter(([familyId]) => validFamilyIds.has(Number(familyId)))
            .map(([familyId, membership]) => [Number(familyId), membership]),
        );

        setFamilies(nextFamilies);

        const selectedFamilyExists = nextFamilies.some((family) => family.id === currentActiveFamilyId);
        const nextActiveFamilyId = selectedFamilyExists ? currentActiveFamilyId : nextFamilies[0]?.id ?? null;
        if (nextActiveFamilyId !== currentActiveFamilyId) {
          setActiveFamilyId(nextActiveFamilyId);
        }
      } catch {
        if (!cancelled) {
          resetViewerRoleCache();
          setFamilies([]);
          setActiveMembership(null);
        }
      } finally {
        if (!cancelled) setIsFamiliesLoading(false);
      }
    }

    if (!userId) {
      resetViewerRoleCache();
      setFamilies([]);
      setActiveMembership(null);
      setIsFamiliesLoading(false);
      setIsMembershipLoading(false);
      return;
    }

    if (hasCachedUser) {
      setFamilies(cachedFamilies);
      setIsFamiliesLoading(false);
    }

    void loadFamilies();
    window.addEventListener(ROLE_EVENT, loadFamilies);

    return () => {
      cancelled = true;
      window.removeEventListener(ROLE_EVENT, loadFamilies);
    };
  }, [hasCachedUser, setActiveFamilyId, userId]);

  useEffect(() => {
    let cancelled = false;

    async function loadMembership() {
      if (!userId || !activeFamilyId) {
        setActiveMembership(null);
        setIsMembershipLoading(false);
        return;
      }

      const cachedMembership = cachedMembershipByFamilyId[activeFamilyId];
      if (cachedMembership !== undefined) {
        setActiveMembership(cachedMembership);
        setIsMembershipLoading(false);
        return;
      }

      setIsMembershipLoading(true);

      try {
        const memberList = await familyApi.getMembers(activeFamilyId).catch(() => [] as FamilyMember[]);
        if (cancelled) return;

        const nextMembership = memberList.find((member) => member.userId === userId) || null;
        cachedMembershipByFamilyId = {
          ...cachedMembershipByFamilyId,
          [activeFamilyId]: nextMembership,
        };
        setActiveMembership(nextMembership);
      } catch {
        if (!cancelled) {
          cachedMembershipByFamilyId = {
            ...cachedMembershipByFamilyId,
            [activeFamilyId]: null,
          };
          setActiveMembership(null);
        }
      } finally {
        if (!cancelled) setIsMembershipLoading(false);
      }
    }

    void loadMembership();
    window.addEventListener(ROLE_EVENT, loadMembership);

    return () => {
      cancelled = true;
      window.removeEventListener(ROLE_EVENT, loadMembership);
    };
  }, [activeFamilyId, userId]);

  const memberships = useMemo(
    () => (activeMembership ? [activeMembership] : []),
    [activeMembership],
  );

  const viewerRole: ViewerRole = useMemo(
    () => deriveViewerRole(user, memberships, activeFamilyId),
    [activeFamilyId, memberships, user],
  );

  const activeFamily = useMemo(
    () => families.find((family) => family.id === activeFamilyId) || null,
    [activeFamilyId, families],
  );

  const needsMembershipBootstrap = userId != null
    && activeFamilyId != null
    && cachedMembershipByFamilyId[activeFamilyId] === undefined;
  const isRoleLoading = userId != null
    && (!hasHydrated || isFamiliesLoading || isMembershipLoading || needsMembershipBootstrap);

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
