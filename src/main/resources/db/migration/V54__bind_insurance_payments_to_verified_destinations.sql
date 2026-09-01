ALTER TABLE pms_insurance_premium_payment
    ADD COLUMN payment_configuration_id BIGINT NULL AFTER quote_id,
    ADD INDEX idx_insurance_premium_configuration (payment_configuration_id),
    ADD CONSTRAINT fk_insurance_premium_configuration
        FOREIGN KEY (payment_configuration_id) REFERENCES pms_insurance_payment_configuration (id);
