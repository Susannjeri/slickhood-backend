ALTER TABLE pms_sale_transaction
    MODIFY COLUMN buyer_user_id BIGINT NULL,
    ADD COLUMN invited_buyer_email VARCHAR(254) NULL AFTER buyer_user_id;

CREATE INDEX idx_sale_property_active_created
    ON pms_sale_transaction(property_id, active, created_on);

CREATE UNIQUE INDEX uk_ownership_source_sale
    ON pms_property_ownership(source_sale_transaction_id);
