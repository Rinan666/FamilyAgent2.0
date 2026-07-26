package com.familyagent.module.agent.harness.dto;

import java.util.List;

public record CreateFamilyMemoryInput(
        String content,
        String type,
        String scope,
        String summary,
        Integer importance,
        Long relatedUserId,
        List<String> tags,
        AgentSaveMemoryMetadata metadata
) {
}
