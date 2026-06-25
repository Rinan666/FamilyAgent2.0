package com.familyagent.module.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AgentChatStreamRequest {

    @NotBlank
    @Size(max = 8000)
    @JsonProperty("member_message")
    private String memberMessage;

    @Valid
    @Size(max = 20)
    private List<HistoryMessage> history = List.of();

    @Size(max = 128)
    private String subject = "FamilyAgent";

    @Size(max = 128)
    @JsonProperty("knowledge_point")
    private String knowledgePoint = "family_memory";

    @Size(max = 12000)
    @JsonProperty("memory_context")
    private String memoryContext = "";

    @Size(max = 32)
    @JsonProperty("viewer_role")
    private String viewerRole = "MEMBER";

    @Size(max = 32)
    @JsonProperty("target_role")
    private String targetRole = "MEMBER";

    @Size(max = 16)
    @JsonProperty("response_mode")
    private String responseMode = "think";

    @Size(max = 64)
    @JsonProperty("client_timestamp")
    private String clientTimestamp = "";

    @Size(max = 64)
    @JsonProperty("client_timezone")
    private String clientTimezone = "";

    public Map<String, Object> toAiPayload() {
        return Map.of(
                "member_message", valueOrEmpty(memberMessage),
                "history", history == null ? List.of() : history.stream().map(HistoryMessage::toAiPayload).toList(),
                "subject", valueOrDefault(subject, "FamilyAgent"),
                "knowledge_point", valueOrDefault(knowledgePoint, "family_memory"),
                "memory_context", valueOrEmpty(memoryContext),
                "viewer_role", valueOrDefault(viewerRole, "MEMBER"),
                "target_role", valueOrDefault(targetRole, "MEMBER"),
                "response_mode", valueOrDefault(responseMode, "think"),
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
        @Pattern(regexp = "^(user|assistant)$")
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
