package com.familyagent.module.family.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PersonaMemberVO {

    private Long id;
    private Long familyId;
    private String name;
    private String description;
    private String eraIdentity;
    private String values;
    private String speakingStyle;
    private String personality;
    private Long createdBy;
    private boolean hasMaterial;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
