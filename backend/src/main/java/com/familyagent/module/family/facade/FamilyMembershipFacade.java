package com.familyagent.module.family.facade;

import com.familyagent.module.family.service.FamilyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FamilyMembershipFacade {

    private final FamilyService familyService;

    public void checkMembership(Long familyId) {
        familyService.checkMembership(familyId);
    }
}
