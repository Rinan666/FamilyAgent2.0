package com.familyagent.module.family.facade;

import com.familyagent.module.family.dto.FamilyMemberVO;
import com.familyagent.module.family.service.FamilyLifecycleService;
import com.familyagent.module.family.service.FamilyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FamilyAdministrationFacadeTest {

    @Mock private FamilyService familyService;
    @Mock private FamilyLifecycleService familyLifecycleService;
    @InjectMocks private FamilyAdministrationFacade facade;

    @Test
    void delegatesAdministrativeFamilyOperations() {
        FamilyMemberVO member = FamilyMemberVO.builder().userId(8L).build();
        when(familyService.listMemberViewsForAdmin(3L)).thenReturn(List.of(member));

        assertEquals(List.of(member), facade.listMemberViews(3L));
        facade.requireMember(3L, 8L);
        facade.prepareForUserDeletion(8L);

        verify(familyService).getFamilyMember(3L, 8L);
        verify(familyLifecycleService).prepareFamiliesForUserDeletion(8L);
    }
}
