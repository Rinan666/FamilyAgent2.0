package com.familyagent.common.constant;

public enum MemoryRecallSourceType {
    LIFE_RECORD("diary", "DIARY"),
    FAMILY_EXPERIENCE("memory", "MEMORY"),
    GROWTH_OBSERVATION("growth", "GROWTH_OBSERVATION"),
    PERSONAL_MEMORY("memory", "MEMORY");

    private final String publicIdPrefix;
    private final String embeddingSourceType;

    MemoryRecallSourceType(String publicIdPrefix, String embeddingSourceType) {
        this.publicIdPrefix = publicIdPrefix;
        this.embeddingSourceType = embeddingSourceType;
    }

    public String publicIdPrefix() {
        return publicIdPrefix;
    }

    public String embeddingSourceType() {
        return embeddingSourceType;
    }
}
