-- Tenants operate only on their invitation-bound unit and lease. These legacy
-- catalogue permissions exposed owner navigation and are intentionally revoked.
DELETE mapping
FROM pms_role_mapping mapping
JOIN pms_role role_record ON role_record.id = mapping.role_id
JOIN pms_permission permission_record ON permission_record.id = mapping.permission_id
WHERE role_record.name = 'Tenant'
  AND permission_record.name IN (
    'view_property_list',
    'view_unit_list',
    'view_lease_template',
    'create_lease_template',
    'edit_lease_template',
    'delete_lease_template',
    'list_lease_template'
  );
