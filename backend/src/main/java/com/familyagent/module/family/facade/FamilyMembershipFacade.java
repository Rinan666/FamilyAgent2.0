package com.familyagent.module.family.facade;

import com.familyagent.module.family.entity.FamilyMember;
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

    public void checkMembership(Long familyId, Long userId) {
        familyService.getFamilyMember(familyId, userId);
    }

    public void checkOwner(Long familyId) {
        familyService.checkOwner(familyId);
    }

    public FamilyMember requireMember(Long familyId, Long userId) {
        return familyService.getFamilyMember(familyId, userId);
    }
}
