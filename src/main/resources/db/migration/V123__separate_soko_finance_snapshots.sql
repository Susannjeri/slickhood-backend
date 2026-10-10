ALTER TABLE pms_soko_order
    ADD COLUMN refund_requested_amount DECIMAL(19,2) NULL,
    ADD COLUMN reversal_status VARCHAR(255) NULL,
    ADD COLUMN reversal_reference VARCHAR(255) NULL,
    ADD COLUMN reversed_amount DECIMAL(19,2) NULL,
    ADD COLUMN chargeback_status VARCHAR(255) NULL,
    ADD COLUMN chargeback_reference VARCHAR(255) NULL,
    ADD COLUMN charged_back_amount DECIMAL(19,2) NULL;

CREATE INDEX idx_soko_order_refund_queue
    ON pms_soko_order (refund_status, active, created_on);
