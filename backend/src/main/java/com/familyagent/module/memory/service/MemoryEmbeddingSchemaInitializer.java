package com.familyagent.module.memory.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemoryEmbeddingSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initialize() {
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS memory_embeddings (
                id BIGSERIAL PRIMARY KEY,
                family_id BIGINT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
                user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                source_type VARCHAR(20) NOT NULL,
                source_id BIGINT NOT NULL,
                content_hash VARCHAR(64) NOT NULL,
                embedding_model VARCHAR(120),
                embedding VECTOR(1536),
                status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                metadata JSONB DEFAULT '{}',
                created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
                UNIQUE(source_type, source_id, content_hash)
            )
            """);
        migrateSourceTypeConstraint();
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_memory_embeddings_family ON memory_embeddings(family_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_memory_embeddings_source ON memory_embeddings(source_type, source_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_memory_embeddings_status ON memory_embeddings(status)");
        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_memory_embeddings_family_status
            ON memory_embeddings(family_id, status)
            """);
        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_memory_embeddings_ready_source_latest
            ON memory_embeddings(family_id, source_type, source_id, updated_at DESC)
            WHERE status = 'READY' AND embedding IS NOT NULL
            """);
        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_memory_embeddings_vector
            ON memory_embeddings USING ivfflat (embedding vector_cosine_ops)
            WHERE embedding IS NOT NULL
            """);
    }

    private void migrateSourceTypeConstraint() {
        jdbcTemplate.execute("""
            DO $$
            DECLARE
                constraint_name text;
            BEGIN
                FOR constraint_name IN
                    SELECT con.conname
                    FROM pg_constraint con
                    JOIN pg_class rel ON rel.oid = con.conrelid
                    JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
                    WHERE rel.relname = 'memory_embeddings'
                      AND nsp.nspname = current_schema()
                      AND con.contype = 'c'
                      AND pg_get_constraintdef(con.oid) LIKE '%source_type%'
                LOOP
                    EXECUTE format('ALTER TABLE memory_embeddings DROP CONSTRAINT %I', constraint_name);
                END LOOP;

                ALTER TABLE memory_embeddings
                    ADD CONSTRAINT chk_memory_embeddings_source_type
                    CHECK (source_type IN ('DIARY', 'MEMORY', 'GROWTH_OBSERVATION'));
            END $$;
            """);
    }
}
