ALTER TABLE pms_insurance_case
 ADD COLUMN proposal_data_json LONGTEXT NULL AFTER risk_details;

UPDATE pms_insurance_agency
SET support_email='info@silverwoodinsurance.com'
WHERE code='SILVERWOOD';

-- Existing ALL_RISKS rows remain unchanged for audit compatibility. New
-- applications use CONTRACTORS_ALL_RISK and the corrected display name.
