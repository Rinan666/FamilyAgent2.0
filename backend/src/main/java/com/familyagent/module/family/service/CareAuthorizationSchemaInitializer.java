package com.familyagent.module.family.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CareAuthorizationSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initialize() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS care_authorizations (
                id BIGSERIAL PRIMARY KEY,
                family_id BIGINT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
                subject_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                caregiver_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                scope VARCHAR(40) NOT NULL DEFAULT 'GROWTH_GUARD',
                status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
                updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
                expires_at TIMESTAMP,
                created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
                UNIQUE(family_id, subject_user_id, caregiver_user_id, scope)
            )
            """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_care_auth_subject ON care_authorizations(family_id, subject_user_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_care_auth_caregiver ON care_authorizations(family_id, caregiver_user_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_care_auth_status ON care_authorizations(status)");
    }
}
