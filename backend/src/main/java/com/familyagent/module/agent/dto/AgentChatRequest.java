package com.familyagent.module.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Non-stream family chat request for mobile clients.
 */
@Data
public class AgentChatRequest {

    @NotNull
    private Long familyId;

    @NotBlank
    @Size(max = 8000)
    private String message;

    @Valid
    @Size(max = 20)
    private List<HistoryMessage> history = List.of();

    @Size(max = 64)
    @JsonProperty("client_timestamp")
    private String clientTimestamp = "";

    @Size(max = 64)
    @JsonProperty("client_timezone")
    private String clientTimezone = "";

    public Map<String, Object> toAiPayload(String subject, String viewerRole, String memoryContext) {
        return Map.of(
                "member_message", valueOrEmpty(message),
                "history", history == null ? List.of() : history.stream().map(HistoryMessage::toAiPayload).toList(),
                "subject", valueOrDefault(subject, "FamilyAgent"),
                "knowledge_point", "family_memory",
                "memory_context", valueOrEmpty(memoryContext),
                "viewer_role", valueOrDefault(viewerRole, "MEMBER"),
                "target_role", "MEMBER",
                "response_mode", "think",
                "client_timestamp", valueOrEmpty(clientTimestamp),
                "client_timezone", valueOrEmpty(clientTimezone)
        );
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    @Data
    public static class HistoryMessage {
        @Size(max = 32)
        private String role;

        @Size(max = 4000)
        private String content;

        private Map<String, Object> toAiPayload() {
            return Map.of(
                    "role", valueOrDefault(role, "user"),
                    "content", valueOrEmpty(content)
            );
        }
    }
}
