CREATE TABLE pms_insurance_agency (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), code VARCHAR(40) NOT NULL, name VARCHAR(160) NOT NULL,
 support_email VARCHAR(254), support_phone VARCHAR(40), PRIMARY KEY(id),
 UNIQUE KEY uk_insurance_agency_uuid(uuid), UNIQUE KEY uk_insurance_agency_code(code)
);

INSERT INTO pms_insurance_agency
(uuid,created_on,active,created_by,last_modified_date,code,name,support_email)
VALUES(UUID_TO_BIN(UUID()),NOW(6),1,NULL,NOW(6),'SILVERWOOD','Silverwood Insurance Agency','info@silverwoodinsurance.com');

ALTER TABLE pms_insurance_company ADD COLUMN agency_id BIGINT NULL AFTER id;
UPDATE pms_insurance_company SET agency_id=(SELECT id FROM pms_insurance_agency WHERE code='SILVERWOOD');
ALTER TABLE pms_insurance_company MODIFY agency_id BIGINT NOT NULL,
 ADD KEY idx_insurance_company_agency(agency_id,active,name),
 ADD CONSTRAINT fk_insurance_company_agency FOREIGN KEY(agency_id) REFERENCES pms_insurance_agency(id);

ALTER TABLE pms_insurance_email_exchange
 ADD UNIQUE KEY uk_insurance_email_external(company_id,external_message_id);

CREATE TABLE pms_insurance_case (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), agency_id BIGINT NOT NULL, customer_user_id BIGINT NOT NULL,
 reference VARCHAR(32) NOT NULL, product_code VARCHAR(40) NOT NULL, status VARCHAR(32) NOT NULL,
 full_name VARCHAR(160) NOT NULL, email VARCHAR(254) NOT NULL, phone VARCHAR(40) NOT NULL,
 subject_type VARCHAR(32) NOT NULL, subject_description VARCHAR(1000) NOT NULL, sum_insured DECIMAL(19,2),
 currency VARCHAR(3) NOT NULL, cover_start_date DATE, risk_details TEXT, consent_at DATETIME(6) NOT NULL,
 assigned_adviser_id BIGINT, submitted_at DATETIME(6), quoted_at DATETIME(6), selected_at DATETIME(6), payment_reminder_sent_at DATETIME(6), selected_quote_id BIGINT,
 version BIGINT NOT NULL DEFAULT 0, PRIMARY KEY(id), UNIQUE KEY uk_insurance_case_uuid(uuid),
 UNIQUE KEY uk_insurance_case_reference(reference),
 KEY idx_insurance_case_customer(customer_user_id,active,created_on),
 KEY idx_insurance_case_queue(agency_id,status,assigned_adviser_id,created_on),
 CONSTRAINT fk_insurance_case_agency FOREIGN KEY(agency_id) REFERENCES pms_insurance_agency(id),
 CONSTRAINT chk_insurance_case_sum CHECK(sum_insured IS NULL OR sum_insured > 0)
);

CREATE TABLE pms_insurance_quote (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), case_id BIGINT NOT NULL, company_id BIGINT NOT NULL,
 quote_number VARCHAR(80), status VARCHAR(24) NOT NULL, currency VARCHAR(3) NOT NULL,
 base_premium DECIMAL(19,2) NOT NULL, taxes_levies DECIMAL(19,2) NOT NULL DEFAULT 0,
 total_premium DECIMAL(19,2) NOT NULL, excess_details VARCHAR(1000), coverage_summary TEXT,
 exclusions TEXT, valid_until DATE NOT NULL, prepared_by BIGINT NOT NULL, approved_by BIGINT, approved_at DATETIME(6),
 version BIGINT NOT NULL DEFAULT 0, PRIMARY KEY(id), UNIQUE KEY uk_insurance_quote_uuid(uuid),
 UNIQUE KEY uk_insurance_quote_case_company_number(case_id,company_id,quote_number),
 KEY idx_insurance_quote_case(case_id,status,valid_until), KEY idx_insurance_quote_company(company_id),
 CONSTRAINT fk_insurance_quote_case FOREIGN KEY(case_id) REFERENCES pms_insurance_case(id),
 CONSTRAINT fk_insurance_quote_company FOREIGN KEY(company_id) REFERENCES pms_insurance_company(id),
 CONSTRAINT chk_insurance_quote_amounts CHECK(base_premium >= 0 AND taxes_levies >= 0 AND total_premium > 0)
);

ALTER TABLE pms_insurance_case ADD CONSTRAINT fk_insurance_case_selected_quote
 FOREIGN KEY(selected_quote_id) REFERENCES pms_insurance_quote(id);

CREATE TABLE pms_insurance_premium_payment (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), case_id BIGINT NOT NULL, quote_id BIGINT NOT NULL,
 amount DECIMAL(19,2) NOT NULL, currency VARCHAR(3) NOT NULL, payment_reference VARCHAR(120) NOT NULL,
 paid_at DATETIME(6) NOT NULL, status VARCHAR(24) NOT NULL, proof_file_ref VARCHAR(800), proof_content_type VARCHAR(120),
 proof_file_size BIGINT, proof_checksum VARCHAR(64), verified_by BIGINT, verified_at DATETIME(6),
 remittance_reference VARCHAR(120), remitted_at DATETIME(6), rejection_reason VARCHAR(500),
 version BIGINT NOT NULL DEFAULT 0, PRIMARY KEY(id), UNIQUE KEY uk_insurance_premium_uuid(uuid),
 KEY idx_insurance_premium_case(case_id,status), UNIQUE KEY uk_insurance_premium_case_reference(case_id,payment_reference),
 CONSTRAINT fk_insurance_premium_case FOREIGN KEY(case_id) REFERENCES pms_insurance_case(id),
 CONSTRAINT fk_insurance_premium_quote FOREIGN KEY(quote_id) REFERENCES pms_insurance_quote(id),
 CONSTRAINT chk_insurance_premium_amount CHECK(amount > 0)
);

CREATE TABLE pms_insurance_policy (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), case_id BIGINT NOT NULL, quote_id BIGINT NOT NULL,
 company_id BIGINT NOT NULL, customer_user_id BIGINT NOT NULL, policy_number VARCHAR(120) NOT NULL,
 status VARCHAR(24) NOT NULL, start_date DATE NOT NULL, end_date DATE NOT NULL,
 renewal_status VARCHAR(24) NOT NULL, renewal_contacted_at DATETIME(6), renewal_reminder_sent_at DATETIME(6), issued_by BIGINT NOT NULL,
 issued_at DATETIME(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0, PRIMARY KEY(id),
 UNIQUE KEY uk_insurance_policy_uuid(uuid), UNIQUE KEY uk_insurance_policy_number(policy_number),
 KEY idx_insurance_policy_customer(customer_user_id,active,end_date),
 KEY idx_insurance_policy_renewal(status,renewal_status,end_date),
 CONSTRAINT fk_insurance_policy_case FOREIGN KEY(case_id) REFERENCES pms_insurance_case(id),
 CONSTRAINT fk_insurance_policy_quote FOREIGN KEY(quote_id) REFERENCES pms_insurance_quote(id),
 CONSTRAINT fk_insurance_policy_company FOREIGN KEY(company_id) REFERENCES pms_insurance_company(id),
 CONSTRAINT chk_insurance_policy_dates CHECK(end_date >= start_date)
);

CREATE TABLE pms_insurance_claim (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), policy_id BIGINT NOT NULL, customer_user_id BIGINT NOT NULL,
 reference VARCHAR(32) NOT NULL, status VARCHAR(24) NOT NULL, incident_at DATETIME(6) NOT NULL,
 incident_location VARCHAR(300), description TEXT NOT NULL, estimated_amount DECIMAL(19,2),
 assigned_adviser_id BIGINT, insurer_reference VARCHAR(120), resolution_notes TEXT, submitted_at DATETIME(6),
 closed_at DATETIME(6), version BIGINT NOT NULL DEFAULT 0, PRIMARY KEY(id),
 UNIQUE KEY uk_insurance_claim_uuid(uuid), UNIQUE KEY uk_insurance_claim_reference(reference),
 KEY idx_insurance_claim_customer(customer_user_id,active,created_on),
 KEY idx_insurance_claim_queue(status,assigned_adviser_id,created_on),
 CONSTRAINT fk_insurance_claim_policy FOREIGN KEY(policy_id) REFERENCES pms_insurance_policy(id),
 CONSTRAINT chk_insurance_claim_amount CHECK(estimated_amount IS NULL OR estimated_amount >= 0)
);

CREATE TABLE pms_insurance_document (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), customer_user_id BIGINT NOT NULL,
 case_id BIGINT, policy_id BIGINT, claim_id BIGINT, category VARCHAR(40) NOT NULL,
 display_name VARCHAR(255) NOT NULL, file_ref VARCHAR(800) NOT NULL, content_type VARCHAR(120) NOT NULL,
 file_size BIGINT NOT NULL, checksum_sha256 VARCHAR(64) NOT NULL, uploaded_by BIGINT NOT NULL,
 version_number INT NOT NULL DEFAULT 1, PRIMARY KEY(id), UNIQUE KEY uk_insurance_document_uuid(uuid),
 KEY idx_insurance_document_case(case_id,active,created_on),
 KEY idx_insurance_document_policy(policy_id,active,created_on),
 KEY idx_insurance_document_claim(claim_id,active,created_on),
 KEY idx_insurance_document_owner(customer_user_id,active,created_on),
 CONSTRAINT fk_insurance_document_case FOREIGN KEY(case_id) REFERENCES pms_insurance_case(id),
 CONSTRAINT fk_insurance_document_policy FOREIGN KEY(policy_id) REFERENCES pms_insurance_policy(id),
 CONSTRAINT fk_insurance_document_claim FOREIGN KEY(claim_id) REFERENCES pms_insurance_claim(id),
 CONSTRAINT chk_insurance_document_parent CHECK((case_id IS NOT NULL) + (policy_id IS NOT NULL) + (claim_id IS NOT NULL) = 1)
);

CREATE TABLE pms_insurance_activity (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), case_id BIGINT, claim_id BIGINT,
 event_type VARCHAR(40) NOT NULL, from_status VARCHAR(32), to_status VARCHAR(32), note VARCHAR(1000), actor_user_id BIGINT NOT NULL,
 PRIMARY KEY(id), UNIQUE KEY uk_insurance_activity_uuid(uuid),
 KEY idx_insurance_activity_case(case_id,created_on), KEY idx_insurance_activity_claim(claim_id,created_on),
 CONSTRAINT fk_insurance_activity_case FOREIGN KEY(case_id) REFERENCES pms_insurance_case(id),
 CONSTRAINT fk_insurance_activity_claim FOREIGN KEY(claim_id) REFERENCES pms_insurance_claim(id)
);
