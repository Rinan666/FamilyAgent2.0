package com.familyagent.module.diary.service;

import com.familyagent.common.constant.DiarySource;
import com.familyagent.common.constant.EntityStatus;
import com.familyagent.common.constant.MediaRecordType;
import com.familyagent.module.diary.dto.DiaryEntryMetadata;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class DiaryEntryMetadataSupport {

    private static final String MERGE_POLICY = "MANUAL_SELF_SINGLE_CANDIDATE";

    private DiaryEntryMetadataSupport() {
    }

    static Map<String, Object> build(
            DiaryEntryMetadata requestMetadata,
            String diaryDate,
            int singleEntryMaxChars,
            int mergedEntryMaxChars) {
        Map<String, Object> metadata = requestMetadata == null
                ? new HashMap<>()
                : new HashMap<>(requestMetadata.toMap());
        metadata.putIfAbsent("status", EntityStatus.ACTIVE.name());
        metadata.put("sourceModule", MediaRecordType.DIARY.name());
        metadata.put("diaryDate", diaryDate);
        metadata.putIfAbsent("mergePolicy", MERGE_POLICY);
        metadata.putIfAbsent("source", DiarySource.DIARY_MANUAL.name());
        metadata.putIfAbsent("singleMaxChars", singleEntryMaxChars);
        metadata.putIfAbsent("mergedMaxChars", mergedEntryMaxChars);
        return metadata;
    }

    static boolean allowsAutoMerge(DiaryEntryMetadata metadata) {
        if (metadata != null && Boolean.TRUE.equals(metadata.getDisableAutoMerge())) {
            return false;
        }
        if (metadata != null
                && metadata.getRelatedUserId() != null
                && metadata.getRelatedUserId() != 0) {
            return false;
        }
        return DiarySource.DIARY_MANUAL.name().equals(resolveEntrySource(metadata));
    }

    static String resolveEntrySource(DiaryEntryMetadata metadata) {
        String source = metadata == null ? null : metadata.getSource();
        String normalized = source == null ? "" : source.trim().toUpperCase(Locale.ROOT);
        return normalized.isEmpty() ? DiarySource.DIARY_MANUAL.name() : normalized;
    }

    static Map<String, Object> merge(Object currentMetadata, DiaryEntryMetadata nextMetadata) {
        return merge(
                currentMetadata,
                nextMetadata == null ? null : nextMetadata.toMap());
    }

    static Map<String, Object> merge(Object currentMetadata, Map<String, Object> nextMetadata) {
        Map<String, Object> metadata = mutableMap(currentMetadata);
        if (nextMetadata != null) {
            metadata.putAll(nextMetadata);
        }
        metadata.putIfAbsent("status", EntityStatus.ACTIVE.name());
        metadata.put("sourceModule", MediaRecordType.DIARY.name());
        return metadata;
    }

    static String resolveDiaryDate(DiaryEntryMetadata metadata) {
        Object eventAt = metadata == null ? null : firstNonNull(
                metadata.getEventAt(),
                metadata.getRecordedAt(),
                metadata.getSavedFromFamilyChatAt());
        LocalDate date = parseDate(eventAt);
        return (date == null ? LocalDate.now() : date).toString();
    }

    private static Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null && !String.valueOf(value).isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static LocalDate parseDate(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.length() >= 10) {
            try {
                return LocalDate.parse(text.substring(0, 10));
            } catch (DateTimeParseException ignored) {
                // Try ISO date-time below.
            }
        }
        try {
            return OffsetDateTime.parse(text).toLocalDate();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mutableMap(Object metadata) {
        if (metadata instanceof Map<?, ?> map) {
            return new HashMap<>((Map<String, Object>) map);
        }
        return new HashMap<>();
    }
}
