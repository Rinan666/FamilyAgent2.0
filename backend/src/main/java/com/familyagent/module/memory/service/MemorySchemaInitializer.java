package com.familyagent.module.memory.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemorySchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initialize() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS memory_entries (
                id BIGSERIAL PRIMARY KEY,
                user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                family_id BIGINT REFERENCES families(id) ON DELETE CASCADE,
                subject VARCHAR(50),
                knowledge_point_id BIGINT REFERENCES knowledge_points(id),
                type VARCHAR(50) NOT NULL DEFAULT 'LEARNING',
                scope VARCHAR(30) NOT NULL DEFAULT 'PRIVATE',
                content TEXT NOT NULL,
                summary TEXT,
                importance INTEGER NOT NULL DEFAULT 3 CHECK (importance BETWEEN 1 AND 5),
                confidence DECIMAL(5,4) NOT NULL DEFAULT 0.7,
                source_session_id BIGINT REFERENCES chat_sessions(id) ON DELETE SET NULL,
                status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                metadata JSONB DEFAULT '{}',
                created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMP NOT NULL DEFAULT NOW()
            )
            """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_memory_entries_user ON memory_entries(user_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_memory_entries_family_user ON memory_entries(family_id, user_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_memory_entries_subject ON memory_entries(subject)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_memory_entries_kp ON memory_entries(knowledge_point_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_memory_entries_status ON memory_entries(status)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_memory_entries_created ON memory_entries(created_at DESC)");
        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_memory_entries_family_scope_status_updated
            ON memory_entries(family_id, scope, status, updated_at DESC)
            """);
        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_memory_entries_family_type_status_updated
            ON memory_entries(family_id, type, status, updated_at DESC)
            """);
        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_memory_entries_diary_promotion_source
            ON memory_entries(family_id, ((metadata->>'sourceDiaryId')))
            WHERE metadata->>'source' = 'DIARY_PROMOTION'
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS memory_entry_votes (
                id BIGSERIAL PRIMARY KEY,
                memory_id BIGINT NOT NULL REFERENCES memory_entries(id) ON DELETE CASCADE,
                family_id BIGINT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
                user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                vote_type VARCHAR(10) NOT NULL CHECK (vote_type IN ('UP', 'DOWN')),
                created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
                CONSTRAINT uk_memory_entry_votes_memory_user UNIQUE(memory_id, user_id)
            )
            """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_memory_entry_votes_memory ON memory_entry_votes(memory_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_memory_entry_votes_family ON memory_entry_votes(family_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_memory_entry_votes_user ON memory_entry_votes(user_id)");
    }
}
