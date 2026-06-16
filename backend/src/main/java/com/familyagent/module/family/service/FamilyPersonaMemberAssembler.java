package com.familyagent.module.family.service;

import com.familyagent.module.family.dto.PersonaMemberVO;
import com.familyagent.module.family.entity.FamilyPersonaMember;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FamilyPersonaMemberAssembler {

    public PersonaMemberVO toVO(FamilyPersonaMember entity) {
        return PersonaMemberVO.builder()
                .id(entity.getId())
                .familyId(entity.getFamilyId())
                .name(entity.getName())
                .description(entity.getDescription())
                .eraIdentity(entity.getEraIdentity())
                .values(entity.getValues())
                .speakingStyle(entity.getSpeakingStyle())
                .personality(entity.getPersonality())
                .createdBy(entity.getCreatedBy())
                // hasMaterial: Phase 2 will set this; Phase 1 always false
                .hasMaterial(false)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
