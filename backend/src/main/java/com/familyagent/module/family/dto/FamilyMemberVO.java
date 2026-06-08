package com.familyagent.module.family.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 家族成员视图
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FamilyMemberVO {

    private Long id;
    private Long familyId;
    private Long userId;
    private String username;
    private String nickname;
    private String avatarUrl;
    private String role;
    private String relationshipLabel;
    private String reverseRelationshipLabel;
    private String birthDate;
    private String birthYear;
    private Object metadata;
    private LocalDateTime joinedAt;
}
