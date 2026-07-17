package com.familyagent.module.memory.dto;

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
public class WriteMemoryMetadata {

    private String source;
    private String authorName;
    private String relatedMemberName;
    private String observerPerspective;
    private String evidenceType;

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
        put(metadata, "authorName", authorName);
        put(metadata, "relatedMemberName", relatedMemberName);
        put(metadata, "observerPerspective", observerPerspective);
        put(metadata, "evidenceType", evidenceType);
        return metadata;
    }

    private static void put(Map<String, Object> metadata, String key, String value) {
        if (value != null && !value.isBlank()) {
            metadata.put(key, value.trim());
        }
    }
}
