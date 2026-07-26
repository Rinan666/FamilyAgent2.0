package com.familyagent.module.memory.service;

import com.familyagent.common.constant.MemoryOriginType;
import com.familyagent.module.memory.entity.MemoryEntry;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class UnifiedMemoryIndexMetadataAssembler {

    public Map<String, Object> enrich(MemoryEntry entry) {
        Map<String, Object> metadata = mutableMetadata(entry.getMetadata());
        if (MemoryOriginType.DIARY.name().equals(entry.getOriginType())) {
            Map<?, ?> diary = nested(metadata, "legacyDiary");
            return MemoryIndexMetadataBuilder.enrichDiary(
                    metadata,
                    entry.getContent(),
                    text(diary.get("entryType"), "DAILY"),
                    text(diary.get("mood"), null),
                    entry.getTags());
        }
        if (MemoryOriginType.GROWTH.name().equals(entry.getOriginType())) {
            Map<?, ?> growth = nested(metadata, "legacyGrowth");
            return MemoryIndexMetadataBuilder.enrichGrowth(
                    metadata,
                    entry.getContent(),
                    text(growth.get("category"), "OTHER"),
                    integer(growth.get("severity"), entry.getImportance() == null ? 3 : entry.getImportance()),
                    occurredDate(entry));
        }
        return MemoryIndexMetadataBuilder.enrichFamilyMemory(
                metadata,
                entry.getContent(),
                entry.getSummary(),
                entry.getType(),
                entry.getImportance() == null ? 3 : entry.getImportance());
    }

    private static LocalDate occurredDate(MemoryEntry entry) {
        return entry.getOccurredAt() == null ? null : entry.getOccurredAt().toLocalDate();
    }

    private static Map<?, ?> nested(Map<String, Object> metadata, String key) {
        return metadata.get(key) instanceof Map<?, ?> map ? map : Map.of();
    }

    private static String text(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private static int integer(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Map<String, Object> mutableMetadata(Object metadata) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (metadata instanceof Map<?, ?> map) {
            map.forEach((key, value) -> values.put(String.valueOf(key), value));
        }
        return values;
    }
}
