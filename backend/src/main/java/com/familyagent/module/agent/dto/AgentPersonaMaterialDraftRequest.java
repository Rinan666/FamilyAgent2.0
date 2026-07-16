package com.familyagent.module.agent.dto;

import com.familyagent.infra.ai.dto.PersonaMaterialDraftPayload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentPersonaMaterialDraftRequest {

    @NotNull
    @Positive
    private Long familyId;

    @NotBlank
    @Size(min = 8, max = 6000)
    private String content;

    @Valid
    @NotNull
    private Profile profile = new Profile();

    @Size(max = 1200)
    private String familyContext = "";

    @Size(max = 128)
    private String requestId;

    public PersonaMaterialDraftPayload toAiPayload() {
        return new PersonaMaterialDraftPayload(
                content,
                profile.toAiPayload(),
                familyContext == null ? "" : familyContext);
    }

    @Data
    public static class Profile {
        @Size(max = 100)
        private String name = "";

        @Size(max = 500)
        private String description = "";

        @Size(max = 200)
        private String eraIdentity = "";

        @Size(max = 1000)
        private String values = "";

        @Size(max = 1000)
        private String speakingStyle = "";

        @Size(max = 1000)
        private String personality = "";

        private PersonaMaterialDraftPayload.Profile toAiPayload() {
            return new PersonaMaterialDraftPayload.Profile(
                    value(name),
                    value(description),
                    value(eraIdentity),
                    value(values),
                    value(speakingStyle),
                    value(personality));
        }

        private String value(String text) {
            return text == null ? "" : text;
        }
    }
}
