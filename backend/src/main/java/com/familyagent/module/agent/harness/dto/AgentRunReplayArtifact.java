package com.familyagent.module.agent.harness.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AgentRunReplayArtifact(
        RunSummary run,
        List<AgentReplayEvent> trajectory,
        Metrics metrics) {

    public AgentRunReplayArtifact {
        trajectory = trajectory == null ? List.of() : List.copyOf(trajectory);
    }

    public record RunSummary(
            Long runId,
            String requestRef,
            String status,
            String errorCode,
            LocalDateTime startedAt,
            LocalDateTime completedAt) {
    }

    public record Metrics(
            int eventCount,
            int failedEventCount,
            int degradedEventCount,
            long totalLatencyMs) {
    }
}
