ALTER TABLE pms_kyc_matrix_requirement
 ADD COLUMN condition_rule VARCHAR(50) NULL AFTER condition_description,
 ADD COLUMN condition_value VARCHAR(120) NULL AFTER condition_rule;

UPDATE pms_kyc_matrix_requirement
SET condition_rule='NON_PASSPORT_IDENTITY', condition_value=NULL
WHERE obligation='CONDITIONAL' AND requirement_code='IDENTITY_BACK';

ALTER TABLE pms_soko_order
 ADD COLUMN delivery_recovery_otp VARBINARY(1024) NULL,
 ADD COLUMN delivery_recovery_otp_expires_at DATETIME(6) NULL,
 ADD COLUMN delivery_recovery_requested_at DATETIME(6) NULL,
 ADD COLUMN delivery_recovery_window_started_at DATETIME(6) NULL,
 ADD COLUMN delivery_recovery_request_count INT NOT NULL DEFAULT 0,
 ADD COLUMN delivery_recovery_otp_attempts INT NOT NULL DEFAULT 0,
 ADD COLUMN delivery_recovery_completed_at DATETIME(6) NULL,
 ADD COLUMN delivery_recovery_requested_by BIGINT NULL,
 ADD COLUMN delivery_recovery_support_reason VARCHAR(1000) NULL,
 ADD KEY idx_soko_delivery_recovery(delivery_recovery_otp_expires_at,delivery_recovery_request_count);

-- Soko is a grocery marketplace. Retain legacy products for audit/display, but only
-- the controlled grocery catalogue is active in the KYC matrix for new publishing.
UPDATE pms_kyc_matrix_requirement
SET active=0
WHERE scope_type='SOKO_CATEGORY'
  AND scope_key NOT IN ('FRESH_PRODUCE','MEAT_POULTRY_SEAFOOD','DAIRY_EGGS','BAKERY','PANTRY_STAPLES',
                        'NON_ALCOHOLIC_BEVERAGES','SNACKS_CONFECTIONERY','FROZEN_FOODS',
                        'BREAKFAST_CEREALS','COOKING_OILS_SPICES');

INSERT IGNORE INTO pms_kyc_matrix_requirement
 (uuid,created_on,active,created_by,last_modified_date,release_id,scope_type,scope_key,scope_label,
  requirement_code,requirement_label,obligation,profile_scope,accepted_document_types,condition_description,
  condition_rule,condition_value,validity_days,renewal_lead_days)
SELECT UUID_TO_BIN(UUID()),UTC_TIMESTAMP(6),1,NULL,UTC_TIMESTAMP(6),r.id,'SOKO_CATEGORY',c.scope_key,c.scope_label,
       CONCAT('SOKO_',c.scope_key),'Common merchant KYC','OPTIONAL','BOTH',
       'BUSINESS_REGISTRATION_CERTIFICATE,PROFESSIONAL_CERTIFICATE',
       'Grocery products use approved common merchant KYC; no extra category document is required.',NULL,NULL,NULL,30
FROM pms_kyc_matrix_release r
JOIN (
 SELECT 'FRESH_PRODUCE' scope_key,'Fresh produce' scope_label UNION ALL
 SELECT 'MEAT_POULTRY_SEAFOOD','Meat, poultry & seafood' UNION ALL
 SELECT 'DAIRY_EGGS','Dairy & eggs' UNION ALL SELECT 'BAKERY','Bakery' UNION ALL
 SELECT 'PANTRY_STAPLES','Pantry staples' UNION ALL
 SELECT 'NON_ALCOHOLIC_BEVERAGES','Non-alcoholic beverages' UNION ALL
 SELECT 'SNACKS_CONFECTIONERY','Snacks & confectionery' UNION ALL
 SELECT 'FROZEN_FOODS','Frozen foods' UNION ALL
 SELECT 'BREAKFAST_CEREALS','Breakfast & cereals' UNION ALL
 SELECT 'COOKING_OILS_SPICES','Cooking oils & spices'
) c
WHERE r.active=1 AND r.status IN ('PUBLISHED','DRAFT');
