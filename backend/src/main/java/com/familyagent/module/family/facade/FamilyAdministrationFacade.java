package com.familyagent.module.family.facade;

import com.familyagent.module.family.dto.FamilyMemberVO;
import com.familyagent.module.family.service.FamilyLifecycleService;
import com.familyagent.module.family.service.FamilyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class FamilyAdministrationFacade {

    private final FamilyService familyService;
    private final FamilyLifecycleService familyLifecycleService;

    public List<FamilyMemberVO> listMemberViews(Long familyId) {
        return familyService.listMemberViewsForAdmin(familyId);
    }

    public void requireMember(Long familyId, Long userId) {
        familyService.getFamilyMember(familyId, userId);
    }

    public void prepareForUserDeletion(Long userId) {
        familyLifecycleService.prepareFamiliesForUserDeletion(userId);
    }
}
