package com.familyagent.module.growth.service;

import com.familyagent.common.constant.CareAuthorizationScope;
import com.familyagent.common.constant.MemoryScope;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.family.facade.FamilyCareAuthorizationFacade;
import com.familyagent.module.family.facade.FamilyMembershipFacade;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrowthGuardPermissionGateTest {

    @Mock private FamilyMembershipFacade familyMembershipFacade;
    @Mock private FamilyCareAuthorizationFacade careAuthorizationFacade;
    @InjectMocks private GrowthGuardPermissionGate permissionGate;

    @Test
    void ensureCanCareForTargetRequiresGrowthAuthorization() {
        when(careAuthorizationFacade.canViewScope(
                1L, 20L, 10L, CareAuthorizationScope.GROWTH_GUARD.name()))
                .thenReturn(false);

        assertThrows(BusinessException.class,
                () -> permissionGate.ensureCanCareForTarget(1L, 20L, 10L));
    }

    @Test
    void ensureCanViewRecordAllowsFamilyVisibleWithoutCareLookup() {
        GrowthGuardRecord record = record(20L, 30L, MemoryScope.FAMILY_VISIBLE.name());

        assertDoesNotThrow(() -> permissionGate.ensureCanViewRecord(record, 10L));

        verify(careAuthorizationFacade, never()).canViewScope(
                1L, 20L, 10L, CareAuthorizationScope.GROWTH_GUARD.name());
    }

    @Test
    void ensureCanViewPrivateRecordAllowsAuthorizedCaregiver() {
        GrowthGuardRecord record = record(20L, 30L, MemoryScope.PRIVATE.name());
        when(careAuthorizationFacade.canViewScope(
                1L, 20L, 10L, CareAuthorizationScope.GROWTH_GUARD.name()))
                .thenReturn(true);

        assertDoesNotThrow(() -> permissionGate.ensureCanViewRecord(record, 10L));
    }

    @Test
    void ensureCanModifyRecordAllowsFamilyOwner() {
        GrowthGuardRecord record = record(20L, 30L, MemoryScope.PRIVATE.name());
        try (MockedStatic<CurrentUserGuard> currentUser = mockStatic(CurrentUserGuard.class)) {
            currentUser.when(CurrentUserGuard::currentUserId).thenReturn(10L);

            assertDoesNotThrow(() -> permissionGate.ensureCanModifyRecord(record));
        }

        verify(familyMembershipFacade).checkOwner(1L);
    }

    private GrowthGuardRecord record(Long targetUserId, Long createdBy, String visibility) {
        GrowthGuardRecord record = new GrowthGuardRecord();
        record.setFamilyId(1L);
        record.setTargetUserId(targetUserId);
        record.setCreatedBy(createdBy);
        record.setVisibility(visibility);
        return record;
    }
}
