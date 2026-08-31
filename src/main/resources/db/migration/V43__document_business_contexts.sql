ALTER TABLE pms_lease_document
    ADD COLUMN sale_id BIGINT NULL AFTER lease_id,
    ADD INDEX idx_document_sale (sale_id, document_type, status, active),
    ADD CONSTRAINT fk_document_sale FOREIGN KEY (sale_id) REFERENCES pms_sale_transaction (id);

-- Preserve historical snapshots while removing obsolete choices from future document creation.
UPDATE pms_lease_document_template
SET active = 0
WHERE document_type IN ('RENTAL_LETTER_OF_OFFER', 'ESTATE_AGREEMENT')
  AND active = 1;

CREATE TABLE pms_document_branding (
    id BIGINT NOT NULL AUTO_INCREMENT,
    uuid VARCHAR(36) NOT NULL,
    created_on DATETIME(6) NULL,
    active BIT NOT NULL DEFAULT 1,
    created_by BIGINT NOT NULL,
    last_modified_date DATETIME(6) NULL,
    owner_user_id BIGINT NOT NULL,
    logo_mime_type VARCHAR(40) NOT NULL,
    logo_sha256 VARCHAR(64) NOT NULL,
    logo_content MEDIUMBLOB NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_document_branding_uuid (uuid),
    UNIQUE KEY uk_document_branding_owner (owner_user_id),
    CONSTRAINT fk_document_branding_owner FOREIGN KEY (owner_user_id) REFERENCES pms_users (id)
);
