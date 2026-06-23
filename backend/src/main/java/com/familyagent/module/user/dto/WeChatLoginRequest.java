package com.familyagent.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * WeChat mini app login request.
 */
@Data
public class WeChatLoginRequest {

    @NotBlank
    @Size(max = 256)
    private String code;

    @Size(max = 100)
    private String nickname;

    @Size(max = 500)
    private String avatarUrl;
}
