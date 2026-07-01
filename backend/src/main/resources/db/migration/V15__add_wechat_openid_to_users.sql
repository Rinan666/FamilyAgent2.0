ALTER TABLE users
    ADD COLUMN IF NOT EXISTS wechat_open_id VARCHAR(128);

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_wechat_open_id
    ON users(wechat_open_id)
    WHERE wechat_open_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_users_wechat_open_id
    ON users(wechat_open_id);
