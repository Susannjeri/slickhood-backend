ALTER TABLE pms_lease
    ADD COLUMN lifecycle_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN termination_effective_date DATE NULL,
    ADD COLUMN termination_reason VARCHAR(1000) NULL,
    ADD COLUMN termination_requested_by BIGINT NULL,
    ADD COLUMN termination_requested_at DATETIME(6) NULL,
    ADD INDEX idx_lease_termination_scan (active, lifecycle_status, termination_effective_date),
    ADD INDEX idx_lease_expiry_scan (active, signed, lifecycle_status, move_out_date),
    ADD INDEX idx_lease_payment_scan (active, payment_due, next_payment_date);

UPDATE pms_lease SET lifecycle_status = CASE WHEN signed = 1 THEN 'ACTIVE' ELSE 'DRAFT' END;

ALTER TABLE pms_unit_tenant
    ADD INDEX idx_tenancy_unit_active_accepted (unit_id, active, lease_accepted);
