-- ============================================
-- FamilyAgent 数据库初始化脚本
-- 在 PostgreSQL 容器首次启动时自动执行
-- ============================================

-- 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================
-- 1. 用户表
-- ============================================
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

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_status ON users(status);

-- ============================================
-- 2. 家族表
-- ============================================
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

CREATE INDEX idx_families_invite_code ON families(invite_code);

-- ============================================
-- 3. 家族成员表
-- ============================================
CREATE TABLE IF NOT EXISTS family_members (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    permissions JSONB DEFAULT '{}',
    joined_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(family_id, user_id)
);

CREATE INDEX idx_family_members_family ON family_members(family_id);
CREATE INDEX idx_family_members_user ON family_members(user_id);

-- ============================================
-- 4. 知识点树
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

CREATE INDEX idx_kp_subject ON knowledge_points(subject);
CREATE INDEX idx_kp_parent ON knowledge_points(parent_id);
CREATE INDEX idx_kp_level ON knowledge_points(level);

-- ============================================
-- 5. 题库表
-- ============================================
CREATE TABLE IF NOT EXISTS questions (
    id BIGSERIAL PRIMARY KEY,
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
    created_by BIGINT REFERENCES users(id),
    reviewed_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_questions_kp ON questions(kp_id);
CREATE INDEX idx_questions_subject ON questions(subject);
CREATE INDEX idx_questions_difficulty ON questions(difficulty);
CREATE INDEX idx_questions_status ON questions(status);
CREATE INDEX idx_questions_type ON questions(type);

-- ============================================
-- 6. 测试记录表
-- ============================================
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
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_test_records_user ON test_records(user_id);
CREATE INDEX idx_test_records_family ON test_records(family_id);
CREATE INDEX idx_test_records_created ON test_records(created_at DESC);

-- ============================================
-- 7. 学力档案表
-- ============================================
CREATE TABLE IF NOT EXISTS ability_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    kp_id BIGINT NOT NULL REFERENCES knowledge_points(id),
    mastery_probability DECIMAL(5,4) NOT NULL DEFAULT 0.5,
    total_attempts INTEGER DEFAULT 0,
    correct_attempts INTEGER DEFAULT 0,
    consecutive_correct INTEGER DEFAULT 0,
    last_attempt_at TIMESTAMP,
    last_correct_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, kp_id)
);

CREATE INDEX idx_ability_user ON ability_profiles(user_id);
CREATE INDEX idx_ability_mastery ON ability_profiles(mastery_probability);

-- ============================================
-- 8. 家教会话表
-- ============================================
CREATE TABLE IF NOT EXISTS chat_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    question_id BIGINT REFERENCES questions(id),
    subject VARCHAR(50),
    knowledge_point_id BIGINT REFERENCES knowledge_points(id),
    messages JSONB DEFAULT '[]',
    summary TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    started_at TIMESTAMP NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMP
);

CREATE INDEX idx_chat_sessions_user ON chat_sessions(user_id);
CREATE INDEX idx_chat_sessions_status ON chat_sessions(status);
CREATE INDEX idx_chat_sessions_started ON chat_sessions(started_at DESC);

-- ============================================
-- 9. 日记表（Phase 2 预留）
-- ============================================
CREATE TABLE IF NOT EXISTS diary_entries (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    raw_text TEXT,
    structured JSONB,
    mood VARCHAR(20),
    tags TEXT[],
    privacy_level VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    voice_url VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_diary_user ON diary_entries(user_id);
CREATE INDEX idx_diary_privacy ON diary_entries(privacy_level);
CREATE INDEX idx_diary_created ON diary_entries(created_at DESC);

-- ============================================
-- 10. 家族知识库表（Phase 2 预留）
-- ============================================
CREATE TABLE IF NOT EXISTS family_knowledge (
    id BIGSERIAL PRIMARY KEY,
    family_id BIGINT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    title VARCHAR(300) NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(50) NOT NULL DEFAULT 'WISDOM',
    tags TEXT[],
    author_id BIGINT REFERENCES users(id),
    embedding vector(1536),
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fk_family ON family_knowledge(family_id);
CREATE INDEX idx_fk_type ON family_knowledge(type);

-- ============================================
-- 11. 镜像Agent数据表（Phase 3 预留）
-- ============================================
CREATE TABLE IF NOT EXISTS mirror_agent_data (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    personality_vector vector(1536),
    memory_embeddings vector(1536)[],
    traits JSONB,
    interaction_count INTEGER DEFAULT 0,
    last_updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(user_id)
);

-- ============================================
-- 插入默认数据：初中数学知识点示例
-- ============================================
INSERT INTO knowledge_points (id, parent_id, subject, grade, name, description, level, sort_order) VALUES
(1, NULL, 'math', 'grade7', '数与式', '初中数学基础：数与代数式', 1, 1),
(2, 1, 'math', 'grade7', '有理数', '正负数、数轴、绝对值', 2, 1),
(3, 1, 'math', 'grade7', '整式的加减', '代数式的加减运算', 2, 2),
(4, 1, 'math', 'grade8', '整式的乘除', '代数式的乘除与因式分解', 2, 3),
(5, NULL, 'math', 'grade7', '方程与不等式', '一元一次方程与不等式', 1, 2),
(6, 5, 'math', 'grade7', '一元一次方程', '含有一个未知数的一次方程', 2, 1),
(7, 5, 'math', 'grade7', '一元一次不等式', '含有一个未知数的一次不等式', 2, 2),
(8, 5, 'math', 'grade8', '二元一次方程组', '两个未知数的方程组求解', 2, 3),
(9, NULL, 'math', 'grade8', '几何初步', '平面几何基础概念', 1, 3),
(10, 9, 'math', 'grade7', '线段与角', '线段、角的度量与关系', 2, 1),
(11, 9, 'math', 'grade8', '三角形', '三角形的性质、全等与相似', 2, 2),
(12, 9, 'math', 'grade8', '四边形', '平行四边形、矩形、菱形', 2, 3),
(13, NULL, 'math', 'grade8', '函数基础', '函数概念与一次函数', 1, 4),
(14, 13, 'math', 'grade8', '平面直角坐标系', '坐标系的概念与应用', 2, 1),
(15, 13, 'math', 'grade8', '一次函数', 'y=kx+b的图像与性质', 2, 2);

-- 重置序列
SELECT setval('knowledge_points_id_seq', (SELECT MAX(id) FROM knowledge_points));
