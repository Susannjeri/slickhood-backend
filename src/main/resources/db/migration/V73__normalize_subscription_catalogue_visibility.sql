-- Keep the subscription catalogue limited to the supported SlickHood offers.
-- Rows are never deleted here: invoices, payment completions and subscriptions
-- retain their original plan codes for audit and historical display.

-- These are the only retired aliases left by earlier catalogue versions.
UPDATE pms_subscription_plan
SET active = 0
WHERE code IN ('STARTER', 'STANDARD', 'STANDARD_AFFILIATE');

-- Restore every supported offer. Some of these rows were manually retired on
-- the test host even though active subscriptions still reference them.
UPDATE pms_subscription_plan
SET active = 1
WHERE code IN (
    'LANDLORD_BRONZE', 'LANDLORD_SILVER', 'LANDLORD_GOLD', 'LANDLORD_PLATINUM_CUSTOM',
    'LANDLORD_BRONZE_ANNUAL', 'LANDLORD_SILVER_ANNUAL', 'LANDLORD_GOLD_ANNUAL',
    'LANDLORD_PLATINUM_ANNUAL_CUSTOM',
    'ESTATE_BRONZE', 'ESTATE_SILVER', 'ESTATE_GOLD', 'ESTATE_PLATINUM_CUSTOM',
    'ESTATE_BRONZE_ANNUAL', 'ESTATE_SILVER_ANNUAL', 'ESTATE_GOLD_ANNUAL',
    'ESTATE_PLATINUM_ANNUAL_CUSTOM',
    'SALE_BRONZE', 'SALE_SILVER', 'SALE_GOLD', 'SALE_PLATINUM_CUSTOM',
    'SALE_BRONZE_ANNUAL', 'SALE_SILVER_ANNUAL', 'SALE_GOLD_ANNUAL',
    'SALE_PLATINUM_ANNUAL_CUSTOM',
    'WEALTH_BRONZE', 'WEALTH_SILVER', 'WEALTH_GOLD', 'WEALTH_PLATINUM_CUSTOM',
    'WEALTH_BRONZE_ANNUAL', 'WEALTH_SILVER_ANNUAL', 'WEALTH_GOLD_ANNUAL',
    'WEALTH_PLATINUM_ANNUAL_CUSTOM',
    'SERVICES_FREE', 'SOKO_FREE', 'AFFILIATE_FREE',
    'ADDON_GATE_MANAGEMENT', 'ADDON_LISTING', 'ADDON_PORTFOLIO_MANAGEMENT'
);
