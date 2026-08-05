package com.familyagent.infra.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OrganizeDraftPayload(
        String content,
        @JsonProperty("memory_library") String memoryLibrary,
        @JsonProperty("family_context") String familyContext,
        @JsonProperty("current_memory_type") String currentMemoryType,
        @JsonProperty("current_visibility") String currentVisibility,
        String target
) {
}
