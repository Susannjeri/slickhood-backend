ALTER TABLE pms_lease
    ADD COLUMN governed_document_required BIT NOT NULL DEFAULT 0;

ALTER TABLE pms_lease_document
    ADD INDEX idx_document_lease_type_status (lease_id, document_type, status, active);
