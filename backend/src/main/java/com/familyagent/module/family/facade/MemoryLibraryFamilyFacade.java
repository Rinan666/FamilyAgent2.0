package com.familyagent.module.family.facade;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.common.security.CurrentUserGuard;
import com.familyagent.module.family.service.FamilyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemoryLibraryFamilyFacade {

    private final FamilyService familyService;

    public void checkMembership(Long familyId) {
        familyService.checkMembership(familyId);
    }

    public void ensureCreatorOrOwner(Long familyId, Long creatorUserId, String message) {
        if (CurrentUserGuard.currentUserId().equals(creatorUserId)) {
            return;
        }
        try {
            familyService.checkOwner(familyId);
        } catch (BusinessException ignored) {
            throw new BusinessException(ErrorCode.FORBIDDEN, message);
        }
    }
}
