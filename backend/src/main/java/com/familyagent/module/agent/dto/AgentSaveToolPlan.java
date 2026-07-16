package com.familyagent.module.agent.dto;

import com.familyagent.module.agent.constant.AgentSaveTool;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentSaveToolPlan {

    @JsonProperty("should_save")
    private boolean shouldSave;

    private AgentSaveTool tool;
    private String content;
    private String title;
    private String summary;
    private String visibility;

    @JsonProperty("entry_type")
    private String entryType;

    @JsonProperty("memory_type")
    private String memoryType;

    private String scope;
    private String category;
    private Integer severity;
    private Integer importance;
    private List<String> tags;
    private String reason;

    @JsonProperty("confirmation_message")
    private String confirmationMessage;
}
