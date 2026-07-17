CREATE TABLE IF NOT EXISTS agent_record_provenance (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    agent_run_id BIGINT NOT NULL REFERENCES agent_runs(id) ON DELETE CASCADE,
    tool_call_id BIGINT NOT NULL REFERENCES agent_tool_calls(id) ON DELETE CASCADE,
    tool_name VARCHAR(100) NOT NULL,
    tool_version VARCHAR(40) NOT NULL,
    record_type VARCHAR(40) NOT NULL,
    memory_entry_id BIGINT REFERENCES memory_entries(id) ON DELETE CASCADE,
    diary_entry_id BIGINT REFERENCES diary_entries(id) ON DELETE CASCADE,
    growth_guard_record_id BIGINT REFERENCES growth_guard_records(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_agent_record_provenance_tool_call UNIQUE (tool_call_id),
    CONSTRAINT ck_agent_record_provenance_single_record CHECK (
        (CASE WHEN memory_entry_id IS NULL THEN 0 ELSE 1 END)
        + (CASE WHEN diary_entry_id IS NULL THEN 0 ELSE 1 END)
        + (CASE WHEN growth_guard_record_id IS NULL THEN 0 ELSE 1 END) = 1
    ),
    CONSTRAINT ck_agent_record_provenance_type CHECK (
        (record_type = 'MEMORY_ENTRY' AND memory_entry_id IS NOT NULL)
        OR (record_type = 'DIARY_ENTRY' AND diary_entry_id IS NOT NULL)
        OR (record_type = 'GROWTH_GUARD_RECORD' AND growth_guard_record_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_record_provenance_memory
    ON agent_record_provenance(memory_entry_id)
    WHERE memory_entry_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_record_provenance_diary
    ON agent_record_provenance(diary_entry_id)
    WHERE diary_entry_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_record_provenance_growth
    ON agent_record_provenance(growth_guard_record_id)
    WHERE growth_guard_record_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_agent_record_provenance_run
    ON agent_record_provenance(agent_run_id, created_at);
