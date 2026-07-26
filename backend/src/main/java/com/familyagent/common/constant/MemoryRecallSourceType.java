package com.familyagent.common.constant;

public enum MemoryRecallSourceType {
    LIFE_RECORD("diary"),
    FAMILY_EXPERIENCE("memory"),
    GROWTH_OBSERVATION("growth"),
    PERSONAL_MEMORY("memory");

    private final String publicIdPrefix;

    MemoryRecallSourceType(String publicIdPrefix) {
        this.publicIdPrefix = publicIdPrefix;
    }

    public String publicIdPrefix() {
        return publicIdPrefix;
    }
}
