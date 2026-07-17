package com.familyagent.module.memory.dto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record MemoryRecallRagMetadata(
        String retrievalMode,
        long embeddingReadyCount,
        int diaryCount,
        int memoryCount,
        int growthRecordCount,
        int libraryCount,
        int sessionSavedCount,
        int totalReferenceCount,
        List<RecallSourceSummary> sources) {

    public MemoryRecallRagMetadata {
        retrievalMode = normalize(retrievalMode);
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> data = new LinkedHashMap<>();
        if (retrievalMode != null) {
            data.put("retrievalMode", retrievalMode);
        }
        data.put("embeddingReadyCount", embeddingReadyCount);
        data.put("diaryCount", diaryCount);
        data.put("memoryCount", memoryCount);
        data.put("growthRecordCount", growthRecordCount);
        data.put("libraryCount", libraryCount);
        data.put("sessionSavedCount", sessionSavedCount);
        data.put("totalReferenceCount", totalReferenceCount);
        data.put("sources", sources);
        return Map.copyOf(data);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
