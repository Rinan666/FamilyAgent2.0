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
            case PERSONAL_MEMORY -> "PERSONAL_MEMORY";
            case DIARY, FAMILY_MEMORY, GROWTH_GUARD -> "FAMILY_MEMORY";
        };
    }
}
