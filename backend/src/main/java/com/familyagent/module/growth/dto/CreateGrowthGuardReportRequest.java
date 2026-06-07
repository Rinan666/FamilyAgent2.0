package com.familyagent.module.growth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.Map;

@Data
public class CreateGrowthGuardReportRequest {

    @NotNull
    private Long familyId;

    private Long targetUserId;
    private LocalDate weekStart;
    private LocalDate weekEnd;

    @NotBlank
    private String title;

    private String summary;
    private String visibility;

    @NotNull
    private Map<String, Object> report;

    private Map<String, Object> metadata;
}
