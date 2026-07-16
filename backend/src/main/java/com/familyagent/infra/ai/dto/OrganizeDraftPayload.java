package com.familyagent.infra.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OrganizeDraftPayload(
        String content,
        String scene,
        @JsonProperty("family_context") String familyContext,
        @JsonProperty("current_type") String currentType,
        @JsonProperty("current_visibility") String currentVisibility,
        String target
) {
}
