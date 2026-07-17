package com.familyagent.module.family.facade;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.family.service.FamilyService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

class MemoryLibraryFamilyFacadeTest {

    @Test
    void ensureCreatorOrOwner_shouldAllowCreatorWithoutOwnerCheck() {
        FamilyService familyService = mock(FamilyService.class);
        MemoryLibraryFamilyFacade facade = new MemoryLibraryFamilyFacade(familyService);
        try (MockedStatic<CurrentUserGuard> currentUser = mockStatic(CurrentUserGuard.class)) {
            currentUser.when(CurrentUserGuard::currentUserId).thenReturn(101L);

            facade.ensureCreatorOrOwner(10L, 101L, "forbidden");

            verify(familyService, never()).checkOwner(10L);
        }
    }

    @Test
    void ensureCreatorOrOwner_shouldAllowFamilyOwner() {
        FamilyService familyService = mock(FamilyService.class);
        MemoryLibraryFamilyFacade facade = new MemoryLibraryFamilyFacade(familyService);
        try (MockedStatic<CurrentUserGuard> currentUser = mockStatic(CurrentUserGuard.class)) {
            currentUser.when(CurrentUserGuard::currentUserId).thenReturn(102L);

            facade.ensureCreatorOrOwner(10L, 101L, "forbidden");

            verify(familyService).checkOwner(10L);
        }
    }

    @Test
    void ensureCreatorOrOwner_shouldPreserveForbiddenErrorForNonOwner() {
        FamilyService familyService = mock(FamilyService.class);
        doThrow(new BusinessException(ErrorCode.FORBIDDEN)).when(familyService).checkOwner(10L);
        MemoryLibraryFamilyFacade facade = new MemoryLibraryFamilyFacade(familyService);
        try (MockedStatic<CurrentUserGuard> currentUser = mockStatic(CurrentUserGuard.class)) {
            currentUser.when(CurrentUserGuard::currentUserId).thenReturn(102L);

            BusinessException error = assertThrows(
                    BusinessException.class,
                    () -> facade.ensureCreatorOrOwner(10L, 101L, "memory edit forbidden"));

            assertEquals(ErrorCode.FORBIDDEN.getCode(), error.getCode());
            assertEquals("memory edit forbidden", error.getMessage());
        }
    }
}
