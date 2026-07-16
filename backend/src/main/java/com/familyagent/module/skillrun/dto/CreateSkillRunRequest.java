package com.familyagent.module.skillrun.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateSkillRunRequest {

    @NotNull
    private Long familyId;

    @NotBlank
    private String skillName;

    private String status;
    private String source;
    private String inputSummary;
    private String outputSummary;
    private Boolean saved;
    private List<SkillRunSourceRef> usedSources;
    private SkillRunMetadata metadata;
}
