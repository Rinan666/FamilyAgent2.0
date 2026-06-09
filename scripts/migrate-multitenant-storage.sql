-- ============================================
-- FamilyAgent 澶氱鎴峰瓨鍌ㄥ瓧娈佃縼绉昏剼鏈?-- 閫傜敤浜庡凡缁忔墽琛岃繃 init-db.sql 鐨勬湰鍦?寮€鍙戞暟鎹簱
-- 鍙噸澶嶆墽琛?-- ============================================

CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE questions
    ADD COLUMN IF NOT EXISTS family_id BIGINT REFERENCES families(id) ON DELETE CASCADE,
    ADD COLUMN IF NOT EXISTS visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    ADD COLUMN IF NOT EXISTS permission_scope JSONB DEFAULT '{}';

CREATE INDEX IF NOT EXISTS idx_questions_family ON questions(family_id);
CREATE INDEX IF NOT EXISTS idx_questions_visibility ON questions(visibility);
CREATE INDEX IF NOT EXISTS idx_questions_tags ON questions USING GIN(tags);

ALTER TABLE test_records
    ADD COLUMN IF NOT EXISTS visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    ADD COLUMN IF NOT EXISTS permission_scope JSONB DEFAULT '{}';

CREATE INDEX IF NOT EXISTS idx_test_records_family_user ON test_records(family_id, user_id);

ALTER TABLE ability_profiles
    ADD COLUMN IF NOT EXISTS family_id BIGINT REFERENCES families(id),
    ADD COLUMN IF NOT EXISTS visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    ADD COLUMN IF NOT EXISTS permission_scope JSONB DEFAULT '{}';

CREATE INDEX IF NOT EXISTS idx_ability_family_user ON ability_profiles(family_id, user_id);

ALTER TABLE chat_sessions
    ADD COLUMN IF NOT EXISTS family_id BIGINT REFERENCES families(id),
    ADD COLUMN IF NOT EXISTS visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    ADD COLUMN IF NOT EXISTS permission_scope JSONB DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS source VARCHAR(50) DEFAULT 'FAMILY_AGENT',
    ADD COLUMN IF NOT EXISTS metadata JSONB DEFAULT '{}';

CREATE INDEX IF NOT EXISTS idx_chat_sessions_family_user ON chat_sessions(family_id, user_id);

ALTER TABLE diary_entries
    ADD COLUMN IF NOT EXISTS family_id BIGINT REFERENCES families(id) ON DELETE CASCADE,
    ADD COLUMN IF NOT EXISTS visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    ADD COLUMN IF NOT EXISTS permission_scope JSONB DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS source VARCHAR(50) DEFAULT 'USER_INPUT',
    ADD COLUMN IF NOT EXISTS embedding vector(1536),
    ADD COLUMN IF NOT EXISTS metadata JSONB DEFAULT '{}';

CREATE INDEX IF NOT EXISTS idx_diary_family_user ON diary_entries(family_id, user_id);
CREATE INDEX IF NOT EXISTS idx_diary_visibility ON diary_entries(visibility);
CREATE INDEX IF NOT EXISTS idx_diary_tags ON diary_entries USING GIN(tags);

ALTER TABLE family_knowledge
    ADD COLUMN IF NOT EXISTS visibility VARCHAR(20) NOT NULL DEFAULT 'FAMILY',
    ADD COLUMN IF NOT EXISTS permission_scope JSONB DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS source VARCHAR(50) DEFAULT 'MANUAL';

CREATE INDEX IF NOT EXISTS idx_fk_family_type ON family_knowledge(family_id, type);
CREATE INDEX IF NOT EXISTS idx_fk_tags ON family_knowledge USING GIN(tags);

ALTER TABLE mirror_agent_data
    ADD COLUMN IF NOT EXISTS primary_family_id BIGINT REFERENCES families(id),
    ADD COLUMN IF NOT EXISTS visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    ADD COLUMN IF NOT EXISTS permission_scope JSONB DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS memory_scope JSONB DEFAULT '{}';

CREATE INDEX IF NOT EXISTS idx_mirror_primary_family ON mirror_agent_data(primary_family_id);

CREATE TABLE IF NOT EXISTS tenant_storage_routes (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    storage_tier VARCHAR(30) NOT NULL DEFAULT 'SHARED',
    shard_key VARCHAR(100),
    database_alias VARCHAR(100),
    schema_name VARCHAR(100),
    bucket_prefix VARCHAR(300),
    vector_namespace VARCHAR(300),
    encryption_key_ref VARCHAR(300),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(family_id)
);

CREATE INDEX IF NOT EXISTS idx_tenant_storage_tier ON tenant_storage_routes(storage_tier);
