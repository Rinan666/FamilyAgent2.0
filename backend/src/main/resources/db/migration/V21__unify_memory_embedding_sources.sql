INSERT INTO memory_embeddings (
    family_id,
    user_id,
    source_type,
    source_id,
    content_hash,
    embedding_model,
    embedding,
    status,
    metadata,
    created_at,
    updated_at
)
SELECT
    me.family_id,
    me.user_id,
    'MEMORY',
    me.id,
    legacy.content_hash,
    legacy.embedding_model,
    legacy.embedding,
    legacy.status,
    legacy.metadata,
    legacy.created_at,
    legacy.updated_at
FROM memory_embeddings legacy
JOIN memory_entries me
  ON (legacy.source_type = 'DIARY'
      AND me.origin_type = 'DIARY'
      AND me.origin_id = legacy.source_id)
  OR (legacy.source_type = 'GROWTH_OBSERVATION'
      AND me.origin_type = 'GROWTH'
      AND me.origin_id = legacy.source_id)
ON CONFLICT (source_type, source_id, content_hash)
DO UPDATE SET
    family_id = EXCLUDED.family_id,
    user_id = EXCLUDED.user_id,
    embedding_model = EXCLUDED.embedding_model,
    embedding = EXCLUDED.embedding,
    status = EXCLUDED.status,
    metadata = EXCLUDED.metadata,
    updated_at = GREATEST(memory_embeddings.updated_at, EXCLUDED.updated_at);

DELETE FROM memory_embeddings
WHERE source_type IN ('DIARY', 'GROWTH_OBSERVATION');

ALTER TABLE memory_embeddings
    DROP CONSTRAINT chk_memory_embeddings_source_type,
    ADD CONSTRAINT chk_memory_embeddings_source_type
        CHECK (source_type = 'MEMORY');
