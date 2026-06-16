ALTER TABLE photos
    ADD COLUMN scope VARCHAR(16) NOT NULL DEFAULT 'FAMILY',
    ADD COLUMN mime_type VARCHAR(128),
    ADD COLUMN file_size BIGINT,
    ADD COLUMN original_name VARCHAR(255),
    ADD COLUMN description VARCHAR(512);

CREATE INDEX idx_photos_scope_family ON photos (scope, family_id);
CREATE INDEX idx_photos_scope_uploader ON photos (scope, uploader_id);
