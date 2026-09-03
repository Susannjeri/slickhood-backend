-- Preserve machine-read evidence and reviewer-verified corrections independently.
-- Both payloads are encrypted by the application before persistence.
ALTER TABLE pms_kyc_document
    ADD COLUMN encrypted_reviewer_verified_data LONGBLOB NULL AFTER encrypted_extracted_data,
    ADD COLUMN reviewer_correction_reason TEXT NULL AFTER encrypted_reviewer_verified_data;
