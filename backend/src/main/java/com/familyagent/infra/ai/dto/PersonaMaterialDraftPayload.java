package com.familyagent.infra.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PersonaMaterialDraftPayload(
        String content,
        Profile profile,
        @JsonProperty("family_context") String familyContext
) {
    public record Profile(
            String name,
            String description,
            @JsonProperty("era_identity") String eraIdentity,
            String values,
            @JsonProperty("speaking_style") String speakingStyle,
            String personality
    ) {
    }
}
