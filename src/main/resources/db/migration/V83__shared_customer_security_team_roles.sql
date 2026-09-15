-- Make the approved shared security roles available for rentals and sales.
-- Preserve existing IDs, customized/disabled definitions and staff assignments.
INSERT INTO pms_team_role_definition
    (uuid, created_on, active, created_by, last_modified_date, code, display_name,
     description, business_area, permission_template)
SELECT UUID_TO_BIN(UUID()), NOW(6), 1, 0, NOW(6),
       CONCAT(area.code_prefix, '_', template.permission_template), template.display_name,
       template.description, area.business_area, template.permission_template
FROM (
    SELECT 'LANDLORD' AS business_area, 'LANDLORD' AS code_prefix
    UNION ALL SELECT 'PROPERTY_SALE_MANAGEMENT', 'SALE'
) area
CROSS JOIN (
    SELECT 'SECURITY_SUPERVISOR' AS permission_template, 'Security supervisor' AS display_name,
           'Security and smart-gate supervision within assigned responsibility areas' AS description
    UNION ALL SELECT 'GUARD', 'Guard', 'Live gate operations within assigned responsibility areas'
) template
WHERE NOT EXISTS (
    SELECT 1 FROM pms_team_role_definition existing
    WHERE existing.business_area = area.business_area
      AND existing.permission_template = template.permission_template
)
AND NOT EXISTS (
    SELECT 1 FROM pms_team_role_definition existing
    WHERE existing.code = CONCAT(area.code_prefix, '_', template.permission_template)
);
