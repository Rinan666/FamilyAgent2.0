package com.familyagent.module.mirror.service;

import com.familyagent.common.constant.MemoryType;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.memory.entity.MemoryEntry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MirrorTemporalLayerAnnotator {

    public void annotate(
            List<DiaryEntry> diaries,
            List<MemoryEntry> memories,
            List<GrowthGuardRecord> growthRecords) {
        if (diaries != null) {
            diaries.forEach(entry -> entry.setMetadata(mergeMetadata(
                    entry.getMetadata(),
                    layer(referenceTime(entry.getMetadata(), entry.getCreatedAt()), false, 0, TemporalKind.DIARY))));
        }
        if (memories != null) {
            memories.forEach(memory -> {
                LocalDateTime fallbackTime = memory.getUpdatedAt() == null
                        ? memory.getCreatedAt()
                        : memory.getUpdatedAt();
                TemporalLayer layer = layer(
                        referenceTime(memory.getMetadata(), fallbackTime),
                        isCoreMemory(memory),
                        memory.getImportance() == null ? 0 : memory.getImportance(),
                        kind(memory));
                memory.setMetadata(mergeMetadata(memory.getMetadata(), layer));
            });
        }
        if (growthRecords != null) {
            growthRecords.forEach(record -> {
                LocalDateTime fallbackTime = record.getObservedAt() == null
                        ? record.getCreatedAt()
                        : record.getObservedAt().atStartOfDay();
                TemporalLayer layer = layer(
                        referenceTime(record.getMetadata(), fallbackTime),
                        false,
                        record.getSeverity() == null ? 0 : record.getSeverity(),
                        TemporalKind.GROWTH_OBSERVATION);
                record.setMetadata(mergeMetadata(record.getMetadata(), layer));
            });
        }
    }

    LocalDateTime referenceTime(Object metadata, LocalDateTime fallback) {
        for (String key : List.of("eventAt", "occurredAt", "observedAt", "happenedAt", "recordedAt")) {
            LocalDateTime parsed = parseTime(metadataText(metadata, key));
            if (parsed != null) {
                return parsed;
            }
        }
        return fallback;
    }

    private static boolean isCoreMemory(MemoryEntry memory) {
        if (memory == null) {
            return false;
        }
        if ("true".equalsIgnoreCase(metadataText(memory.getMetadata(), "coreMemory"))) {
            return true;
        }
        if (memory.getImportance() != null && memory.getImportance() >= 4) {
            return true;
        }
        String type = memory.getType() == null ? "" : memory.getType();
        return MemoryType.ELDER_ADVICE.name().equals(type)
                || MemoryType.VALUE.name().equals(type)
                || MemoryType.FAMILY_STORY.name().equals(type);
    }

    private static TemporalKind kind(MemoryEntry memory) {
        if (memory == null) {
            return TemporalKind.FAMILY_MEMORY;
        }
        String sourceType = metadataText(memory.getMetadata(), "sourceType");
        String plannedTool = metadataText(memory.getMetadata(), "plannedTool");
        String type = memory.getType() == null ? "" : memory.getType();
        if ("GROWTH_OBSERVATION".equals(sourceType) || "GROWTH_GUARD".equals(plannedTool)) {
            return TemporalKind.GROWTH_OBSERVATION;
        }
        if ("LEARNING".equals(type) || "MISTAKE".equals(type)) {
            return TemporalKind.DIARY;
        }
        return TemporalKind.FAMILY_MEMORY;
    }

    private static TemporalLayer layer(
            LocalDateTime time,
            boolean coreMemory,
            int importance,
            TemporalKind kind) {
        if (coreMemory) {
            double weight = importance >= 5 ? 1.0 : 0.9;
            return new TemporalLayer(
                    "CORE_MEMORY",
                    "沉淀记忆",
                    BigDecimal.valueOf(weight),
                    "这类记录更像经验沉淀或价值观沉淀，时间衰减较慢。");
        }
        if (time == null) {
            return new TemporalLayer(
                    "IMPRESSION",
                    "印象",
                    BigDecimal.valueOf(0.35),
                    "缺少明确时间，只能作为模糊印象参考。");
        }
        long days = Math.max(0, Duration.between(time, LocalDateTime.now()).toDays());
        return switch (kind) {
            case GROWTH_OBSERVATION -> growthLayer(days);
            case FAMILY_MEMORY -> familyMemoryLayer(days);
            case DIARY -> diaryLayer(days);
        };
    }

    private static TemporalLayer diaryLayer(long days) {
        if (days <= 30) {
            return new TemporalLayer(
                    "FRESH",
                    "近期",
                    BigDecimal.valueOf(1.0),
                    "近期记录，可以作为当前线索，但仍需结合上下文。");
        }
        if (days <= 180) {
            return new TemporalLayer(
                    "FADING",
                    "淡出",
                    BigDecimal.valueOf(0.65),
                    "这件事正在淡出，只能说明一段时间内有过相关迹象。");
        }
        return new TemporalLayer(
                "IMPRESSION",
                "印象",
                BigDecimal.valueOf(0.35),
                "时间较久，只能作为过去印象，不能直接判断当前状态。");
    }

    private static TemporalLayer growthLayer(long days) {
        if (days <= 14) {
            return new TemporalLayer(
                    "FRESH",
                    "近期观察",
                    BigDecimal.valueOf(1.0),
                    "近期成长观察可以作为当前线索，但只能提示继续观察，不能当作诊断。");
        }
        if (days <= 90) {
            return new TemporalLayer(
                    "FADING",
                    "待复核",
                    BigDecimal.valueOf(0.55),
                    "这条成长观察已经过了一段时间，只能说明曾经出现过相关信号，需要复核后再判断现状。");
        }
        return new TemporalLayer(
                "IMPRESSION",
                "旧观察",
                BigDecimal.valueOf(0.25),
                "这条成长观察时间较久，不能表示当前状态，只能作为历史线索。");
    }

    private static TemporalLayer familyMemoryLayer(long days) {
        if (days <= 180) {
            return new TemporalLayer(
                    "FRESH",
                    "近期经验",
                    BigDecimal.valueOf(0.9),
                    "这条经验沉淀较近，可以优先作为当前场景参考，但仍要结合用户当下处境。");
        }
        if (days <= 730) {
            return new TemporalLayer(
                    "FADING",
                    "沉淀中",
                    BigDecimal.valueOf(0.7),
                    "这条经验沉淀正在沉淀，适合作为价值观或方法参考，不代表当前事实。");
        }
        return new TemporalLayer(
                "IMPRESSION",
                "远期经验",
                BigDecimal.valueOf(0.5),
                "这条经验时间较久，更适合作为家族历史和价值观背景，不能直接套用到当下。");
    }

    private static LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        try {
            return OffsetDateTime.parse(text).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // Try local date-time and date-only formats below.
        }
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
            // Try date-only format below.
        }
        try {
            return LocalDate.parse(text).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mergeMetadata(Object metadata, TemporalLayer layer) {
        Map<String, Object> next = new LinkedHashMap<>();
        if (metadata instanceof Map<?, ?> map) {
            next.putAll((Map<String, Object>) map);
        }
        next.put("temporalLayer", layer.code());
        next.put("temporalLayerLabel", layer.label());
        next.put("temporalWeight", layer.weight());
        next.put("temporalNote", layer.note());
        return next;
    }

    private static String metadataText(Object metadata, String key) {
        if (metadata instanceof Map<?, ?> map && map.get(key) != null) {
            return String.valueOf(map.get(key));
        }
        return "";
    }

    private enum TemporalKind {
        DIARY,
        GROWTH_OBSERVATION,
        FAMILY_MEMORY
    }

    private record TemporalLayer(String code, String label, BigDecimal weight, String note) {
    }
}
