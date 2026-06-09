-- ============================================
-- FamilyAgent 閺佺増宓佹惔鎾冲灥婵瀵查懘姘拱
-- 閸?PostgreSQL 鐎圭懓娅掓＃鏍偧閸氼垰濮╅弮鎯板殰閸斻劍澧界悰?-- ============================================

-- 閸氼垳鏁?pgvector 閹碘晛鐫?
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================
-- 1. 閻劍鍩涚悰?-- ============================================
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
-- 2. 鐎硅埖妫岀悰?-- ============================================
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
-- 2.1 Beta 閭€璇风爜琛?
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
    ('FAMILY001', 'seed-family-001', '绗竴鎵瑰唴娴嬪搴?001', 5),
    ('FAMILY002', 'seed-family-002', '绗竴鎵瑰唴娴嬪搴?002', 5),
    ('FAMILY003', 'seed-family-003', '绗竴鎵瑰唴娴嬪搴?003', 5),
    ('FAMILY004', 'seed-family-004', '绗竴鎵瑰唴娴嬪搴?004', 5),
    ('FAMILY005', 'seed-family-005', '绗竴鎵瑰唴娴嬪搴?005', 5),
    ('FAMILY006', 'seed-family-006', '绗竴鎵瑰唴娴嬪搴?006', 5),
    ('FAMILY007', 'seed-family-007', '绗竴鎵瑰唴娴嬪搴?007', 5),
    ('FAMILY008', 'seed-family-008', '绗竴鎵瑰唴娴嬪搴?008', 5),
    ('FAMILY009', 'seed-family-009', '绗竴鎵瑰唴娴嬪搴?009', 5),
    ('FAMILY010', 'seed-family-010', '绗竴鎵瑰唴娴嬪搴?010', 5)
ON CONFLICT (code) DO NOTHING;

-- ============================================
-- 3. 鐎硅埖妫岄幋鎰喅鐞?-- ============================================
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
-- 4. 閻儴鐦戦悙瑙勭埐
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
-- 5. 妫版ê绨辩悰?-- ============================================
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
-- 6. 濞村鐦拋鏉跨秿鐞?-- ============================================
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
    member_answer TEXT,
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
-- 7. 鐎涳箑濮忓锝嗩攳鐞?-- ============================================
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
-- 8. 鐎硅埖鏆€娴兼俺鐦界悰?-- ============================================
CREATE TABLE IF NOT EXISTS chat_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    family_id BIGINT REFERENCES families(id),
    question_id BIGINT REFERENCES questions(id),
    subject VARCHAR(50),
    knowledge_point_id BIGINT REFERENCES knowledge_points(id),
    messages JSONB DEFAULT '[]',
    title VARCHAR(120),
    summary TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    permission_scope JSONB DEFAULT '{}',
    source VARCHAR(50) DEFAULT 'FAMILY_AGENT',
    metadata JSONB DEFAULT '{}',
    last_message_at TIMESTAMP,
    message_count INTEGER NOT NULL DEFAULT 0,
    token_count INTEGER NOT NULL DEFAULT 0,
    archived_before_seq INTEGER NOT NULL DEFAULT 0,
    archive_status VARCHAR(20) NOT NULL DEFAULT 'NONE',
    archive_metadata JSONB DEFAULT '{}',
    started_at TIMESTAMP NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_sessions_user ON chat_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_chat_sessions_family_user ON chat_sessions(family_id, user_id);
CREATE INDEX IF NOT EXISTS idx_chat_sessions_status ON chat_sessions(status);
CREATE INDEX IF NOT EXISTS idx_chat_sessions_started ON chat_sessions(started_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_sessions_last_message ON chat_sessions(last_message_at DESC, started_at DESC);

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
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_session_archives_session_start
    ON chat_session_archives(session_id, start_seq);

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
-- 9. 閺冦儴顔囩悰顭掔礄Phase 2 妫板嫮鏆€閿?-- ============================================
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
-- 10. 鐎硅埖妫岄惌銉ㄧ槕鎼存捁銆冮敍鍦ase 2 妫板嫮鏆€閿?-- ============================================
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
-- 11. 闂€婊冨剼Agent閺佺増宓佺悰顭掔礄Phase 3 妫板嫮鏆€閿?-- ============================================
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
-- 12. 缁夌喐鍩涚€涙ê鍋嶇捄顖滄暠鐞涱煉绱橮hase 4 妫板嫮鏆€閿?-- ============================================
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
-- 閹绘帒鍙嗘妯款吇閺佺増宓侀敍姘灥娑擃厽鏆熺€涳妇鐓＄拠鍡欏仯缁€杞扮伐
-- ============================================
INSERT INTO knowledge_points (id, parent_id, subject, grade, name, description, level, sort_order) VALUES
(1, NULL, 'math', 'grade7', '閺侀绗屽?, '閸掓繀鑵戦弫鏉款劅閸╄櫣顢呴敍姘殶娑撳簼鍞弫鏉跨础', 1, 1),
(2, 1, 'math', 'grade7', '閺堝鎮婇弫?, '濮濓綀绀嬮弫鑸偓浣规殶鏉炴番鈧胶绮风€电懓鈧?, 2, 1),
(3, 1, 'math', 'grade7', '閺佹潙绱￠惃鍕閸?, '娴狅絾鏆熷蹇曟畱閸旂姴鍣烘潻鎰暬', 2, 2),
(4, 1, 'math', 'grade8', '閺佹潙绱￠惃鍕闂?, '娴狅絾鏆熷蹇曟畱娑旀﹢娅庢稉搴℃礈瀵繐鍨庣憴?, 2, 3),
(5, NULL, 'math', 'grade7', '閺傚湱鈻兼稉搴濈瑝缁涘绱?, '娑撯偓閸忓啩绔村▎鈩冩煙缁嬪绗屾稉宥囩搼瀵?, 1, 2),
(6, 5, 'math', 'grade7', '娑撯偓閸忓啩绔村▎鈩冩煙缁?, '閸氼偅婀佹稉鈧稉顏呮弓閻儲鏆熼惃鍕濞嗏剝鏌熺粙?, 2, 1),
(7, 5, 'math', 'grade7', '娑撯偓閸忓啩绔村▎鈥茬瑝缁涘绱?, '閸氼偅婀佹稉鈧稉顏呮弓閻儲鏆熼惃鍕濞嗏€茬瑝缁涘绱?, 2, 2),
(8, 5, 'math', 'grade8', '娴滃苯鍘撴稉鈧▎鈩冩煙缁嬪绮?, '娑撱倓閲滈張顏嗙叀閺佹壆娈戦弬鍦柤缂佸嫭鐪扮憴?, 2, 3),
(9, NULL, 'math', 'grade8', '閸戠姳缍嶉崚婵囶劄', '楠炴娊娼伴崙鐘辩秿閸╄櫣顢呭鍌氬悍', 1, 3),
(10, 9, 'math', 'grade7', '缁炬寧顔屾稉搴ゎ潡', '缁炬寧顔岄妴浣筋潡閻ㄥ嫬瀹抽柌蹇庣瑢閸忓磭閮?, 2, 1),
(11, 9, 'math', 'grade8', '娑撳顫楄ぐ?, '娑撳顫楄ぐ銏㈡畱閹嗗窛閵嗕礁鍙忕粵澶夌瑢閻╅晲鎶€', 2, 2),
(12, 9, 'math', 'grade8', '閸ユ稖绔熻ぐ?, '楠炲疇顢戦崶娑滅珶瑜邦潿鈧胶鐓╄ぐ顫偓浣藉降瑜?, 2, 3),
(13, NULL, 'math', 'grade8', '閸戣姤鏆熼崺铏诡攨', '閸戣姤鏆熷鍌氬悍娑撳簼绔村▎鈥冲毐閺?, 1, 4),
(14, 13, 'math', 'grade8', '楠炴娊娼伴惄纾嬵潡閸ф劖鐖ｇ化?, '閸ф劖鐖ｇ化鑽ゆ畱濮掑倸搴锋稉搴＄安閻?, 2, 1),
(15, 13, 'math', 'grade8', '娑撯偓濞嗏€冲毐閺?, 'y=kx+b閻ㄥ嫬娴橀崓蹇庣瑢閹嗗窛', 2, 2);

-- 闁插秶鐤嗘惔蹇撳灙
SELECT setval('knowledge_points_id_seq', (SELECT MAX(id) FROM knowledge_points));

-- ============================================
-- 閹绘帒鍙嗘妯款吇閺佺増宓侀敍姘灥娑擃厽鏆熺€涳附绁寸拠鏇㈩暯鎼存挾銇氭笟?-- 閻劋绨?AI鐎硅埖鏆€閹朵粙顣介妴浣圭ゴ鐠囨洘膩瀵繈鈧線鏁婃０妯绘拱閸滃苯顒熼崝娑滅槑娴间即妫撮悳?-- ============================================
INSERT INTO questions
(id, kp_id, subject, grade, type, difficulty, content, answer, tags, source, status)
VALUES
(1, 6, 'math', 'grade7', 'CALCULATION', 1,
 '{"stem":"鐟欙絾鏌熺粙瀣剁窗x + 7 = 12"}'::jsonb,
 '{"value":"x = 5","steps":["缁涘绱℃稉銈堢珶閸氬本妞傞崙蹇撳箵 7閿涙 = 12 - 7","鐠侊紕鐣诲妤€鍩?x = 5"],"explanation":"鐟欙絼绔撮崗鍐х濞嗏剝鏌熺粙瀣畱閻╊喗鐖ｉ弰顖涘Ω閺堫亞鐓￠弫鏉垮礋閻欘剛鏆€閸︺劎鐡戝蹇庣鏉堝箍鈧?}'::jsonb,
 ARRAY['娑撯偓閸忓啩绔村▎鈩冩煙缁?,'缁涘绱￠幀褑宸?], 'SEED', 'ACTIVE'),
(2, 6, 'math', 'grade7', 'CALCULATION', 2,
 '{"stem":"鐟欙絾鏌熺粙瀣剁窗2x + 5 = 13"}'::jsonb,
 '{"value":"x = 4","steps":["缁涘绱℃稉銈堢珶閸氬本妞傞崙蹇撳箵 5閿?x = 8","缁涘绱℃稉銈堢珶閸氬本妞傞梽銈勪簰 2閿涙 = 4"],"explanation":"閸忓牏些鐢憡鏆熸い鐧哥礉閸愬秵濡搁張顏嗙叀閺佹壆閮撮弫鏉垮娑?1閵?}'::jsonb,
 ARRAY['娑撯偓閸忓啩绔村▎鈩冩煙缁?,'缁夊銆?], 'SEED', 'ACTIVE'),
(3, 6, 'math', 'grade7', 'CALCULATION', 3,
 '{"stem":"鐟欙絾鏌熺粙瀣剁窗3(x - 2) = 2x + 5"}'::jsonb,
 '{"value":"x = 11","steps":["鐏炴洖绱戝锕佺珶閿?x - 6 = 2x + 5","缁涘绱℃稉銈堢珶閸氬本妞傞崙蹇撳箵 2x閿涙 - 6 = 5","缁涘绱℃稉銈堢珶閸氬本妞傞崝?6閿涙 = 11"],"explanation":"閸氼偅瀚崣椋庢畱娑撯偓閸忓啩绔村▎鈩冩煙缁嬪鈧艾鐖堕崗鍫濆箵閹奉剙褰块敍灞藉晙缁夊銆嶉崥鍫濊嫙閵?}'::jsonb,
 ARRAY['娑撯偓閸忓啩绔村▎鈩冩煙缁?,'閸樼粯瀚崣?,'缁夊銆?], 'SEED', 'ACTIVE'),
(4, 6, 'math', 'grade7', 'CALCULATION', 4,
 '{"stem":"鐟欙絾鏌熺粙瀣剁窗(x + 1)/3 + (x - 2)/2 = 4"}'::jsonb,
 '{"value":"x = 28/5","steps":["缁涘绱℃稉銈堢珶閸氬本妞傛稊妯逛簰 6閿?(x + 1) + 3(x - 2) = 24","鐏炴洖绱戦敍?x + 2 + 3x - 6 = 24","閸氬牆鑻熼崥宀€琚い鐧哥窗5x - 4 = 24","鐟欙絽绶遍敍?x = 28閿涘瘓 = 28/5"],"explanation":"鏉╂瑩顣介惃鍕櫢閻愯妲搁崗鍫濆箵閸掑棙鐦濋敍灞藉晙閸樼粯瀚崣鏋偓浣告値楠炶泛鎮撶猾濠氥€嶉妴?}'::jsonb,
 ARRAY['娑撯偓閸忓啩绔村▎鈩冩煙缁?,'閸樿鍨庡В?], 'SEED', 'ACTIVE'),
(5, 7, 'math', 'grade7', 'CALCULATION', 1,
 '{"stem":"鐟欙絼绗夌粵澶婄础閿涙 + 3 > 8"}'::jsonb,
 '{"value":"x > 5","steps":["娑撳秶鐡戝蹇庤⒈鏉堢懓鎮撻弮璺哄櫤閸?3閿涙 > 5"],"explanation":"娑撳秶鐡戝蹇庤⒈鏉堢懓鎮撻弮璺哄閸戝繐鎮撴稉鈧稉顏呮殶閿涘奔绗夌粵澶婂娇閺傜懓鎮滄稉宥呭綁閵?}'::jsonb,
 ARRAY['娑撯偓閸忓啩绔村▎鈥茬瑝缁涘绱?], 'SEED', 'ACTIVE'),
(6, 7, 'math', 'grade7', 'CHOICE', 2,
 '{"stem":"娑撳秶鐡戝?2x - 3 > 5 閻ㄥ嫯袙闂嗗棙妲搁敍?,"options":["x > 4","x < 4","x > 1","x < 1"]}'::jsonb,
 '{"value":"x > 4","steps":["娑撱倛绔熼崥灞炬閸?3閿?x > 8","娑撱倛绔熼崥灞炬闂勩倓浜?2閿涙 > 4"],"explanation":"濮濓絾鏆熼梽銈嗙《娑撳秳绱伴弨鐟板綁娑撳秶鐡戦崣閿嬫煙閸氭垯鈧?}'::jsonb,
 ARRAY['娑撯偓閸忓啩绔村▎鈥茬瑝缁涘绱?,'闁瀚ㄦ０?], 'SEED', 'ACTIVE'),
(7, 7, 'math', 'grade7', 'CALCULATION', 3,
 '{"stem":"鐟欙絼绗夌粵澶婄础閿?3x + 6 <= 12"}'::jsonb,
 '{"value":"x >= -2","steps":["娑撱倛绔熼崥灞炬閸戝繐骞?6閿?3x <= 6","娑撱倛绔熼崥灞炬闂勩倓浜?-3閿涘奔绗夌粵澶婂娇閺傜懓鎮滈弨鐟板綁閿涙 >= -2"],"explanation":"娑撳秶鐡戝蹇庤⒈鏉堢懓鎮撻弮鏈电闂勩倛绀嬮弫鐗堟閿涘奔绗夌粵澶婂娇閺傜懓鎮滅憰浣规暭閸欐ǜ鈧?}'::jsonb,
 ARRAY['娑撯偓閸忓啩绔村▎鈥茬瑝缁涘绱?,'鐠愮喐鏆?], 'SEED', 'ACTIVE'),
(8, 3, 'math', 'grade7', 'CALCULATION', 1,
 '{"stem":"閸栨牜鐣濋敍?a + 2a - 5a"}'::jsonb,
 '{"value":"0","steps":["閸氬牆鑻熼崥宀€琚い鐧哥窗(3 + 2 - 5)a","鐠侊紕鐣荤化缁樻殶閿?a = 0"],"explanation":"閸氬瞼琚い鐟版値楠炶埖妞傞敍灞藉涧閸氬牆鑻熺化缁樻殶閿涘苯鐡уВ宥夊劥閸掑棔绗夐崣妯糕偓?}'::jsonb,
 ARRAY['閺佹潙绱￠惃鍕閸?,'閸氬牆鑻熼崥宀€琚い?], 'SEED', 'ACTIVE'),
(9, 3, 'math', 'grade7', 'CALCULATION', 2,
 '{"stem":"閸栨牜鐣濋敍?(3x - 4) - (x + 5)"}'::jsonb,
 '{"value":"5x - 13","steps":["閸樼粯瀚崣鍑ょ窗6x - 8 - x - 5","閸氬牆鑻熼崥宀€琚い鐧哥窗5x - 13"],"explanation":"閹奉剙褰块崜宥嗘Ц鐠愮喎褰块弮璁圭礉閸樼粯瀚崣宄版倵閹奉剙褰块崘鍛倗妞ゅ綊鍏樼憰浣稿綁閸欐灚鈧?}'::jsonb,
 ARRAY['閺佹潙绱￠惃鍕閸?,'閸樼粯瀚崣?], 'SEED', 'ACTIVE'),
(10, 4, 'math', 'grade8', 'CALCULATION', 2,
 '{"stem":"鐠侊紕鐣婚敍?2x)^2 璺?3x"}'::jsonb,
 '{"value":"12x^3","steps":["閸忓牏鐣绘稊妯绘煙閿?2x)^2 = 4x^2","閸愬秳绠?3x閿?x^2 璺?3x = 12x^3"],"explanation":"閸氬苯绨抽弫鏉跨畵閻╅晲绠婚敍灞惧瘹閺佹壆娴夐崝鐘偓?}'::jsonb,
 ARRAY['閺佹潙绱￠惃鍕闂?,'楠炲倽绻嶇粻?], 'SEED', 'ACTIVE'),
(11, 4, 'math', 'grade8', 'CALCULATION', 3,
 '{"stem":"閸ョ姴绱￠崚鍡毿掗敍姝篰2 - 9"}'::jsonb,
 '{"value":"(x + 3)(x - 3)","steps":["鐠囧棗鍩嗛獮铏煙瀹割噯绱皒^2 - 9 = x^2 - 3^2","婵傛鏁ら崗顒€绱?a^2 - b^2 = (a + b)(a - b)","瀵版鍩?(x + 3)(x - 3)"],"explanation":"楠炶櫕鏌熷顔煎彆瀵繑妲搁崚婵呰厬閸ョ姴绱￠崚鍡毿掗惃鍕埗閻劌浼愰崗鏋偓?}'::jsonb,
 ARRAY['閸ョ姴绱￠崚鍡毿?,'楠炶櫕鏌熷?], 'SEED', 'ACTIVE'),
(12, 8, 'math', 'grade8', 'CALCULATION', 2,
 '{"stem":"鐟欙絾鏌熺粙瀣矋閿涙 + y = 7閿涘瘓 - y = 1"}'::jsonb,
 '{"value":"x = 4, y = 3","steps":["娑撱倕绱￠惄绋垮閿?x = 8","鐟欙絽绶?x = 4","娴狅絽鍙?x + y = 7閿涘苯绶?y = 3"],"explanation":"閸旂姴鍣哄☉鍫濆帗闁倸鎮庣化缁樻殶娴滄帊璐熼惄绋垮冀閺佺増鍨ㄩ惄绋挎倱閻ㄥ嫭鏌熺粙瀣矋閵?}'::jsonb,
 ARRAY['娴滃苯鍘撴稉鈧▎鈩冩煙缁嬪绮?,'閸旂姴鍣哄☉鍫濆帗'], 'SEED', 'ACTIVE'),
(13, 8, 'math', 'grade8', 'CALCULATION', 3,
 '{"stem":"鐟欙絾鏌熺粙瀣矋閿?x + y = 9閿涘瘓 + 2y = 8"}'::jsonb,
 '{"value":"x = 10/3, y = 7/3","steps":["閻?2x + y = 9 瀵?y = 9 - 2x","娴狅絽鍙?x + 2y = 8閿涙 + 2(9 - 2x) = 8","鐟欙絽绶?-3x = -10閿涘本澧嶆禒?x = 10/3","娴狅絽娲栧?y = 7/3"],"explanation":"娴狅絽鍙嗗☉鍫濆帗閻ㄥ嫭鐗宠箛鍐╂Ц閻劋绔存稉顏呮弓閻儲鏆熺悰銊с仛閸欙缚绔存稉顏呮弓閻儲鏆熼妴?}'::jsonb,
 ARRAY['娴滃苯鍘撴稉鈧▎鈩冩煙缁嬪绮?,'娴狅絽鍙嗗☉鍫濆帗'], 'SEED', 'ACTIVE'),
(14, 10, 'math', 'grade7', 'CALCULATION', 1,
 '{"stem":"瀹歌尙鐓?閳嚄 = 35鎺抽敍灞稿灩B 娑?閳嚄 娴滄帊缍戦敍灞剧湴 閳嚇閵?}'::jsonb,
 '{"value":"55鎺?,"steps":["娴滄帊缍戦惃鍕⒈娑擃亣顫楅崪灞艰礋 90鎺?,"閳嚇 = 90鎺?- 35鎺?= 55鎺?],"explanation":"娴滄帊缍戦惇?90鎺抽敍灞肩鞍鐞涖儳婀?180鎺抽妴?}'::jsonb,
 ARRAY['缁炬寧顔屾稉搴ゎ潡','娴滄帊缍?], 'SEED', 'ACTIVE'),
(15, 10, 'math', 'grade7', 'CALCULATION', 2,
 '{"stem":"瀹歌尙鐓?閳嚄 = 120鎺抽敍灞稿灩B 娑?閳嚄 娴滄帟藟閿涘本鐪?閳嚇閵?}'::jsonb,
 '{"value":"60鎺?,"steps":["娴滄帟藟閻ㄥ嫪琚辨稉顏囶潡閸滃奔璐?180鎺?,"閳嚇 = 180鎺?- 120鎺?= 60鎺?],"explanation":"閸掋倖鏌囨禍鎺曀夐弮鑸靛娴ｅ繑鈧鎷?180鎺抽妴?}'::jsonb,
 ARRAY['缁炬寧顔屾稉搴ゎ潡','娴滄帟藟'], 'SEED', 'ACTIVE'),
(16, 11, 'math', 'grade8', 'CHOICE', 2,
 '{"stem":"娑撳顫楄ぐ顫⒈鏉堝綊鏆遍崚鍡楀焼娑?3 閸?5閿涘瞼顑囨稉澶庣珶閸欘垵鍏橀弰顖ょ吹","options":["1","2","7","9"]}'::jsonb,
 '{"value":"7","steps":["娑撳顫楄ぐ銏㈩儑娑撳绔?c 濠娐ゅ喕 |5 - 3| < c < 5 + 3","閸?2 < c < 8","闁銆嶆稉顓炲涧閺?7 濠娐ゅ喕"],"explanation":"娑撳顫楄ぐ顫崲閹板繋琚辨潏閫涚閸滃苯銇囨禍搴ｎ儑娑撳绔熼敍灞兼崲閹板繋琚辨潏閫涚瀹割喖鐨禍搴ｎ儑娑撳绔熼妴?}'::jsonb,
 ARRAY['娑撳顫楄ぐ?,'娑撳绔熼崗宕囬兇'], 'SEED', 'ACTIVE'),
(17, 11, 'math', 'grade8', 'CALCULATION', 3,
 '{"stem":"娑撯偓娑擃亙绗佺憴鎺戣埌娑撳閲滈崘鍛邦潡娑斿鐦稉?2:3:4閿涘本鐪伴張鈧径褑顫楅妴?}'::jsonb,
 '{"value":"80鎺?,"steps":["娑撳顫楄ぐ銏犲敶鐟欐帒鎷版稉?180鎺?,"閹鍞ら弫?2 + 3 + 4 = 9","濮ｅ繋鍞ゆ稉?180鎺?/ 9 = 20鎺?,"閺堚偓婢堆嗩潡娑?4 娴犳枻绱?0鎺?],"explanation":"濮ｆ柧绶ラ梻顕€顣介崗鍫熺湴閹鍞ら弫甯礉閸愬秵鐪板В蹇庡敜婢堆冪毈閵?}'::jsonb,
 ARRAY['娑撳顫楄ぐ?,'閸愬懓顫楅崪?], 'SEED', 'ACTIVE'),
(18, 14, 'math', 'grade8', 'CALCULATION', 1,
 '{"stem":"閻?A(3, -2) 閸︺劎顑囬崙鐘鸿杽闂勬劧绱?}'::jsonb,
 '{"value":"缁楊剙娲撶挒锟犳","steps":["濡亜娼楅弽?x = 3 > 0","缁鹃潧娼楅弽?y = -2 < 0","濡亝顒滅痪浣冪閻ㄥ嫮鍋ｉ崷銊ь儑閸ユ稖钖勯梽?],"explanation":"鐠烇繝妾洪崚銈嗘焽閻铆缁鹃潧娼楅弽鍥╂畱濮濓綀绀嬮妴?}'::jsonb,
 ARRAY['楠炴娊娼伴惄纾嬵潡閸ф劖鐖ｇ化?,'鐠烇繝妾?], 'SEED', 'ACTIVE'),
(19, 15, 'math', 'grade8', 'CALCULATION', 2,
 '{"stem":"娑撯偓濞嗏€冲毐閺?y = 2x + 1閿涘苯缍?x = 3 閺冭绱漼 閻ㄥ嫬鈧吋妲告径姘毌閿?}'::jsonb,
 '{"value":"7","steps":["閹?x = 3 娴狅絽鍙?y = 2x + 1","y = 2 鑴?3 + 1 = 7"],"explanation":"濮瑰倸鍤遍弫鏉库偓鐓庢皑閺勵垱濡搁懛顏勫綁闁插繋鍞崗銉ㄐ掗弸鎰础閵?}'::jsonb,
 ARRAY['娑撯偓濞嗏€冲毐閺?,'閸戣姤鏆熼崐?], 'SEED', 'ACTIVE'),
(20, 15, 'math', 'grade8', 'CALCULATION', 3,
 '{"stem":"瀹歌尙鐓℃稉鈧▎鈥冲毐閺?y = kx + 2 缂佸繗绻冮悙?(3, 8)閿涘本鐪?k閵?}'::jsonb,
 '{"value":"k = 2","steps":["閹跺﹦鍋?(3, 8) 娴狅絽鍙?y = kx + 2","瀵版鍩?8 = 3k + 2","鐟欙絽绶?3k = 6閿涘本澧嶆禒?k = 2"],"explanation":"閸戣姤鏆熼崶鎯у剼缂佸繗绻冮弻鎰仯閿涘矁顕╅弰搴ゎ嚉閻愮懓娼楅弽鍥ㄥ姬鐡掑啿鍤遍弫鎷屝掗弸鎰础閵?}'::jsonb,
 ARRAY['娑撯偓濞嗏€冲毐閺?,'瀵板懎鐣剧化缁樻殶濞?], 'SEED', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

-- 闁插秶鐤嗘０妯虹氨鎼村繐鍨?
SELECT setval('questions_id_seq', (SELECT MAX(id) FROM questions));

