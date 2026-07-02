package com.familyagent.module.agent.harness;

/**
 * Request-scoped context for an Agent tool call.
 */
public record AgentRunContext(
        String requestId,
        Long familyId,
        Long viewerUserId,
        Long sessionId,
        String agentMode,
        String subject,
        String contextLabel
) {
}
