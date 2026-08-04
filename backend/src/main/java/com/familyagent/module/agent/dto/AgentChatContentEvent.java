package com.familyagent.module.agent.dto;

public record AgentChatContentEvent(
        String type,
        String content,
        String requestId,
        Long runId) {

    public AgentChatContentEvent(String content, String requestId, Long runId) {
        this("content", content == null ? "" : content, requestId, runId);
    }
}
