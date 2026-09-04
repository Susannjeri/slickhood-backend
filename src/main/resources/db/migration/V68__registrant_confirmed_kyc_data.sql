-- Keep the registrant's confirmation separate from immutable OCR and reviewer corrections.
ALTER TABLE pms_kyc_document
    ADD COLUMN encrypted_registrant_confirmed_data LONGBLOB NULL AFTER encrypted_extracted_data,
    ADD COLUMN registrant_confirmed_at DATETIME(6) NULL AFTER encrypted_registrant_confirmed_data,
    ADD COLUMN registrant_confirmed_by BIGINT NULL AFTER registrant_confirmed_at;
