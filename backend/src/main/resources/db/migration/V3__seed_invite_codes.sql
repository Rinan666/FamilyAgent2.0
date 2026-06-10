INSERT INTO invite_codes (code, source, description, max_uses)
VALUES
    ('FAMILY001', 'seed-family-001', 'First beta family 001', 5),
    ('FAMILY002', 'seed-family-002', 'First beta family 002', 5),
    ('FAMILY003', 'seed-family-003', 'First beta family 003', 5),
    ('FAMILY004', 'seed-family-004', 'First beta family 004', 5),
    ('FAMILY005', 'seed-family-005', 'First beta family 005', 5),
    ('FAMILY006', 'seed-family-006', 'First beta family 006', 5),
    ('FAMILY007', 'seed-family-007', 'First beta family 007', 5),
    ('FAMILY008', 'seed-family-008', 'First beta family 008', 5),
    ('FAMILY009', 'seed-family-009', 'First beta family 009', 5),
    ('FAMILY010', 'seed-family-010', 'First beta family 010', 5)
ON CONFLICT (code) DO NOTHING;
