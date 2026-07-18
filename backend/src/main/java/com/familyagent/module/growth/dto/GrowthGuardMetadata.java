package com.familyagent.module.growth.dto;

import com.familyagent.common.dto.ExtensibleMetadata;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = false)
public class GrowthGuardMetadata extends ExtensibleMetadata {

    private String source;
    private Long relatedUserId;
    private String relatedMemberName;
    private String sourceType;
    private String followUpStatus;

    public Map<String, Object> toMap() {
        Map<String, Object> metadata = copyExtraProperties();
        putIfPresent(metadata, "source", source);
        putIfPresent(metadata, "relatedUserId", relatedUserId);
        putIfPresent(metadata, "relatedMemberName", relatedMemberName);
        putIfPresent(metadata, "sourceType", sourceType);
        putIfPresent(metadata, "followUpStatus", followUpStatus);
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
            case "sourceType" -> sourceType = textValue(value);
            case "followUpStatus" -> followUpStatus = textValue(value);
            default -> putExtra(key, value);
        }
    }
}
