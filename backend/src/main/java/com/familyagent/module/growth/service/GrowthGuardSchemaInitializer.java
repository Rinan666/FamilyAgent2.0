package com.familyagent.module.growth.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GrowthGuardSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initialize() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS growth_guard_records (
                id BIGSERIAL PRIMARY KEY,
                family_id BIGINT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
                target_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
                created_by BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                category VARCHAR(50) NOT NULL,
                content TEXT NOT NULL,
                severity INTEGER NOT NULL DEFAULT 3 CHECK (severity BETWEEN 1 AND 5),
                observed_at DATE NOT NULL DEFAULT CURRENT_DATE,
                follow_up_at DATE,
                visibility VARCHAR(30) NOT NULL DEFAULT 'CARE_VISIBLE',
                status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                metadata JSONB DEFAULT '{}',
                created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMP NOT NULL DEFAULT NOW()
            )
            """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_growth_guard_family ON growth_guard_records(family_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_growth_guard_target ON growth_guard_records(target_user_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_growth_guard_category ON growth_guard_records(category)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_growth_guard_status ON growth_guard_records(status)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_growth_guard_observed ON growth_guard_records(observed_at DESC)");

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS growth_guard_reports (
                id BIGSERIAL PRIMARY KEY,
                family_id BIGINT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
                target_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
                created_by BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                week_start DATE NOT NULL DEFAULT CURRENT_DATE,
                week_end DATE NOT NULL DEFAULT CURRENT_DATE,
                title VARCHAR(100) NOT NULL,
                summary TEXT,
                visibility VARCHAR(30) NOT NULL DEFAULT 'CARE_VISIBLE',
                status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                report JSONB NOT NULL DEFAULT '{}',
                metadata JSONB DEFAULT '{}',
                created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMP NOT NULL DEFAULT NOW()
            )
            """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_growth_report_family ON growth_guard_reports(family_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_growth_report_target ON growth_guard_reports(target_user_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_growth_report_status ON growth_guard_reports(status)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_growth_report_week ON growth_guard_reports(week_end DESC)");
    }
}
