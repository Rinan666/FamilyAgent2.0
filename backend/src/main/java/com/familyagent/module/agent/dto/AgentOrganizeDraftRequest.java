package com.familyagent.module.agent.dto;

import com.familyagent.infra.ai.dto.OrganizeDraftPayload;
import com.familyagent.module.agent.constant.AgentDraftScene;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentOrganizeDraftRequest {

    @NotNull
    @Positive
    private Long familyId;

    @NotBlank
    @Size(min = 4, max = 3000)
    private String content;

    @NotNull
    private AgentDraftScene scene = AgentDraftScene.DIARY;

    @Size(max = 1200)
    private String familyContext = "";

    @Size(max = 50)
    private String currentType = "";

    @Size(max = 50)
    private String currentVisibility = "";

    @Size(max = 100)
    private String target = "";

    @Size(max = 128)
    private String requestId;

    public OrganizeDraftPayload toAiPayload() {
        return new OrganizeDraftPayload(
                content,
                scene.name(),
                value(familyContext),
                value(currentType),
                value(currentVisibility),
                value(target));
    }

    private String value(String text) {
        return text == null ? "" : text;
    }
}
