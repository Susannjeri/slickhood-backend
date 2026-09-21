ALTER TABLE pms_notification
    ADD COLUMN provider_status VARCHAR(32) NULL AFTER delivered,
    ADD COLUMN accepted_at DATETIME(6) NULL AFTER provider_status,
    ADD COLUMN delivered_at DATETIME(6) NULL AFTER accepted_at,
    ADD COLUMN failed_at DATETIME(6) NULL AFTER delivered_at;

UPDATE pms_notification
SET provider_status = CASE
        WHEN delivered = TRUE THEN 'DELIVERED'
        WHEN active = FALSE THEN 'FAILED'
        ELSE 'QUEUED'
    END,
    delivered_at = CASE WHEN delivered = TRUE THEN updated_on ELSE NULL END,
    failed_at = CASE WHEN delivered = FALSE AND active = FALSE THEN updated_on ELSE NULL END
WHERE provider_status IS NULL;

CREATE INDEX idx_notification_provider_status_v110
    ON pms_notification(provider_status, channel, created_on);
