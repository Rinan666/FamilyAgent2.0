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
    }
}
