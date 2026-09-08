CREATE INDEX idx_invite_recipient_type_active_expiry
    ON pms_invite (recipient, type, active, expiry_date);
