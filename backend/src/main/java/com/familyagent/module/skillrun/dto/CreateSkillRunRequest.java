package com.familyagent.module.skillrun.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

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
    private List<Map<String, Object>> usedSources;
    private Map<String, Object> metadata;
}
