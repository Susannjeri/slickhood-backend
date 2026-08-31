-- V34 is already the subscription lifecycle migration in production. Keep that
-- checksum stable and append this property change after the catalogue chain.
ALTER TABLE pms_property
    ADD COLUMN management_mode VARCHAR(32) NOT NULL DEFAULT 'RENTAL',
    MODIFY COLUMN address VARCHAR(500);

CREATE INDEX idx_property_owner_mode_active
    ON pms_property (created_by, management_mode, active);
