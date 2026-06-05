-- ============================================
-- FamilyAgent Beta 邀请码迁移脚本
-- 可重复执行
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
