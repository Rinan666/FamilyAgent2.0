package com.familyagent.module.agent.dto;

import com.familyagent.module.agent.harness.dto.AgentSaveMemoryMetadata;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class AgentSaveMemoryRequest {

    @NotNull
    private Long familyId;

    @NotBlank
    private String memoryLibrary;

    @NotBlank
    private String memoryType;

    @NotBlank
    private String content;

    private String title;
    private List<String> tags;
    private String visibility;
    private Long relatedUserId;
    private List<Long> selectedFamilyIds;

    private String requestId;
    private Long sessionId;
    private String agentMode;
    private String subject;
    private String contextLabel;
    private AgentSaveMemoryMetadata metadata;
}
