package com.familyagent.module.family.facade;

import com.familyagent.module.family.dto.FamilyMemberVO;
import com.familyagent.module.family.service.FamilyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MirrorFamilyContextFacade {

    private final FamilyService familyService;

    public void checkMembership(Long familyId) {
        familyService.checkMembership(familyId);
    }

    public FamilyMemberVO getMemberView(Long familyId, Long userId) {
        return familyService.getMemberView(familyId, userId);
    }

    public void attachRelationshipLabels(
            Long familyId,
            Long viewerUserId,
            List<FamilyMemberVO> members) {
        familyService.attachRelationshipLabels(familyId, viewerUserId, members);
    }
}
