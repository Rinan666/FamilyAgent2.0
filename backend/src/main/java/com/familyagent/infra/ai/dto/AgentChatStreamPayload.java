package com.familyagent.infra.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AgentChatStreamPayload(
        @JsonProperty("member_message") String memberMessage,
        List<HistoryMessage> history,
        String subject,
        @JsonProperty("knowledge_point") String knowledgePoint,
        @JsonProperty("memory_context") String memoryContext,
        @JsonProperty("viewer_role") String viewerRole,
        @JsonProperty("target_role") String targetRole,
        @JsonProperty("response_mode") String responseMode,
        @JsonProperty("client_timestamp") String clientTimestamp,
        @JsonProperty("client_timezone") String clientTimezone
) {
    public AgentChatStreamPayload {
        memberMessage = valueOrEmpty(memberMessage);
        history = history == null ? List.of() : List.copyOf(history);
        subject = valueOrDefault(subject, "FamilyAgent");
        knowledgePoint = valueOrDefault(knowledgePoint, "family_memory");
        memoryContext = valueOrEmpty(memoryContext);
        viewerRole = valueOrDefault(viewerRole, "MEMBER");
        targetRole = valueOrDefault(targetRole, "MEMBER");
        responseMode = valueOrDefault(responseMode, "think");
        clientTimestamp = valueOrEmpty(clientTimestamp);
        clientTimezone = valueOrEmpty(clientTimezone);
    }

    public record HistoryMessage(String role, String content) {
        public HistoryMessage {
            role = valueOrDefault(role, "user");
            content = valueOrEmpty(content);
        }
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
