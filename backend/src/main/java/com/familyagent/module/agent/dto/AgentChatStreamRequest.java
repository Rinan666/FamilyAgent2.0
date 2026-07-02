package com.familyagent.module.agent.dto;

import com.familyagent.infra.ai.dto.AgentChatStreamPayload;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class AgentChatStreamRequest {

    private static final String FAMILY_MEMORY_CONTEXT = "family_memory";
    private static final String MIRROR_AGENT_CONTEXT = "mirror_agent";
    private static final String PERSONA_MEMBER_CONTEXT = "persona_member";

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

    @JsonProperty("family_id")
    private Long familyId;

    @Positive
    @JsonProperty("target_user_id")
    private Long targetUserId;

    @Positive
    @JsonProperty("target_persona_id")
    private Long targetPersonaId;

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

    public AgentChatStreamPayload toAiPayload() {
        return toAiPayload(memoryContext);
    }

    public AgentChatStreamPayload toAiPayload(String effectiveMemoryContext) {
        return new AgentChatStreamPayload(
                memberMessage,
                history == null ? List.of() : history.stream().map(HistoryMessage::toAiPayload).toList(),
                subject,
                knowledgePoint,
                effectiveMemoryContext,
                viewerRole,
                targetRole,
                responseMode,
                clientTimestamp,
                clientTimezone
        );
    }

    public boolean shouldUseServerFamilyMemoryContext() {
        return familyId != null
                && FAMILY_MEMORY_CONTEXT.equalsIgnoreCase(contextLabel())
                && !"quick".equalsIgnoreCase(responseMode == null ? "" : responseMode.trim());
    }

    public boolean shouldUseServerMirrorContext() {
        return familyId != null
                && targetUserId != null
                && MIRROR_AGENT_CONTEXT.equalsIgnoreCase(contextLabel());
    }

    public boolean shouldUseServerPersonaContext() {
        return familyId != null
                && targetPersonaId != null
                && PERSONA_MEMBER_CONTEXT.equalsIgnoreCase(contextLabel());
    }

    public List<String> userHistoryContents() {
        return history == null ? List.of() : history.stream()
                .filter(item -> "user".equals(item.getRole()))
                .map(HistoryMessage::getContent)
                .filter(content -> content != null && !content.isBlank())
                .toList();
    }

    public boolean isThinkMode() {
        return !"quick".equalsIgnoreCase(responseMode == null ? "" : responseMode.trim());
    }

    private String contextLabel() {
        return knowledgePoint == null ? "" : knowledgePoint.trim();
    }

    @Data
    public static class HistoryMessage {
        @Pattern(regexp = "^(user|assistant)$")
        @Size(max = 32)
        private String role;

        @Size(max = 4000)
        private String content;

        private AgentChatStreamPayload.HistoryMessage toAiPayload() {
            return new AgentChatStreamPayload.HistoryMessage(role, content);
        }
    }
}
