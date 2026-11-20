package com.familyagent.module.agent.dto;

import com.familyagent.module.agent.harness.constant.AgentReplayEventType;

import java.time.LocalDateTime;

public record AgentRunEventView(
        Long id,
        AgentReplayEventType eventType,
        String stepType,
        String operation,
        String status,
        String errorCode,
        Long latencyMs,
        boolean degraded,
        LocalDateTime startedAt,
        LocalDateTime completedAt) {
}
