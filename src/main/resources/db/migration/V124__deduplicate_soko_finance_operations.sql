CREATE TABLE pms_soko_finance_operation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    uuid BINARY(16) NOT NULL,
    created_on DATETIME(6),
    order_id BIGINT NOT NULL,
    operation_type VARCHAR(40) NOT NULL,
    provider_reference VARCHAR(120) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_soko_finance_operation_uuid UNIQUE (uuid),
    CONSTRAINT uk_soko_finance_provider_ref UNIQUE (order_id, provider_reference),
    KEY idx_soko_finance_order (order_id, created_on),
    CONSTRAINT fk_soko_finance_operation_order FOREIGN KEY (order_id) REFERENCES pms_soko_order(id)
);
