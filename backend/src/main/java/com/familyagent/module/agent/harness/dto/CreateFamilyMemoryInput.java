package com.familyagent.module.agent.harness.dto;

public record CreateFamilyMemoryInput(
        String content,
        String type,
        String scope,
        String summary,
        Integer importance
) {
}
