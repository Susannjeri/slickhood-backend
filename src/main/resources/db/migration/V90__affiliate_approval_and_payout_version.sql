ALTER TABLE pms_affiliate_profile
    ADD COLUMN reviewed_at DATETIME(6) NULL,
    ADD COLUMN reviewed_by_user_id BIGINT NULL,
    ADD COLUMN review_notes VARCHAR(1000) NULL,
    ADD KEY idx_affiliate_profile_status (status, active, created_on);

ALTER TABLE pms_affiliate_payout
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN payment_reference_key VARCHAR(100) NULL,
    ADD UNIQUE KEY uq_affiliate_payout_payment_reference_key (payment_reference_key);

UPDATE pms_subscription_plan
SET active = 0
WHERE product_key = 'AFFILIATE' OR plan_category = 'AFFILIATE' OR role_family = 'AFFILIATE';
