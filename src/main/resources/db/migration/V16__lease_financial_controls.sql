CREATE TABLE pms_lease_financial_event (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6),
 idempotency_key VARCHAR(190) NOT NULL, lease_id BIGINT NOT NULL, invoice_id BIGINT,
 event_type VARCHAR(40) NOT NULL, amount DECIMAL(19,2) NOT NULL, currency VARCHAR(12) NOT NULL,
 external_reference VARCHAR(120), reason VARCHAR(1000), occurred_at DATETIME(6) NOT NULL, initiated_by BIGINT NOT NULL,
 PRIMARY KEY (id), UNIQUE KEY uk_lease_fin_event_uuid (uuid), UNIQUE KEY uk_lease_fin_event_idempotency (idempotency_key),
 KEY idx_lease_fin_event_lease (lease_id, occurred_at), KEY idx_lease_fin_event_invoice (invoice_id),
 CONSTRAINT fk_lease_fin_event_lease FOREIGN KEY (lease_id) REFERENCES pms_lease(id),
 CONSTRAINT fk_lease_fin_event_invoice FOREIGN KEY (invoice_id) REFERENCES pms_invoice(id),
 CONSTRAINT chk_lease_fin_event_amount CHECK (amount > 0)
);

CREATE TABLE pms_late_fee_rule (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6),
 active BIT NOT NULL, created_by BIGINT, last_modified_date DATETIME(6), lease_id BIGINT NOT NULL,
 flat_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00, percentage_rate DECIMAL(8,4) NOT NULL DEFAULT 0.0000,
 grace_days INT NOT NULL DEFAULT 0, maximum_amount DECIMAL(19,2), enabled BIT NOT NULL DEFAULT 1,
 PRIMARY KEY(id), UNIQUE KEY uk_late_fee_rule_uuid(uuid), UNIQUE KEY uk_late_fee_rule_lease(lease_id),
 CONSTRAINT fk_late_fee_rule_lease FOREIGN KEY(lease_id) REFERENCES pms_lease(id),
 CONSTRAINT chk_late_fee_rule_values CHECK(flat_amount >= 0 AND percentage_rate >= 0 AND grace_days >= 0 AND (maximum_amount IS NULL OR maximum_amount > 0))
);
