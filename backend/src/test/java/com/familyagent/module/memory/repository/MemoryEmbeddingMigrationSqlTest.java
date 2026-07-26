package com.familyagent.module.memory.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryEmbeddingMigrationSqlTest {

    @Test
    void v21MapsLegacySourcesToUnifiedMemoryIdsBeforeConstrainingWrites() throws IOException {
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V21__unify_memory_embedding_sources.sql")) {
            if (stream == null) {
                throw new IOException("V21 migration is missing");
            }
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("\\s+", " ");
            assertTrue(sql.contains("me.origin_id = legacy.source_id"));
            assertTrue(sql.contains("'MEMORY', me.id"));
            assertTrue(sql.contains("DELETE FROM memory_embeddings WHERE source_type IN"));
            assertTrue(sql.contains("CHECK (source_type = 'MEMORY')"));
        }
    }
}
