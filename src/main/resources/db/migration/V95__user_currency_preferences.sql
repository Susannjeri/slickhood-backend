CREATE TABLE pms_user_currency_preference (
    id BIGINT NOT NULL AUTO_INCREMENT,
    uuid BINARY(16) NOT NULL,
    created_on DATETIME(6) NULL,
    active BIT(1) NOT NULL DEFAULT b'1',
    created_by BIGINT NULL,
    last_modified_date DATETIME(6) NULL,
    user_id BIGINT NOT NULL,
    default_currency VARCHAR(3) NOT NULL,
    enabled_currencies VARCHAR(1000) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_currency_preference_uuid UNIQUE (uuid),
    CONSTRAINT uk_user_currency_preference_user UNIQUE (user_id),
    CONSTRAINT fk_user_currency_preference_user FOREIGN KEY (user_id) REFERENCES pms_users(id)
);

-- ISO validity is enforced by the application. This view detects missing or malformed
-- codes instead of hard-coding a two-currency allow-list.
CREATE OR REPLACE VIEW pms_transaction_currency_reconciliation AS
SELECT 'INVOICE' record_type,id record_id,currency FROM pms_invoice
 WHERE currency IS NULL OR currency NOT REGEXP '^[A-Z]{3}$'
UNION ALL SELECT 'PAYMENT',id,currency_code FROM pms_payment
 WHERE amount IS NOT NULL AND (currency_code IS NULL OR currency_code NOT REGEXP '^[A-Z]{3}$')
UNION ALL SELECT 'UNIT',id,currency FROM pms_unit
 WHERE currency IS NULL OR currency NOT REGEXP '^[A-Z]{3}$'
UNION ALL SELECT 'LEASE',id,currency FROM pms_lease
 WHERE currency IS NULL OR currency NOT REGEXP '^[A-Z]{3}$';
