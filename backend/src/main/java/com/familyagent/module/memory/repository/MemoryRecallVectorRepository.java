package com.familyagent.module.memory.repository;

import java.util.List;

public interface MemoryRecallVectorRepository {

    List<Long> rankSourceIds(
            Long familyId,
            String sourceType,
            List<Long> sourceIds,
            List<Double> queryEmbedding,
            double maxDistance,
            int limit);
}
