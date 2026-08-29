-- Canonical subscription catalogue based on subscription PMS.xlsx.
-- Preserve live subscription history by remapping legacy codes before removing test/duplicate rows.

UPDATE pms_user_subscription
SET plan_code = 'LANDLORD_BRONZE'
WHERE plan_code IN ('BRONZE', 'BRONZE-');

UPDATE pms_invoice
SET subscription_plan_code = 'LANDLORD_BRONZE'
WHERE subscription_plan_code IN ('BRONZE', 'BRONZE-');

UPDATE pms_subscription_payment_completion
SET plan_code = 'LANDLORD_BRONZE'
WHERE plan_code IN ('BRONZE', 'BRONZE-');

DELETE f
FROM pms_plan_feature f
JOIN pms_subscription_plan p ON p.id = f.subscription_plan_id
WHERE p.code IN ('BRONZE', 'BRONZE-', 'STARTER', 'TEST', 'TEST2', 'TEST3', 'TEST4', 'TEST5', 'TEST7', 'CARO', 'BASIC');

DELETE q
FROM pms_plan_quota q
JOIN pms_subscription_plan p ON p.id = q.subscription_plan_id
WHERE p.code IN ('BRONZE', 'BRONZE-', 'STARTER', 'TEST', 'TEST2', 'TEST3', 'TEST4', 'TEST5', 'TEST7', 'CARO', 'BASIC');

DELETE FROM pms_subscription_plan
WHERE code IN ('BRONZE', 'BRONZE-', 'STARTER', 'TEST', 'TEST2', 'TEST3', 'TEST4', 'TEST5', 'TEST7', 'CARO', 'BASIC');

-- Billing cycle is a selector on one tier, not part of the customer-facing package name.
UPDATE pms_subscription_plan SET display_name = 'Bronze', price = 1000.00, currency = 'KES'
WHERE code IN ('LANDLORD_BRONZE', 'ESTATE_BRONZE', 'SALE_BRONZE', 'WEALTH_BRONZE');
UPDATE pms_subscription_plan SET display_name = 'Silver', price = 3500.00, currency = 'KES'
WHERE code IN ('LANDLORD_SILVER', 'ESTATE_SILVER', 'SALE_SILVER', 'WEALTH_SILVER');
UPDATE pms_subscription_plan SET display_name = 'Gold', price = 7000.00, currency = 'KES'
WHERE code IN ('LANDLORD_GOLD', 'ESTATE_GOLD', 'SALE_GOLD', 'WEALTH_GOLD');
UPDATE pms_subscription_plan SET display_name = 'Platinum', price = 0.00, currency = 'KES'
WHERE code IN ('LANDLORD_PLATINUM_CUSTOM', 'ESTATE_PLATINUM_CUSTOM', 'SALE_PLATINUM_CUSTOM', 'WEALTH_PLATINUM_CUSTOM');

UPDATE pms_subscription_plan SET display_name = 'Bronze', price = 10800.00, currency = 'KES'
WHERE code IN ('LANDLORD_BRONZE_ANNUAL', 'ESTATE_BRONZE_ANNUAL', 'SALE_BRONZE_ANNUAL', 'WEALTH_BRONZE_ANNUAL');
UPDATE pms_subscription_plan SET display_name = 'Silver', price = 37800.00, currency = 'KES'
WHERE code IN ('LANDLORD_SILVER_ANNUAL', 'ESTATE_SILVER_ANNUAL', 'SALE_SILVER_ANNUAL', 'WEALTH_SILVER_ANNUAL');
UPDATE pms_subscription_plan SET display_name = 'Gold', price = 75600.00, currency = 'KES'
WHERE code IN ('LANDLORD_GOLD_ANNUAL', 'ESTATE_GOLD_ANNUAL', 'SALE_GOLD_ANNUAL', 'WEALTH_GOLD_ANNUAL');
UPDATE pms_subscription_plan SET display_name = 'Platinum', price = 0.00, currency = 'KES'
WHERE code IN ('LANDLORD_PLATINUM_ANNUAL_CUSTOM', 'ESTATE_PLATINUM_ANNUAL_CUSTOM', 'SALE_PLATINUM_ANNUAL_CUSTOM', 'WEALTH_PLATINUM_ANNUAL_CUSTOM');

-- Portfolio Management was renamed to Wealth. Remove the legacy duplicate entitlement.
DELETE FROM pms_plan_feature
WHERE feature_key = 'PORTFOLIO_MANAGEMENT_INCLUDED_UNITS';

-- Platinum is the only custom-priced/API tier in the workbook.
UPDATE pms_plan_feature f
JOIN pms_subscription_plan p ON p.id = f.subscription_plan_id
SET f.enabled = 0, f.active = 0
WHERE f.feature_key IN ('API_ACCESS', 'CUSTOM_PRICING')
  AND p.code NOT LIKE '%PLATINUM%';

INSERT INTO pms_plan_feature
    (uuid, created_on, active, created_by, last_modified_date, enabled, feature_key, subscription_plan_id)
SELECT UUID_TO_BIN(UUID()), NOW(6), 1, 0, NOW(6), 1, 'CUSTOM_PRICING', p.id
FROM pms_subscription_plan p
WHERE p.active = 1
  AND p.code LIKE '%PLATINUM%'
  AND NOT EXISTS (
      SELECT 1 FROM pms_plan_feature f
      WHERE f.subscription_plan_id = p.id AND f.feature_key = 'CUSTOM_PRICING' AND f.active = 1
  );

-- Enforce the same business rule at database level to close concurrent/direct-write races.
-- Inactive historical rows deliberately evaluate to NULL and therefore remain maintainable.
ALTER TABLE pms_subscription_plan
    ADD COLUMN active_package_key VARCHAR(700)
        GENERATED ALWAYS AS (
            CASE
                WHEN active = b'1' THEN LOWER(CONCAT(role_family, '|', billing_cycle, '|', TRIM(display_name)))
                ELSE NULL
            END
        ) STORED,
    ADD UNIQUE INDEX uk_subscription_active_package (active_package_key);
