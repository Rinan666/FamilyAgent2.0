package com.familyagent.module.agent.dto;

import com.familyagent.infra.ai.dto.SaveMemoryPlanPayload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class AgentSaveMemoryPlanRequest {

    @NotNull
    @Positive
    private Long familyId;

    @NotBlank
    @Size(max = 3000)
    private String message;

    @Size(max = 1200)
    private String familyContext = "";

    @Valid
    @Size(max = 20)
    private List<ConversationMessage> conversationContext = List.of();

    @Size(max = 100)
    private String targetMemberName = "";

    @Size(max = 32)
    private String viewerRole = "";

    @Pattern(regexp = "^[A-Z0-9_]{2,50}$")
    private String source = "FAMILY_AGENT_CHAT";

    @Size(max = 128)
    private String requestId;

    public SaveMemoryPlanPayload toAiPayload() {
        List<SaveMemoryPlanPayload.ConversationMessage> context = conversationContext == null
                ? List.of()
                : conversationContext.stream().map(ConversationMessage::toAiPayload).toList();
        return new SaveMemoryPlanPayload(
                message,
                familyContext == null ? "" : familyContext,
                context,
                targetMemberName == null ? "" : targetMemberName,
                viewerRole == null ? "" : viewerRole);
    }

    @Data
    public static class ConversationMessage {

        @Pattern(regexp = "^(user|assistant)$")
        private String role;

        @Size(max = 4000)
        private String content;

        private SaveMemoryPlanPayload.ConversationMessage toAiPayload() {
            return new SaveMemoryPlanPayload.ConversationMessage(role, content);
        }
    }
}
