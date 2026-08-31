ALTER TABLE pms_property_ownership
    ADD COLUMN termination_reason VARCHAR(500) NULL,
    ADD COLUMN terminated_by BIGINT NULL,
    ADD COLUMN terminated_at DATETIME NULL;

CREATE INDEX idx_ownership_terminated_by
    ON pms_property_ownership (terminated_by, terminated_at);
