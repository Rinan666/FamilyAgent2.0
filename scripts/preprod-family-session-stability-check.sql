-- Read-only verification queries for the family/session stability release.
-- Run after scripts/migrate-chat-session-storage.sql in preprod or production.

-- 1) Family lifecycle audit candidates: empty, ownerless, or multi-owner families.
SELECT
    f.id AS family_id,
    f.name AS family_name,
    COUNT(fm.id) AS member_count,
    COUNT(*) FILTER (WHERE UPPER(COALESCE(fm.role, '')) = 'OWNER') AS owner_count
FROM families f
LEFT JOIN family_members fm ON fm.family_id = f.id
GROUP BY f.id, f.name
HAVING COUNT(fm.id) = 0
    OR COUNT(*) FILTER (WHERE UPPER(COALESCE(fm.role, '')) = 'OWNER') = 0
    OR COUNT(*) FILTER (WHERE UPPER(COALESCE(fm.role, '')) = 'OWNER') > 1
ORDER BY f.id ASC;

-- 2) Chat session storage health: compare logical message count to live + archived rows.
SELECT
    s.id AS session_id,
    s.family_id,
    s.message_count,
    s.archived_before_seq,
    s.archive_status,
    COALESCE(live.live_count, 0) AS live_message_rows,
    COALESCE(arch.archived_count, 0) AS archived_message_rows,
    COALESCE(live.live_count, 0) + COALESCE(arch.archived_count, 0) AS total_materialized_rows
FROM chat_sessions s
LEFT JOIN (
    SELECT session_id, COUNT(*) AS live_count
    FROM chat_session_messages
    GROUP BY session_id
) live ON live.session_id = s.id
LEFT JOIN (
    SELECT session_id, SUM(message_count) AS archived_count
    FROM chat_session_archives
    GROUP BY session_id
) arch ON arch.session_id = s.id
WHERE s.message_count > 0
ORDER BY s.last_message_at DESC NULLS LAST, s.id DESC
LIMIT 100;

-- 3) Chat session archive ranges: check for descending ranges and obvious gaps.
SELECT
    session_id,
    id AS archive_id,
    start_seq,
    end_seq,
    message_count,
    created_at
FROM chat_session_archives
ORDER BY session_id ASC, start_seq ASC, id ASC;

-- 4) Admin safety check: list platform admin accounts so delete validation can be tested safely.
SELECT
    id,
    username,
    nickname,
    role,
    status
FROM users
WHERE UPPER(COALESCE(role, '')) = 'ADMIN'
ORDER BY id ASC;
