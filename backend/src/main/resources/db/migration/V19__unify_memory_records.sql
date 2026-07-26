ALTER TABLE memory_entries
    ADD COLUMN title VARCHAR(120),
    ADD COLUMN related_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN occurred_at TIMESTAMP,
    ADD COLUMN tags TEXT[] DEFAULT ARRAY[]::TEXT[],
    ADD COLUMN origin_type VARCHAR(20),
    ADD COLUMN origin_id BIGINT;

UPDATE memory_entries
SET metadata = COALESCE(metadata, '{}'::jsonb)
        || jsonb_build_object('legacyMemoryType', type)
WHERE type NOT IN ('NOTE', 'KNOWLEDGE', 'INSIGHT', 'EXPERIENCE', 'OBSERVATION', 'PREFERENCE', 'PLAN');

UPDATE memory_entries
SET type = CASE type
        WHEN 'LEARNING' THEN 'KNOWLEDGE'
        WHEN 'ELDER_ADVICE' THEN 'KNOWLEDGE'
        WHEN 'MISTAKE' THEN 'INSIGHT'
        WHEN 'VALUE' THEN 'INSIGHT'
        WHEN 'FAMILY_STORY' THEN 'EXPERIENCE'
        WHEN 'GROWTH_RISK' THEN 'OBSERVATION'
        WHEN 'HEALTH_REMINDER' THEN 'PLAN'
        WHEN 'NOTE' THEN 'NOTE'
        WHEN 'KNOWLEDGE' THEN 'KNOWLEDGE'
        WHEN 'INSIGHT' THEN 'INSIGHT'
        WHEN 'EXPERIENCE' THEN 'EXPERIENCE'
        WHEN 'OBSERVATION' THEN 'OBSERVATION'
        WHEN 'PREFERENCE' THEN 'PREFERENCE'
        WHEN 'PLAN' THEN 'PLAN'
        ELSE 'NOTE'
    END,
    title = LEFT(COALESCE(NULLIF(metadata->>'title', ''), NULLIF(summary, ''), content), 120),
    occurred_at = COALESCE(occurred_at, created_at),
    tags = CASE
        WHEN jsonb_typeof(metadata->'tags') = 'array'
            THEN ARRAY(SELECT jsonb_array_elements_text(metadata->'tags'))
        ELSE COALESCE(tags, ARRAY[]::TEXT[])
    END;

INSERT INTO memory_entries (
    user_id,
    family_id,
    library_kind,
    title,
    related_user_id,
    type,
    scope,
    content,
    summary,
    importance,
    confidence,
    status,
    tags,
    occurred_at,
    origin_type,
    origin_id,
    metadata,
    created_at,
    updated_at
)
SELECT
    de.user_id,
    de.family_id,
    'FAMILY',
    LEFT(COALESCE(NULLIF(de.structured->>'title', ''), NULLIF(de.structured->>'summary', ''), de.raw_text), 120),
    (
        SELECT u.id
        FROM users u
        WHERE u.id = CASE
            WHEN COALESCE(de.metadata->>'relatedUserId', '') ~ '^[0-9]+$'
                THEN (de.metadata->>'relatedUserId')::BIGINT
            ELSE NULL
        END
        LIMIT 1
    ),
    CASE COALESCE(de.structured->>'entryType', 'DAILY')
        WHEN 'LESSON' THEN 'KNOWLEDGE'
        WHEN 'EMOTION' THEN 'INSIGHT'
        WHEN 'SELF_REFLECTION' THEN 'INSIGHT'
        ELSE 'NOTE'
    END,
    CASE WHEN de.visibility = 'FAMILY' THEN 'FAMILY_VISIBLE' ELSE de.visibility END,
    de.raw_text,
    COALESCE(NULLIF(de.structured->>'summary', ''), LEFT(de.raw_text, 120)),
    3,
    0.8500,
    CASE WHEN de.metadata->>'status' = 'ARCHIVED' THEN 'ARCHIVED' ELSE 'ACTIVE' END,
    COALESCE(de.tags, ARRAY[]::TEXT[]),
    de.created_at,
    'DIARY',
    de.id,
    COALESCE(de.metadata, '{}'::jsonb) || jsonb_build_object(
        'legacyDiary',
        jsonb_strip_nulls(jsonb_build_object(
            'entryType', de.structured->>'entryType',
            'mood', de.mood,
            'source', de.source,
            'voiceUrl', de.voice_url
        ))
    ),
    de.created_at,
    de.updated_at
FROM diary_entries de;

INSERT INTO memory_entries (
    user_id,
    family_id,
    library_kind,
    title,
    related_user_id,
    type,
    scope,
    content,
    summary,
    importance,
    confidence,
    status,
    tags,
    occurred_at,
    origin_type,
    origin_id,
    metadata,
    created_at,
    updated_at
)
SELECT
    gr.created_by,
    gr.family_id,
    'FAMILY',
    LEFT(COALESCE(NULLIF(gr.metadata->>'title', ''), gr.content), 120),
    gr.target_user_id,
    'OBSERVATION',
    gr.visibility,
    gr.content,
    LEFT(gr.content, 120),
    3,
    0.8500,
    gr.status,
    CASE
        WHEN jsonb_typeof(gr.metadata->'tags') = 'array'
            THEN ARRAY(SELECT jsonb_array_elements_text(gr.metadata->'tags'))
        ELSE ARRAY[]::TEXT[]
    END,
    gr.observed_at::TIMESTAMP,
    'GROWTH',
    gr.id,
    COALESCE(gr.metadata, '{}'::jsonb) || jsonb_build_object(
        'legacyGrowth',
        jsonb_strip_nulls(jsonb_build_object(
            'category', gr.category,
            'severity', gr.severity,
            'followUpAt', gr.follow_up_at
        ))
    ),
    gr.created_at,
    gr.updated_at
FROM growth_guard_records gr;

ALTER TABLE memory_entries
    ADD CONSTRAINT chk_memory_entries_unified_type
        CHECK (type IN ('NOTE', 'KNOWLEDGE', 'INSIGHT', 'EXPERIENCE', 'OBSERVATION', 'PREFERENCE', 'PLAN')),
    ADD CONSTRAINT chk_memory_entries_origin_type
        CHECK (origin_type IS NULL OR origin_type IN ('DIARY', 'GROWTH')),
    ADD CONSTRAINT chk_memory_entries_library_ownership
        CHECK (
            (library_kind = 'PERSONAL' AND family_id IS NULL)
            OR (library_kind = 'FAMILY' AND family_id IS NOT NULL)
        );

CREATE UNIQUE INDEX idx_memory_entries_origin
    ON memory_entries(origin_type, origin_id)
    WHERE origin_type IS NOT NULL AND origin_id IS NOT NULL;

CREATE INDEX idx_memory_entries_family_visibility_status_time
    ON memory_entries(family_id, scope, status, occurred_at DESC, updated_at DESC)
    WHERE library_kind = 'FAMILY';

CREATE INDEX idx_memory_entries_related_user
    ON memory_entries(related_user_id, status, occurred_at DESC);
