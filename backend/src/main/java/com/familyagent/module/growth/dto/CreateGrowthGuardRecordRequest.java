package com.familyagent.module.growth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateGrowthGuardRecordRequest {

    @NotNull
    private Long familyId;

    private Long targetUserId;

    @NotBlank
    private String category;

    @NotBlank
    private String content;

    @Min(1)
    @Max(5)
    private Integer severity;

    private LocalDate observedAt;
    private LocalDate followUpAt;
    private String visibility;
    private GrowthGuardMetadata metadata;
}
