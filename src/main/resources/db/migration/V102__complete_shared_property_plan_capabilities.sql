-- Apply the shared property-area capabilities to every existing property plan,
-- including inactive catalogue records that may later be reactivated.
UPDATE pms_plan_feature feature
JOIN pms_subscription_plan plan ON plan.id = feature.subscription_plan_id
SET feature.active = 1,
    feature.enabled = 1,
    feature.last_modified_date = NOW(6)
WHERE plan.product_key IN ('LANDLORD', 'ESTATE_MANAGEMENT', 'PROPERTY_SALES')
  AND feature.feature_key IN ('PROPERTY_RENTALS', 'ESTATE_MANAGEMENT', 'PROPERTY_SALES');

INSERT INTO pms_plan_feature
    (uuid, created_on, active, created_by, last_modified_date, enabled, feature_key, subscription_plan_id)
SELECT UUID_TO_BIN(UUID()), NOW(6), 1, 0, NOW(6), 1, capability.feature_key, plan.id
FROM pms_subscription_plan plan
JOIN (
    SELECT 'PROPERTY_RENTALS' AS feature_key
    UNION ALL SELECT 'ESTATE_MANAGEMENT'
    UNION ALL SELECT 'PROPERTY_SALES'
) capability
WHERE plan.product_key IN ('LANDLORD', 'ESTATE_MANAGEMENT', 'PROPERTY_SALES')
  AND NOT EXISTS (
      SELECT 1 FROM pms_plan_feature existing
      WHERE existing.subscription_plan_id = plan.id
        AND existing.feature_key = capability.feature_key
  );
