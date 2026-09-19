UPDATE pms_tax_assist_configuration
SET estimates_enabled = b'1',
    updated_at = NOW(6)
WHERE id = 1;
