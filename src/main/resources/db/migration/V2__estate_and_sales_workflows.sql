CREATE TABLE pms_property_ownership (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), property_id BIGINT NOT NULL, unit_id BIGINT,
 homeowner_user_id BIGINT NOT NULL, ownership_start DATE NOT NULL, ownership_end DATE, source VARCHAR(255),
 source_sale_transaction_id BIGINT, PRIMARY KEY(id), UNIQUE KEY uk_ownership_uuid(uuid),
 KEY idx_ownership_homeowner(homeowner_user_id,active), KEY idx_ownership_property_unit(property_id,unit_id,active)
);

CREATE TABLE pms_sale_transaction (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), property_id BIGINT NOT NULL, unit_id BIGINT,
 sales_agent_user_id BIGINT NOT NULL, buyer_user_id BIGINT NOT NULL, status VARCHAR(30) NOT NULL,
 asking_price DECIMAL(19,2) NOT NULL, offer_amount DECIMAL(19,2), currency VARCHAR(12),
 offer_accepted_at DATETIME(6), completed_at DATETIME(6), notes VARCHAR(1000), PRIMARY KEY(id),
 UNIQUE KEY uk_sale_uuid(uuid), KEY idx_sale_agent(sales_agent_user_id,active),
 KEY idx_sale_buyer(buyer_user_id,active), KEY idx_sale_property(property_id,unit_id,active)
);
