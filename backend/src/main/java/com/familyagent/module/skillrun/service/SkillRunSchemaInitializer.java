package com.familyagent.module.skillrun.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SkillRunSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initialize() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS skill_runs (
                id BIGSERIAL PRIMARY KEY,
                family_id BIGINT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
                triggered_by BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                skill_name VARCHAR(80) NOT NULL,
                status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
                source VARCHAR(50) NOT NULL DEFAULT 'FAMILY_AGENT',
                input_summary TEXT,
                output_summary TEXT,
                saved BOOLEAN NOT NULL DEFAULT FALSE,
                used_sources JSONB DEFAULT '[]',
                metadata JSONB DEFAULT '{}',
                created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMP NOT NULL DEFAULT NOW()
            )
            """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_skill_runs_family ON skill_runs(family_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_skill_runs_triggered_by ON skill_runs(triggered_by)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_skill_runs_skill ON skill_runs(skill_name)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_skill_runs_status ON skill_runs(status)");
        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_skill_runs_family_created
            ON skill_runs(family_id, created_at DESC)
            """);
    }
}
