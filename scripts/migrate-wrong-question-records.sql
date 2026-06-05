-- Formal wrong-question records.
CREATE TABLE IF NOT EXISTS wrong_question_records (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    family_id BIGINT REFERENCES families(id),
    test_record_id BIGINT NOT NULL REFERENCES test_records(id) ON DELETE CASCADE,
    question_id BIGINT NOT NULL REFERENCES questions(id),
    kp_id BIGINT REFERENCES knowledge_points(id),
    student_answer TEXT,
    score DECIMAL(5,2),
    correct BOOLEAN NOT NULL DEFAULT false,
    error_type VARCHAR(100),
    feedback TEXT,
    parent_explanation TEXT,
    next_suggestion TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_wrong_question_record_once UNIQUE (test_record_id, question_id)
);

ALTER TABLE wrong_question_records ADD COLUMN IF NOT EXISTS parent_explanation TEXT;
ALTER TABLE wrong_question_records ADD COLUMN IF NOT EXISTS next_suggestion TEXT;

CREATE INDEX IF NOT EXISTS idx_wrong_question_records_user ON wrong_question_records(user_id);
CREATE INDEX IF NOT EXISTS idx_wrong_question_records_question ON wrong_question_records(question_id);
CREATE INDEX IF NOT EXISTS idx_wrong_question_records_test ON wrong_question_records(test_record_id);
CREATE INDEX IF NOT EXISTS idx_wrong_question_records_user_status ON wrong_question_records(user_id, status);
CREATE INDEX IF NOT EXISTS idx_wrong_question_records_created ON wrong_question_records(created_at DESC);

INSERT INTO wrong_question_records
(user_id, family_id, test_record_id, question_id, kp_id, student_answer, score, correct, status, created_at, updated_at)
SELECT
    source.user_id,
    source.family_id,
    source.test_record_id,
    source.question_id,
    q.kp_id,
    source.student_answer,
    source.score,
    false AS correct,
    'OPEN' AS status,
    source.created_at,
    NOW()
FROM (
    SELECT
        tr.user_id,
        tr.family_id,
        tr.id AS test_record_id,
        score.question_id::bigint AS question_id,
        COALESCE(tr.answers ->> score.question_id, '') AS student_answer,
        score.score_value::numeric AS score,
        tr.created_at
    FROM test_records tr
    JOIN LATERAL jsonb_each_text(COALESCE(tr.scores, '{}'::jsonb)) score(question_id, score_value) ON true
    WHERE score.question_id ~ '^[0-9]+$'
    AND score.score_value ~ '^-?[0-9]+(\.[0-9]+)?$'
    AND score.score_value::numeric < 60
) source
JOIN questions q ON q.id = source.question_id
ON CONFLICT (test_record_id, question_id) DO NOTHING;
