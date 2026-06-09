-- Repair local development data after user.metadata was overwritten with a malformed JSON string.
-- Strategy:
-- 1. Restore entries whose original metadata was confirmed during debugging.
-- 2. Clear unsafe fabricated values for the remaining affected users instead of keeping wrong birth dates/invite codes.

BEGIN;

UPDATE users
SET metadata = CASE username
    WHEN 'family003_grandpa' THEN jsonb_build_object(
        'seed', 'FAMILY003',
        'relation', '爷爷',
        'inviteCode', 'FAMILY003',
        'profileType', 'ELDER',
        'inviteSource', 'seed-family-003'
    )
    WHEN 'family003_mother' THEN jsonb_build_object(
        'seed', 'FAMILY003',
        'relation', '母亲',
        'inviteCode', 'FAMILY003',
        'profileType', 'PARENT',
        'inviteSource', 'seed-family-003'
    )
    WHEN 'family003_father' THEN jsonb_build_object(
        'seed', 'FAMILY003',
        'relation', '父亲',
        'inviteCode', 'FAMILY003',
        'profileType', 'PARENT',
        'inviteSource', 'seed-family-003'
    )
    WHEN 'family003_aunt' THEN jsonb_build_object(
        'seed', 'FAMILY003',
        'relation', '姑姑',
        'inviteCode', 'FAMILY003',
        'profileType', 'ADULT_MEMBER',
        'inviteSource', 'seed-family-003'
    )
    WHEN 'family003_member' THEN jsonb_build_object(
        'seed', 'FAMILY003',
        'grade', '七年级',
        'relation', '孩子',
        'inviteCode', 'FAMILY003',
        'profileType', 'MEMBER',
        'inviteSource', 'seed-family-003'
    )
    WHEN '郭008' THEN jsonb_build_object(
        'birthDate', '2005-09-22',
        'birthYear', '2005'
    )
    WHEN '周001' THEN jsonb_build_object(
        'inviteCode', 'FAMILY004',
        'inviteSource', 'seed-family-004'
    )
    WHEN '江068' THEN jsonb_build_object(
        'inviteCode', 'FAMILY004',
        'inviteSource', 'seed-family-004'
    )
    WHEN 'codex_manual_20260609134818' THEN jsonb_build_object(
        'inviteCode', 'FAMILY005',
        'inviteSource', 'seed-family-005'
    )
    WHEN 'smoke_1780998521892' THEN jsonb_build_object(
        'inviteCode', 'FAMILY002',
        'inviteSource', 'seed-family-002'
    )
    WHEN 'smoke_1780998655408' THEN jsonb_build_object(
        'inviteCode', 'FAMILY002',
        'inviteSource', 'seed-family-002'
    )
    WHEN 'smoke_1780998725419' THEN jsonb_build_object(
        'inviteCode', 'FAMILY002',
        'inviteSource', 'seed-family-002'
    )
    ELSE '{}'::jsonb
END,
updated_at = NOW()
WHERE username IN (
    'demo',
    'testuser',
    'e2e_test',
    'test',
    'flowtest_1780542548',
    'debug_231368658',
    'beta53612',
    'root',
    'family001',
    'tongxue',
    'abc',
    'root008',
    't002_1',
    't002_2',
    'family003_grandpa',
    'family003_mother',
    'family003_father',
    'family003_aunt',
    'family003_member',
    '郭008',
    '周001',
    '韦525',
    '周123',
    '江068',
    'codex_manual_20260609134818',
    'smoke_1780998521892',
    'smoke_1780998655408',
    'smoke_1780998725419'
);

COMMIT;
