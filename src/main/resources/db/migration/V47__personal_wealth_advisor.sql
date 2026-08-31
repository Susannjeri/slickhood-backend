ALTER TABLE pms_wealth_asset
 ADD COLUMN exchange_code VARCHAR(20),
 ADD COLUMN instrument_symbol VARCHAR(40),
 ADD COLUMN quantity DECIMAL(28,8),
 ADD COLUMN average_unit_cost DECIMAL(19,6),
 ADD COLUMN pricing_mode VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
 ADD COLUMN market_price DECIMAL(19,6),
 ADD COLUMN quote_provider VARCHAR(40),
 ADD COLUMN quote_status VARCHAR(30),
 ADD COLUMN quote_as_of DATETIME(6),
 ADD KEY idx_wealth_asset_market (pricing_mode,exchange_code,instrument_symbol,active);

ALTER TABLE pms_wealth_vault_document
 ADD COLUMN owner_user_id BIGINT,
 MODIFY COLUMN asset_id BIGINT NULL,
 ADD KEY idx_wealth_vault_owner (owner_user_id,active,created_on);

UPDATE pms_wealth_vault_document d
 JOIN pms_wealth_asset a ON a.id=d.asset_id
 SET d.owner_user_id=a.owner_user_id
 WHERE d.owner_user_id IS NULL;

ALTER TABLE pms_wealth_vault_document MODIFY COLUMN owner_user_id BIGINT NOT NULL;

CREATE TABLE pms_wealth_market_quote (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), exchange_code VARCHAR(20) NOT NULL,
 instrument_symbol VARCHAR(40) NOT NULL, currency VARCHAR(3) NOT NULL, price DECIMAL(19,6) NOT NULL,
 previous_close DECIMAL(19,6), change_amount DECIMAL(19,6), change_percent DECIMAL(12,6),
 provider VARCHAR(40) NOT NULL, quote_as_of DATETIME(6) NOT NULL, freshness VARCHAR(20) NOT NULL,
 PRIMARY KEY(id), UNIQUE KEY uk_wealth_market_quote_uuid(uuid),
 UNIQUE KEY uk_wealth_market_quote_instrument(exchange_code,instrument_symbol),
 KEY idx_wealth_market_quote_asof(quote_as_of)
);
