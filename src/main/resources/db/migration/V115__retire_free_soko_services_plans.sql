-- Existing subscriptions retain their historical plan references. Deactivation only
-- removes the former free offers from new package selection after the paid monthly
-- plans are seeded by LandlordPlanCatalogSeedService.
UPDATE pms_subscription_plan
SET active = FALSE
WHERE code IN ('SERVICES_FREE', 'SOKO_FREE')
  AND active = TRUE;
