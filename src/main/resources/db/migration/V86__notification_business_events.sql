ALTER TABLE pms_notification
    ADD COLUMN businessEventKey VARCHAR(64) NULL,
    ADD COLUMN deliveryKey VARCHAR(64) NULL,
    ADD COLUMN actionPath VARCHAR(500) NULL;
CREATE UNIQUE INDEX uk_notification_delivery_key ON pms_notification(deliveryKey);
CREATE INDEX idx_notification_business_event ON pms_notification(businessEventKey,recipient,channel);

ALTER TABLE pms_sms
    ADD COLUMN receiptCheckAttempts INT NOT NULL DEFAULT 0,
    ADD COLUMN nextReceiptCheckAt DATETIME(6) NULL;
CREATE INDEX idx_sms_receipt_due ON pms_sms(channel,nextReceiptCheckAt,active);
