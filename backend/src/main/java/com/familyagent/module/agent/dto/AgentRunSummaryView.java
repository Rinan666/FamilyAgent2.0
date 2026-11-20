package com.familyagent.module.agent.dto;

import java.time.LocalDateTime;

public record AgentRunSummaryView(
        Long runId,
        Long sessionId,
        String contextLabel,
        String status,
        String errorCode,
        boolean retryable,
        LocalDateTime startedAt,
        LocalDateTime completedAt) {
}
