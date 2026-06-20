CREATE TABLE media_attachments (
    id            BIGSERIAL PRIMARY KEY,
    uploader_id   BIGINT NOT NULL,
    family_id     BIGINT NOT NULL,
    object_key    VARCHAR(512) NOT NULL,
    mime_type     VARCHAR(128),
    file_size     BIGINT,
    original_name VARCHAR(255),
    record_type   VARCHAR(32) NOT NULL,
    record_id     BIGINT NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_media_attachments_record ON media_attachments (record_type, record_id);
CREATE INDEX idx_media_attachments_uploader ON media_attachments (uploader_id);
CREATE INDEX idx_media_attachments_family ON media_attachments (family_id);
