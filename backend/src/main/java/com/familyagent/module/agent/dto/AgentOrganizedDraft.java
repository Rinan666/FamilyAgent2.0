package com.familyagent.module.agent.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.List;

@Data
public class AgentOrganizedDraft {

    private String title;
    private String content;
    private List<String> tags;

    @JsonAlias("memory_type")
    private String memoryType;

    private String visibility;
    private String reason;
}
