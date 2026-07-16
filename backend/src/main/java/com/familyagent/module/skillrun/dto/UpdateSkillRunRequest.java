package com.familyagent.module.skillrun.dto;

import lombok.Data;

import java.util.List;

@Data
public class UpdateSkillRunRequest {

    private String status;
    private String outputSummary;
    private Boolean saved;
    private List<SkillRunSourceRef> usedSources;
    private SkillRunMetadata metadata;
}
