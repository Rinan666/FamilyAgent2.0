package com.familyagent.module.diary.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DiarySchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initialize() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS diary_entries (
                id BIGSERIAL PRIMARY KEY,
                user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                family_id BIGINT REFERENCES families(id) ON DELETE CASCADE,
                raw_text TEXT,
                structured JSONB DEFAULT '{}',
                mood VARCHAR(20),
                tags TEXT[] DEFAULT '{}',
                privacy_level VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
                visibility VARCHAR(30) NOT NULL DEFAULT 'PRIVATE',
                permission_scope JSONB DEFAULT '{}',
                source VARCHAR(50) DEFAULT 'USER_INPUT',
                voice_url VARCHAR(500),
                metadata JSONB DEFAULT '{}',
                created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMP NOT NULL DEFAULT NOW()
            )
            """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_diary_user ON diary_entries(user_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_diary_family_user ON diary_entries(family_id, user_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_diary_visibility ON diary_entries(visibility)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_diary_created ON diary_entries(created_at DESC)");
    }
}
