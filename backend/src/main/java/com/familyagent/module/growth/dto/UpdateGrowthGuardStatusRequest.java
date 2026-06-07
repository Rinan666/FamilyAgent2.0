package com.familyagent.module.growth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateGrowthGuardStatusRequest {

    @NotBlank
    private String followUpStatus;
}
