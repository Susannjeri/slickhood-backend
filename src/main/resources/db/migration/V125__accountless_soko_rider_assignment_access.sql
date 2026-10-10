ALTER TABLE pms_soko_order
    ADD COLUMN rider_assignment_token_hash VARCHAR(64) NULL,
    ADD COLUMN rider_assignment_token_issued_at DATETIME(6) NULL,
    ADD COLUMN rider_assignment_token_expires_at DATETIME(6) NULL,
    ADD COLUMN rider_assignment_token_revoked_at DATETIME(6) NULL,
    ADD COLUMN rider_assignment_link_requested_at DATETIME(6) NULL,
    ADD COLUMN rider_assignment_link_request_count INT NOT NULL DEFAULT 0,
    ADD COLUMN rider_assignment_declined_at DATETIME(6) NULL,
    ADD COLUMN rider_assignment_decline_reason VARCHAR(500) NULL;

CREATE UNIQUE INDEX uk_soko_order_rider_assignment_token
    ON pms_soko_order (rider_assignment_token_hash);

CREATE INDEX idx_soko_order_rider_assignment_expiry
    ON pms_soko_order (rider_assignment_token_expires_at, rider_assignment_token_revoked_at);
