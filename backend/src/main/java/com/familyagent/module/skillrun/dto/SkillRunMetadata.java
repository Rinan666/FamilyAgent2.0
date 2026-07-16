package com.familyagent.module.skillrun.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class SkillRunMetadata {

    private String savedRecordType;
    private String plannedTool;
    private String plannedReason;
    private String requestId;
    private Long agentRunId;
    private String skillVersion;
    private String promptVersion;
    private String schemaVersion;
    private Long confirmationId;
    private String executionStatus;
    private String executionErrorCode;
    private Map<String, Object> extra = new LinkedHashMap<>();

    public void setExtra(Map<String, Object> extra) {
        this.extra = extra == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extra);
    }

    @JsonAnySetter
    public void captureLegacyField(String key, Object value) {
        extra.put(key, value);
    }
}
