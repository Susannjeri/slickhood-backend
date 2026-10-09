-- These customer roles can be either the billed party or the payee on
-- subscription, marketplace, or property-sale invoices. The invoice and
-- payment queries remain participant-scoped; this grants read access only.
INSERT INTO pms_role_mapping (uuid, created_on, role_id, permission_id)
SELECT UUID_TO_BIN(UUID()), UTC_TIMESTAMP(6), role_record.id, permission_record.id
FROM pms_role role_record
JOIN pms_permission permission_record
  ON permission_record.name IN ('view_invoice_list', 'view_invoice_pdf', 'view_payment_list')
WHERE role_record.name IN ('ServiceProvider', 'SalesAgent', 'AssetPortfolioManager', 'Buyer')
  AND NOT EXISTS (
    SELECT 1
    FROM pms_role_mapping existing_mapping
    WHERE existing_mapping.role_id = role_record.id
      AND existing_mapping.permission_id = permission_record.id
  );
