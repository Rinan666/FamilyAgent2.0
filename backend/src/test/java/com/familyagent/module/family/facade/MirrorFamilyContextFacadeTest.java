package com.familyagent.module.family.facade;

import com.familyagent.module.family.dto.FamilyMemberVO;
import com.familyagent.module.family.service.FamilyService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MirrorFamilyContextFacadeTest {

    @Test
    void shouldDelegateMembershipAndMemberContextOperations() {
        FamilyService familyService = mock(FamilyService.class);
        FamilyMemberVO member = FamilyMemberVO.builder().userId(201L).build();
        List<FamilyMemberVO> members = List.of(member);
        when(familyService.getMemberView(10L, 201L)).thenReturn(member);
        MirrorFamilyContextFacade facade = new MirrorFamilyContextFacade(familyService);

        facade.checkMembership(10L);
        assertEquals(member, facade.getMemberView(10L, 201L));
        facade.attachRelationshipLabels(10L, 101L, members);

        verify(familyService).checkMembership(10L);
        verify(familyService).getMemberView(10L, 201L);
        verify(familyService).attachRelationshipLabels(10L, 101L, members);
    }
}
