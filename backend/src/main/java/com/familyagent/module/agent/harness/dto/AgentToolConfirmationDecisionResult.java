package com.familyagent.module.agent.harness.dto;

public record AgentToolConfirmationDecisionResult(
        AgentToolConfirmationVO confirmation,
        AgentToolCallResult<?> toolResult
) {
}
