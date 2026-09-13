ALTER TABLE pms_insurance_company
    ADD COLUMN logo_file_ref VARCHAR(800) NULL AFTER logo_url;

ALTER TABLE pms_insurance_quote
    ADD COLUMN source_exchange_id BIGINT NULL AFTER company_id,
    ADD UNIQUE KEY uk_insurance_quote_source_exchange(source_exchange_id),
    ADD CONSTRAINT fk_insurance_quote_source_exchange
        FOREIGN KEY(source_exchange_id) REFERENCES pms_insurance_email_exchange(id);
