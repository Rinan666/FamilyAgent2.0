package com.familyagent.module.agent.harness.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AgentSaveMemoryMetadata {

    private String skillName;
    private String source;
    private String relationSource;
    private String familyName;
    private String viewerRole;
    private String savedFromMessageRole;
    private String memoryLibrary;
    private String memoryType;
    private String plannedTitle;
    private String plannedReason;
    private String visibility;
    private String confirmationPolicy;
    private String savedAt;
    private Long relatedUserId;
    private String relatedMemberName;
    private Long relatedPersonaId;
    private String relatedPersonaName;
    private String sourceType;
    private String scenario;
    private String target;
    private String followUpStatus;

    public Map<String, Object> toMap() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        put(metadata, "skillName", skillName);
        put(metadata, "source", source);
        put(metadata, "relationSource", relationSource);
        put(metadata, "familyName", familyName);
        put(metadata, "viewerRole", viewerRole);
        put(metadata, "savedFromMessageRole", savedFromMessageRole);
        put(metadata, "memoryLibrary", memoryLibrary);
        put(metadata, "memoryType", memoryType);
        put(metadata, "plannedTitle", plannedTitle);
        put(metadata, "plannedReason", plannedReason);
        put(metadata, "visibility", visibility);
        put(metadata, "confirmationPolicy", confirmationPolicy);
        put(metadata, "savedAt", savedAt);
        put(metadata, "relatedUserId", relatedUserId);
        put(metadata, "relatedMemberName", relatedMemberName);
        put(metadata, "relatedPersonaId", relatedPersonaId);
        put(metadata, "relatedPersonaName", relatedPersonaName);
        put(metadata, "sourceType", sourceType);
        put(metadata, "scenario", scenario);
        put(metadata, "target", target);
        put(metadata, "followUpStatus", followUpStatus);
        return metadata;
    }

    private static void put(Map<String, Object> metadata, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        metadata.put(key, value);
    }
}
