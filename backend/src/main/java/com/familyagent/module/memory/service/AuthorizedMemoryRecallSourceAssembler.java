package com.familyagent.module.memory.service;

import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.memory.dto.RecallSourceSummary;
import com.familyagent.module.memory.entity.MemoryEntry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class AuthorizedMemoryRecallSourceAssembler {

    public List<RecallSourceSummary> assemble(
            List<DiaryEntry> diaries,
            List<MemoryEntry> memories,
            List<GrowthGuardRecord> growthRecords) {
        List<RecallSourceSummary> summaries = new ArrayList<>();
        appendDiaries(summaries, diaries);
        appendMemories(summaries, memories);
        appendGrowthRecords(summaries, growthRecords);
        return summaries;
    }

    private static void appendDiaries(List<RecallSourceSummary> summaries, List<DiaryEntry> entries) {
        for (DiaryEntry entry : entries) {
            if (entry == null || entry.getId() == null) {
                continue;
            }
            Map<?, ?> index = metadataIndex(entry.getMetadata());
            summaries.add(RecallSourceSummary.builder()
                    .id("diary-" + entry.getId())
                    .sourceType("LIFE_RECORD")
                    .title(firstNonBlank(
                            structuredText(entry.getStructured(), "title"),
                            structuredText(entry.getStructured(), "entryType"),
                            "每日记录"))
                    .snippet(snippet(entry.getRawText()))
                    .visibility(entry.getVisibility())
                    .temporalLayer(asString(index.get("temporalLayer")))
                    .topics(stringList(index.get("topics")))
                    .scenes(stringList(index.get("scenes")))
                    .build());
        }
    }

    private static void appendMemories(List<RecallSourceSummary> summaries, List<MemoryEntry> entries) {
        for (MemoryEntry entry : entries) {
            if (entry == null || entry.getId() == null) {
                continue;
            }
            Map<?, ?> index = metadataIndex(entry.getMetadata());
            summaries.add(RecallSourceSummary.builder()
                    .id("memory-" + entry.getId())
                    .sourceType("FAMILY_EXPERIENCE")
                    .title(firstNonBlank(entry.getSummary(), entry.getType(), "经验沉淀"))
                    .snippet(snippet(firstNonBlank(entry.getSummary(), entry.getContent(), "")))
                    .visibility(entry.getScope())
                    .temporalLayer(asString(index.get("temporalLayer")))
                    .topics(stringList(index.get("topics")))
                    .scenes(stringList(index.get("scenes")))
                    .build());
        }
    }

    private static void appendGrowthRecords(
            List<RecallSourceSummary> summaries,
            List<GrowthGuardRecord> records) {
        for (GrowthGuardRecord record : records) {
            if (record == null || record.getId() == null) {
                continue;
            }
            Map<?, ?> index = metadataIndex(record.getMetadata());
            summaries.add(RecallSourceSummary.builder()
                    .id("growth-" + record.getId())
                    .sourceType("GROWTH_OBSERVATION")
                    .title(firstNonBlank(record.getCategory(), "成长观察"))
                    .snippet(snippet(record.getContent()))
                    .visibility(record.getVisibility())
                    .temporalLayer(asString(index.get("temporalLayer")))
                    .topics(stringList(index.get("topics")))
                    .scenes(stringList(index.get("scenes")))
                    .build());
        }
    }

    private static Map<?, ?> metadataIndex(Object metadata) {
        if (metadata instanceof Map<?, ?> map && map.get("index") instanceof Map<?, ?> index) {
            return index;
        }
        return Map.of();
    }

    private static String structuredText(Object structured, String key) {
        if (structured instanceof Map<?, ?> map) {
            return asString(map.get(key));
        }
        return "";
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .filter(item -> !item.isBlank())
                .limit(6)
                .toList();
    }

    private static String snippet(String value) {
        String text = firstNonBlank(value, "")
                .replaceAll("\\s+", " ")
                .trim();
        return text.length() <= 90 ? text : text.substring(0, 90) + "...";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
