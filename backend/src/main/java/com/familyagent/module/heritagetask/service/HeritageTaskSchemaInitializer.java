package com.familyagent.module.heritagetask.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HeritageTaskSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initialize() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS heritage_tasks (
                id BIGSERIAL PRIMARY KEY,
                family_id BIGINT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
                memory_id BIGINT REFERENCES memory_entries(id) ON DELETE SET NULL,
                created_by BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                title VARCHAR(100) NOT NULL,
                action TEXT NOT NULL,
                target_label VARCHAR(100),
                due_date DATE,
                status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                completion_note TEXT,
                completed_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
                completed_at TIMESTAMP,
                metadata JSONB DEFAULT '{}',
                created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMP NOT NULL DEFAULT NOW()
            )
            """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_heritage_task_family ON heritage_tasks(family_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_heritage_task_memory ON heritage_tasks(memory_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_heritage_task_status ON heritage_tasks(status)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_heritage_task_due ON heritage_tasks(due_date ASC)");
        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_heritage_task_family_status_due
            ON heritage_tasks(family_id, status, due_date ASC, created_at DESC)
            """);
    }
}
