CREATE TABLE pms_receivable_late_fee_policy (
 id BIGINT NOT NULL AUTO_INCREMENT,
 uuid BINARY(16) NOT NULL,
 created_on DATETIME(6),
 active BIT NOT NULL,
 created_by BIGINT NOT NULL,
 last_modified_date DATETIME(6),
 billing_type VARCHAR(40) NOT NULL,
 percentage_rate DECIMAL(8,4) NOT NULL DEFAULT 0.0000,
 grace_days INT NOT NULL DEFAULT 0,
 effective_from DATE NOT NULL,
 enabled BIT NOT NULL DEFAULT 0,
 PRIMARY KEY (id),
 UNIQUE KEY uk_receivable_late_fee_policy_uuid (uuid),
 UNIQUE KEY uk_receivable_late_fee_policy_biller_type (created_by, billing_type),
 CONSTRAINT chk_receivable_late_fee_policy_values
   CHECK (percentage_rate >= 0 AND percentage_rate <= 100 AND grace_days >= 0)
);

ALTER TABLE pms_invoice
    ADD COLUMN late_fee_source_invoice_id BIGINT NULL,
    ADD COLUMN late_fee_percentage_rate DECIMAL(8,4) NULL,
    ADD UNIQUE KEY uk_invoice_late_fee_source (late_fee_source_invoice_id),
    ADD CONSTRAINT fk_invoice_late_fee_source
      FOREIGN KEY (late_fee_source_invoice_id) REFERENCES pms_invoice(id);
