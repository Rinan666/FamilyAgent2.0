-- Idempotent migration for chat session message/archive storage.
-- Beta/production deployments should run this script explicitly instead of relying on startup DDL.

ALTER TABLE chat_sessions
ADD COLUMN IF NOT EXISTS title VARCHAR(120);

ALTER TABLE chat_sessions
ADD COLUMN IF NOT EXISTS last_message_at TIMESTAMP;

ALTER TABLE chat_sessions
ADD COLUMN IF NOT EXISTS message_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE chat_sessions
ADD COLUMN IF NOT EXISTS token_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE chat_sessions
ADD COLUMN IF NOT EXISTS archived_before_seq INTEGER NOT NULL DEFAULT 0;

ALTER TABLE chat_sessions
ADD COLUMN IF NOT EXISTS archive_status VARCHAR(20) NOT NULL DEFAULT 'NONE';

ALTER TABLE chat_sessions
ADD COLUMN IF NOT EXISTS archive_metadata JSONB DEFAULT '{}';

CREATE TABLE IF NOT EXISTS chat_session_messages (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    seq INTEGER NOT NULL,
    client_message_id VARCHAR(64),
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    tool_name VARCHAR(80),
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    token_count INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_chat_session_messages_session_seq UNIQUE(session_id, seq)
);

CREATE TABLE IF NOT EXISTS chat_session_archives (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    start_seq INTEGER NOT NULL,
    end_seq INTEGER NOT NULL,
    summary TEXT,
    object_key VARCHAR(500),
    message_count INTEGER NOT NULL DEFAULT 0,
    token_count INTEGER NOT NULL DEFAULT 0,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_sessions_last_message
ON chat_sessions(last_message_at DESC, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_chat_session_messages_session_seq
ON chat_session_messages(session_id, seq);

CREATE INDEX IF NOT EXISTS idx_chat_session_messages_session_created
ON chat_session_messages(session_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_chat_session_archives_session_start
ON chat_session_archives(session_id, start_seq);

CREATE INDEX IF NOT EXISTS idx_chat_session_archives_session_end
ON chat_session_archives(session_id, end_seq DESC);
