CREATE TABLE pms_payment_operation (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6),
 idempotency_key VARCHAR(190) NOT NULL, case_reference VARCHAR(120) NOT NULL,
 payment_id BIGINT NOT NULL, invoice_id BIGINT NOT NULL, operation_type VARCHAR(40) NOT NULL,
 status VARCHAR(30) NOT NULL, amount DECIMAL(19,2) NOT NULL, currency VARCHAR(12) NOT NULL,
 provider VARCHAR(50), provider_reference VARCHAR(120), reason VARCHAR(1000),
 occurred_at DATETIME(6) NOT NULL, initiated_by BIGINT NOT NULL,
 PRIMARY KEY (id), UNIQUE KEY uk_payment_operation_uuid (uuid),
 UNIQUE KEY uk_payment_operation_idempotency (idempotency_key),
 KEY idx_payment_operation_case (case_reference, occurred_at),
 KEY idx_payment_operation_payment (payment_id, occurred_at),
 KEY idx_payment_operation_invoice (invoice_id, occurred_at),
 CONSTRAINT fk_payment_operation_payment FOREIGN KEY (payment_id) REFERENCES pms_payment(id),
 CONSTRAINT fk_payment_operation_invoice FOREIGN KEY (invoice_id) REFERENCES pms_invoice(id),
 CONSTRAINT chk_payment_operation_amount CHECK (amount > 0)
);
