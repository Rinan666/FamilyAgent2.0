package com.familyagent.module.memory.facade;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UnifiedMemorySyncMetadata(
        LegacyDiaryMetadata legacyDiary,
        LegacyGrowthMetadata legacyGrowth,
        Map<String, Object> extra) {

    public UnifiedMemorySyncMetadata {
        extra = extra == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(extra));
    }

    public static UnifiedMemorySyncMetadata empty() {
        return new UnifiedMemorySyncMetadata(null, null, Map.of());
    }

    public static UnifiedMemorySyncMetadata diary(
            String entryType,
            String mood,
            String source,
            String voiceUrl,
            Map<String, Object> extra) {
        return new UnifiedMemorySyncMetadata(
                new LegacyDiaryMetadata(entryType, mood, source, voiceUrl),
                null,
                extra);
    }

    public static UnifiedMemorySyncMetadata growth(
            String category,
            Integer severity,
            LocalDate followUpAt,
            Map<String, Object> extra) {
        return new UnifiedMemorySyncMetadata(
                null,
                new LegacyGrowthMetadata(category, severity, followUpAt),
                extra);
    }

    public Map<String, Object> toPersistenceMap() {
        Map<String, Object> metadata = new LinkedHashMap<>(extra);
        if (legacyDiary != null) {
            metadata.put("legacyDiary", legacyDiary);
        }
        if (legacyGrowth != null) {
            metadata.put("legacyGrowth", legacyGrowth);
        }
        return metadata;
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
