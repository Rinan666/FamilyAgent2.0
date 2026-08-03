UPDATE invite_codes
SET status = 'ARCHIVED',
    updated_at = NOW()
WHERE code IN (
    'FAMILY001',
    'FAMILY002',
    'FAMILY003',
    'FAMILY004',
    'FAMILY005',
    'FAMILY006',
    'FAMILY007',
    'FAMILY008',
    'FAMILY009',
    'FAMILY010'
);
