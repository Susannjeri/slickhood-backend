ALTER TABLE pms_kyc_case
    ADD COLUMN pending_role_id BIGINT NULL,
    ADD INDEX idx_kyc_case_pending_role (pending_role_id);
