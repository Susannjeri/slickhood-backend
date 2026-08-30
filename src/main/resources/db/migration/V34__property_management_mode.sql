ALTER TABLE pms_property
    ADD COLUMN IF NOT EXISTS management_mode VARCHAR(32) NOT NULL DEFAULT 'RENTAL',
    MODIFY COLUMN address VARCHAR(500);

CREATE INDEX idx_property_owner_mode_active
    ON pms_property (created_by, management_mode, active);
