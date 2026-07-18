package com.familyagent.module.memorylibrary.dto;

import com.familyagent.common.constant.MemoryLibraryMetadataSource;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public record MemoryClassicalizationMetadata(
        String originalContent,
        String originalSummary,
        String plainSummary,
        String styleNote,
        LocalDateTime classicalizedAt,
        Long classicalizedBy,
        MemoryLibraryMetadataSource source) {

    public Map<String, Object> mergeInto(Object existingMetadata) {
        Map<String, Object> metadata = mutableMap(existingMetadata);
        if (originalContent != null) {
            metadata.putIfAbsent("originalContent", originalContent);
        }
        if (originalSummary != null) {
            metadata.putIfAbsent("originalSummary", originalSummary);
        }
        metadata.put("plainSummary", plainSummary);
        metadata.put("styleNote", styleNote);
        metadata.put("classicalizedAt", classicalizedAt.toString());
        metadata.put("classicalizedBy", classicalizedBy);
        metadata.put("source", source.name());
        return metadata;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mutableMap(Object metadata) {
        if (metadata instanceof Map<?, ?> map) {
            return new HashMap<>((Map<String, Object>) map);
        }
        return new HashMap<>();
    }
}
