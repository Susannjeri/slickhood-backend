ALTER TABLE pms_receivable_late_fee_policy
    ADD COLUMN maximum_fee DECIMAL(19,2) NULL AFTER grace_days;
