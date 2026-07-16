package com.familyagent.module.agent.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.List;

@Data
public class AgentOrganizedDraft {

    private String title;
    private String content;
    private List<String> tags;

    @JsonAlias("diary_entry_type")
    private String diaryEntryType;

    @JsonAlias("diary_visibility")
    private String diaryVisibility;

    @JsonAlias("memory_type")
    private String memoryType;

    @JsonAlias("memory_scope")
    private String memoryScope;

    @JsonAlias("growth_category")
    private String growthCategory;

    @JsonAlias("growth_severity")
    private Integer growthSeverity;

    private String scenario;
    private String reason;
}
