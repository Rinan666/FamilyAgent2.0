package com.familyagent.module.memory.dto;

import java.util.LinkedHashMap;
import java.util.Map;

public record MemoryRecallContextMetadata(
        MemoryRecallRagMetadata rag,
        String retrievalQuery) {

    public MemoryRecallContextMetadata {
        retrievalQuery = retrievalQuery == null || retrievalQuery.isBlank()
                ? null
                : retrievalQuery.trim();
    }

    public static MemoryRecallContextMetadata empty() {
        return new MemoryRecallContextMetadata(null, null);
    }

    public Map<String, Object> toMap() {
        if (rag == null && retrievalQuery == null) {
            return Map.of();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        if (rag != null) {
            data.put("rag", rag.toMap());
        }
        if (retrievalQuery != null) {
            data.put("retrievalQuery", retrievalQuery);
        }
        return Map.copyOf(data);
    }

    public boolean isEmpty() {
        return rag == null && retrievalQuery == null;
    }
}
