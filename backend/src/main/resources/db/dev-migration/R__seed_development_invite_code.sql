INSERT INTO invite_codes (
    code,
    source,
    description,
    max_uses,
    used_count,
    status,
    expires_at
)
VALUES (
    'DEV-FAMILY-LOCAL',
    'development-profile',
    'Local development invite code',
    1000,
    0,
    'ACTIVE',
    NOW() + INTERVAL '1 year'
)
ON CONFLICT (code) DO UPDATE
SET source = EXCLUDED.source,
    description = EXCLUDED.description,
    max_uses = EXCLUDED.max_uses,
    status = EXCLUDED.status,
    expires_at = EXCLUDED.expires_at,
    updated_at = NOW();
