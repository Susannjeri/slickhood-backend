CREATE TABLE pms_wealth_asset (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), owner_user_id BIGINT NOT NULL, property_id BIGINT,
 asset_type VARCHAR(40) NOT NULL, name VARCHAR(160) NOT NULL, reference VARCHAR(120), location VARCHAR(500),
 currency VARCHAR(3) NOT NULL, acquisition_cost DECIMAL(19,2), acquisition_date DATE,
 current_value DECIMAL(19,2) NOT NULL, valuation_date DATE NOT NULL, status VARCHAR(30) NOT NULL,
 PRIMARY KEY(id), UNIQUE KEY uk_wealth_asset_uuid(uuid), KEY idx_wealth_asset_owner(owner_user_id,active),
 KEY idx_wealth_asset_property(property_id)
);

CREATE TABLE pms_wealth_valuation (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), asset_id BIGINT NOT NULL, amount DECIMAL(19,2) NOT NULL,
 valuation_date DATE NOT NULL, source VARCHAR(60) NOT NULL, notes VARCHAR(1000), PRIMARY KEY(id),
 UNIQUE KEY uk_wealth_valuation_uuid(uuid), KEY idx_wealth_valuation_asset(asset_id,valuation_date),
 CONSTRAINT fk_wealth_valuation_asset FOREIGN KEY(asset_id) REFERENCES pms_wealth_asset(id)
);

CREATE TABLE pms_wealth_cash_flow (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), asset_id BIGINT NOT NULL, flow_type VARCHAR(20) NOT NULL,
 category VARCHAR(60) NOT NULL, amount DECIMAL(19,2) NOT NULL, entry_date DATE NOT NULL,
 description VARCHAR(500), recurring BIT NOT NULL, PRIMARY KEY(id), UNIQUE KEY uk_wealth_cash_uuid(uuid),
 KEY idx_wealth_cash_asset_date(asset_id,entry_date), CONSTRAINT fk_wealth_cash_asset FOREIGN KEY(asset_id) REFERENCES pms_wealth_asset(id)
);

CREATE TABLE pms_wealth_liability (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), asset_id BIGINT NOT NULL, lender VARCHAR(100) NOT NULL,
 currency VARCHAR(3) NOT NULL, original_principal DECIMAL(19,2) NOT NULL, outstanding_principal DECIMAL(19,2) NOT NULL,
 annual_interest_rate DECIMAL(8,4), monthly_payment DECIMAL(19,2), start_date DATE, maturity_date DATE,
 PRIMARY KEY(id), UNIQUE KEY uk_wealth_liability_uuid(uuid), KEY idx_wealth_liability_asset(asset_id,active),
 CONSTRAINT fk_wealth_liability_asset FOREIGN KEY(asset_id) REFERENCES pms_wealth_asset(id)
);

CREATE TABLE pms_wealth_obligation (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), asset_id BIGINT NOT NULL, obligation_type VARCHAR(40) NOT NULL,
 title VARCHAR(160) NOT NULL, effective_date DATE, due_date DATE, expiry_date DATE, amount DECIMAL(19,2),
 currency VARCHAR(3), status VARCHAR(30) NOT NULL, reminder_days INT NOT NULL, notes VARCHAR(500), PRIMARY KEY(id),
 UNIQUE KEY uk_wealth_obligation_uuid(uuid), KEY idx_wealth_obligation_due(asset_id,due_date,status),
 CONSTRAINT fk_wealth_obligation_asset FOREIGN KEY(asset_id) REFERENCES pms_wealth_asset(id)
);

CREATE TABLE pms_wealth_vault_document (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), asset_id BIGINT NOT NULL, category VARCHAR(50) NOT NULL,
 display_name VARCHAR(255) NOT NULL, file_ref VARCHAR(800) NOT NULL, content_type VARCHAR(120) NOT NULL,
 file_size BIGINT NOT NULL, checksum_sha256 VARCHAR(64) NOT NULL, document_date DATE, expiry_date DATE,
 notes VARCHAR(500), PRIMARY KEY(id), UNIQUE KEY uk_wealth_vault_uuid(uuid), KEY idx_wealth_vault_asset(asset_id,active),
 CONSTRAINT fk_wealth_vault_asset FOREIGN KEY(asset_id) REFERENCES pms_wealth_asset(id)
);

CREATE TABLE pms_wealth_goal (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), owner_user_id BIGINT NOT NULL, goal_type VARCHAR(40) NOT NULL,
 name VARCHAR(160) NOT NULL, target_amount DECIMAL(19,2) NOT NULL, currency VARCHAR(3) NOT NULL,
 target_date DATE NOT NULL, status VARCHAR(30) NOT NULL, PRIMARY KEY(id), UNIQUE KEY uk_wealth_goal_uuid(uuid),
 KEY idx_wealth_goal_owner(owner_user_id,status)
);
