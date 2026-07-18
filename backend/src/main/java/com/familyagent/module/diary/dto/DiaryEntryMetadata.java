package com.familyagent.module.diary.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class DiaryEntryMetadata {

    private String source;
    private Long relatedUserId;
    private String relatedMemberName;
    private String eventAt;
    private String recordedAt;
    private String savedFromFamilyChatAt;
    private Boolean disableAutoMerge;

    @JsonIgnore
    @EqualsAndHashCode.Exclude
    @Getter(AccessLevel.NONE)
    @ToString.Exclude
    private final Map<String, Object> extra = new LinkedHashMap<>();

    @JsonAnySetter
    public void putExtra(String key, Object value) {
        if (key != null && !key.isBlank()) {
            extra.put(key, value);
        }
    }

    @JsonAnyGetter
    public Map<String, Object> extraProperties() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(extra));
    }

    public Map<String, Object> toMap() {
        Map<String, Object> metadata = new LinkedHashMap<>(extra);
        put(metadata, "source", source);
        put(metadata, "relatedUserId", relatedUserId);
        put(metadata, "relatedMemberName", relatedMemberName);
        put(metadata, "eventAt", eventAt);
        put(metadata, "recordedAt", recordedAt);
        put(metadata, "savedFromFamilyChatAt", savedFromFamilyChatAt);
        put(metadata, "disableAutoMerge", disableAutoMerge);
        return metadata;
    }

    public static DiaryEntryMetadata fromMap(Map<String, ?> values) {
        DiaryEntryMetadata metadata = new DiaryEntryMetadata();
        if (values != null) {
            values.forEach(metadata::putValue);
        }
        return metadata;
    }

    private void putValue(String key, Object value) {
        if (key == null || key.isBlank()) {
            return;
        }
        switch (key) {
            case "source" -> source = text(value);
            case "relatedUserId" -> {
                Long parsed = longValue(value);
                if (parsed == null && value != null) {
                    extra.put(key, value);
                } else {
                    relatedUserId = parsed;
                }
            }
            case "relatedMemberName" -> relatedMemberName = text(value);
            case "eventAt" -> eventAt = text(value);
            case "recordedAt" -> recordedAt = text(value);
            case "savedFromFamilyChatAt" -> savedFromFamilyChatAt = text(value);
            case "disableAutoMerge" -> {
                Boolean parsed = booleanValue(value);
                if (parsed == null && value != null) {
                    extra.put(key, value);
                } else {
                    disableAutoMerge = parsed;
                }
            }
            default -> extra.put(key, value);
        }
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? null : Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Boolean booleanValue(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(normalized)) {
            return true;
        }
        if ("false".equalsIgnoreCase(normalized)) {
            return false;
        }
        return null;
    }

    private static void put(Map<String, Object> metadata, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text) {
            if (!text.isBlank()) {
                metadata.put(key, text.trim());
            }
            return;
        }
        metadata.put(key, value);
    }
}
