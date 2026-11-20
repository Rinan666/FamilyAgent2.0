package com.familyagent.module.agent.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AgentRunView(
        Long runId,
        Long sessionId,
        String contextLabel,
        String status,
        String errorCode,
        boolean retryable,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        List<AgentRunEventView> events) {

    public AgentRunView {
        events = events == null ? List.of() : List.copyOf(events);
    }
}
