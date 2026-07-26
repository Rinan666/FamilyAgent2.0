CREATE SEQUENCE unified_diary_record_id_seq;
CREATE SEQUENCE unified_growth_record_id_seq;

SELECT setval(
    'unified_diary_record_id_seq',
    GREATEST(
        COALESCE((SELECT MAX(origin_id) FROM memory_entries WHERE origin_type = 'DIARY'), 0),
        COALESCE((SELECT MAX(id) FROM diary_entries), 0)
    ) + 1,
    false
);

SELECT setval(
    'unified_growth_record_id_seq',
    GREATEST(
        COALESCE((SELECT MAX(origin_id) FROM memory_entries WHERE origin_type = 'GROWTH'), 0),
        COALESCE((SELECT MAX(id) FROM growth_guard_records), 0)
    ) + 1,
    false
);

ALTER TABLE growth_guard_staleness_votes
    DROP CONSTRAINT IF EXISTS growth_guard_staleness_votes_record_id_fkey;

UPDATE growth_guard_staleness_votes vote
SET record_id = memory.id
FROM memory_entries memory
WHERE memory.origin_type = 'GROWTH'
  AND memory.origin_id = vote.record_id;

ALTER TABLE growth_guard_staleness_votes
    RENAME COLUMN record_id TO memory_entry_id;

ALTER TABLE growth_guard_staleness_votes
    RENAME CONSTRAINT uk_growth_guard_staleness_record_user
    TO uk_growth_guard_staleness_memory_user;

ALTER INDEX idx_growth_guard_staleness_record
    RENAME TO idx_growth_guard_staleness_memory;

ALTER TABLE growth_guard_staleness_votes
    ADD CONSTRAINT growth_guard_staleness_votes_memory_entry_id_fkey
        FOREIGN KEY (memory_entry_id) REFERENCES memory_entries(id) ON DELETE CASCADE;

ALTER TABLE agent_record_provenance
    DROP CONSTRAINT IF EXISTS ck_agent_record_provenance_single_record,
    DROP CONSTRAINT IF EXISTS ck_agent_record_provenance_type;

UPDATE agent_record_provenance provenance
SET memory_entry_id = memory.id
FROM memory_entries memory
WHERE provenance.memory_entry_id IS NULL
  AND (
    (provenance.diary_entry_id IS NOT NULL
      AND memory.origin_type = 'DIARY'
      AND memory.origin_id = provenance.diary_entry_id)
    OR
    (provenance.growth_guard_record_id IS NOT NULL
      AND memory.origin_type = 'GROWTH'
      AND memory.origin_id = provenance.growth_guard_record_id)
  );

ALTER TABLE agent_record_provenance
    DROP CONSTRAINT IF EXISTS agent_record_provenance_diary_entry_id_fkey,
    DROP CONSTRAINT IF EXISTS agent_record_provenance_growth_guard_record_id_fkey;

DROP INDEX IF EXISTS uk_agent_record_provenance_diary;
DROP INDEX IF EXISTS uk_agent_record_provenance_growth;

ALTER TABLE agent_record_provenance
    DROP COLUMN diary_entry_id,
    DROP COLUMN growth_guard_record_id,
    ALTER COLUMN memory_entry_id SET NOT NULL,
    ADD CONSTRAINT ck_agent_record_provenance_type
        CHECK (record_type IN ('MEMORY_ENTRY', 'DIARY_ENTRY', 'GROWTH_GUARD_RECORD'));

DROP TABLE diary_entries;
DROP TABLE growth_guard_records;
