CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(100),
    avatar_url VARCHAR(500),
    email VARCHAR(200),
    phone VARCHAR(20),
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    metadata JSONB DEFAULT '{}'::jsonb,
    last_login_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);

CREATE TABLE IF NOT EXISTS families (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    avatar_url VARCHAR(500),
    invite_code VARCHAR(20) UNIQUE,
    max_members INTEGER DEFAULT 20,
    settings JSONB DEFAULT '{}'::jsonb,
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_families_invite_code ON families(invite_code);

CREATE TABLE IF NOT EXISTS invite_codes (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    source VARCHAR(100),
    description TEXT,
    max_uses INTEGER NOT NULL DEFAULT 1,
    used_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    expires_at TIMESTAMP,
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CHECK (max_uses > 0),
    CHECK (used_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_invite_codes_code ON invite_codes(code);
CREATE INDEX IF NOT EXISTS idx_invite_codes_status ON invite_codes(status);

CREATE TABLE IF NOT EXISTS family_members (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    permissions JSONB DEFAULT '{}'::jsonb,
    joined_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (family_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_family_members_family ON family_members(family_id);
CREATE INDEX IF NOT EXISTS idx_family_members_user ON family_members(user_id);

CREATE TABLE IF NOT EXISTS family_relationships (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    from_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    to_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label VARCHAR(60) NOT NULL,
    reverse_label VARCHAR(60),
    note VARCHAR(500),
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (family_id, from_user_id, to_user_id)
);

CREATE INDEX IF NOT EXISTS idx_family_relationships_viewer ON family_relationships(family_id, from_user_id);
CREATE INDEX IF NOT EXISTS idx_family_relationships_target ON family_relationships(family_id, to_user_id);

CREATE TABLE IF NOT EXISTS care_authorizations (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    subject_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    caregiver_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    scope VARCHAR(40) NOT NULL DEFAULT 'GROWTH_GUARD',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (family_id, subject_user_id, caregiver_user_id, scope)
);

CREATE INDEX IF NOT EXISTS idx_care_auth_subject ON care_authorizations(family_id, subject_user_id);
CREATE INDEX IF NOT EXISTS idx_care_auth_caregiver ON care_authorizations(family_id, caregiver_user_id);
CREATE INDEX IF NOT EXISTS idx_care_auth_status ON care_authorizations(status);
CREATE INDEX IF NOT EXISTS idx_care_auth_lookup_active
    ON care_authorizations(family_id, subject_user_id, caregiver_user_id, scope, expires_at)
    WHERE status = 'ACTIVE';

CREATE TABLE IF NOT EXISTS diary_entries (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    family_id BIGINT REFERENCES families(id) ON DELETE CASCADE,
    raw_text TEXT,
    structured JSONB,
    mood VARCHAR(20),
    tags TEXT[],
    privacy_level VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    permission_scope JSONB DEFAULT '{}'::jsonb,
    source VARCHAR(50) DEFAULT 'USER_INPUT',
    voice_url VARCHAR(500),
    embedding VECTOR(1536),
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_diary_user ON diary_entries(user_id);
CREATE INDEX IF NOT EXISTS idx_diary_family_user ON diary_entries(family_id, user_id);
CREATE INDEX IF NOT EXISTS idx_diary_privacy ON diary_entries(privacy_level);
CREATE INDEX IF NOT EXISTS idx_diary_visibility ON diary_entries(visibility);
CREATE INDEX IF NOT EXISTS idx_diary_tags ON diary_entries USING GIN(tags);
CREATE INDEX IF NOT EXISTS idx_diary_created ON diary_entries(created_at DESC);

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
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_growth_guard_family ON growth_guard_records(family_id);
CREATE INDEX IF NOT EXISTS idx_growth_guard_target ON growth_guard_records(target_user_id);
CREATE INDEX IF NOT EXISTS idx_growth_guard_category ON growth_guard_records(category);
CREATE INDEX IF NOT EXISTS idx_growth_guard_status ON growth_guard_records(status);
CREATE INDEX IF NOT EXISTS idx_growth_guard_observed ON growth_guard_records(observed_at DESC);
CREATE INDEX IF NOT EXISTS idx_growth_guard_family_visibility_status_observed
    ON growth_guard_records(family_id, visibility, status, observed_at DESC, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_growth_guard_family_target_status_observed
    ON growth_guard_records(family_id, target_user_id, status, observed_at DESC, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_growth_guard_family_creator_status_observed
    ON growth_guard_records(family_id, created_by, status, observed_at DESC, created_at DESC);

CREATE TABLE IF NOT EXISTS growth_guard_staleness_votes (
    id BIGSERIAL PRIMARY KEY,
    record_id BIGINT NOT NULL REFERENCES growth_guard_records(id) ON DELETE CASCADE,
    family_id BIGINT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_growth_guard_staleness_record_user UNIQUE (record_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_growth_guard_staleness_record ON growth_guard_staleness_votes(record_id);
CREATE INDEX IF NOT EXISTS idx_growth_guard_staleness_family ON growth_guard_staleness_votes(family_id);
CREATE INDEX IF NOT EXISTS idx_growth_guard_staleness_user ON growth_guard_staleness_votes(user_id);

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
    report JSONB NOT NULL DEFAULT '{}'::jsonb,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_growth_report_family ON growth_guard_reports(family_id);
CREATE INDEX IF NOT EXISTS idx_growth_report_target ON growth_guard_reports(target_user_id);
CREATE INDEX IF NOT EXISTS idx_growth_report_status ON growth_guard_reports(status);
CREATE INDEX IF NOT EXISTS idx_growth_report_week ON growth_guard_reports(week_end DESC);
CREATE INDEX IF NOT EXISTS idx_growth_report_family_target_status_week
    ON growth_guard_reports(family_id, target_user_id, status, week_end DESC);

CREATE TABLE IF NOT EXISTS chat_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    family_id BIGINT REFERENCES families(id),
    messages JSONB DEFAULT '[]'::jsonb,
    title VARCHAR(120),
    subject VARCHAR(50),
    summary TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    permission_scope JSONB DEFAULT '{}'::jsonb,
    source VARCHAR(50) DEFAULT 'FAMILY_AGENT',
    metadata JSONB DEFAULT '{}'::jsonb,
    last_message_at TIMESTAMP,
    message_count INTEGER NOT NULL DEFAULT 0,
    token_count INTEGER NOT NULL DEFAULT 0,
    archived_before_seq INTEGER NOT NULL DEFAULT 0,
    archive_status VARCHAR(20) NOT NULL DEFAULT 'NONE',
    archive_metadata JSONB DEFAULT '{}'::jsonb,
    started_at TIMESTAMP NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_sessions_user ON chat_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_chat_sessions_family_user ON chat_sessions(family_id, user_id);
CREATE INDEX IF NOT EXISTS idx_chat_sessions_status ON chat_sessions(status);
CREATE INDEX IF NOT EXISTS idx_chat_sessions_started ON chat_sessions(started_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_sessions_last_message
    ON chat_sessions(last_message_at DESC, started_at DESC);

CREATE TABLE IF NOT EXISTS chat_session_messages (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    seq INTEGER NOT NULL,
    client_message_id VARCHAR(64),
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    tool_name VARCHAR(80),
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    token_count INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_chat_session_messages_session_seq UNIQUE (session_id, seq)
);

CREATE INDEX IF NOT EXISTS idx_chat_session_messages_session_seq
    ON chat_session_messages(session_id, seq);
CREATE INDEX IF NOT EXISTS idx_chat_session_messages_session_created
    ON chat_session_messages(session_id, created_at DESC);

CREATE TABLE IF NOT EXISTS chat_session_archives (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    start_seq INTEGER NOT NULL,
    end_seq INTEGER NOT NULL,
    summary TEXT,
    object_key VARCHAR(500),
    message_count INTEGER NOT NULL DEFAULT 0,
    token_count INTEGER NOT NULL DEFAULT 0,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_session_archives_session_start
    ON chat_session_archives(session_id, start_seq);
CREATE INDEX IF NOT EXISTS idx_chat_session_archives_session_end
    ON chat_session_archives(session_id, end_seq DESC);

CREATE TABLE IF NOT EXISTS memory_entries (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    family_id BIGINT REFERENCES families(id) ON DELETE CASCADE,
    subject VARCHAR(50),
    type VARCHAR(50) NOT NULL DEFAULT 'LEARNING',
    scope VARCHAR(30) NOT NULL DEFAULT 'PRIVATE',
    content TEXT NOT NULL,
    summary TEXT,
    importance INTEGER NOT NULL DEFAULT 3 CHECK (importance BETWEEN 1 AND 5),
    confidence DECIMAL(5,4) NOT NULL DEFAULT 0.7,
    source_session_id BIGINT REFERENCES chat_sessions(id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_memory_entries_user ON memory_entries(user_id);
CREATE INDEX IF NOT EXISTS idx_memory_entries_family_user ON memory_entries(family_id, user_id);
CREATE INDEX IF NOT EXISTS idx_memory_entries_subject ON memory_entries(subject);
CREATE INDEX IF NOT EXISTS idx_memory_entries_status ON memory_entries(status);
CREATE INDEX IF NOT EXISTS idx_memory_entries_created ON memory_entries(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_memory_entries_family_scope_status_updated
    ON memory_entries(family_id, scope, status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_memory_entries_family_type_status_updated
    ON memory_entries(family_id, type, status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_memory_entries_diary_promotion_source
    ON memory_entries(family_id, ((metadata->>'sourceDiaryId')))
    WHERE metadata->>'source' = 'DIARY_PROMOTION';

CREATE TABLE IF NOT EXISTS memory_entry_votes (
    id BIGSERIAL PRIMARY KEY,
    memory_id BIGINT NOT NULL REFERENCES memory_entries(id) ON DELETE CASCADE,
    family_id BIGINT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    vote_type VARCHAR(10) NOT NULL CHECK (vote_type IN ('UP', 'DOWN')),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_memory_entry_votes_memory_user UNIQUE (memory_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_memory_entry_votes_memory ON memory_entry_votes(memory_id);
CREATE INDEX IF NOT EXISTS idx_memory_entry_votes_family ON memory_entry_votes(family_id);
CREATE INDEX IF NOT EXISTS idx_memory_entry_votes_user ON memory_entry_votes(user_id);

CREATE TABLE IF NOT EXISTS memory_embeddings (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source_type VARCHAR(20) NOT NULL,
    source_id BIGINT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    embedding_model VARCHAR(120),
    embedding VECTOR(1536),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_memory_embeddings_source_hash UNIQUE (source_type, source_id, content_hash),
    CONSTRAINT chk_memory_embeddings_source_type
        CHECK (source_type IN ('DIARY', 'MEMORY', 'GROWTH_OBSERVATION'))
);

CREATE INDEX IF NOT EXISTS idx_memory_embeddings_family ON memory_embeddings(family_id);
CREATE INDEX IF NOT EXISTS idx_memory_embeddings_source ON memory_embeddings(source_type, source_id);
CREATE INDEX IF NOT EXISTS idx_memory_embeddings_status ON memory_embeddings(status);
CREATE INDEX IF NOT EXISTS idx_memory_embeddings_family_status
    ON memory_embeddings(family_id, status);
CREATE INDEX IF NOT EXISTS idx_memory_embeddings_ready_source_latest
    ON memory_embeddings(family_id, source_type, source_id, updated_at DESC)
    WHERE status = 'READY' AND embedding IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_memory_embeddings_vector
    ON memory_embeddings USING ivfflat (embedding vector_cosine_ops)
    WHERE embedding IS NOT NULL;

CREATE TABLE IF NOT EXISTS mirror_agent_data (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    primary_family_id BIGINT REFERENCES families(id) ON DELETE CASCADE,
    traits JSONB DEFAULT '{}'::jsonb,
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    permission_scope JSONB DEFAULT '{}'::jsonb,
    memory_scope JSONB DEFAULT '{}'::jsonb,
    interaction_count INTEGER DEFAULT 0,
    last_updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (user_id)
);

CREATE INDEX IF NOT EXISTS idx_mirror_primary_family ON mirror_agent_data(primary_family_id);
CREATE INDEX IF NOT EXISTS idx_mirror_user_family ON mirror_agent_data(primary_family_id, user_id);
CREATE INDEX IF NOT EXISTS idx_mirror_visibility ON mirror_agent_data(visibility);

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
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_heritage_task_family ON heritage_tasks(family_id);
CREATE INDEX IF NOT EXISTS idx_heritage_task_memory ON heritage_tasks(memory_id);
CREATE INDEX IF NOT EXISTS idx_heritage_task_status ON heritage_tasks(status);
CREATE INDEX IF NOT EXISTS idx_heritage_task_due ON heritage_tasks(due_date ASC);
CREATE INDEX IF NOT EXISTS idx_heritage_task_family_status_due
    ON heritage_tasks(family_id, status, due_date ASC, created_at DESC);

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
    used_sources JSONB DEFAULT '[]'::jsonb,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_skill_runs_family ON skill_runs(family_id);
CREATE INDEX IF NOT EXISTS idx_skill_runs_triggered_by ON skill_runs(triggered_by);
CREATE INDEX IF NOT EXISTS idx_skill_runs_skill ON skill_runs(skill_name);
CREATE INDEX IF NOT EXISTS idx_skill_runs_status ON skill_runs(status);
CREATE INDEX IF NOT EXISTS idx_skill_runs_family_created
    ON skill_runs(family_id, created_at DESC);
