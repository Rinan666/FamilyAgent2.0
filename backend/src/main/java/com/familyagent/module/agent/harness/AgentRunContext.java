package com.familyagent.module.agent.harness;

/**
 * Request-scoped context for an Agent tool call.
 */
public record AgentRunContext(
        Long runId,
        String requestId,
        Long familyId,
        Long viewerUserId,
        Long sessionId,
        String agentMode,
        String subject,
        String contextLabel,
        boolean completeRunAfterTool
) {

    public AgentRunContext(
            String requestId,
            Long familyId,
            Long viewerUserId,
            Long sessionId,
            String agentMode,
            String subject,
            String contextLabel) {
        this(null, requestId, familyId, viewerUserId, sessionId, agentMode, subject, contextLabel, true);
    }

    public AgentRunContext withRunId(Long value) {
        return new AgentRunContext(
                value,
                requestId,
                familyId,
                viewerUserId,
                sessionId,
                agentMode,
                subject,
                contextLabel,
                completeRunAfterTool);
    }
}
