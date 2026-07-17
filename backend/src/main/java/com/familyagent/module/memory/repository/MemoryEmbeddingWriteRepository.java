package com.familyagent.module.memory.repository;

import java.util.List;

public interface MemoryEmbeddingWriteRepository {

    Long upsertPending(
            String sourceType,
            Long sourceId,
            Long familyId,
            Long userId,
            String contentHash);

    void markReady(
            Long id,
            String model,
            List<Double> values,
            List<String> privacyCategories,
            String provider,
            int dimensions);

    void markFailed(Long id, String error);

    void deleteBySource(String sourceType, Long sourceId);
}
