package com.familyagent.module.agent.harness.dto;

import com.familyagent.module.agent.harness.constant.AgentReplayEventType;

import java.time.LocalDateTime;

public record AgentReplayEvent(
        AgentReplayEventType eventType,
        String stepType,
        String operation,
        String status,
        String errorCode,
        String provider,
        String model,
        String promptVersion,
        String skillVersion,
        Long latencyMs,
        boolean degraded,
        String privacyCategories,
        String inputType,
        String spanId,
        String parentSpanId,
        LocalDateTime occurredAt,
        LocalDateTime completedAt) {
}
