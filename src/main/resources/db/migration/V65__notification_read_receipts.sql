ALTER TABLE pms_notification
    ADD COLUMN viewed_on DATETIME NULL AFTER updated_on,
    ADD INDEX idx_notification_recipient_viewed (recipient, viewed_on);
