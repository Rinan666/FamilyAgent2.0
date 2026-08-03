package com.familyagent.module.family.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.family.dto.DeleteFamilyRequest;
import com.familyagent.module.family.entity.Family;
import com.familyagent.module.family.entity.FamilyMember;
import com.familyagent.module.family.repository.FamilyMemberRepository;
import com.familyagent.module.family.repository.FamilyRelationshipRepository;
import com.familyagent.module.family.repository.FamilyRepository;
import com.familyagent.module.user.facade.UserAccountAccess;
import com.familyagent.module.user.facade.UserAccountAccessFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FamilyServiceTest {

    @Mock private FamilyRepository familyRepository;
    @Mock private FamilyMemberRepository memberRepository;
    @Mock private FamilyRelationshipRepository relationshipRepository;
    @Mock private FamilyLifecycleService familyLifecycleService;
    @Mock private UserAccountAccessFacade userAccountAccessFacade;
    @InjectMocks private FamilyService familyService;

    @Test
    void deleteFamily_allowsOwnerWithExactConfirmation() {
        when(userAccountAccessFacade.requireCurrent()).thenReturn(user(8L, false));
        when(familyRepository.selectById(10L)).thenReturn(family(10L, "Smith Family"));
        when(memberRepository.findByFamilyAndUser(10L, 8L)).thenReturn(member(10L, 8L, "OWNER"));

        familyService.deleteFamily(10L, deleteRequest("Smith Family", true));

        verify(familyLifecycleService).dissolveFamily(10L, "FAMILY_DELETE_BY_OWNER");
    }

    @Test
    void deleteFamily_allowsPlatformAdminWithoutMembership() {
        when(userAccountAccessFacade.requireCurrent()).thenReturn(user(1L, true));
        when(familyRepository.selectById(10L)).thenReturn(family(10L, "Smith Family"));
        when(memberRepository.findByFamilyAndUser(10L, 1L)).thenReturn(null);

        familyService.deleteFamily(10L, deleteRequest("Smith Family", true));

        verify(familyLifecycleService).dissolveFamily(10L, "FAMILY_DELETE_BY_OWNER");
    }

    @Test
    void deleteFamily_rejectsNonOwner() {
        when(userAccountAccessFacade.requireCurrent()).thenReturn(user(8L, false));
        when(familyRepository.selectById(10L)).thenReturn(family(10L, "Smith Family"));
        when(memberRepository.findByFamilyAndUser(10L, 8L)).thenReturn(member(10L, 8L, "MEMBER"));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> familyService.deleteFamily(10L, deleteRequest("Smith Family", true)));

        assertEquals(ErrorCode.INSUFFICIENT_PERMISSION.getCode(), error.getCode());
        verify(familyLifecycleService, never()).dissolveFamily(10L, "FAMILY_DELETE_BY_OWNER");
    }

    @Test
    void deleteFamily_rejectsMismatchedConfirmation() {
        when(userAccountAccessFacade.requireCurrent()).thenReturn(user(8L, false));
        when(familyRepository.selectById(10L)).thenReturn(family(10L, "Smith Family"));
        when(memberRepository.findByFamilyAndUser(10L, 8L)).thenReturn(member(10L, 8L, "OWNER"));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> familyService.deleteFamily(10L, deleteRequest("Other Family", true)));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), error.getCode());
        verify(familyLifecycleService, never()).dissolveFamily(10L, "FAMILY_DELETE_BY_OWNER");
    }

    private static DeleteFamilyRequest deleteRequest(String confirmationName, boolean deleteAllData) {
        DeleteFamilyRequest request = new DeleteFamilyRequest();
        request.setConfirmationName(confirmationName);
        request.setDeleteAllData(deleteAllData);
        return request;
    }

    private static Family family(Long id, String name) {
        Family family = new Family();
        family.setId(id);
        family.setName(name);
        return family;
    }

    private static FamilyMember member(Long familyId, Long userId, String role) {
        FamilyMember member = new FamilyMember();
        member.setFamilyId(familyId);
        member.setUserId(userId);
        member.setRole(role);
        return member;
    }

    private static UserAccountAccess user(Long id, boolean platformAdmin) {
        return new UserAccountAccess(id, platformAdmin);
    }
}
