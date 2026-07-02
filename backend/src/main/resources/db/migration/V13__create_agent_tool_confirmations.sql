CREATE TABLE IF NOT EXISTS agent_tool_confirmations (
    id BIGSERIAL PRIMARY KEY,
    tool_name VARCHAR(100) NOT NULL,
    family_id BIGINT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    viewer_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    request_id VARCHAR(128),
    idempotency_key VARCHAR(128) NOT NULL,
    input_summary VARCHAR(500),
    status VARCHAR(40) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    decided_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_agent_tool_confirmations_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_agent_tool_confirmations_family_status
    ON agent_tool_confirmations(family_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_agent_tool_confirmations_request
    ON agent_tool_confirmations(request_id);
