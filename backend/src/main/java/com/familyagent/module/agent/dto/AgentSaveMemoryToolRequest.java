package com.familyagent.module.agent.dto;

import com.familyagent.module.agent.harness.dto.AgentSaveMemoryMetadata;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class AgentSaveMemoryToolRequest {

    @NotNull
    private Long familyId;

    @NotBlank
    private String writeCategory;

    @NotBlank
    private String content;

    private String title;
    private List<String> tags;
    private String visibility;
    private Long relatedUserId;
    private String diaryEntryType;
    private String memoryType;
    private String growthCategory;

    @Min(1)
    @Max(5)
    private Integer growthSeverity;

    private String requestId;
    private Long sessionId;
    private String agentMode;
    private String subject;
    private String contextLabel;
    private AgentSaveMemoryMetadata metadata;
}
