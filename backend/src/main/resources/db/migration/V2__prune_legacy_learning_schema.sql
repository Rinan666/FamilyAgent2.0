DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'chat_sessions'
          AND column_name = 'question_id'
    ) THEN
        EXECUTE $stmt$
            UPDATE chat_sessions
            SET metadata = COALESCE(metadata, '{}'::jsonb) || jsonb_build_object('legacyQuestionId', question_id)
            WHERE question_id IS NOT NULL
        $stmt$;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'chat_sessions'
          AND column_name = 'knowledge_point_id'
    ) THEN
        EXECUTE $stmt$
            UPDATE chat_sessions
            SET metadata = COALESCE(metadata, '{}'::jsonb) || jsonb_build_object('legacyKnowledgePointId', knowledge_point_id)
            WHERE knowledge_point_id IS NOT NULL
        $stmt$;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'memory_entries'
          AND column_name = 'knowledge_point_id'
    ) THEN
        EXECUTE $stmt$
            UPDATE memory_entries
            SET metadata = COALESCE(metadata, '{}'::jsonb) || jsonb_build_object('legacyKnowledgePointId', knowledge_point_id)
            WHERE knowledge_point_id IS NOT NULL
        $stmt$;
    END IF;
END $$;

ALTER TABLE IF EXISTS chat_sessions
    DROP COLUMN IF EXISTS question_id,
    DROP COLUMN IF EXISTS knowledge_point_id;

ALTER TABLE IF EXISTS memory_entries
    DROP COLUMN IF EXISTS knowledge_point_id;

DROP TABLE IF EXISTS wrong_question_records;
DROP TABLE IF EXISTS test_records;
DROP TABLE IF EXISTS ability_profiles;
DROP TABLE IF EXISTS questions;
DROP TABLE IF EXISTS knowledge_points;
