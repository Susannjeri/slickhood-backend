ALTER TABLE pms_users
    ADD COLUMN organization_name VARCHAR(160) NULL AFTER profile_type;

