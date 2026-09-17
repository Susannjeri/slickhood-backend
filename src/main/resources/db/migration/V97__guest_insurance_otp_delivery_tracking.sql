ALTER TABLE pms_insurance_guest_access
 ADD COLUMN delivery_channel VARCHAR(12) NULL AFTER otp_attempts,
 ADD COLUMN notification_id BIGINT NULL AFTER delivery_channel,
 ADD COLUMN last_sent_at DATETIME(6) NULL AFTER notification_id,
 ADD COLUMN send_count INT NOT NULL DEFAULT 0 AFTER last_sent_at,
 ADD KEY idx_insurance_guest_notification(notification_id),
 ADD CONSTRAINT fk_insurance_guest_notification FOREIGN KEY(notification_id) REFERENCES pms_notification(id);
