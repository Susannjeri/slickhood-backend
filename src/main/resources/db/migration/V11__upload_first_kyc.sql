ALTER TABLE pms_users ADD COLUMN phone_verified BIT NOT NULL DEFAULT 0;

CREATE TABLE pms_kyc_case (
    id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
    user_id BIGINT NOT NULL, status VARCHAR(30) NOT NULL, consent_version VARCHAR(40) NOT NULL,
    consent_at DATETIME(6) NOT NULL, phone_verified BIT NOT NULL DEFAULT 0,
    registry_status VARCHAR(30) NOT NULL DEFAULT 'NOT_CONFIGURED', submitted_at DATETIME(6),
    reviewed_at DATETIME(6), reviewed_by BIGINT, review_notes VARCHAR(1000), PRIMARY KEY (id),
    UNIQUE KEY uk_kyc_case_uuid (uuid), UNIQUE KEY idx_kyc_case_user (user_id),
    CONSTRAINT fk_kyc_case_user FOREIGN KEY (user_id) REFERENCES pms_users(id),
    CONSTRAINT fk_kyc_case_reviewer FOREIGN KEY (reviewed_by) REFERENCES pms_users(id)
);

CREATE TABLE pms_kyc_document (
    id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
    case_id BIGINT NOT NULL, user_id BIGINT NOT NULL, document_type VARCHAR(60) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL, content_type VARCHAR(80) NOT NULL, file_ref VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL, sha256 CHAR(64) NOT NULL, width INT, height INT, quality_score DOUBLE,
    quality_status VARCHAR(30) NOT NULL, status VARCHAR(30) NOT NULL, ocr_provider VARCHAR(40),
    ocr_confidence DOUBLE, encrypted_extracted_data LONGBLOB, rejection_reason VARCHAR(1000),
    supersedes_document_id BIGINT, reviewed_at DATETIME(6), reviewed_by BIGINT, PRIMARY KEY (id),
    UNIQUE KEY uk_kyc_document_uuid (uuid), KEY idx_kyc_document_case (case_id, active),
    KEY idx_kyc_document_hash (user_id, sha256),
    CONSTRAINT fk_kyc_document_case FOREIGN KEY (case_id) REFERENCES pms_kyc_case(id),
    CONSTRAINT fk_kyc_document_user FOREIGN KEY (user_id) REFERENCES pms_users(id),
    CONSTRAINT fk_kyc_document_reviewer FOREIGN KEY (reviewed_by) REFERENCES pms_users(id),
    CONSTRAINT fk_kyc_document_supersedes FOREIGN KEY (supersedes_document_id) REFERENCES pms_kyc_document(id)
);
