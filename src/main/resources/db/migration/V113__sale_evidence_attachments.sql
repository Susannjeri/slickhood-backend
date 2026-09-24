CREATE TABLE pms_sale_evidence_attachment (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), sale_id BIGINT NOT NULL, category VARCHAR(40) NOT NULL,
 display_name VARCHAR(255) NOT NULL, file_ref VARCHAR(500) NOT NULL, content_type VARCHAR(100) NOT NULL,
 file_size BIGINT NOT NULL, checksum_sha256 VARCHAR(64) NOT NULL, uploaded_by_user_id BIGINT NOT NULL,
 PRIMARY KEY(id), UNIQUE KEY uk_sale_evidence_uuid(uuid),
 KEY idx_sale_evidence_sale(sale_id,active,created_on),
 CONSTRAINT fk_sale_evidence_sale FOREIGN KEY(sale_id) REFERENCES pms_sale_transaction(id)
);
ALTER TABLE pms_sale_milestone ADD COLUMN evidence_attachment_id BIGINT NULL;
ALTER TABLE pms_sale_milestone ADD CONSTRAINT fk_sale_milestone_attachment
 FOREIGN KEY(evidence_attachment_id) REFERENCES pms_sale_evidence_attachment(id);
