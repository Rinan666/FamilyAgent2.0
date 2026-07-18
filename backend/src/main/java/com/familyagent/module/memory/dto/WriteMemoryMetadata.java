package com.familyagent.module.memory.dto;

import com.familyagent.common.dto.ExtensibleMetadata;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = false)
public class WriteMemoryMetadata extends ExtensibleMetadata {

    private String source;
    private Long sourceDiaryId;
    private String authorName;
    private String relatedMemberName;
    private String observerPerspective;
    private String evidenceType;

    public Map<String, Object> toMap() {
        Map<String, Object> metadata = copyExtraProperties();
        putIfPresent(metadata, "source", source);
        putIfPresent(metadata, "sourceDiaryId", sourceDiaryId);
        putIfPresent(metadata, "authorName", authorName);
        putIfPresent(metadata, "relatedMemberName", relatedMemberName);
        putIfPresent(metadata, "observerPerspective", observerPerspective);
        putIfPresent(metadata, "evidenceType", evidenceType);
        return metadata;
    }

    public static WriteMemoryMetadata fromMap(Map<String, ?> values) {
        WriteMemoryMetadata metadata = new WriteMemoryMetadata();
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
            case "source" -> source = textValue(value);
            case "sourceDiaryId" -> {
                Long parsed = longValue(value);
                if (parsed == null && value != null) {
                    putExtra(key, value);
                } else {
                    sourceDiaryId = parsed;
                }
            }
            case "authorName" -> authorName = textValue(value);
            case "relatedMemberName" -> relatedMemberName = textValue(value);
            case "observerPerspective" -> observerPerspective = textValue(value);
            case "evidenceType" -> evidenceType = textValue(value);
            default -> putExtra(key, value);
        }
    }

    @JsonIgnore
    @AssertTrue(message = "metadata must contain no more than 50 entries")
    public boolean isSizeValid() {
        return toMap().size() <= 50;
    }
}
