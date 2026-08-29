ALTER TABLE pms_payment_account MODIFY COLUMN category VARCHAR(40) NOT NULL;

CREATE TABLE pms_insurance_company (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), code VARCHAR(50) NOT NULL, name VARCHAR(160) NOT NULL,
 logo_url VARCHAR(800), description VARCHAR(1000), quotation_email VARCHAR(254), claims_email VARCHAR(254),
 renewals_email VARCHAR(254), PRIMARY KEY(id),
 UNIQUE KEY uk_insurance_company_uuid(uuid), UNIQUE KEY uk_insurance_company_code(code),
 KEY idx_insurance_company_active_name(active,name)
);

CREATE TABLE pms_insurance_payment_configuration (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), company_id BIGINT NOT NULL, payment_account_id BIGINT NOT NULL,
 payment_channel VARCHAR(30) NOT NULL, label VARCHAR(120) NOT NULL, instructions VARCHAR(1500) NOT NULL,
 reference_template VARCHAR(240), version INT NOT NULL, effective_from DATE NOT NULL, effective_to DATE,
 PRIMARY KEY(id), UNIQUE KEY uk_insurance_payment_uuid(uuid),
 UNIQUE KEY uk_insurance_payment_version(company_id,payment_channel,version),
 KEY idx_insurance_payment_company(company_id,active), KEY idx_insurance_payment_account(payment_account_id),
 CONSTRAINT fk_insurance_payment_company FOREIGN KEY(company_id) REFERENCES pms_insurance_company(id),
 CONSTRAINT fk_insurance_payment_account FOREIGN KEY(payment_account_id) REFERENCES pms_payment_account(id),
 CONSTRAINT chk_insurance_payment_version CHECK(version > 0),
 CONSTRAINT chk_insurance_payment_dates CHECK(effective_to IS NULL OR effective_to >= effective_from)
);

INSERT INTO pms_insurance_company
(uuid,created_on,active,created_by,last_modified_date,code,name,logo_url,description) VALUES
(UUID_TO_BIN(UUID()),NOW(6),1,NULL,NOW(6),'OLD_MUTUAL','Old Mutual',NULL,'Insurance partner available through Silverwood Insurance Agency.'),
(UUID_TO_BIN(UUID()),NOW(6),1,NULL,NOW(6),'BRITAM','Britam',NULL,'Insurance partner available through Silverwood Insurance Agency.'),
(UUID_TO_BIN(UUID()),NOW(6),1,NULL,NOW(6),'CIC','CIC Insurance',NULL,'Insurance partner available through Silverwood Insurance Agency.'),
(UUID_TO_BIN(UUID()),NOW(6),1,NULL,NOW(6),'ICEA_LION','ICEA LION',NULL,'Insurance partner available through Silverwood Insurance Agency.'),
(UUID_TO_BIN(UUID()),NOW(6),1,NULL,NOW(6),'APA','APA Insurance',NULL,'Insurance partner available through Silverwood Insurance Agency.');

CREATE TABLE pms_insurance_email_exchange (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), company_id BIGINT NOT NULL, case_reference VARCHAR(80) NOT NULL,
 correlation_id VARCHAR(36) NOT NULL, message_type VARCHAR(30) NOT NULL, direction VARCHAR(12) NOT NULL,
 status VARCHAR(24) NOT NULL, sender_address VARCHAR(254) NOT NULL, recipient_address VARCHAR(254) NOT NULL,
 subject VARCHAR(400) NOT NULL, encrypted_body LONGBLOB NOT NULL, body_hash VARCHAR(64) NOT NULL,
 external_message_id VARCHAR(500), in_reply_to VARCHAR(500), sent_at DATETIME(6), received_at DATETIME(6),
 last_error VARCHAR(1000), PRIMARY KEY(id), UNIQUE KEY uk_insurance_email_uuid(uuid),
 UNIQUE KEY uk_insurance_email_correlation(correlation_id), KEY idx_insurance_email_case(case_reference,created_on),
 KEY idx_insurance_email_status(status,created_on),
 CONSTRAINT fk_insurance_email_company FOREIGN KEY(company_id) REFERENCES pms_insurance_company(id)
);
