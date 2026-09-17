CREATE TABLE pms_insurance_guest_access (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), challenge_id VARCHAR(36) NOT NULL,
 full_name VARCHAR(160) NOT NULL, email VARCHAR(254) NOT NULL, phone VARCHAR(40) NOT NULL,
 otp_hash VARCHAR(60) NOT NULL, otp_expires_at DATETIME(6) NOT NULL, otp_attempts INT NOT NULL DEFAULT 0,
 verified_at DATETIME(6), access_token_hash CHAR(64), encrypted_access_token LONGBLOB,
 access_expires_at DATETIME(6), case_id BIGINT, claimed_at DATETIME(6), claimed_by_user_id BIGINT,
 PRIMARY KEY(id), UNIQUE KEY uk_insurance_guest_uuid(uuid),
 UNIQUE KEY uk_insurance_guest_challenge(challenge_id), UNIQUE KEY uk_insurance_guest_token(access_token_hash),
 KEY idx_insurance_guest_email(email,active,created_on), KEY idx_insurance_guest_expiry(access_expires_at,active)
);

ALTER TABLE pms_insurance_case ADD COLUMN guest_access_id BIGINT NULL AFTER customer_user_id,
 ADD KEY idx_insurance_case_guest(guest_access_id,active,created_on),
 ADD CONSTRAINT fk_insurance_case_guest FOREIGN KEY(guest_access_id) REFERENCES pms_insurance_guest_access(id);

ALTER TABLE pms_insurance_guest_access ADD CONSTRAINT fk_insurance_guest_case
 FOREIGN KEY(case_id) REFERENCES pms_insurance_case(id);
