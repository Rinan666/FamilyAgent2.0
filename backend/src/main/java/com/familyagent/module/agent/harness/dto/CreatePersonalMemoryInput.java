package com.familyagent.module.agent.harness.dto;

import java.util.List;

public record CreatePersonalMemoryInput(
        String content,
        String type,
        String visibility,
        String summary,
        Integer importance,
        List<Long> selectedFamilyIds,
        AgentSaveMemoryMetadata metadata) {

    public CreatePersonalMemoryInput {
        selectedFamilyIds = selectedFamilyIds == null ? List.of() : List.copyOf(selectedFamilyIds);
    }
}
