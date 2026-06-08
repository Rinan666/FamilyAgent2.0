package com.familyagent.module.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class FailedSkillRunSummary {

    private Long id;
    private Long familyId;
    private Long triggeredBy;
    private String skillName;
    private String source;
    private String inputSummary;
    private String outputSummary;
    private LocalDateTime updatedAt;
}
