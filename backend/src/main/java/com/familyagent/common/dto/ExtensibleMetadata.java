package com.familyagent.common.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class ExtensibleMetadata {

    @JsonIgnore
    private final Map<String, Object> extra = new LinkedHashMap<>();

    @JsonAnySetter
    public final void putExtra(String key, Object value) {
        if (key != null && !key.isBlank()) {
            extra.put(key, value);
        }
    }

    @JsonAnyGetter
    public final Map<String, Object> extraProperties() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(extra));
    }

    protected final Map<String, Object> copyExtraProperties() {
        return new LinkedHashMap<>(extra);
    }

    protected static String textValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    protected static Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? null : Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    protected static Boolean booleanValue(Object value) {
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

    protected static void putIfPresent(Map<String, Object> metadata, String key, Object value) {
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
