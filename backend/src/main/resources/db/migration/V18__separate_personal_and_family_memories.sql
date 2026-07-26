ALTER TABLE memory_entries
    ADD COLUMN library_kind VARCHAR(20);

UPDATE memory_entries
SET library_kind = CASE WHEN family_id IS NULL THEN 'PERSONAL' ELSE 'FAMILY' END
WHERE library_kind IS NULL;

UPDATE memory_entries
SET scope = 'PRIVATE'
WHERE library_kind = 'PERSONAL'
  AND scope NOT IN ('PRIVATE', 'ALL_FAMILIES_VISIBLE', 'SELECTED_FAMILIES_VISIBLE', 'CARE_VISIBLE');

ALTER TABLE memory_entries
    ALTER COLUMN library_kind SET DEFAULT 'FAMILY',
    ALTER COLUMN library_kind SET NOT NULL;

ALTER TABLE memory_entries
    ADD CONSTRAINT chk_memory_entries_library_kind
        CHECK (library_kind IN ('PERSONAL', 'FAMILY'));

CREATE INDEX idx_memory_entries_library_owner
    ON memory_entries(library_kind, user_id, status, updated_at DESC);

CREATE TABLE personal_memory_family_grants (
    id BIGSERIAL PRIMARY KEY,
    memory_id BIGINT NOT NULL REFERENCES memory_entries(id) ON DELETE CASCADE,
    family_id BIGINT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    granted_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (memory_id, family_id)
);

CREATE INDEX idx_personal_memory_grants_family
    ON personal_memory_family_grants(family_id, memory_id);

ALTER TABLE memory_embeddings
    ALTER COLUMN family_id DROP NOT NULL;
