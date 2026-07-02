ALTER TABLE agent_tool_confirmations
    ADD COLUMN IF NOT EXISTS session_id BIGINT,
    ADD COLUMN IF NOT EXISTS agent_mode VARCHAR(80),
    ADD COLUMN IF NOT EXISTS subject VARCHAR(200),
    ADD COLUMN IF NOT EXISTS context_label VARCHAR(200),
    ADD COLUMN IF NOT EXISTS input_payload TEXT,
    ADD COLUMN IF NOT EXISTS execution_status VARCHAR(40),
    ADD COLUMN IF NOT EXISTS execution_error_code VARCHAR(80),
    ADD COLUMN IF NOT EXISTS executed_at TIMESTAMP;

ALTER TABLE agent_tool_calls
    ADD COLUMN IF NOT EXISTS confirmation_id BIGINT REFERENCES agent_tool_confirmations(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_agent_tool_calls_confirmation
    ON agent_tool_calls(confirmation_id);
