ALTER TABLE pms_invite
    ADD COLUMN lease_start_date DATE NULL,
    ADD COLUMN lease_end_date DATE NULL,
    ADD COLUMN agreement_template_id BIGINT NULL;

CREATE INDEX idx_invite_tenant_unit_active
    ON pms_invite (entity_id, type, active, expiry_date);

CREATE INDEX idx_invite_agreement_template
    ON pms_invite (agreement_template_id);

ALTER TABLE pms_lease_document
    ADD COLUMN recipient_rejection_reason VARCHAR(1000) NULL;

-- Existing tenant links pre-date landlord-defined lease terms and cannot be
-- accepted safely by the new workflow. Retire only those links; landlords can
-- resend an email-bound assignment with explicit dates after deployment.
UPDATE pms_invite
SET active = 0
WHERE type = 'TENANT'
  AND active = 1
  AND (lease_start_date IS NULL OR lease_end_date IS NULL OR agreement_template_id IS NULL);
