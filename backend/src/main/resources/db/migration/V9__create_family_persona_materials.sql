-- family_persona_materials stores curated persona material cards.
-- Raw pasted source text is intentionally not persisted.
CREATE TABLE family_persona_materials (
    id          BIGSERIAL    PRIMARY KEY,
    family_id   BIGINT       NOT NULL,
    persona_id  BIGINT       NOT NULL,
    title       VARCHAR(100) NOT NULL,
    content     TEXT         NOT NULL,
    tags        TEXT[]       NOT NULL DEFAULT '{}',
    created_by  BIGINT       NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_fpmat_family FOREIGN KEY (family_id) REFERENCES families(id) ON DELETE CASCADE,
    CONSTRAINT fk_fpmat_persona FOREIGN KEY (persona_id) REFERENCES family_persona_members(id) ON DELETE CASCADE
);

CREATE INDEX idx_fpmat_persona_id ON family_persona_materials(persona_id);
CREATE INDEX idx_fpmat_family_persona ON family_persona_materials(family_id, persona_id);
