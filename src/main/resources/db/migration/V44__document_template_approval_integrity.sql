ALTER TABLE pms_lease_document_template
    ADD COLUMN content_sha256 VARCHAR(64) NULL AFTER body_html,
    ADD COLUMN legal_reviewed_at DATETIME(6) NULL AFTER legal_review_required,
    ADD COLUMN legal_reviewed_by BIGINT NULL AFTER legal_reviewed_at,
    ADD INDEX idx_document_template_reviewed_by (legal_reviewed_by),
    ADD CONSTRAINT fk_document_template_reviewed_by FOREIGN KEY (legal_reviewed_by) REFERENCES pms_users (id);

UPDATE pms_lease_document_template
SET content_sha256 = SHA2(body_html, 256),
    legal_reviewed_at = CASE
        WHEN legal_review_required = 0 THEN COALESCE(last_modified_date, created_on, CURRENT_TIMESTAMP(6))
        ELSE NULL
    END;

-- Keep the new column nullable during the rollback window. The new application always writes and
-- verifies the hash; a later contract migration may enforce NOT NULL after the previous artifact retires.
