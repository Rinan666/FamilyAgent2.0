package com.familyagent.module.diary.dto;

import com.familyagent.common.dto.ExtensibleMetadata;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = false)
public class DiaryEntryMetadata extends ExtensibleMetadata {

    private String source;
    private Long relatedUserId;
    private String relatedMemberName;
    private String eventAt;
    private String recordedAt;
    private String savedFromFamilyChatAt;
    private Boolean disableAutoMerge;

    public Map<String, Object> toMap() {
        Map<String, Object> metadata = copyExtraProperties();
        putIfPresent(metadata, "source", source);
        putIfPresent(metadata, "relatedUserId", relatedUserId);
        putIfPresent(metadata, "relatedMemberName", relatedMemberName);
        putIfPresent(metadata, "eventAt", eventAt);
        putIfPresent(metadata, "recordedAt", recordedAt);
        putIfPresent(metadata, "savedFromFamilyChatAt", savedFromFamilyChatAt);
        putIfPresent(metadata, "disableAutoMerge", disableAutoMerge);
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
            case "source" -> source = textValue(value);
            case "relatedUserId" -> {
                Long parsed = longValue(value);
                if (parsed == null && value != null) {
                    putExtra(key, value);
                } else {
                    relatedUserId = parsed;
                }
            }
            case "relatedMemberName" -> relatedMemberName = textValue(value);
            case "eventAt" -> eventAt = textValue(value);
            case "recordedAt" -> recordedAt = textValue(value);
            case "savedFromFamilyChatAt" -> savedFromFamilyChatAt = textValue(value);
            case "disableAutoMerge" -> {
                Boolean parsed = booleanValue(value);
                if (parsed == null && value != null) {
                    putExtra(key, value);
                } else {
                    disableAutoMerge = parsed;
                }
            }
            default -> putExtra(key, value);
        }
    }
}
