package com.familyagent.module.user.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 登录响应
 */
@Data
@Builder
public class LoginResponse {

    private Long userId;
    private String username;
    private String nickname;
    private String avatarUrl;
    private String token;
    private String tokenName;
}
