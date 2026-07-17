package com.familyagent.module.family.facade;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.module.family.dto.FamilyMemberVO;
import com.familyagent.module.family.service.FamilyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MirrorFamilyContextFacade {

    private final FamilyService familyService;

    public MirrorFamilyContext load(Long familyId, Long targetUserId, Long viewerUserId) {
        familyService.checkMembership(familyId);
        FamilyMemberVO target = familyService.getMemberView(familyId, targetUserId);
        FamilyMemberVO viewer;
        try {
            viewer = familyService.getMemberView(familyId, viewerUserId);
        } catch (BusinessException ignored) {
            viewer = null;
        }
        List<FamilyMemberVO> members = new ArrayList<>();
        members.add(target);
        if (viewer != null) {
            members.add(viewer);
        }
        familyService.attachRelationshipLabels(familyId, viewerUserId, members);
        return new MirrorFamilyContext(target, viewer);
    }

    public record MirrorFamilyContext(FamilyMemberVO target, FamilyMemberVO viewer) {}
}
