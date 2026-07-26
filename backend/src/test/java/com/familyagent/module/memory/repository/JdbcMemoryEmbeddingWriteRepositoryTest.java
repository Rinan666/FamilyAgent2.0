package com.familyagent.module.memory.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcMemoryEmbeddingWriteRepositoryTest {

    @Mock private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void upsertPending_shouldSupersedeOlderPendingAndReturnRowId() throws Exception {
        when(jdbcTemplate.queryForObject(
                any(String.class),
                eq(Long.class),
                eq(11L),
                eq(34L),
                eq("MEMORY"),
                eq(44L),
                eq("new-hash")))
                .thenReturn(123L);
        JdbcMemoryEmbeddingWriteRepository repository = repository();

        Long id = repository.upsertPending("MEMORY", 44L, 11L, 34L, "new-hash");

        assertEquals(123L, id);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> metadataCaptor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(
                sqlCaptor.capture(),
                metadataCaptor.capture(),
                eq("MEMORY"),
                eq(44L),
                eq("new-hash"));
        assertTrue(sqlCaptor.getValue().contains("content_hash <> ?"));
        JsonNode expected = objectMapper.readTree("""
                {
                  "error": "Superseded by newer embedding request",
                  "cleanupReason": "STALE_PENDING_SUPERSEDED",
                  "supersededByContentHash": "new-hash"
                }
                """);
        assertEquals(expected, objectMapper.readTree(String.valueOf(metadataCaptor.getValue())));
    }

    @Test
    void markReady_shouldWriteVectorAndTypedMetadata() throws Exception {
        JdbcMemoryEmbeddingWriteRepository repository = repository();

        repository.markReady(
                123L,
                " test-model ",
                List.of(0.1, -0.2, 0.3),
                List.of("family"),
                " test-provider ",
                3);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> metadataCaptor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(
                sqlCaptor.capture(),
                eq("test-model"),
                eq("[0.1,-0.2,0.3]"),
                metadataCaptor.capture(),
                eq(123L));
        assertTrue(sqlCaptor.getValue().contains("status = 'READY'"));
        assertTrue(sqlCaptor.getValue().contains("AND status = 'PENDING'"));
        JsonNode expected = objectMapper.readTree("""
                {
                  "privacyCategories": ["family"],
                  "provider": "test-provider",
                  "dimensions": 3
                }
                """);
        assertEquals(expected, objectMapper.readTree(String.valueOf(metadataCaptor.getValue())));
    }

    @Test
    void markFailed_shouldWriteTypedFailureMetadata() throws Exception {
        JdbcMemoryEmbeddingWriteRepository repository = repository();

        repository.markFailed(123L, " dimension mismatch ");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> metadataCaptor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(
                sqlCaptor.capture(),
                metadataCaptor.capture(),
                eq(123L));
        assertTrue(sqlCaptor.getValue().contains("status = 'FAILED'"));
        assertTrue(sqlCaptor.getValue().contains("AND status = 'PENDING'"));
        assertEquals(
                objectMapper.readTree("{\"error\":\"dimension mismatch\"}"),
                objectMapper.readTree(String.valueOf(metadataCaptor.getValue())));
    }

    @Test
    void deleteBySource_shouldDeleteAllRowsForSource() {
        JdbcMemoryEmbeddingWriteRepository repository = repository();

        repository.deleteBySource("MEMORY", 88L);

        verify(jdbcTemplate).update(
                "DELETE FROM memory_embeddings WHERE source_type = ? AND source_id = ?",
                "MEMORY",
                88L);
    }

    private JdbcMemoryEmbeddingWriteRepository repository() {
        return new JdbcMemoryEmbeddingWriteRepository(jdbcTemplate, objectMapper);
    }
}
