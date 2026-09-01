ALTER TABLE pms_insurance_agency ADD COLUMN logo_url VARCHAR(800) NULL AFTER support_phone;

UPDATE pms_insurance_agency
SET logo_url='/insurance/brands/silverwood.webp'
WHERE code='SILVERWOOD' AND (logo_url IS NULL OR TRIM(logo_url)='');

UPDATE pms_insurance_company
SET logo_url=CASE code
    WHEN 'OLD_MUTUAL' THEN '/insurance/brands/old-mutual.webp'
    WHEN 'BRITAM' THEN '/insurance/brands/britam.webp'
    WHEN 'APA' THEN '/insurance/brands/apa.webp'
    WHEN 'ICEA_LION' THEN '/insurance/brands/icea-lion.webp'
    ELSE logo_url
END
WHERE code IN ('OLD_MUTUAL','BRITAM','APA','ICEA_LION')
  AND (logo_url IS NULL OR TRIM(logo_url)='');

INSERT INTO pms_insurance_company
(uuid,created_on,active,created_by,last_modified_date,agency_id,code,name,logo_url,description)
SELECT UUID_TO_BIN(UUID()),NOW(6),1,NULL,NOW(6),a.id,'PIONEER','Pioneer Insurance',
       '/insurance/brands/pioneer.webp','Insurance partner available through Silverwood Insurance Agency.'
FROM pms_insurance_agency a
WHERE a.code='SILVERWOOD'
  AND NOT EXISTS (SELECT 1 FROM pms_insurance_company c WHERE c.code='PIONEER');
