package com.familyagent.module.memory.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class JdbcMemoryEmbeddingWriteRepository implements MemoryEmbeddingWriteRepository {

    private static final String UNKNOWN_ERROR_METADATA = "{\"error\":\"unknown\"}";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Long upsertPending(
            String sourceType,
            Long sourceId,
            Long familyId,
            Long userId,
            String contentHash) {
        supersedeOlderPending(sourceType, sourceId, contentHash);
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO memory_embeddings (
                    family_id, user_id, source_type, source_id, content_hash, status, metadata
                )
                VALUES (?, ?, ?, ?, ?, 'PENDING', '{}'::jsonb)
                ON CONFLICT (source_type, source_id, content_hash)
                DO UPDATE SET status = 'PENDING', updated_at = NOW()
                RETURNING id
                """, Long.class, familyId, userId, sourceType, sourceId, contentHash);
        if (id == null) {
            throw new IllegalStateException("Embedding pending row was not returned");
        }
        return id;
    }

    @Override
    public void markReady(
            Long id,
            String model,
            List<Double> values,
            List<String> privacyCategories,
            String provider,
            int dimensions) {
        ReadyMetadata metadata = new ReadyMetadata(
                privacyCategories,
                normalize(provider),
                dimensions);
        jdbcTemplate.update("""
                UPDATE memory_embeddings
                SET embedding_model = ?,
                    embedding = ?::vector,
                    status = 'READY',
                    metadata = ?::jsonb,
                    updated_at = NOW()
                WHERE id = ?
                """,
                normalize(model),
                toVectorLiteral(values),
                serialize(metadata),
                id);
    }

    @Override
    public void markFailed(Long id, String error) {
        if (id == null) {
            return;
        }
        String metadata;
        try {
            metadata = objectMapper.writeValueAsString(new FailedMetadata(normalizeError(error)));
        } catch (JsonProcessingException serializationError) {
            log.warn(
                    "Failed to serialize embedding error metadata: id={}, errorType={}",
                    id,
                    serializationError.getClass().getSimpleName());
            metadata = UNKNOWN_ERROR_METADATA;
        }
        jdbcTemplate.update("""
                UPDATE memory_embeddings
                SET status = 'FAILED',
                    metadata = ?::jsonb,
                    updated_at = NOW()
                WHERE id = ?
                """, metadata, id);
    }

    @Override
    public void deleteBySource(String sourceType, Long sourceId) {
        jdbcTemplate.update(
                "DELETE FROM memory_embeddings WHERE source_type = ? AND source_id = ?",
                sourceType,
                sourceId);
    }

    private void supersedeOlderPending(String sourceType, Long sourceId, String contentHash) {
        try {
            SupersededMetadata metadata = new SupersededMetadata(
                    "Superseded by newer embedding request",
                    "STALE_PENDING_SUPERSEDED",
                    normalize(contentHash));
            jdbcTemplate.update("""
                    UPDATE memory_embeddings
                    SET status = 'FAILED',
                        metadata = ?::jsonb,
                        updated_at = NOW()
                    WHERE source_type = ?
                      AND source_id = ?
                      AND status = 'PENDING'
                      AND content_hash <> ?
                    """, serialize(metadata), sourceType, sourceId, contentHash);
        } catch (RuntimeException error) {
            log.warn(
                    "Failed to supersede stale pending embeddings: sourceType={}, sourceId={}, errorType={}",
                    sourceType,
                    sourceId,
                    error.getClass().getSimpleName());
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Embedding metadata serialization failed", error);
        }
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

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    private static String normalizeError(String error) {
        return error == null || error.isBlank() ? "unknown" : error.trim();
    }

    private record ReadyMetadata(
            List<String> privacyCategories,
            String provider,
            int dimensions) {

        private ReadyMetadata {
            privacyCategories = privacyCategories == null ? List.of() : List.copyOf(privacyCategories);
        }
    }

    private record FailedMetadata(String error) {
    }

    private record SupersededMetadata(
            String error,
            String cleanupReason,
            String supersededByContentHash) {
    }
}
