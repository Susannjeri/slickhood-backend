-- V86 has already been applied. Preserve its checksum and columns; expand to
-- the snake_case names used by Spring Boot's physical naming strategy.
ALTER TABLE pms_notification
    ADD COLUMN business_event_key VARCHAR(64) NULL,
    ADD COLUMN delivery_key VARCHAR(64) NULL,
    ADD COLUMN action_path VARCHAR(500) NULL;
UPDATE pms_notification
    SET business_event_key=businessEventKey, delivery_key=deliveryKey, action_path=actionPath;
CREATE UNIQUE INDEX uk_notification_delivery_key_v87 ON pms_notification(delivery_key);
CREATE INDEX idx_notification_business_event_v87 ON pms_notification(business_event_key,recipient,channel);

ALTER TABLE pms_sms
    ADD COLUMN receipt_check_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN next_receipt_check_at DATETIME(6) NULL;
UPDATE pms_sms
    SET receipt_check_attempts=receiptCheckAttempts, next_receipt_check_at=nextReceiptCheckAt;
CREATE INDEX idx_sms_receipt_due_v87 ON pms_sms(channel,next_receipt_check_at,active);
