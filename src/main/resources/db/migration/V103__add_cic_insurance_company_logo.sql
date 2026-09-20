UPDATE pms_insurance_company
SET logo_url = '/insurance/brands/cic-group.webp'
WHERE code = 'CIC'
  AND (logo_url IS NULL OR TRIM(logo_url) = '');
