-- Explicit subscription products and purchase modes close price-based activation bypasses.
ALTER TABLE pms_subscription_plan
    ADD COLUMN product_key VARCHAR(64) NULL AFTER currency,
    ADD COLUMN purchase_mode VARCHAR(32) NULL AFTER product_key,
    ADD COLUMN tier_rank INT NULL AFTER purchase_mode;

UPDATE pms_subscription_plan SET product_key = CASE plan_category
    WHEN 'LANDLORD' THEN 'LANDLORD'
    WHEN 'ESTATE_MANAGEMENT' THEN 'ESTATE_MANAGEMENT'
    WHEN 'PROPERTY_SALES' THEN 'PROPERTY_SALES'
    WHEN 'ASSET_PORTFOLIO_MANAGER' THEN 'MY_WEALTH'
    WHEN 'AFFILIATE' THEN 'AFFILIATE'
    ELSE 'SERVICES' END;
UPDATE pms_subscription_plan SET purchase_mode = CASE
    WHEN code LIKE '%CUSTOM%' THEN 'SALES_MANAGED'
    WHEN price > 0 THEN 'SELF_SERVICE'
    ELSE 'FREE' END;
UPDATE pms_subscription_plan SET tier_rank = CASE UPPER(TRIM(display_name))
    WHEN 'BRONZE' THEN 10 WHEN 'SILVER' THEN 20 WHEN 'GOLD' THEN 30 WHEN 'PLATINUM' THEN 40 ELSE 0 END;

ALTER TABLE pms_subscription_plan
    MODIFY product_key VARCHAR(64) NOT NULL,
    MODIFY purchase_mode VARCHAR(32) NOT NULL,
    MODIFY tier_rank INT NOT NULL;

ALTER TABLE pms_subscription_plan DROP INDEX uk_subscription_active_package;
ALTER TABLE pms_subscription_plan DROP COLUMN active_package_key;
ALTER TABLE pms_subscription_plan
    ADD COLUMN active_package_key VARCHAR(700)
        GENERATED ALWAYS AS (
            CASE WHEN active = b'1'
                THEN LOWER(CONCAT(product_key, '|', billing_cycle, '|', TRIM(display_name)))
                ELSE NULL END
        ) STORED,
    ADD UNIQUE INDEX uk_subscription_active_package (active_package_key),
    ADD INDEX idx_subscription_product (product_key, active);

ALTER TABLE pms_user_subscription
    ADD COLUMN product_key VARCHAR(64) NULL AFTER plan_code,
    ADD COLUMN term_version BIGINT NOT NULL DEFAULT 0 AFTER source_payment_ref;
UPDATE pms_user_subscription us
JOIN pms_subscription_plan p ON p.code = us.plan_code
SET us.product_key = p.product_key;
UPDATE pms_user_subscription SET product_key = CASE role
    WHEN 'LANDLORD' THEN 'LANDLORD'
    WHEN 'ESTATE_MANAGER' THEN 'ESTATE_MANAGEMENT'
    WHEN 'SALES_AGENT' THEN 'PROPERTY_SALES'
    WHEN 'ASSET_PORTFOLIO_MANAGER' THEN 'MY_WEALTH'
    WHEN 'AFFILIATE' THEN 'AFFILIATE'
    ELSE 'SERVICES' END
WHERE product_key IS NULL;
ALTER TABLE pms_user_subscription
    MODIFY product_key VARCHAR(64) NOT NULL,
    ADD INDEX idx_user_subscription_product (created_by, product_key, status, active);

-- Document maintenance metadata; previous files remain immutable versions.
ALTER TABLE pms_kyc_document
    ADD COLUMN version_no INT NOT NULL DEFAULT 1,
    ADD COLUMN issued_at DATETIME(6) NULL,
    ADD COLUMN expires_at DATETIME(6) NULL,
    ADD COLUMN reverification_due_at DATETIME(6) NULL,
    ADD COLUMN maintenance_reason VARCHAR(500) NULL;

-- Existing affiliate profiles adopt the approved spreadsheet rate.
UPDATE pms_affiliate_profile SET commission_rate = 25.00;
ALTER TABLE pms_affiliate_commission
    ADD COLUMN eligible_sequence INT NULL,
    ADD COLUMN reversed_at DATETIME(6) NULL,
    ADD COLUMN reversal_reason VARCHAR(500) NULL;
UPDATE pms_affiliate_commission c
JOIN (SELECT id, ROW_NUMBER() OVER (PARTITION BY referred_user_id ORDER BY earned_at, id) AS sequence_no
      FROM pms_affiliate_commission) ranked ON ranked.id = c.id
SET c.eligible_sequence = ranked.sequence_no;
ALTER TABLE pms_affiliate_commission
    MODIFY eligible_sequence INT NOT NULL,
    ADD UNIQUE INDEX uk_affiliate_referred_sequence (referred_user_id, eligible_sequence);
