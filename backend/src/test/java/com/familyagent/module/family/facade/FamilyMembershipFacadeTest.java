package com.familyagent.module.family.facade;

import com.familyagent.module.family.service.FamilyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FamilyMembershipFacadeTest {

    @Mock private FamilyService familyService;

    @Test
    void checkMembership_shouldDelegateCurrentViewerCheck() {
        FamilyMembershipFacade facade = new FamilyMembershipFacade(familyService);

        facade.checkMembership(11L);

        verify(familyService).checkMembership(11L);
    }

    @Test
    void checkMembership_shouldDelegateExplicitViewerCheck() {
        FamilyMembershipFacade facade = new FamilyMembershipFacade(familyService);

        facade.checkMembership(11L, 34L);

        verify(familyService).getFamilyMember(11L, 34L);
    }
}
