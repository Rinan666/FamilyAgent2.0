-- ============================================
-- FamilyAgent 鏁版嵁搴撳垵濮嬪寲鑴氭湰
-- 鍦?PostgreSQL 瀹瑰櫒棣栨鍚姩鏃惰嚜鍔ㄦ墽琛?-- ============================================

-- 鍚敤 pgvector 鎵╁睍
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================
-- 1. 鐢ㄦ埛琛?-- ============================================
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
    metadata JSONB DEFAULT '{}',
    last_login_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);

-- ============================================
-- 2. 瀹舵棌琛?-- ============================================
CREATE TABLE IF NOT EXISTS families (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    avatar_url VARCHAR(500),
    invite_code VARCHAR(20) UNIQUE,
    max_members INTEGER DEFAULT 20,
    settings JSONB DEFAULT '{}',
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_families_invite_code ON families(invite_code);

-- ============================================
-- 2.1 Beta 邀请码表
-- ============================================
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

INSERT INTO invite_codes (code, source, description, max_uses)
VALUES
    ('FAMILY001', 'seed-family-001', '第一批内测家庭 001', 5),
    ('FAMILY002', 'seed-family-002', '第一批内测家庭 002', 5),
    ('FAMILY003', 'seed-family-003', '第一批内测家庭 003', 5),
    ('FAMILY004', 'seed-family-004', '第一批内测家庭 004', 5),
    ('FAMILY005', 'seed-family-005', '第一批内测家庭 005', 5),
    ('FAMILY006', 'seed-family-006', '第一批内测家庭 006', 5),
    ('FAMILY007', 'seed-family-007', '第一批内测家庭 007', 5),
    ('FAMILY008', 'seed-family-008', '第一批内测家庭 008', 5),
    ('FAMILY009', 'seed-family-009', '第一批内测家庭 009', 5),
    ('FAMILY010', 'seed-family-010', '第一批内测家庭 010', 5)
ON CONFLICT (code) DO NOTHING;

-- ============================================
-- 3. 瀹舵棌鎴愬憳琛?-- ============================================
CREATE TABLE IF NOT EXISTS family_members (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    permissions JSONB DEFAULT '{}',
    joined_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(family_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_family_members_family ON family_members(family_id);
CREATE INDEX IF NOT EXISTS idx_family_members_user ON family_members(user_id);

-- ============================================
-- 4. 鐭ヨ瘑鐐规爲
-- ============================================
CREATE TABLE IF NOT EXISTS knowledge_points (
    id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT REFERENCES knowledge_points(id),
    subject VARCHAR(50) NOT NULL,
    grade VARCHAR(20),
    name VARCHAR(200) NOT NULL,
    description TEXT,
    level INTEGER NOT NULL DEFAULT 1,
    sort_order INTEGER DEFAULT 0,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_kp_subject ON knowledge_points(subject);
CREATE INDEX IF NOT EXISTS idx_kp_parent ON knowledge_points(parent_id);
CREATE INDEX IF NOT EXISTS idx_kp_level ON knowledge_points(level);

-- ============================================
-- 5. 棰樺簱琛?-- ============================================
CREATE TABLE IF NOT EXISTS questions (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT REFERENCES families(id) ON DELETE CASCADE,
    kp_id BIGINT REFERENCES knowledge_points(id),
    subject VARCHAR(50) NOT NULL,
    grade VARCHAR(20),
    type VARCHAR(30) NOT NULL,
    difficulty INTEGER NOT NULL CHECK(difficulty BETWEEN 1 AND 5),
    content JSONB NOT NULL,
    answer JSONB NOT NULL,
    tags TEXT[],
    source VARCHAR(50) DEFAULT 'AI_GENERATED',
    usage_count INTEGER DEFAULT 0,
    correct_rate DECIMAL(5,4),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    permission_scope JSONB DEFAULT '{}',
    created_by BIGINT REFERENCES users(id),
    reviewed_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_questions_family ON questions(family_id);
CREATE INDEX IF NOT EXISTS idx_questions_kp ON questions(kp_id);
CREATE INDEX IF NOT EXISTS idx_questions_subject ON questions(subject);
CREATE INDEX IF NOT EXISTS idx_questions_difficulty ON questions(difficulty);
CREATE INDEX IF NOT EXISTS idx_questions_status ON questions(status);
CREATE INDEX IF NOT EXISTS idx_questions_type ON questions(type);
CREATE INDEX IF NOT EXISTS idx_questions_tags ON questions USING GIN(tags);
CREATE INDEX IF NOT EXISTS idx_questions_visibility ON questions(visibility);

-- ============================================
-- 6. 娴嬭瘯璁板綍琛?-- ============================================
CREATE TABLE IF NOT EXISTS test_records (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    family_id BIGINT REFERENCES families(id),
    question_ids BIGINT[] NOT NULL,
    answers JSONB,
    scores JSONB,
    time_spent INTEGER[],
    total_score DECIMAL(5,2),
    total_time INTEGER,
    status VARCHAR(20) DEFAULT 'COMPLETED',
    source VARCHAR(50) DEFAULT 'ADAPTIVE',
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    permission_scope JSONB DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_test_records_user ON test_records(user_id);
CREATE INDEX IF NOT EXISTS idx_test_records_family ON test_records(family_id);
CREATE INDEX IF NOT EXISTS idx_test_records_family_user ON test_records(family_id, user_id);
CREATE INDEX IF NOT EXISTS idx_test_records_created ON test_records(created_at DESC);

-- ============================================
-- 6.1 Wrong question records
-- ============================================
CREATE TABLE IF NOT EXISTS wrong_question_records (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    family_id BIGINT REFERENCES families(id),
    test_record_id BIGINT NOT NULL REFERENCES test_records(id) ON DELETE CASCADE,
    question_id BIGINT NOT NULL REFERENCES questions(id),
    kp_id BIGINT REFERENCES knowledge_points(id),
    student_answer TEXT,
    score DECIMAL(5,2),
    correct BOOLEAN NOT NULL DEFAULT false,
    error_type VARCHAR(100),
    feedback TEXT,
    parent_explanation TEXT,
    next_suggestion TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_wrong_question_record_once UNIQUE (test_record_id, question_id)
);

CREATE INDEX IF NOT EXISTS idx_wrong_question_records_user ON wrong_question_records(user_id);
CREATE INDEX IF NOT EXISTS idx_wrong_question_records_question ON wrong_question_records(question_id);
CREATE INDEX IF NOT EXISTS idx_wrong_question_records_test ON wrong_question_records(test_record_id);
CREATE INDEX IF NOT EXISTS idx_wrong_question_records_user_status ON wrong_question_records(user_id, status);
CREATE INDEX IF NOT EXISTS idx_wrong_question_records_created ON wrong_question_records(created_at DESC);

-- ============================================
-- 7. 瀛﹀姏妗ｆ琛?-- ============================================
CREATE TABLE IF NOT EXISTS ability_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    family_id BIGINT REFERENCES families(id),
    kp_id BIGINT NOT NULL REFERENCES knowledge_points(id),
    mastery_probability DECIMAL(5,4) NOT NULL DEFAULT 0.5,
    total_attempts INTEGER DEFAULT 0,
    correct_attempts INTEGER DEFAULT 0,
    consecutive_correct INTEGER DEFAULT 0,
    last_attempt_at TIMESTAMP,
    last_correct_at TIMESTAMP,
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    permission_scope JSONB DEFAULT '{}',
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, kp_id)
);

CREATE INDEX IF NOT EXISTS idx_ability_user ON ability_profiles(user_id);
CREATE INDEX IF NOT EXISTS idx_ability_family_user ON ability_profiles(family_id, user_id);
CREATE INDEX IF NOT EXISTS idx_ability_mastery ON ability_profiles(mastery_probability);

-- ============================================
-- 8. 瀹舵暀浼氳瘽琛?-- ============================================
CREATE TABLE IF NOT EXISTS chat_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    family_id BIGINT REFERENCES families(id),
    question_id BIGINT REFERENCES questions(id),
    subject VARCHAR(50),
    knowledge_point_id BIGINT REFERENCES knowledge_points(id),
    messages JSONB DEFAULT '[]',
    summary TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    permission_scope JSONB DEFAULT '{}',
    source VARCHAR(50) DEFAULT 'TUTOR',
    metadata JSONB DEFAULT '{}',
    started_at TIMESTAMP NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_sessions_user ON chat_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_chat_sessions_family_user ON chat_sessions(family_id, user_id);
CREATE INDEX IF NOT EXISTS idx_chat_sessions_status ON chat_sessions(status);
CREATE INDEX IF NOT EXISTS idx_chat_sessions_started ON chat_sessions(started_at DESC);

-- ============================================
-- 8.1 Learning memory entries
-- ============================================
CREATE TABLE IF NOT EXISTS memory_entries (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    family_id BIGINT REFERENCES families(id) ON DELETE CASCADE,
    subject VARCHAR(50),
    knowledge_point_id BIGINT REFERENCES knowledge_points(id),
    type VARCHAR(50) NOT NULL DEFAULT 'LEARNING',
    scope VARCHAR(30) NOT NULL DEFAULT 'PRIVATE',
    content TEXT NOT NULL,
    summary TEXT,
    importance INTEGER NOT NULL DEFAULT 3 CHECK (importance BETWEEN 1 AND 5),
    confidence DECIMAL(5,4) NOT NULL DEFAULT 0.7,
    source_session_id BIGINT REFERENCES chat_sessions(id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_memory_entries_user ON memory_entries(user_id);
CREATE INDEX IF NOT EXISTS idx_memory_entries_family_user ON memory_entries(family_id, user_id);
CREATE INDEX IF NOT EXISTS idx_memory_entries_subject ON memory_entries(subject);
CREATE INDEX IF NOT EXISTS idx_memory_entries_kp ON memory_entries(knowledge_point_id);
CREATE INDEX IF NOT EXISTS idx_memory_entries_status ON memory_entries(status);
CREATE INDEX IF NOT EXISTS idx_memory_entries_created ON memory_entries(created_at DESC);

-- ============================================
-- 9. 鏃ヨ琛紙Phase 2 棰勭暀锛?-- ============================================
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
    permission_scope JSONB DEFAULT '{}',
    source VARCHAR(50) DEFAULT 'USER_INPUT',
    voice_url VARCHAR(500),
    embedding vector(1536),
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_diary_user ON diary_entries(user_id);
CREATE INDEX IF NOT EXISTS idx_diary_family_user ON diary_entries(family_id, user_id);
CREATE INDEX IF NOT EXISTS idx_diary_privacy ON diary_entries(privacy_level);
CREATE INDEX IF NOT EXISTS idx_diary_visibility ON diary_entries(visibility);
CREATE INDEX IF NOT EXISTS idx_diary_tags ON diary_entries USING GIN(tags);
CREATE INDEX IF NOT EXISTS idx_diary_created ON diary_entries(created_at DESC);

-- ============================================
-- 10. 瀹舵棌鐭ヨ瘑搴撹〃锛圥hase 2 棰勭暀锛?-- ============================================
CREATE TABLE IF NOT EXISTS family_knowledge (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    title VARCHAR(300) NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(50) NOT NULL DEFAULT 'WISDOM',
    tags TEXT[],
    author_id BIGINT REFERENCES users(id),
    visibility VARCHAR(20) NOT NULL DEFAULT 'FAMILY',
    permission_scope JSONB DEFAULT '{}',
    source VARCHAR(50) DEFAULT 'MANUAL',
    embedding vector(1536),
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_fk_family ON family_knowledge(family_id);
CREATE INDEX IF NOT EXISTS idx_fk_family_type ON family_knowledge(family_id, type);
CREATE INDEX IF NOT EXISTS idx_fk_type ON family_knowledge(type);
CREATE INDEX IF NOT EXISTS idx_fk_tags ON family_knowledge USING GIN(tags);

-- ============================================
-- 11. 闀滃儚Agent鏁版嵁琛紙Phase 3 棰勭暀锛?-- ============================================
CREATE TABLE IF NOT EXISTS mirror_agent_data (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    primary_family_id BIGINT REFERENCES families(id),
    personality_vector vector(1536),
    memory_embeddings vector(1536)[],
    traits JSONB,
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    permission_scope JSONB DEFAULT '{}',
    memory_scope JSONB DEFAULT '{}',
    interaction_count INTEGER DEFAULT 0,
    last_updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(user_id)
);

CREATE INDEX IF NOT EXISTS idx_mirror_primary_family ON mirror_agent_data(primary_family_id);

-- ============================================
-- 12. 绉熸埛瀛樺偍璺敱琛紙Phase 4 棰勭暀锛?-- ============================================
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

-- ============================================
-- 鎻掑叆榛樿鏁版嵁锛氬垵涓暟瀛︾煡璇嗙偣绀轰緥
-- ============================================
INSERT INTO knowledge_points (id, parent_id, subject, grade, name, description, level, sort_order) VALUES
(1, NULL, 'math', 'grade7', '鏁颁笌寮?, '鍒濅腑鏁板鍩虹锛氭暟涓庝唬鏁板紡', 1, 1),
(2, 1, 'math', 'grade7', '鏈夌悊鏁?, '姝ｈ礋鏁般€佹暟杞淬€佺粷瀵瑰€?, 2, 1),
(3, 1, 'math', 'grade7', '鏁村紡鐨勫姞鍑?, '浠ｆ暟寮忕殑鍔犲噺杩愮畻', 2, 2),
(4, 1, 'math', 'grade8', '鏁村紡鐨勪箻闄?, '浠ｆ暟寮忕殑涔橀櫎涓庡洜寮忓垎瑙?, 2, 3),
(5, NULL, 'math', 'grade7', '鏂圭▼涓庝笉绛夊紡', '涓€鍏冧竴娆℃柟绋嬩笌涓嶇瓑寮?, 1, 2),
(6, 5, 'math', 'grade7', '涓€鍏冧竴娆℃柟绋?, '鍚湁涓€涓湭鐭ユ暟鐨勪竴娆℃柟绋?, 2, 1),
(7, 5, 'math', 'grade7', '涓€鍏冧竴娆′笉绛夊紡', '鍚湁涓€涓湭鐭ユ暟鐨勪竴娆′笉绛夊紡', 2, 2),
(8, 5, 'math', 'grade8', '浜屽厓涓€娆℃柟绋嬬粍', '涓や釜鏈煡鏁扮殑鏂圭▼缁勬眰瑙?, 2, 3),
(9, NULL, 'math', 'grade8', '鍑犱綍鍒濇', '骞抽潰鍑犱綍鍩虹姒傚康', 1, 3),
(10, 9, 'math', 'grade7', '绾挎涓庤', '绾挎銆佽鐨勫害閲忎笌鍏崇郴', 2, 1),
(11, 9, 'math', 'grade8', '涓夎褰?, '涓夎褰㈢殑鎬ц川銆佸叏绛変笌鐩镐技', 2, 2),
(12, 9, 'math', 'grade8', '鍥涜竟褰?, '骞宠鍥涜竟褰€佺煩褰€佽彵褰?, 2, 3),
(13, NULL, 'math', 'grade8', '鍑芥暟鍩虹', '鍑芥暟姒傚康涓庝竴娆″嚱鏁?, 1, 4),
(14, 13, 'math', 'grade8', '骞抽潰鐩磋鍧愭爣绯?, '鍧愭爣绯荤殑姒傚康涓庡簲鐢?, 2, 1),
(15, 13, 'math', 'grade8', '涓€娆″嚱鏁?, 'y=kx+b鐨勫浘鍍忎笌鎬ц川', 2, 2);

-- 閲嶇疆搴忓垪
SELECT setval('knowledge_points_id_seq', (SELECT MAX(id) FROM knowledge_points));

-- ============================================
-- 鎻掑叆榛樿鏁版嵁锛氬垵涓暟瀛︽祴璇曢搴撶ず渚?-- 鐢ㄤ簬 AI瀹舵暀鎶介銆佹祴璇曟ā寮忋€侀敊棰樻湰鍜屽鍔涜瘎浼伴棴鐜?-- ============================================
INSERT INTO questions
(id, kp_id, subject, grade, type, difficulty, content, answer, tags, source, status)
VALUES
(1, 6, 'math', 'grade7', 'CALCULATION', 1,
 '{"stem":"瑙ｆ柟绋嬶細x + 7 = 12"}'::jsonb,
 '{"value":"x = 5","steps":["绛夊紡涓よ竟鍚屾椂鍑忓幓 7锛歺 = 12 - 7","璁＄畻寰楀埌 x = 5"],"explanation":"瑙ｄ竴鍏冧竴娆℃柟绋嬬殑鐩爣鏄妸鏈煡鏁板崟鐙暀鍦ㄧ瓑寮忎竴杈广€?}'::jsonb,
 ARRAY['涓€鍏冧竴娆℃柟绋?,'绛夊紡鎬ц川'], 'SEED', 'ACTIVE'),
(2, 6, 'math', 'grade7', 'CALCULATION', 2,
 '{"stem":"瑙ｆ柟绋嬶細2x + 5 = 13"}'::jsonb,
 '{"value":"x = 4","steps":["绛夊紡涓よ竟鍚屾椂鍑忓幓 5锛?x = 8","绛夊紡涓よ竟鍚屾椂闄や互 2锛歺 = 4"],"explanation":"鍏堢Щ甯告暟椤癸紝鍐嶆妸鏈煡鏁扮郴鏁板寲涓?1銆?}'::jsonb,
 ARRAY['涓€鍏冧竴娆℃柟绋?,'绉婚」'], 'SEED', 'ACTIVE'),
(3, 6, 'math', 'grade7', 'CALCULATION', 3,
 '{"stem":"瑙ｆ柟绋嬶細3(x - 2) = 2x + 5"}'::jsonb,
 '{"value":"x = 11","steps":["灞曞紑宸﹁竟锛?x - 6 = 2x + 5","绛夊紡涓よ竟鍚屾椂鍑忓幓 2x锛歺 - 6 = 5","绛夊紡涓よ竟鍚屾椂鍔?6锛歺 = 11"],"explanation":"鍚嫭鍙风殑涓€鍏冧竴娆℃柟绋嬮€氬父鍏堝幓鎷彿锛屽啀绉婚」鍚堝苟銆?}'::jsonb,
 ARRAY['涓€鍏冧竴娆℃柟绋?,'鍘绘嫭鍙?,'绉婚」'], 'SEED', 'ACTIVE'),
(4, 6, 'math', 'grade7', 'CALCULATION', 4,
 '{"stem":"瑙ｆ柟绋嬶細(x + 1)/3 + (x - 2)/2 = 4"}'::jsonb,
 '{"value":"x = 28/5","steps":["绛夊紡涓よ竟鍚屾椂涔樹互 6锛?(x + 1) + 3(x - 2) = 24","灞曞紑锛?x + 2 + 3x - 6 = 24","鍚堝苟鍚岀被椤癸細5x - 4 = 24","瑙ｅ緱锛?x = 28锛寈 = 28/5"],"explanation":"杩欓鐨勯噸鐐规槸鍏堝幓鍒嗘瘝锛屽啀鍘绘嫭鍙枫€佸悎骞跺悓绫婚」銆?}'::jsonb,
 ARRAY['涓€鍏冧竴娆℃柟绋?,'鍘诲垎姣?], 'SEED', 'ACTIVE'),
(5, 7, 'math', 'grade7', 'CALCULATION', 1,
 '{"stem":"瑙ｄ笉绛夊紡锛歺 + 3 > 8"}'::jsonb,
 '{"value":"x > 5","steps":["涓嶇瓑寮忎袱杈瑰悓鏃跺噺鍘?3锛歺 > 5"],"explanation":"涓嶇瓑寮忎袱杈瑰悓鏃跺姞鍑忓悓涓€涓暟锛屼笉绛夊彿鏂瑰悜涓嶅彉銆?}'::jsonb,
 ARRAY['涓€鍏冧竴娆′笉绛夊紡'], 'SEED', 'ACTIVE'),
(6, 7, 'math', 'grade7', 'CHOICE', 2,
 '{"stem":"涓嶇瓑寮?2x - 3 > 5 鐨勮В闆嗘槸锛?,"options":["x > 4","x < 4","x > 1","x < 1"]}'::jsonb,
 '{"value":"x > 4","steps":["涓よ竟鍚屾椂鍔?3锛?x > 8","涓よ竟鍚屾椂闄や互 2锛歺 > 4"],"explanation":"姝ｆ暟闄ゆ硶涓嶄細鏀瑰彉涓嶇瓑鍙锋柟鍚戙€?}'::jsonb,
 ARRAY['涓€鍏冧竴娆′笉绛夊紡','閫夋嫨棰?], 'SEED', 'ACTIVE'),
(7, 7, 'math', 'grade7', 'CALCULATION', 3,
 '{"stem":"瑙ｄ笉绛夊紡锛?3x + 6 <= 12"}'::jsonb,
 '{"value":"x >= -2","steps":["涓よ竟鍚屾椂鍑忓幓 6锛?3x <= 6","涓よ竟鍚屾椂闄や互 -3锛屼笉绛夊彿鏂瑰悜鏀瑰彉锛歺 >= -2"],"explanation":"涓嶇瓑寮忎袱杈瑰悓鏃朵箻闄よ礋鏁版椂锛屼笉绛夊彿鏂瑰悜瑕佹敼鍙樸€?}'::jsonb,
 ARRAY['涓€鍏冧竴娆′笉绛夊紡','璐熸暟'], 'SEED', 'ACTIVE'),
(8, 3, 'math', 'grade7', 'CALCULATION', 1,
 '{"stem":"鍖栫畝锛?a + 2a - 5a"}'::jsonb,
 '{"value":"0","steps":["鍚堝苟鍚岀被椤癸細(3 + 2 - 5)a","璁＄畻绯绘暟锛?a = 0"],"explanation":"鍚岀被椤瑰悎骞舵椂锛屽彧鍚堝苟绯绘暟锛屽瓧姣嶉儴鍒嗕笉鍙樸€?}'::jsonb,
 ARRAY['鏁村紡鐨勫姞鍑?,'鍚堝苟鍚岀被椤?], 'SEED', 'ACTIVE'),
(9, 3, 'math', 'grade7', 'CALCULATION', 2,
 '{"stem":"鍖栫畝锛?(3x - 4) - (x + 5)"}'::jsonb,
 '{"value":"5x - 13","steps":["鍘绘嫭鍙凤細6x - 8 - x - 5","鍚堝苟鍚岀被椤癸細5x - 13"],"explanation":"鎷彿鍓嶆槸璐熷彿鏃讹紝鍘绘嫭鍙峰悗鎷彿鍐呭悇椤归兘瑕佸彉鍙枫€?}'::jsonb,
 ARRAY['鏁村紡鐨勫姞鍑?,'鍘绘嫭鍙?], 'SEED', 'ACTIVE'),
(10, 4, 'math', 'grade8', 'CALCULATION', 2,
 '{"stem":"璁＄畻锛?2x)^2 路 3x"}'::jsonb,
 '{"value":"12x^3","steps":["鍏堢畻涔樻柟锛?2x)^2 = 4x^2","鍐嶄箻 3x锛?x^2 路 3x = 12x^3"],"explanation":"鍚屽簳鏁板箓鐩镐箻锛屾寚鏁扮浉鍔犮€?}'::jsonb,
 ARRAY['鏁村紡鐨勪箻闄?,'骞傝繍绠?], 'SEED', 'ACTIVE'),
(11, 4, 'math', 'grade8', 'CALCULATION', 3,
 '{"stem":"鍥犲紡鍒嗚В锛歺^2 - 9"}'::jsonb,
 '{"value":"(x + 3)(x - 3)","steps":["璇嗗埆骞虫柟宸細x^2 - 9 = x^2 - 3^2","濂楃敤鍏紡 a^2 - b^2 = (a + b)(a - b)","寰楀埌 (x + 3)(x - 3)"],"explanation":"骞虫柟宸叕寮忔槸鍒濅腑鍥犲紡鍒嗚В鐨勫父鐢ㄥ伐鍏枫€?}'::jsonb,
 ARRAY['鍥犲紡鍒嗚В','骞虫柟宸?], 'SEED', 'ACTIVE'),
(12, 8, 'math', 'grade8', 'CALCULATION', 2,
 '{"stem":"瑙ｆ柟绋嬬粍锛歺 + y = 7锛寈 - y = 1"}'::jsonb,
 '{"value":"x = 4, y = 3","steps":["涓ゅ紡鐩稿姞锛?x = 8","瑙ｅ緱 x = 4","浠ｅ叆 x + y = 7锛屽緱 y = 3"],"explanation":"鍔犲噺娑堝厓閫傚悎绯绘暟浜掍负鐩稿弽鏁版垨鐩稿悓鐨勬柟绋嬬粍銆?}'::jsonb,
 ARRAY['浜屽厓涓€娆℃柟绋嬬粍','鍔犲噺娑堝厓'], 'SEED', 'ACTIVE'),
(13, 8, 'math', 'grade8', 'CALCULATION', 3,
 '{"stem":"瑙ｆ柟绋嬬粍锛?x + y = 9锛寈 + 2y = 8"}'::jsonb,
 '{"value":"x = 10/3, y = 7/3","steps":["鐢?2x + y = 9 寰?y = 9 - 2x","浠ｅ叆 x + 2y = 8锛歺 + 2(9 - 2x) = 8","瑙ｅ緱 -3x = -10锛屾墍浠?x = 10/3","浠ｅ洖寰?y = 7/3"],"explanation":"浠ｅ叆娑堝厓鐨勬牳蹇冩槸鐢ㄤ竴涓湭鐭ユ暟琛ㄧず鍙︿竴涓湭鐭ユ暟銆?}'::jsonb,
 ARRAY['浜屽厓涓€娆℃柟绋嬬粍','浠ｅ叆娑堝厓'], 'SEED', 'ACTIVE'),
(14, 10, 'math', 'grade7', 'CALCULATION', 1,
 '{"stem":"宸茬煡 鈭燗 = 35掳锛屸垹B 涓?鈭燗 浜掍綑锛屾眰 鈭燘銆?}'::jsonb,
 '{"value":"55掳","steps":["浜掍綑鐨勪袱涓鍜屼负 90掳","鈭燘 = 90掳 - 35掳 = 55掳"],"explanation":"浜掍綑鐪?90掳锛屼簰琛ョ湅 180掳銆?}'::jsonb,
 ARRAY['绾挎涓庤','浜掍綑'], 'SEED', 'ACTIVE'),
(15, 10, 'math', 'grade7', 'CALCULATION', 2,
 '{"stem":"宸茬煡 鈭燗 = 120掳锛屸垹B 涓?鈭燗 浜掕ˉ锛屾眰 鈭燘銆?}'::jsonb,
 '{"value":"60掳","steps":["浜掕ˉ鐨勪袱涓鍜屼负 180掳","鈭燘 = 180掳 - 120掳 = 60掳"],"explanation":"鍒ゆ柇浜掕ˉ鏃舵姄浣忔€诲拰 180掳銆?}'::jsonb,
 ARRAY['绾挎涓庤','浜掕ˉ'], 'SEED', 'ACTIVE'),
(16, 11, 'math', 'grade8', 'CHOICE', 2,
 '{"stem":"涓夎褰袱杈归暱鍒嗗埆涓?3 鍜?5锛岀涓夎竟鍙兘鏄紵","options":["1","2","7","9"]}'::jsonb,
 '{"value":"7","steps":["涓夎褰㈢涓夎竟 c 婊¤冻 |5 - 3| < c < 5 + 3","鍗?2 < c < 8","閫夐」涓彧鏈?7 婊¤冻"],"explanation":"涓夎褰换鎰忎袱杈逛箣鍜屽ぇ浜庣涓夎竟锛屼换鎰忎袱杈逛箣宸皬浜庣涓夎竟銆?}'::jsonb,
 ARRAY['涓夎褰?,'涓夎竟鍏崇郴'], 'SEED', 'ACTIVE'),
(17, 11, 'math', 'grade8', 'CALCULATION', 3,
 '{"stem":"涓€涓笁瑙掑舰涓変釜鍐呰涔嬫瘮涓?2:3:4锛屾眰鏈€澶ц銆?}'::jsonb,
 '{"value":"80掳","steps":["涓夎褰㈠唴瑙掑拰涓?180掳","鎬讳唤鏁?2 + 3 + 4 = 9","姣忎唤涓?180掳 / 9 = 20掳","鏈€澶ц涓?4 浠斤細80掳"],"explanation":"姣斾緥闂鍏堟眰鎬讳唤鏁帮紝鍐嶆眰姣忎唤澶у皬銆?}'::jsonb,
 ARRAY['涓夎褰?,'鍐呰鍜?], 'SEED', 'ACTIVE'),
(18, 14, 'math', 'grade8', 'CALCULATION', 1,
 '{"stem":"鐐?A(3, -2) 鍦ㄧ鍑犺薄闄愶紵"}'::jsonb,
 '{"value":"绗洓璞￠檺","steps":["妯潗鏍?x = 3 > 0","绾靛潗鏍?y = -2 < 0","妯绾佃礋鐨勭偣鍦ㄧ鍥涜薄闄?],"explanation":"璞￠檺鍒ゆ柇鐪嬫í绾靛潗鏍囩殑姝ｈ礋銆?}'::jsonb,
 ARRAY['骞抽潰鐩磋鍧愭爣绯?,'璞￠檺'], 'SEED', 'ACTIVE'),
(19, 15, 'math', 'grade8', 'CALCULATION', 2,
 '{"stem":"涓€娆″嚱鏁?y = 2x + 1锛屽綋 x = 3 鏃讹紝y 鐨勫€兼槸澶氬皯锛?}'::jsonb,
 '{"value":"7","steps":["鎶?x = 3 浠ｅ叆 y = 2x + 1","y = 2 脳 3 + 1 = 7"],"explanation":"姹傚嚱鏁板€煎氨鏄妸鑷彉閲忎唬鍏ヨВ鏋愬紡銆?}'::jsonb,
 ARRAY['涓€娆″嚱鏁?,'鍑芥暟鍊?], 'SEED', 'ACTIVE'),
(20, 15, 'math', 'grade8', 'CALCULATION', 3,
 '{"stem":"宸茬煡涓€娆″嚱鏁?y = kx + 2 缁忚繃鐐?(3, 8)锛屾眰 k銆?}'::jsonb,
 '{"value":"k = 2","steps":["鎶婄偣 (3, 8) 浠ｅ叆 y = kx + 2","寰楀埌 8 = 3k + 2","瑙ｅ緱 3k = 6锛屾墍浠?k = 2"],"explanation":"鍑芥暟鍥惧儚缁忚繃鏌愮偣锛岃鏄庤鐐瑰潗鏍囨弧瓒冲嚱鏁拌В鏋愬紡銆?}'::jsonb,
 ARRAY['涓€娆″嚱鏁?,'寰呭畾绯绘暟娉?], 'SEED', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

-- 閲嶇疆棰樺簱搴忓垪
SELECT setval('questions_id_seq', (SELECT MAX(id) FROM questions));

