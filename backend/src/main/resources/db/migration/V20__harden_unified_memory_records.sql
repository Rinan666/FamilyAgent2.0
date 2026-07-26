UPDATE memory_entries
SET scope = CASE
        WHEN library_kind = 'PERSONAL' THEN 'PRIVATE'
        WHEN scope = 'LEGACY_VISIBLE' THEN 'FAMILY_VISIBLE'
        ELSE 'PRIVATE'
    END
WHERE (library_kind = 'PERSONAL'
        AND scope NOT IN ('PRIVATE', 'ALL_FAMILIES_VISIBLE', 'SELECTED_FAMILIES_VISIBLE', 'CARE_VISIBLE'))
   OR (library_kind = 'FAMILY'
        AND scope NOT IN ('PRIVATE', 'FAMILY_VISIBLE', 'CARE_VISIBLE'));

UPDATE memory_entries
SET tags = ARRAY[]::TEXT[]
WHERE tags IS NULL;

UPDATE memory_entries
SET tags = tags[1:10]
WHERE cardinality(tags) > 10;

UPDATE memory_entries
SET metadata = '{}'::jsonb
WHERE metadata IS NULL;

UPDATE memory_entries
SET occurred_at = COALESCE(created_at, updated_at, NOW())
WHERE occurred_at IS NULL;

UPDATE memory_entries
SET confidence = GREATEST(0, LEAST(1, confidence))
WHERE confidence < 0 OR confidence > 1;

UPDATE memory_entries
SET origin_type = NULL,
    origin_id = NULL
WHERE (origin_type IS NULL) <> (origin_id IS NULL)
   OR origin_id <= 0
   OR (origin_type IS NOT NULL AND library_kind <> 'FAMILY');

UPDATE memory_entries me
SET related_user_id = NULL
WHERE me.library_kind = 'FAMILY'
  AND me.related_user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM family_members fm
      WHERE fm.family_id = me.family_id
        AND fm.user_id = me.related_user_id
  );

ALTER TABLE memory_entries
    ALTER COLUMN tags SET DEFAULT ARRAY[]::TEXT[],
    ALTER COLUMN tags SET NOT NULL,
    ALTER COLUMN metadata SET DEFAULT '{}'::jsonb,
    ALTER COLUMN metadata SET NOT NULL,
    ALTER COLUMN occurred_at SET DEFAULT NOW(),
    ALTER COLUMN occurred_at SET NOT NULL;

ALTER TABLE memory_entries
    ADD CONSTRAINT chk_memory_entries_origin_pair
        CHECK (
            (origin_type IS NULL AND origin_id IS NULL)
            OR (origin_type IS NOT NULL AND origin_id IS NOT NULL AND origin_id > 0)
        ),
    ADD CONSTRAINT chk_memory_entries_origin_library
        CHECK (origin_type IS NULL OR library_kind = 'FAMILY'),
    ADD CONSTRAINT chk_memory_entries_scope_by_library
        CHECK (
            (library_kind = 'PERSONAL'
                AND scope IN ('PRIVATE', 'ALL_FAMILIES_VISIBLE', 'SELECTED_FAMILIES_VISIBLE', 'CARE_VISIBLE'))
            OR (library_kind = 'FAMILY'
                AND scope IN ('PRIVATE', 'FAMILY_VISIBLE', 'CARE_VISIBLE'))
        ),
    ADD CONSTRAINT chk_memory_entries_confidence
        CHECK (confidence BETWEEN 0 AND 1),
    ADD CONSTRAINT chk_memory_entries_tag_count
        CHECK (cardinality(tags) <= 10);

ALTER TABLE memory_entries
    ADD CONSTRAINT uk_memory_entries_id_library_kind
        UNIQUE (id, library_kind);

DELETE FROM personal_memory_family_grants pmfg
WHERE NOT EXISTS (
    SELECT 1
    FROM memory_entries me
    WHERE me.id = pmfg.memory_id
      AND me.library_kind = 'PERSONAL'
);

ALTER TABLE personal_memory_family_grants
    ADD COLUMN memory_library_kind VARCHAR(20) NOT NULL DEFAULT 'PERSONAL',
    ADD CONSTRAINT chk_personal_memory_grant_library
        CHECK (memory_library_kind = 'PERSONAL');

ALTER TABLE personal_memory_family_grants
    DROP CONSTRAINT personal_memory_family_grants_memory_id_fkey,
    ADD CONSTRAINT fk_personal_memory_grant_memory
        FOREIGN KEY (memory_id, memory_library_kind)
        REFERENCES memory_entries(id, library_kind)
        ON DELETE CASCADE;

DROP INDEX IF EXISTS idx_memory_entries_family_scope_status_updated;
DROP INDEX IF EXISTS idx_memory_entries_related_user;

CREATE INDEX idx_memory_entries_family_status_time
    ON memory_entries(family_id, status, occurred_at DESC, updated_at DESC)
    WHERE library_kind = 'FAMILY';

CREATE INDEX idx_memory_entries_family_subject_status_time
    ON memory_entries(family_id, (COALESCE(related_user_id, user_id)), status, occurred_at DESC)
    WHERE library_kind = 'FAMILY';
