package com.familyagent.module.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class AgentMemorySavePlan {

    @JsonProperty("should_save")
    private boolean shouldSave;

    @JsonProperty("memory_library")
    private String memoryLibrary;

    @JsonProperty("memory_type")
    private String memoryType;

    private String content;
    private String title;
    private String summary;
    private String visibility;
    private Integer importance;
    private List<String> tags;
    private String reason;

    @JsonProperty("confirmation_message")
    private String confirmationMessage;
}
