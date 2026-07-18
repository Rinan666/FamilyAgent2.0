package com.familyagent.module.growth.dto;

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
public class GrowthGuardMetadata {

    private String source;
    private Long relatedUserId;
    private String relatedMemberName;
    private String sourceType;
    private String followUpStatus;

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
        put(metadata, "sourceType", sourceType);
        put(metadata, "followUpStatus", followUpStatus);
        return metadata;
    }

    public static GrowthGuardMetadata fromMap(Map<String, ?> values) {
        GrowthGuardMetadata metadata = new GrowthGuardMetadata();
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
            case "sourceType" -> sourceType = text(value);
            case "followUpStatus" -> followUpStatus = text(value);
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
