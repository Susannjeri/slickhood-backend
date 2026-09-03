-- Reconcile the runtime catalogue with subscription PMS.xlsx and the distinct
-- Estate Management, Property Sale Management and My Wealth business areas.

-- Retire duplicate free-plan identities without breaking current users or history.
UPDATE pms_user_subscription
SET plan_code = 'SERVICES_FREE', product_key = 'SERVICES'
WHERE plan_code = 'STANDARD';

UPDATE pms_user_subscription
SET plan_code = 'AFFILIATE_FREE', product_key = 'AFFILIATE'
WHERE plan_code = 'STANDARD_AFFILIATE';

UPDATE pms_invoice SET subscription_plan_code = 'SERVICES_FREE'
WHERE subscription_plan_code = 'STANDARD';
UPDATE pms_invoice SET subscription_plan_code = 'AFFILIATE_FREE'
WHERE subscription_plan_code = 'STANDARD_AFFILIATE';

UPDATE pms_subscription_payment_completion SET plan_code = 'SERVICES_FREE'
WHERE plan_code = 'STANDARD';
UPDATE pms_subscription_payment_completion SET plan_code = 'AFFILIATE_FREE'
WHERE plan_code = 'STANDARD_AFFILIATE';

UPDATE pms_subscription_plan SET active = 0
WHERE code IN ('STANDARD', 'STANDARD_AFFILIATE');
UPDATE pms_plan_feature f
JOIN pms_subscription_plan p ON p.id = f.subscription_plan_id
SET f.active = 0, f.enabled = 0
WHERE p.code IN ('STANDARD', 'STANDARD_AFFILIATE');
UPDATE pms_plan_quota q
JOIN pms_subscription_plan p ON p.id = q.subscription_plan_id
SET q.active = 0
WHERE p.code IN ('STANDARD', 'STANDARD_AFFILIATE');

-- The spreadsheet includes listing and gate management for subscribed landlord
-- units. These server-side keys are the ones enforced by the application.
INSERT INTO pms_plan_feature
    (uuid, created_on, active, created_by, last_modified_date, enabled, feature_key, subscription_plan_id)
SELECT UUID_TO_BIN(UUID()), NOW(6), 1, 0, NOW(6), 1, 'PROPERTY_LISTINGS', p.id
FROM pms_subscription_plan p
WHERE p.active = 1 AND p.product_key = 'LANDLORD'
  AND NOT EXISTS (
      SELECT 1 FROM pms_plan_feature f
      WHERE f.subscription_plan_id = p.id
        AND f.feature_key = 'PROPERTY_LISTINGS' AND f.active = 1 AND f.enabled = 1
  );

-- Remove landlord-only labels that leaked into other product catalogues. Keep
-- genuinely shared payment, reporting, branding and support capabilities.
UPDATE pms_plan_feature f
JOIN pms_subscription_plan p ON p.id = f.subscription_plan_id
SET f.active = 0, f.enabled = 0
WHERE p.product_key = 'ESTATE_MANAGEMENT'
  AND f.feature_key IN ('AUTOMATED_RENT_REMINDERS', 'LANDLORD_PAYMENT_SETUP',
                        'LATE_FEE_RULES', 'TENANT_ONBOARDING',
                        'UNIT_LISTING_INCLUDED_UNITS', 'WEALTH_INCLUDED_UNITS');

UPDATE pms_plan_feature f
JOIN pms_subscription_plan p ON p.id = f.subscription_plan_id
SET f.active = 0, f.enabled = 0
WHERE p.product_key = 'PROPERTY_SALES'
  AND f.feature_key IN ('AUTOMATED_RENT_REMINDERS', 'LANDLORD_PAYMENT_SETUP',
                        'LATE_FEE_RULES', 'TENANT_ONBOARDING',
                        'TENANT_SERVICE_PROVIDER_ACCESS', 'GATE_MANAGEMENT_INCLUDED_UNITS',
                        'WEALTH_INCLUDED_UNITS');

UPDATE pms_plan_feature f
JOIN pms_subscription_plan p ON p.id = f.subscription_plan_id
SET f.active = 0, f.enabled = 0
WHERE p.product_key = 'MY_WEALTH'
  AND f.feature_key IN ('AUTOMATED_RENT_REMINDERS', 'LANDLORD_PAYMENT_SETUP',
                        'LATE_FEE_RULES', 'TENANT_ONBOARDING',
                        'TENANT_SERVICE_PROVIDER_ACCESS', 'GATE_MANAGEMENT_INCLUDED_UNITS',
                        'UNIT_LISTING_INCLUDED_UNITS', 'PER_PROPERTY_PAYMENT_ACCOUNT');
