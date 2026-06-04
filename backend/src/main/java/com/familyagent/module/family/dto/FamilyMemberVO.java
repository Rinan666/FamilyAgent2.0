package com.familyagent.module.family.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家族成员视图
 */
@Data
@Builder
public class FamilyMemberVO {

    private Long id;
    private Long userId;
    private String username;
    private String nickname;
    private String avatarUrl;
    private String role;
    private LocalDateTime joinedAt;
}
