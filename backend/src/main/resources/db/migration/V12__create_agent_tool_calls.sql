CREATE TABLE IF NOT EXISTS agent_tool_calls (
    id BIGSERIAL PRIMARY KEY,
    tool_name VARCHAR(100) NOT NULL,
    family_id BIGINT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    viewer_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    request_id VARCHAR(128),
    input_summary VARCHAR(500),
    status VARCHAR(40) NOT NULL,
    error_code VARCHAR(80),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_agent_tool_calls_family_created
    ON agent_tool_calls(family_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_agent_tool_calls_request
    ON agent_tool_calls(request_id);

CREATE INDEX IF NOT EXISTS idx_agent_tool_calls_tool_status
    ON agent_tool_calls(tool_name, status, created_at DESC);
