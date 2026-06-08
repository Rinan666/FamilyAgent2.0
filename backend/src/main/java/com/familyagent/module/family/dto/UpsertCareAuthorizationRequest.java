package com.familyagent.module.family.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UpsertCareAuthorizationRequest {

    @Size(max = 40, message = "授权范围不能超过 40 个字符")
    private String scope;

    private Boolean active;

    private LocalDateTime expiresAt;
}
