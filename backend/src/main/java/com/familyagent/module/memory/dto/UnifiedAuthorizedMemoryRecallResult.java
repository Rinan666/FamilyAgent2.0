package com.familyagent.module.memory.dto;

import java.util.List;

public record UnifiedAuthorizedMemoryRecallResult(
        List<AuthorizedMemoryRecallCandidate> items,
        List<RecallSourceSummary> sources,
        String retrievalMode,
        List<String> queries,
        long embeddingReadyCount,
        EmbeddingCallObservation embeddingObservation) {

    public UnifiedAuthorizedMemoryRecallResult {
        items = items == null ? List.of() : List.copyOf(items);
        sources = sources == null ? List.of() : List.copyOf(sources);
        queries = queries == null ? List.of() : List.copyOf(queries);
    }
}
