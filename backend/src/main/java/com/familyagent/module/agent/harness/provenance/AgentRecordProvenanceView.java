package com.familyagent.module.agent.harness.provenance;

import java.time.LocalDateTime;

public record AgentRecordProvenanceView(
        AgentCreatedRecordType recordType,
        Long recordId,
        Long agentRunId,
        Long toolCallId,
        String toolName,
        String toolVersion,
        LocalDateTime createdAt) {
}
