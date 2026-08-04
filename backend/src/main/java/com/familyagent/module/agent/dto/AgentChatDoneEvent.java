package com.familyagent.module.agent.dto;

public record AgentChatDoneEvent(
        String type,
        boolean done,
        boolean degraded,
        String requestId,
        Long runId) {

    public AgentChatDoneEvent(boolean degraded, String requestId, Long runId) {
        this("done", true, degraded, requestId, runId);
    }
}
