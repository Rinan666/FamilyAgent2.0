package com.familyagent.module.agent.constant;

public enum AgentSaveTool {
    NONE,
    DIARY,
    PERSONAL_MEMORY,
    FAMILY_MEMORY,
    GROWTH_GUARD;

    public boolean requiresPersistence() {
        return this != NONE;
    }

    public String savedRecordType() {
        return switch (this) {
            case NONE -> "NONE";
            case DIARY -> "DIARY_ENTRY";
            case PERSONAL_MEMORY -> "PERSONAL_MEMORY";
            case FAMILY_MEMORY -> "FAMILY_MEMORY";
            case GROWTH_GUARD -> "GROWTH_GUARD";
        };
    }
}
