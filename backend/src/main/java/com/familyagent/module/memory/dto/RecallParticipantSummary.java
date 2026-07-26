package com.familyagent.module.memory.dto;

public record RecallParticipantSummary(
        Long userId,
        String name,
        String relationshipToViewer,
        String relationshipToTarget,
        boolean currentViewer,
        boolean currentTarget) {
}
