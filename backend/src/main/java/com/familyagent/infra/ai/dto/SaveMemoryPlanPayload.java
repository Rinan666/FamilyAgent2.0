package com.familyagent.infra.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SaveMemoryPlanPayload(
        String message,
        @JsonProperty("family_context") String familyContext,
        @JsonProperty("conversation_context") List<ConversationMessage> conversationContext,
        @JsonProperty("target_member_name") String targetMemberName,
        @JsonProperty("viewer_role") String viewerRole
) {
    public record ConversationMessage(String role, String content) {
    }
}
