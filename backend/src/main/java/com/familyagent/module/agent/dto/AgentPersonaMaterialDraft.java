package com.familyagent.module.agent.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.List;

@Data
public class AgentPersonaMaterialDraft {

    private Profile profile;
    private List<MaterialCard> materials;
    private String reason;

    @Data
    public static class Profile {
        private String name;
        private String description;

        @JsonAlias("era_identity")
        private String eraIdentity;

        private String values;

        @JsonAlias("speaking_style")
        private String speakingStyle;

        private String personality;
    }

    @Data
    public static class MaterialCard {
        private String title;
        private String content;
        private List<String> tags;
    }
}
