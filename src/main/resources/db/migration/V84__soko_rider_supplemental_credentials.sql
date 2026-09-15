-- Keep rider-specific evidence separate from approved common KYC and existing account privileges.
CREATE TABLE pms_soko_rider_credential (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), user_id BIGINT NOT NULL,
 document_type VARCHAR(80) NOT NULL, file_ref VARCHAR(255) NOT NULL, content_type VARCHAR(80) NOT NULL,
 sha256 VARCHAR(64) NOT NULL, status VARCHAR(30) NOT NULL, expires_at DATETIME(6),
 reviewed_at DATETIME(6), reviewed_by BIGINT, review_notes VARCHAR(1000),
 PRIMARY KEY(id), UNIQUE KEY uk_soko_rider_credential_uuid(uuid),
 KEY idx_soko_rider_credential_user(user_id,status,active),
 CONSTRAINT fk_soko_rider_credential_user FOREIGN KEY(user_id) REFERENCES pms_users(id)
);
