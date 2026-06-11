CREATE TABLE photos (
    id          BIGSERIAL PRIMARY KEY,
    family_id   BIGINT NOT NULL,
    uploader_id BIGINT NOT NULL,
    object_key  VARCHAR(500) NOT NULL,
    taken_at    TIMESTAMP,
    metadata    JSONB DEFAULT '{}',
    created_at  TIMESTAMP DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_photos_family_id ON photos (family_id);
CREATE INDEX idx_photos_uploader_id ON photos (uploader_id);
