CREATE TABLE IF NOT EXISTS agent_run_steps (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES agent_runs(id) ON DELETE CASCADE,
    request_id VARCHAR(128),
    span_id VARCHAR(64) NOT NULL,
    parent_span_id VARCHAR(64),
    step_type VARCHAR(40) NOT NULL,
    operation VARCHAR(120) NOT NULL,
    status VARCHAR(40) NOT NULL,
    provider VARCHAR(80),
    model VARCHAR(160),
    prompt_version VARCHAR(80),
    skill_version VARCHAR(40),
    latency_ms BIGINT,
    error_code VARCHAR(80),
    degraded BOOLEAN NOT NULL DEFAULT FALSE,
    privacy_categories VARCHAR(300),
    started_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_agent_run_steps_span UNIQUE (span_id)
);

CREATE INDEX IF NOT EXISTS idx_agent_run_steps_run_created
    ON agent_run_steps(run_id, created_at);

CREATE INDEX IF NOT EXISTS idx_agent_run_steps_request
    ON agent_run_steps(request_id, created_at);

CREATE INDEX IF NOT EXISTS idx_agent_run_steps_operation_status
    ON agent_run_steps(operation, status, created_at DESC);
