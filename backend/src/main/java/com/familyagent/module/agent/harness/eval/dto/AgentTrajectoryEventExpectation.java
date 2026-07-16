package com.familyagent.module.agent.harness.eval.dto;

import com.familyagent.module.agent.harness.constant.AgentReplayEventType;

public record AgentTrajectoryEventExpectation(
        AgentReplayEventType eventType,
        String operation,
        String status,
        String errorCode,
        boolean degraded,
        String inputType) {
}
