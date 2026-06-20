package com.familyagent.module.family.service;

import com.familyagent.module.family.dto.PersonaMaterialVO;
import com.familyagent.module.family.entity.FamilyPersonaMaterial;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class FamilyPersonaMaterialAssembler {

    public PersonaMaterialVO toVO(FamilyPersonaMaterial entity) {
        return PersonaMaterialVO.builder()
                .id(entity.getId())
                .familyId(entity.getFamilyId())
                .personaId(entity.getPersonaId())
                .title(entity.getTitle())
                .content(entity.getContent())
                .tags(toTags(entity.getTags()))
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private List<String> toTags(String[] tags) {
        if (tags == null || tags.length == 0) {
            return List.of();
        }
        return Arrays.stream(tags)
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }
}
