ALTER TABLE chat_sessions
    ADD COLUMN agent_context_type VARCHAR(16) NOT NULL DEFAULT 'FAMILY',
    ADD COLUMN target_user_id BIGINT REFERENCES users(id),
    ADD COLUMN target_persona_id BIGINT REFERENCES family_persona_members(id);

UPDATE chat_sessions session
SET agent_context_type = CASE
        WHEN COALESCE(session.metadata->>'agentMode', '') = 'mirror' THEN 'MIRROR'
        WHEN COALESCE(session.metadata->>'agentMode', '') = 'persona' THEN 'PERSONA'
        ELSE 'FAMILY'
    END,
    target_user_id = CASE
        WHEN COALESCE(session.metadata->>'targetUserId', '') ~ '^[0-9]+$'
             AND EXISTS (
                 SELECT 1 FROM users user_account
                 WHERE user_account.id = (session.metadata->>'targetUserId')::BIGINT
             )
            THEN (session.metadata->>'targetUserId')::BIGINT
        ELSE NULL
    END,
    target_persona_id = CASE
        WHEN COALESCE(session.metadata->>'targetPersonaId', '') ~ '^[0-9]+$'
             AND EXISTS (
                 SELECT 1 FROM family_persona_members persona
                 WHERE persona.id = (session.metadata->>'targetPersonaId')::BIGINT
                   AND persona.family_id = session.family_id
             )
            THEN (session.metadata->>'targetPersonaId')::BIGINT
        ELSE NULL
    END;

UPDATE chat_sessions
SET agent_context_type = 'FAMILY',
    target_user_id = NULL,
    target_persona_id = NULL
WHERE (agent_context_type = 'MIRROR' AND target_user_id IS NULL)
   OR (agent_context_type = 'PERSONA' AND target_persona_id IS NULL);

ALTER TABLE chat_sessions
    ADD CONSTRAINT chk_chat_sessions_agent_context
        CHECK (
            (agent_context_type = 'FAMILY' AND target_user_id IS NULL AND target_persona_id IS NULL)
            OR (agent_context_type = 'MIRROR' AND target_user_id IS NOT NULL AND target_persona_id IS NULL)
            OR (agent_context_type = 'PERSONA' AND target_user_id IS NULL AND target_persona_id IS NOT NULL)
        );

CREATE INDEX idx_chat_sessions_agent_context
    ON chat_sessions(user_id, family_id, agent_context_type, target_user_id, target_persona_id);
