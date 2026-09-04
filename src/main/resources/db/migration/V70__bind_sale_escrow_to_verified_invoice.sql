ALTER TABLE pms_sale_transaction
    ADD COLUMN escrow_required_amount DECIMAL(19,2) NULL,
    ADD COLUMN escrow_invoice_id BIGINT NULL;

CREATE INDEX idx_sale_escrow_invoice ON pms_sale_transaction (escrow_invoice_id);

ALTER TABLE pms_sale_transaction
    ADD CONSTRAINT fk_sale_escrow_invoice
        FOREIGN KEY (escrow_invoice_id) REFERENCES pms_invoice (id);
