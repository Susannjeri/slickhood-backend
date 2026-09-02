CREATE TABLE pms_insurance_renewal_offer (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL, created_by BIGINT, last_modified_date DATETIME(6),
 policy_id BIGINT NOT NULL, quote_number VARCHAR(80) NOT NULL, currency VARCHAR(3) NOT NULL, base_premium DECIMAL(19,2) NOT NULL, taxes_levies DECIMAL(19,2) NOT NULL,
 total_premium DECIMAL(19,2) NOT NULL, coverage_summary TEXT NOT NULL, exclusions TEXT, valid_until DATE NOT NULL, cover_start_date DATE NOT NULL, cover_end_date DATE NOT NULL,
 status VARCHAR(24) NOT NULL, approved_by BIGINT, approved_at DATETIME(6), accepted_at DATETIME(6), completed_at DATETIME(6), version BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY(id), UNIQUE KEY uk_insurance_renewal_offer_uuid(uuid), UNIQUE KEY uk_insurance_renewal_offer_quote(policy_id,quote_number),
 KEY idx_insurance_renewal_offer_policy(policy_id,active,created_on), CONSTRAINT fk_insurance_renewal_offer_policy FOREIGN KEY(policy_id) REFERENCES pms_insurance_policy(id),
 CONSTRAINT chk_insurance_renewal_offer_amounts CHECK(base_premium >= 0 AND taxes_levies >= 0 AND total_premium > 0)
);
CREATE TABLE pms_insurance_renewal_payment (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL, created_by BIGINT, last_modified_date DATETIME(6),
 policy_id BIGINT NOT NULL, renewal_offer_id BIGINT NOT NULL, payment_configuration_id BIGINT NOT NULL, amount DECIMAL(19,2) NOT NULL, currency VARCHAR(3) NOT NULL,
 payment_reference VARCHAR(120) NOT NULL, paid_at DATETIME(6) NOT NULL, status VARCHAR(24) NOT NULL, proof_file_ref VARCHAR(800), proof_content_type VARCHAR(120),
 proof_file_size BIGINT, proof_checksum VARCHAR(64), verified_by BIGINT, verified_at DATETIME(6), remittance_reference VARCHAR(120), remitted_at DATETIME(6), rejection_reason VARCHAR(500), version BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY(id), UNIQUE KEY uk_insurance_renewal_payment_uuid(uuid), UNIQUE KEY uk_insurance_renewal_payment_ref(policy_id,payment_reference),
 KEY idx_insurance_renewal_payment_offer(renewal_offer_id,status), KEY idx_insurance_renewal_payment_config(payment_configuration_id),
 CONSTRAINT fk_insurance_renewal_payment_policy FOREIGN KEY(policy_id) REFERENCES pms_insurance_policy(id), CONSTRAINT fk_insurance_renewal_payment_offer FOREIGN KEY(renewal_offer_id) REFERENCES pms_insurance_renewal_offer(id),
 CONSTRAINT fk_insurance_renewal_payment_config FOREIGN KEY(payment_configuration_id) REFERENCES pms_insurance_payment_configuration(id), CONSTRAINT chk_insurance_renewal_payment_amount CHECK(amount > 0)
);
CREATE TABLE pms_insurance_renewal_reminder (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL, created_by BIGINT, last_modified_date DATETIME(6),
 policy_id BIGINT NOT NULL, policy_end_date DATE NOT NULL, reminder_days INT NOT NULL, queued_at DATETIME(6) NOT NULL, PRIMARY KEY(id), UNIQUE KEY uk_insurance_renewal_reminder_uuid(uuid),
 UNIQUE KEY uk_insurance_renewal_reminder_stage(policy_id,policy_end_date,reminder_days), KEY idx_insurance_renewal_reminder_policy(policy_id,queued_at),
 CONSTRAINT fk_insurance_renewal_reminder_policy FOREIGN KEY(policy_id) REFERENCES pms_insurance_policy(id)
);
