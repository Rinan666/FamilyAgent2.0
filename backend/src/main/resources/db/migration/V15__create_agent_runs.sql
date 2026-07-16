CREATE TABLE IF NOT EXISTS agent_runs (
    id BIGSERIAL PRIMARY KEY,
    request_id VARCHAR(128),
    family_id BIGINT REFERENCES families(id) ON DELETE CASCADE,
    viewer_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_id BIGINT,
    agent_mode VARCHAR(80),
    subject VARCHAR(200),
    context_label VARCHAR(200),
    status VARCHAR(40) NOT NULL,
    error_code VARCHAR(80),
    started_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_agent_runs_request
    ON agent_runs(request_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_agent_runs_session
    ON agent_runs(session_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_agent_runs_family_status
    ON agent_runs(family_id, status, created_at DESC);

ALTER TABLE agent_tool_calls
    ADD COLUMN IF NOT EXISTS run_id BIGINT REFERENCES agent_runs(id) ON DELETE SET NULL;

ALTER TABLE agent_tool_confirmations
    ADD COLUMN IF NOT EXISTS run_id BIGINT REFERENCES agent_runs(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS complete_run_after_tool BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX IF NOT EXISTS idx_agent_tool_calls_run
    ON agent_tool_calls(run_id, created_at);

CREATE INDEX IF NOT EXISTS idx_agent_tool_confirmations_run
    ON agent_tool_confirmations(run_id, created_at);
