package com.familyagent.module.memory.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class JdbcMemoryRecallVectorRepository implements MemoryRecallVectorRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<Long> rankSourceIds(
            Long familyId,
            String sourceType,
            List<Long> sourceIds,
            List<Double> queryEmbedding,
            double maxDistance,
            int limit) {
        if (sourceIds == null || sourceIds.isEmpty()
                || queryEmbedding == null || queryEmbedding.isEmpty()
                || limit <= 0) {
            return List.of();
        }

        String placeholders = String.join(",", sourceIds.stream().map(id -> "?").toList());
        String vector = toVectorLiteral(queryEmbedding);
        List<Object> params = new ArrayList<>();
        params.add(familyId);
        params.add(sourceType);
        params.addAll(sourceIds);
        params.add(vector);
        params.add(maxDistance);
        params.add(vector);
        params.add(limit);
        return jdbcTemplate.queryForList("""
                SELECT ranked.source_id
                FROM (
                    SELECT DISTINCT ON (source_id) source_id, embedding, updated_at
                    FROM memory_embeddings
                    WHERE (family_id = ? OR (family_id IS NULL AND source_type = 'MEMORY'))
                      AND source_type = ?
                      AND source_id IN (%s)
                      AND status = 'READY'
                      AND embedding IS NOT NULL
                    ORDER BY source_id, updated_at DESC
                ) ranked
                WHERE ranked.embedding <=> ?::vector <= ?
                ORDER BY ranked.embedding <=> ?::vector
                LIMIT ?
                """.formatted(placeholders), Long.class, params.toArray());
    }

    private static String toVectorLiteral(List<Double> values) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(values.get(index));
        }
        return builder.append(']').toString();
    }
}
