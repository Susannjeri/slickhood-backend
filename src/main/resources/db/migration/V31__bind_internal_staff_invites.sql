ALTER TABLE pms_invite
    ADD COLUMN recipient VARCHAR(254) NULL;

CREATE INDEX idx_invite_recipient ON pms_invite (recipient);
