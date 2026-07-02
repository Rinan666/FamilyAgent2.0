package com.familyagent.module.agent.harness.dto;

import com.familyagent.module.agent.harness.AgentRunContext;

/**
 * Typed request for executing one Agent tool.
 */
public record AgentToolCallRequest<I>(
        String toolName,
        AgentRunContext context,
        I input
) {
}
