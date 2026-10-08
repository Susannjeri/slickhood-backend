-- Repair canonical catalogue prices that were truncated during production UAT.
-- These rows are shared catalogue definitions, not customer transactions. Existing
-- invoices retain their recorded amount and are deliberately not rewritten.

UPDATE pms_subscription_plan
SET price = CASE
    WHEN code LIKE '%_BRONZE' THEN 1000.00
    WHEN code LIKE '%_SILVER' THEN 3500.00
    WHEN code LIKE '%_GOLD' THEN 7000.00
    WHEN code LIKE '%_BRONZE_ANNUAL' THEN 10800.00
    WHEN code LIKE '%_SILVER_ANNUAL' THEN 37800.00
    WHEN code LIKE '%_GOLD_ANNUAL' THEN 75600.00
    ELSE price
END,
currency = 'KES'
WHERE code IN (
    'LANDLORD_BRONZE', 'LANDLORD_SILVER', 'LANDLORD_GOLD',
    'LANDLORD_BRONZE_ANNUAL', 'LANDLORD_SILVER_ANNUAL', 'LANDLORD_GOLD_ANNUAL',
    'ESTATE_BRONZE', 'ESTATE_SILVER', 'ESTATE_GOLD',
    'ESTATE_BRONZE_ANNUAL', 'ESTATE_SILVER_ANNUAL', 'ESTATE_GOLD_ANNUAL',
    'SALE_BRONZE', 'SALE_SILVER', 'SALE_GOLD',
    'SALE_BRONZE_ANNUAL', 'SALE_SILVER_ANNUAL', 'SALE_GOLD_ANNUAL',
    'WEALTH_BRONZE', 'WEALTH_SILVER', 'WEALTH_GOLD',
    'WEALTH_BRONZE_ANNUAL', 'WEALTH_SILVER_ANNUAL', 'WEALTH_GOLD_ANNUAL'
);

-- Custom/enterprise packages must remain sales-managed and must never create a
-- misleading low-value self-service checkout.
UPDATE pms_subscription_plan
SET price = 0.00,
    currency = 'KES',
    purchase_mode = 'SALES_MANAGED'
WHERE code IN (
    'LANDLORD_PLATINUM_CUSTOM', 'LANDLORD_PLATINUM_ANNUAL_CUSTOM',
    'ESTATE_PLATINUM_CUSTOM', 'ESTATE_PLATINUM_ANNUAL_CUSTOM',
    'SALE_PLATINUM_CUSTOM', 'SALE_PLATINUM_ANNUAL_CUSTOM',
    'WEALTH_PLATINUM_CUSTOM', 'WEALTH_PLATINUM_ANNUAL_CUSTOM'
);
