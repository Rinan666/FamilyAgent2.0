package com.familyagent.module.mirror.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MirrorAgentDataSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initialize() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS mirror_agent_data (
                id BIGSERIAL PRIMARY KEY,
                user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                primary_family_id BIGINT REFERENCES families(id) ON DELETE CASCADE,
                traits JSONB DEFAULT '{}',
                visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
                permission_scope JSONB DEFAULT '{}',
                memory_scope JSONB DEFAULT '{}',
                interaction_count INTEGER DEFAULT 0,
                last_updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
                created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                UNIQUE(user_id)
            )
            """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_mirror_primary_family ON mirror_agent_data(primary_family_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_mirror_user_family ON mirror_agent_data(primary_family_id, user_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_mirror_visibility ON mirror_agent_data(visibility)");
    }
}
