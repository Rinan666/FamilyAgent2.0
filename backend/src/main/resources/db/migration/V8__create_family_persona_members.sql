-- family_persona_members: non-real "spiritual member" records scoped to a family.
-- These are not linked to any user account and are never involved in real-member
-- governance (invitations, care authorizations, role management).
CREATE TABLE family_persona_members (
    id          BIGSERIAL    PRIMARY KEY,
    family_id   BIGINT       NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    era_identity VARCHAR(200),
    values      TEXT,
    speaking_style TEXT,
    personality TEXT,
    created_by  BIGINT       NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fpm_family_id ON family_persona_members(family_id);
