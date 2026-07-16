package com.familyagent.module.memory.facade;

import com.familyagent.module.memory.dto.AuthorizedMemoryRecallResult;
import com.familyagent.module.memory.dto.EmbeddingCallObservation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AgentMemoryContextResult(
        String context,
        Map<String, Object> metadata,
        boolean success,
        String errorCode,
        EmbeddingCallObservation embeddingObservation) {

    public AgentMemoryContextResult {
        context = context == null ? "" : context;
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        errorCode = errorCode == null || errorCode.isBlank() ? null : errorCode.trim();
    }

    public AgentMemoryContextResult(String context, Map<String, Object> metadata) {
        this(context, metadata, true, null, null);
    }

    public AgentMemoryContextResult(
            String context,
            Map<String, Object> metadata,
            boolean success,
            String errorCode) {
        this(context, metadata, success, errorCode, null);
    }

    public static AgentMemoryContextResult empty() {
        return new AgentMemoryContextResult("", Map.of());
    }

    public static AgentMemoryContextResult failed(AgentMemoryContextErrorCode errorCode) {
        return new AgentMemoryContextResult("", Map.of(), false, errorCode.code(), null);
    }

    public static AgentMemoryContextResult fromRecall(String context, AuthorizedMemoryRecallResult recall) {
        if (recall == null) {
            return new AgentMemoryContextResult(context, Map.of());
        }

        Map<String, Object> rag = new LinkedHashMap<>();
        putIfNotBlank(rag, "retrievalMode", recall.getRetrievalMode());
        rag.put("embeddingReadyCount", recall.getEmbeddingReadyCount());
        rag.put("diaryCount", recall.getDiaryCount());
        rag.put("memoryCount", recall.getMemoryCount());
        rag.put("growthRecordCount", recall.getGrowthRecordCount());
        rag.put("libraryCount", 0);
        rag.put("sessionSavedCount", 0);
        rag.put("totalReferenceCount",
                recall.getDiaryCount() + recall.getMemoryCount() + recall.getGrowthRecordCount());
        rag.put("sources", recall.getSources() == null ? List.of() : recall.getSources());

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("rag", rag);
        putIfNotBlank(metadata, "retrievalQuery", recall.getQuery());
        return new AgentMemoryContextResult(
                context,
                metadata,
                true,
                null,
                recall.getEmbeddingObservation());
    }

    private static void putIfNotBlank(Map<String, Object> data, String key, String value) {
        if (value != null && !value.isBlank()) {
            data.put(key, value.trim());
        }
    }
}
