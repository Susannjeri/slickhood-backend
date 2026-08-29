ALTER TABLE pms_invoice ADD COLUMN billing_type VARCHAR(40), ADD COLUMN due_date DATE;
CREATE TABLE pms_estate_service_charge (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), property_id BIGINT NOT NULL, unit_id BIGINT NOT NULL,
 homeowner_user_id BIGINT NOT NULL, invoice_id BIGINT NOT NULL, amount DECIMAL(19,2) NOT NULL,
 currency VARCHAR(12), due_date DATE NOT NULL, description VARCHAR(255), PRIMARY KEY(id),
 UNIQUE KEY uk_service_charge_uuid(uuid), KEY idx_charge_homeowner(homeowner_user_id,active),
 KEY idx_charge_property(property_id,active), CONSTRAINT fk_service_charge_invoice FOREIGN KEY(invoice_id) REFERENCES pms_invoice(id)
);
