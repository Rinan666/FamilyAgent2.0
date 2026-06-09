package com.familyagent.module.admin.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminUserSummary {

    private Long id;
    private String username;
    private String nickname;
    private String role;
    private String status;
}
