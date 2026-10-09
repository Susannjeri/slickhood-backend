ALTER TABLE pms_users
    ADD COLUMN refresh_token_request_hash VARCHAR(64) NULL,
    ADD COLUMN refresh_token_replay_hash VARCHAR(64) NULL,
    ADD COLUMN refresh_token_replay_request_hash VARCHAR(64) NULL,
    ADD COLUMN refresh_token_replay_expires_at DATETIME(6) NULL,
    ADD UNIQUE KEY uk_users_refresh_token_replay_hash (refresh_token_replay_hash);

-- Both credentials are retained only as one-way hashes. The replacement token
-- is derived with a server-held key, so the database remains free of recoverable
-- bearer credentials while an exact retry can reproduce the committed result.
