ALTER TABLE pms_insurance_case
    ADD COLUMN assignment_offered_at DATETIME(6) NULL AFTER assigned_adviser_id,
    ADD COLUMN adviser_accepted_at DATETIME(6) NULL AFTER assignment_offered_at,
    ADD COLUMN adviser_declined_at DATETIME(6) NULL AFTER adviser_accepted_at,
    ADD COLUMN adviser_decline_reason VARCHAR(500) NULL AFTER adviser_declined_at;

UPDATE pms_insurance_case
SET assignment_offered_at = COALESCE(last_modified_date, submitted_at),
    adviser_accepted_at = COALESCE(last_modified_date, submitted_at)
WHERE assigned_adviser_id IS NOT NULL
  AND status = 'ADVISER_ASSIGNED';

CREATE INDEX idx_insurance_assignment_pending_v111
    ON pms_insurance_case(status, assigned_adviser_id, active);
