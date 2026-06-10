package com.familyagent.module.user.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Login response.
 */
@Data
@Builder
public class LoginResponse {

    private Long userId;
    private String username;
    private String nickname;
    private String avatarUrl;
    private String role;
    private String status;
    private Object metadata;
    private String token;
    private String tokenName;
}
