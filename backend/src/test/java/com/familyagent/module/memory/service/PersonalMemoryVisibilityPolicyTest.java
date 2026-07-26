package com.familyagent.module.memory.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.module.family.facade.FamilyMembershipQueryFacade;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PersonalMemoryVisibilityPolicyTest {

    private final FamilyMembershipQueryFacade membershipFacade = mock(FamilyMembershipQueryFacade.class);
    private final PersonalMemoryVisibilityPolicy policy = new PersonalMemoryVisibilityPolicy(membershipFacade);

    @Test
    void resolve_allFamiliesSnapshotsCurrentMemberships() {
        when(membershipFacade.familyIdsForUser(101L)).thenReturn(Set.of(10L, 20L));

        PersonalMemoryVisibilityPolicy.VisibilityGrant result = policy.resolve(
                101L,
                "ALL_FAMILIES_VISIBLE",
                List.of());

        assertEquals("ALL_FAMILIES_VISIBLE", result.visibility());
        assertEquals(List.of(10L, 20L), result.familyIds());
    }

    @Test
    void resolve_selectedFamiliesRequiresExplicitMemberships() {
        when(membershipFacade.requireMemberships(101L, Set.of(20L))).thenReturn(Set.of(20L));

        PersonalMemoryVisibilityPolicy.VisibilityGrant result = policy.resolve(
                101L,
                "SELECTED_FAMILIES_VISIBLE",
                List.of(20L));

        assertEquals(List.of(20L), result.familyIds());
        verify(membershipFacade).requireMemberships(101L, Set.of(20L));
    }

    @Test
    void resolve_selectedFamiliesRejectsEmptySelection() {
        assertThrows(BusinessException.class, () -> policy.resolve(
                101L,
                "SELECTED_FAMILIES_VISIBLE",
                List.of()));
    }

    @Test
    void resolve_privateDoesNotCreateFamilyGrants() {
        PersonalMemoryVisibilityPolicy.VisibilityGrant result = policy.resolve(101L, "PRIVATE", List.of(10L));

        assertEquals(List.of(), result.familyIds());
        verifyNoInteractions(membershipFacade);
    }
}
