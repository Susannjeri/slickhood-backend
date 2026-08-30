ALTER TABLE pms_estate_service_charge
    ADD COLUMN pre_due_reminder_queued_at DATETIME(6) NULL,
    ADD COLUMN overdue_notice_queued_at DATETIME(6) NULL,
    ADD INDEX idx_charge_reminder_scan (active, due_date, pre_due_reminder_queued_at, overdue_notice_queued_at);
