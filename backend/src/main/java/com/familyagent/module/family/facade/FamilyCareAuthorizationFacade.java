package com.familyagent.module.family.facade;

import com.familyagent.module.family.service.CareAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FamilyCareAuthorizationFacade {

    private final CareAuthorizationService careAuthorizationService;

    public boolean canViewScope(Long familyId, Long subjectUserId, Long viewerUserId, String scope) {
        return careAuthorizationService.canViewCareScope(familyId, subjectUserId, viewerUserId, scope);
    }
}
