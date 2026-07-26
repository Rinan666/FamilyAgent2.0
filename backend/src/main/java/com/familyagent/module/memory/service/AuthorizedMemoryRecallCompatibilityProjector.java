package com.familyagent.module.memory.service;

import com.familyagent.common.constant.DiaryRecallSource;
import com.familyagent.module.diary.entity.DiaryEntry;
import com.familyagent.module.growth.entity.GrowthGuardRecord;
import com.familyagent.module.memory.dto.AuthorizedMemoryRecallCandidate;
import com.familyagent.module.memory.entity.MemoryEntry;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AuthorizedMemoryRecallCompatibilityProjector {

    public List<DiaryEntry> diaries(List<AuthorizedMemoryRecallCandidate> candidates) {
        return candidates.stream().map(this::diary).toList();
    }

    public List<MemoryEntry> memories(List<AuthorizedMemoryRecallCandidate> candidates) {
        return candidates.stream().map(AuthorizedMemoryRecallCandidate::entry).toList();
    }

    public List<GrowthGuardRecord> growthRecords(List<AuthorizedMemoryRecallCandidate> candidates) {
        return candidates.stream().map(this::growthRecord).toList();
    }

    private DiaryEntry diary(AuthorizedMemoryRecallCandidate candidate) {
        MemoryEntry memory = candidate.entry();
        Map<String, Object> legacy = nestedMap(memory.getMetadata(), "legacyDiary");
        DiaryEntry diary = new DiaryEntry();
        diary.setId(candidate.publicSourceId());
        diary.setUserId(memory.getUserId());
        diary.setFamilyId(memory.getFamilyId());
        diary.setRawText(memory.getContent());
        diary.setStructured(Map.of(
                "title", value(memory.getTitle()),
                "entryType", value(legacy.getOrDefault("entryType", "DAILY"))));
        diary.setMood(text(legacy.get("mood")));
        diary.setTags(memory.getTags());
        diary.setPrivacyLevel(memory.getScope());
        diary.setVisibility(memory.getScope());
        diary.setSource(text(legacy.get("source")));
        diary.setVoiceUrl(text(legacy.get("voiceUrl")));
        diary.setMetadata(withMirrorSource(memory.getMetadata(), candidate.mirrorSource()));
        diary.setCreatedAt(memory.getCreatedAt());
        diary.setUpdatedAt(memory.getUpdatedAt());
        return diary;
    }

    private GrowthGuardRecord growthRecord(AuthorizedMemoryRecallCandidate candidate) {
        MemoryEntry memory = candidate.entry();
        Map<String, Object> legacy = nestedMap(memory.getMetadata(), "legacyGrowth");
        GrowthGuardRecord record = new GrowthGuardRecord();
        record.setId(candidate.publicSourceId());
        record.setFamilyId(memory.getFamilyId());
        record.setTargetUserId(candidate.subjectUserId());
        record.setCreatedBy(memory.getUserId());
        record.setCategory(firstNonBlank(text(legacy.get("category")), memory.getTitle(), "OTHER"));
        record.setContent(memory.getContent());
        record.setSeverity(integer(legacy.get("severity"), memory.getImportance()));
        record.setObservedAt(memory.getOccurredAt() == null ? null : memory.getOccurredAt().toLocalDate());
        record.setFollowUpAt(date(legacy.get("followUpAt")));
        record.setVisibility(memory.getScope());
        record.setStatus(memory.getStatus());
        record.setMetadata(memory.getMetadata());
        record.setCreatedAt(memory.getCreatedAt());
        record.setUpdatedAt(memory.getUpdatedAt());
        return record;
    }

    private static Object withMirrorSource(Object metadata, DiaryRecallSource source) {
        if (source == null) {
            return metadata;
        }
        Map<String, Object> values = mutableMap(metadata);
        values.put(DiaryRecallSource.METADATA_KEY, source.name());
        return values;
    }

    private static Map<String, Object> nestedMap(Object metadata, String key) {
        if (metadata instanceof Map<?, ?> map && map.get(key) instanceof Map<?, ?> nested) {
            Map<String, Object> values = new LinkedHashMap<>();
            nested.forEach((nestedKey, nestedValue) -> values.put(String.valueOf(nestedKey), nestedValue));
            return values;
        }
        return Map.of();
    }

    private static Map<String, Object> mutableMap(Object metadata) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (metadata instanceof Map<?, ?> map) {
            map.forEach((key, value) -> values.put(String.valueOf(key), value));
        }
        return values;
    }

    private static Integer integer(Object value, Integer fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static LocalDate date(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(String.valueOf(value));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
