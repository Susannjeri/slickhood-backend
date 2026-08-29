-- Each property-related package applies to one business area only.
-- Services/Soko and Affiliate catalogues are intentionally outside this normalization.

DELETE f
FROM pms_plan_feature f
JOIN pms_subscription_plan p ON p.id = f.subscription_plan_id
WHERE p.role_family IN ('LANDLORD', 'ESTATE_MANAGER', 'SALES_AGENT', 'ASSET_PORTFOLIO_MANAGER')
  AND f.feature_key IN ('PROPERTY_RENTALS', 'ESTATE_MANAGEMENT', 'PROPERTY_SALES', 'WEALTH_MANAGEMENT')
  AND NOT (
      (p.role_family = 'LANDLORD' AND f.feature_key = 'PROPERTY_RENTALS')
      OR (p.role_family = 'ESTATE_MANAGER' AND f.feature_key = 'ESTATE_MANAGEMENT')
      OR (p.role_family = 'SALES_AGENT' AND f.feature_key = 'PROPERTY_SALES')
      OR (p.role_family = 'ASSET_PORTFOLIO_MANAGER' AND f.feature_key = 'WEALTH_MANAGEMENT')
  );

INSERT INTO pms_plan_feature
    (uuid, created_on, active, created_by, last_modified_date, enabled, feature_key, subscription_plan_id)
SELECT UUID_TO_BIN(UUID()), NOW(6), 1, 0, NOW(6), 1,
       CASE p.role_family
           WHEN 'LANDLORD' THEN 'PROPERTY_RENTALS'
           WHEN 'ESTATE_MANAGER' THEN 'ESTATE_MANAGEMENT'
           WHEN 'SALES_AGENT' THEN 'PROPERTY_SALES'
           WHEN 'ASSET_PORTFOLIO_MANAGER' THEN 'WEALTH_MANAGEMENT'
       END,
       p.id
FROM pms_subscription_plan p
WHERE p.active = 1
  AND p.role_family IN ('LANDLORD', 'ESTATE_MANAGER', 'SALES_AGENT', 'ASSET_PORTFOLIO_MANAGER')
  AND NOT EXISTS (
      SELECT 1
      FROM pms_plan_feature f
      WHERE f.subscription_plan_id = p.id
        AND f.active = 1
        AND f.feature_key = CASE p.role_family
            WHEN 'LANDLORD' THEN 'PROPERTY_RENTALS'
            WHEN 'ESTATE_MANAGER' THEN 'ESTATE_MANAGEMENT'
            WHEN 'SALES_AGENT' THEN 'PROPERTY_SALES'
            WHEN 'ASSET_PORTFOLIO_MANAGER' THEN 'WEALTH_MANAGEMENT'
        END
  );
