ALTER TABLE pms_estate_service_charge
    ADD COLUMN last_overdue_reminder_queued_at DATETIME(6) NULL,
    ADD COLUMN overdue_reminder_count INT NOT NULL DEFAULT 0,
    ADD INDEX idx_charge_overdue_scan (active, due_date, overdue_reminder_count, last_overdue_reminder_queued_at);

UPDATE pms_estate_service_charge
SET last_overdue_reminder_queued_at = overdue_notice_queued_at,
    overdue_reminder_count = 1
WHERE overdue_notice_queued_at IS NOT NULL;

ALTER TABLE pms_notification
    ADD INDEX idx_notification_retry_scan (active, delivered, retry, channel, updated_on, retries);
