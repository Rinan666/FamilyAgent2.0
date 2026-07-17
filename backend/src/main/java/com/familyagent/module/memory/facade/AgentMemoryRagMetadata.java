package com.familyagent.module.memory.facade;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentMemoryRagMetadata(
        String retrievalMode,
        long embeddingReadyCount,
        int diaryCount,
        int memoryCount,
        int growthRecordCount,
        int libraryCount,
        int sessionSavedCount,
        int totalReferenceCount,
        List<AgentMemoryRecallSource> sources) {

    public AgentMemoryRagMetadata {
        retrievalMode = normalize(retrievalMode);
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
