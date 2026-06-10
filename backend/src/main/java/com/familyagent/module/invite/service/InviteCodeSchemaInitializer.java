package com.familyagent.module.invite.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InviteCodeSchemaInitializer {

    private static final String DEFAULT_INVITE_CODE = "ASDFGZXCVB";
    private static final int DEFAULT_MAX_USES = 20;

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initialize() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS invite_codes (
                id BIGSERIAL PRIMARY KEY,
                code VARCHAR(50) NOT NULL UNIQUE,
                source VARCHAR(100),
                description VARCHAR(255),
                max_uses INT,
                used_count INT NOT NULL DEFAULT 0,
                status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                expires_at TIMESTAMP NULL,
                created_by BIGINT,
                created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMP NOT NULL DEFAULT NOW()
            )
            """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_invite_codes_status ON invite_codes(status)");
        jdbcTemplate.update("""
            INSERT INTO invite_codes (code, source, description, max_uses, used_count, status)
            VALUES (?, ?, ?, ?, 0, 'ACTIVE')
            ON CONFLICT (code) DO UPDATE
            SET source = EXCLUDED.source,
                description = EXCLUDED.description,
                max_uses = EXCLUDED.max_uses,
                status = 'ACTIVE',
                updated_at = NOW()
            """,
            DEFAULT_INVITE_CODE,
            "system-seed",
            "Default MVP registration invite code",
            DEFAULT_MAX_USES
        );
    }
}
