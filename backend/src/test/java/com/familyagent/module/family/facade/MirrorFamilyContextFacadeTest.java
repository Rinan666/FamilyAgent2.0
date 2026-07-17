package com.familyagent.module.family.facade;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.family.dto.FamilyMemberVO;
import com.familyagent.module.family.service.FamilyService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MirrorFamilyContextFacadeTest {

    @Test
    void load_shouldReturnLabeledTargetAndViewer() {
        FamilyService familyService = mock(FamilyService.class);
        FamilyMemberVO target = FamilyMemberVO.builder().userId(201L).build();
        FamilyMemberVO viewer = FamilyMemberVO.builder().userId(101L).build();
        when(familyService.getMemberView(10L, 201L)).thenReturn(target);
        when(familyService.getMemberView(10L, 101L)).thenReturn(viewer);
        MirrorFamilyContextFacade facade = new MirrorFamilyContextFacade(familyService);

        MirrorFamilyContextFacade.MirrorFamilyContext context = facade.load(10L, 201L, 101L);

        assertEquals(target, context.target());
        assertEquals(viewer, context.viewer());
        verify(familyService).checkMembership(10L);
        verify(familyService).getMemberView(10L, 201L);
        verify(familyService).getMemberView(10L, 101L);
        verify(familyService).attachRelationshipLabels(10L, 101L, List.of(target, viewer));
    }

    @Test
    void load_shouldKeepTargetWhenViewerProfileIsUnavailable() {
        FamilyService familyService = mock(FamilyService.class);
        FamilyMemberVO target = FamilyMemberVO.builder().userId(201L).build();
        when(familyService.getMemberView(10L, 201L)).thenReturn(target);
        when(familyService.getMemberView(10L, 101L))
                .thenThrow(new BusinessException(ErrorCode.NOT_FAMILY_MEMBER));
        MirrorFamilyContextFacade facade = new MirrorFamilyContextFacade(familyService);

        MirrorFamilyContextFacade.MirrorFamilyContext context = facade.load(10L, 201L, 101L);

        assertEquals(target, context.target());
        assertNull(context.viewer());
        verify(familyService).attachRelationshipLabels(10L, 101L, List.of(target));
    }
}
