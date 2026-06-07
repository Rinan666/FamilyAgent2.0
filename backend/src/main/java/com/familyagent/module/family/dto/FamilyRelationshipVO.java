package com.familyagent.module.family.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FamilyRelationshipVO {

    private Long id;
    private Long familyId;
    private Long fromUserId;
    private Long toUserId;
    private String label;
    private String reverseLabel;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
