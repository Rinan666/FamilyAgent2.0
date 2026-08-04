package com.familyagent.module.family.service;

import com.familyagent.common.constant.CareAuthorizationScope;
import com.familyagent.common.constant.CareAuthorizationStatus;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.family.dto.UpsertCareAuthorizationRequest;
import com.familyagent.module.family.entity.CareAuthorization;
import com.familyagent.module.family.entity.FamilyMember;
import com.familyagent.module.family.repository.CareAuthorizationRepository;
import com.familyagent.module.family.repository.FamilyMemberRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CareAuthorizationServiceTest {

    @Test
    void upsertAll_shouldPersistSingleAuthorizationAndRevokeLegacyScopes() {
        FamilyService familyService = mock(FamilyService.class);
        FamilyMemberRepository memberRepository = mock(FamilyMemberRepository.class);
        CareAuthorizationRepository authorizationRepository = mock(CareAuthorizationRepository.class);
        CareAuthorizationService service = new CareAuthorizationService(
                familyService,
                memberRepository,
                authorizationRepository);
        when(memberRepository.findByFamilyAndUser(1L, 10L)).thenReturn(member(1L, 10L));
        when(memberRepository.findByFamilyAndUser(1L, 20L)).thenReturn(member(1L, 20L));
        List<CareAuthorization> legacy = List.of(
                authorization(1L, 10L, 20L, CareAuthorizationScope.DIARY),
                authorization(2L, 10L, 20L, CareAuthorizationScope.MEMORY),
                authorization(3L, 10L, 20L, CareAuthorizationScope.GROWTH_GUARD));
        legacy.forEach(item -> when(authorizationRepository.findOne(
                1L,
                10L,
                20L,
                item.getScope())).thenReturn(item));

        UpsertCareAuthorizationRequest request = request(CareAuthorizationScope.ALL, true);
        try (MockedStatic<CurrentUserGuard> currentUser = mockStatic(CurrentUserGuard.class)) {
            currentUser.when(CurrentUserGuard::currentUserId).thenReturn(10L);

            service.upsertAuthorization(1L, 10L, 20L, request);
        }

        ArgumentCaptor<CareAuthorization> inserted = ArgumentCaptor.forClass(CareAuthorization.class);
        verify(authorizationRepository).insert(inserted.capture());
        assertEquals(CareAuthorizationScope.ALL.name(), inserted.getValue().getScope());
        assertEquals(CareAuthorizationStatus.ACTIVE.name(), inserted.getValue().getStatus());
        legacy.forEach(item -> {
            assertEquals(CareAuthorizationStatus.REVOKED.name(), item.getStatus());
            verify(authorizationRepository).updateById(item);
        });
    }

    @Test
    void upsertSpecificScope_shouldNotTouchOtherScopes() {
        FamilyService familyService = mock(FamilyService.class);
        FamilyMemberRepository memberRepository = mock(FamilyMemberRepository.class);
        CareAuthorizationRepository authorizationRepository = mock(CareAuthorizationRepository.class);
        CareAuthorizationService service = new CareAuthorizationService(
                familyService,
                memberRepository,
                authorizationRepository);
        when(memberRepository.findByFamilyAndUser(1L, 10L)).thenReturn(member(1L, 10L));
        when(memberRepository.findByFamilyAndUser(1L, 20L)).thenReturn(member(1L, 20L));

        try (MockedStatic<CurrentUserGuard> currentUser = mockStatic(CurrentUserGuard.class)) {
            currentUser.when(CurrentUserGuard::currentUserId).thenReturn(10L);

            service.upsertAuthorization(1L, 10L, 20L, request(CareAuthorizationScope.DIARY, true));
        }

        verify(authorizationRepository, never()).findOne(1L, 10L, 20L, CareAuthorizationScope.MEMORY.name());
        verify(authorizationRepository, never()).findOne(1L, 10L, 20L, CareAuthorizationScope.GROWTH_GUARD.name());
    }

    private static UpsertCareAuthorizationRequest request(CareAuthorizationScope scope, boolean active) {
        UpsertCareAuthorizationRequest request = new UpsertCareAuthorizationRequest();
        request.setScope(scope.name());
        request.setActive(active);
        return request;
    }

    private static FamilyMember member(Long familyId, Long userId) {
        FamilyMember member = new FamilyMember();
        member.setFamilyId(familyId);
        member.setUserId(userId);
        member.setRole("MEMBER");
        return member;
    }

    private static CareAuthorization authorization(
            Long id,
            Long subjectUserId,
            Long caregiverUserId,
            CareAuthorizationScope scope) {
        CareAuthorization authorization = new CareAuthorization();
        authorization.setId(id);
        authorization.setFamilyId(1L);
        authorization.setSubjectUserId(subjectUserId);
        authorization.setCaregiverUserId(caregiverUserId);
        authorization.setScope(scope.name());
        authorization.setStatus(CareAuthorizationStatus.ACTIVE.name());
        return authorization;
    }
}
