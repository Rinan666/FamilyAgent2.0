package com.familyagent.module.family.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FamilyRelationshipSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initialize() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS family_relationships (
                id BIGSERIAL PRIMARY KEY,
                family_id BIGINT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
                from_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                to_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                label VARCHAR(60) NOT NULL,
                reverse_label VARCHAR(60),
                note VARCHAR(500),
                created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
                updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
                created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
                UNIQUE(family_id, from_user_id, to_user_id)
            )
            """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_family_relationships_viewer ON family_relationships(family_id, from_user_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_family_relationships_target ON family_relationships(family_id, to_user_id)");
    }
}
