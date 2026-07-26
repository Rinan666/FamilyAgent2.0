package com.familyagent.module.memory.facade;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UnifiedMemorySyncMetadata(
        LegacyDiaryMetadata legacyDiary,
        LegacyGrowthMetadata legacyGrowth) {

    public static UnifiedMemorySyncMetadata empty() {
        return new UnifiedMemorySyncMetadata(null, null);
    }

    public static UnifiedMemorySyncMetadata diary(
            String entryType,
            String mood,
            String source,
            String voiceUrl) {
        return new UnifiedMemorySyncMetadata(
                new LegacyDiaryMetadata(entryType, mood, source, voiceUrl),
                null);
    }

    public static UnifiedMemorySyncMetadata growth(
            String category,
            Integer severity,
            LocalDate followUpAt) {
        return new UnifiedMemorySyncMetadata(
                null,
                new LegacyGrowthMetadata(category, severity, followUpAt));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LegacyDiaryMetadata(
            String entryType,
            String mood,
            String source,
            String voiceUrl) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LegacyGrowthMetadata(
            String category,
            Integer severity,
            LocalDate followUpAt) {
    }
}
