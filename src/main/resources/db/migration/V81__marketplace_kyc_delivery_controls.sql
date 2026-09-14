CREATE TABLE pms_kyc_matrix_release (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), version_no INT NOT NULL, status VARCHAR(20) NOT NULL,
 change_summary VARCHAR(1000), published_at DATETIME(6), published_by BIGINT,
 PRIMARY KEY(id), UNIQUE KEY uk_kyc_matrix_release_uuid(uuid), UNIQUE KEY uk_kyc_matrix_release_version(version_no),
 KEY idx_kyc_matrix_release_status(status,active)
);

CREATE TABLE pms_kyc_matrix_requirement (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), release_id BIGINT NOT NULL,
 scope_type VARCHAR(30) NOT NULL, scope_key VARCHAR(120) NOT NULL, scope_label VARCHAR(180) NOT NULL,
 requirement_code VARCHAR(80) NOT NULL, requirement_label VARCHAR(180) NOT NULL,
 obligation VARCHAR(20) NOT NULL, profile_scope VARCHAR(20) NOT NULL,
 accepted_document_types VARCHAR(1000) NOT NULL, condition_description VARCHAR(1000),
 validity_days INT, renewal_lead_days INT,
 PRIMARY KEY(id), UNIQUE KEY uk_kyc_matrix_requirement_uuid(uuid),
 UNIQUE KEY uk_kyc_matrix_requirement_version(release_id,scope_type,scope_key,requirement_code),
 KEY idx_kyc_matrix_requirement_lookup(release_id,scope_type,scope_key,active),
 CONSTRAINT fk_kyc_matrix_requirement_release FOREIGN KEY(release_id) REFERENCES pms_kyc_matrix_release(id)
);

INSERT INTO pms_kyc_matrix_release
 (uuid,created_on,active,created_by,last_modified_date,version_no,status,change_summary,published_at,published_by)
VALUES (UUID_TO_BIN(UUID()),UTC_TIMESTAMP(6),1,NULL,UTC_TIMESTAMP(6),1,'PUBLISHED',
 'Initial matrix generated from SlickHood common KYC and active marketplace catalogues.',UTC_TIMESTAMP(6),NULL);

SET @kyc_release_id=LAST_INSERT_ID();

INSERT INTO pms_kyc_matrix_requirement
 (uuid,created_on,active,created_by,last_modified_date,release_id,scope_type,scope_key,scope_label,
  requirement_code,requirement_label,obligation,profile_scope,accepted_document_types,condition_description,validity_days,renewal_lead_days)
VALUES
 (UUID_TO_BIN(UUID()),UTC_TIMESTAMP(6),1,NULL,UTC_TIMESTAMP(6),@kyc_release_id,'COMMON','PERSONAL','Common personal KYC','IDENTITY_FRONT','Government identity document','MANDATORY','INDIVIDUAL','NATIONAL_ID_FRONT,PASSPORT,ALIEN_ID_FRONT',NULL,NULL,30),
 (UUID_TO_BIN(UUID()),UTC_TIMESTAMP(6),1,NULL,UTC_TIMESTAMP(6),@kyc_release_id,'COMMON','PERSONAL','Common personal KYC','IDENTITY_BACK','Back of identity document (not required for passports)','CONDITIONAL','INDIVIDUAL','NATIONAL_ID_BACK,ALIEN_ID_BACK','Required when the selected identity document has a reverse side.',NULL,30),
 (UUID_TO_BIN(UUID()),UTC_TIMESTAMP(6),1,NULL,UTC_TIMESTAMP(6),@kyc_release_id,'COMMON','PERSONAL','Common personal KYC','SELFIE','Live selfie','MANDATORY','INDIVIDUAL','SELFIE',NULL,NULL,NULL),
 (UUID_TO_BIN(UUID()),UTC_TIMESTAMP(6),1,NULL,UTC_TIMESTAMP(6),@kyc_release_id,'COMMON','BUSINESS','Common business KYC','ORGANIZATION_REGISTRATION','Company or organisation registration certificate','MANDATORY','COMPANY','BUSINESS_REGISTRATION_CERTIFICATE,CR12',NULL,NULL,30),
 (UUID_TO_BIN(UUID()),UTC_TIMESTAMP(6),1,NULL,UTC_TIMESTAMP(6),@kyc_release_id,'PROVIDER_TYPE','SERVICE_PROVIDER','Service provider','PROFESSIONAL','Professional or business certificate','MANDATORY','BOTH','PROFESSIONAL_CERTIFICATE,BUSINESS_REGISTRATION_CERTIFICATE',NULL,NULL,30),
 (UUID_TO_BIN(UUID()),UTC_TIMESTAMP(6),1,NULL,UTC_TIMESTAMP(6),@kyc_release_id,'PROVIDER_TYPE','DELIVERY_RIDER','Delivery rider','GOOD_CONDUCT','Certificate of good conduct','MANDATORY','INDIVIDUAL','GOOD_CONDUCT_CERTIFICATE','Required before a rider can be verified and assigned deliveries.',365,30),
 (UUID_TO_BIN(UUID()),UTC_TIMESTAMP(6),1,NULL,UTC_TIMESTAMP(6),@kyc_release_id,'PROVIDER_TYPE','SOKO_MERCHANT','Soko merchant','MERCHANT_IDENTITY','Common identity or business registration evidence','MANDATORY','BOTH','NATIONAL_ID_FRONT,PASSPORT,ALIEN_ID_FRONT,BUSINESS_REGISTRATION_CERTIFICATE,CR12','Existing approved common KYC evidence is reused.',NULL,30);

INSERT INTO pms_kyc_matrix_requirement
 (uuid,created_on,active,created_by,last_modified_date,release_id,scope_type,scope_key,scope_label,
  requirement_code,requirement_label,obligation,profile_scope,accepted_document_types,condition_description,validity_days,renewal_lead_days)
SELECT UUID_TO_BIN(UUID()),UTC_TIMESTAMP(6),1,NULL,UTC_TIMESTAMP(6),@kyc_release_id,'SERVICE_CATEGORY',CAST(c.id AS CHAR),c.name,
       CONCAT('SERVICE_CATEGORY_',c.id),'Category-specific supporting evidence','MANDATORY','BOTH',
       GROUP_CONCAT(d.document_type ORDER BY d.document_type SEPARATOR ','),
       'This evidence is requested once and reused for all services in this category.',NULL,30
FROM pms_sp_category c
JOIN pms_sp_category_doc_types d ON d.category_id=c.id
WHERE c.active=1
GROUP BY c.id,c.name;

INSERT INTO pms_kyc_matrix_requirement
 (uuid,created_on,active,created_by,last_modified_date,release_id,scope_type,scope_key,scope_label,
  requirement_code,requirement_label,obligation,profile_scope,accepted_document_types,condition_description,validity_days,renewal_lead_days)
SELECT UUID_TO_BIN(UUID()),UTC_TIMESTAMP(6),1,NULL,UTC_TIMESTAMP(6),@kyc_release_id,'SOKO_CATEGORY',
       UPPER(REPLACE(category,' ','_')),category,CONCAT('SOKO_',UPPER(REPLACE(category,' ','_'))),
       'Common merchant KYC','OPTIONAL','BOTH','BUSINESS_REGISTRATION_CERTIFICATE,PROFESSIONAL_CERTIFICATE',
       'No extra document is required by default. Superadmin should make regulated-category evidence mandatory where applicable.',NULL,30
FROM (
 SELECT DISTINCT TRIM(category) AS category
 FROM pms_soko_product
 WHERE active=1 AND category IS NOT NULL AND TRIM(category)<>''
) existing_soko_categories;

INSERT INTO pms_kyc_matrix_release
 (uuid,created_on,active,created_by,last_modified_date,version_no,status,change_summary)
VALUES (UUID_TO_BIN(UUID()),UTC_TIMESTAMP(6),1,NULL,UTC_TIMESTAMP(6),2,'DRAFT','Working copy of version 1.');
SET @kyc_draft_id=LAST_INSERT_ID();
INSERT INTO pms_kyc_matrix_requirement
 (uuid,created_on,active,created_by,last_modified_date,release_id,scope_type,scope_key,scope_label,
  requirement_code,requirement_label,obligation,profile_scope,accepted_document_types,condition_description,validity_days,renewal_lead_days)
SELECT UUID_TO_BIN(UUID()),UTC_TIMESTAMP(6),active,NULL,UTC_TIMESTAMP(6),@kyc_draft_id,scope_type,scope_key,scope_label,
       requirement_code,requirement_label,obligation,profile_scope,accepted_document_types,condition_description,validity_days,renewal_lead_days
FROM pms_kyc_matrix_requirement WHERE release_id=@kyc_release_id;

ALTER TABLE pms_soko_product
 ADD COLUMN variations_json TEXT NULL;

CREATE TABLE pms_soko_product_variation (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), product_id BIGINT NOT NULL,
 name VARCHAR(80) NOT NULL, value VARCHAR(120) NOT NULL, price_adjustment DECIMAL(19,2) NOT NULL DEFAULT 0,
 stock_quantity INT NOT NULL DEFAULT 0,
 PRIMARY KEY(id), UNIQUE KEY uk_soko_product_variation_uuid(uuid),
 KEY idx_soko_product_variation_product(product_id,active),
 CONSTRAINT fk_soko_product_variation_product FOREIGN KEY(product_id) REFERENCES pms_soko_product(id)
);

ALTER TABLE pms_soko_order_item
 ADD COLUMN variation_id BIGINT NULL,
 ADD COLUMN variation_name VARCHAR(80) NULL,
 ADD COLUMN variation_value VARCHAR(120) NULL;

ALTER TABLE pms_soko_rider
 ADD COLUMN user_id BIGINT NULL,
 ADD COLUMN verification_status VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFICATION',
 ADD COLUMN verified_at DATETIME(6) NULL,
 ADD COLUMN verified_by_user_id BIGINT NULL,
 ADD COLUMN verification_notes VARCHAR(1000) NULL,
 ADD KEY idx_soko_rider_user(user_id,active),
 ADD KEY idx_soko_rider_verification(verification_status,active);

UPDATE pms_soko_rider SET status='PENDING_VERIFICATION',availability='OFFLINE',verified=0
WHERE active=1 AND verified=0;

ALTER TABLE pms_soko_order
 ADD COLUMN assigned_at DATETIME(6) NULL,
 ADD COLUMN assignment_accepted_at DATETIME(6) NULL,
 ADD COLUMN collected_at DATETIME(6) NULL,
 ADD COLUMN delivery_failed_at DATETIME(6) NULL,
 ADD COLUMN returned_at DATETIME(6) NULL,
 ADD COLUMN delivery_exception_reason VARCHAR(1000) NULL,
 ADD COLUMN delivery_code_expires_at DATETIME(6) NULL,
 ADD COLUMN delivery_code_locked_at DATETIME(6) NULL,
 ADD COLUMN delivery_code_reissued_at DATETIME(6) NULL,
 ADD COLUMN delivery_code_reissued_by BIGINT NULL,
 ADD COLUMN delivery_code_reissue_reason VARCHAR(1000) NULL,
 ADD KEY idx_soko_order_rider_state(rider_id,status,active),
 ADD KEY idx_soko_delivery_code_expiry(status,delivery_code_expires_at);
