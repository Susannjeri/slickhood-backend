-- One property subscription covers Rental/Landlord, Estate/Homeowner and Property Sales.
-- Existing invoices and subscription rows remain untouched; role permissions continue
-- to govern the operations available in each workspace.
INSERT INTO pms_plan_feature
    (uuid, created_on, active, created_by, last_modified_date, enabled, feature_key, subscription_plan_id)
SELECT UUID_TO_BIN(UUID()), NOW(6), 1, 0, NOW(6), 1, capability.feature_key, plan.id
FROM pms_subscription_plan plan
JOIN (
    SELECT 'PROPERTY_RENTALS' AS feature_key
    UNION ALL SELECT 'ESTATE_MANAGEMENT'
    UNION ALL SELECT 'PROPERTY_SALES'
) capability
WHERE plan.active = 1
  AND plan.product_key IN ('LANDLORD', 'ESTATE_MANAGEMENT', 'PROPERTY_SALES')
  AND NOT EXISTS (
      SELECT 1 FROM pms_plan_feature existing
      WHERE existing.subscription_plan_id = plan.id
        AND existing.feature_key = capability.feature_key
        AND existing.active = 1
        AND existing.enabled = 1
  );
