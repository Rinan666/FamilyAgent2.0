package com.familyagent.module.skillrun.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class UpdateSkillRunRequest {

    private String status;
    private String outputSummary;
    private Boolean saved;
    private List<Map<String, Object>> usedSources;
    private Map<String, Object> metadata;
}
