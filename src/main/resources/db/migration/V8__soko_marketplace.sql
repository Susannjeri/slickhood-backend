ALTER TABLE pms_payment_account MODIFY COLUMN category VARCHAR(40) NOT NULL;

CREATE TABLE pms_soko_store (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), owner_user_id BIGINT NOT NULL, name VARCHAR(160) NOT NULL,
 description VARCHAR(1000), phone_number VARCHAR(30), address VARCHAR(500), latitude DOUBLE, longitude DOUBLE,
 service_radius_km DECIMAL(8,2), status VARCHAR(30) NOT NULL, pickup_enabled BIT NOT NULL,
 delivery_enabled BIT NOT NULL, delivery_fee DECIMAL(19,2) NOT NULL, currency VARCHAR(3) NOT NULL,
 payment_account_id BIGINT, PRIMARY KEY(id), UNIQUE KEY uk_soko_store_uuid(uuid),
 KEY idx_soko_store_owner(owner_user_id,active), KEY idx_soko_store_status(status,active),
 KEY idx_soko_store_location(latitude,longitude), CONSTRAINT fk_soko_store_account FOREIGN KEY(payment_account_id) REFERENCES pms_payment_account(id)
);

CREATE TABLE pms_soko_product (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), store_id BIGINT NOT NULL, name VARCHAR(180) NOT NULL,
 description VARCHAR(1500), category VARCHAR(80) NOT NULL, unit VARCHAR(40) NOT NULL, price DECIMAL(19,2) NOT NULL,
 currency VARCHAR(3) NOT NULL, stock_quantity INT NOT NULL, image_url VARCHAR(800), status VARCHAR(30) NOT NULL,
 PRIMARY KEY(id), UNIQUE KEY uk_soko_product_uuid(uuid), KEY idx_soko_product_store(store_id,active),
 KEY idx_soko_product_catalog(status,category,active), CONSTRAINT fk_soko_product_store FOREIGN KEY(store_id) REFERENCES pms_soko_store(id)
);

CREATE TABLE pms_soko_order (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), order_number VARCHAR(30) NOT NULL, store_id BIGINT NOT NULL,
 customer_user_id BIGINT NOT NULL, status VARCHAR(30) NOT NULL, payment_status VARCHAR(30) NOT NULL,
 invoice_ref VARCHAR(40), delivery_method VARCHAR(30) NOT NULL, delivery_address VARCHAR(500), customer_phone VARCHAR(30) NOT NULL,
 notes VARCHAR(1000), destination_unit_id BIGINT, subtotal DECIMAL(19,2) NOT NULL, delivery_fee DECIMAL(19,2) NOT NULL,
 total DECIMAL(19,2) NOT NULL, currency VARCHAR(3) NOT NULL, placed_at DATETIME(6), confirmed_at DATETIME(6),
 dispatched_at DATETIME(6), completed_at DATETIME(6), delivery_visitor_id BIGINT, PRIMARY KEY(id),
 UNIQUE KEY uk_soko_order_uuid(uuid), UNIQUE KEY uk_soko_order_number(order_number), UNIQUE KEY idx_soko_order_invoice(invoice_ref),
 KEY idx_soko_order_customer(customer_user_id,created_on), KEY idx_soko_order_store(store_id,status),
 CONSTRAINT fk_soko_order_store FOREIGN KEY(store_id) REFERENCES pms_soko_store(id)
);

CREATE TABLE pms_soko_order_item (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), order_id BIGINT NOT NULL, product_id BIGINT NOT NULL,
 product_name VARCHAR(180) NOT NULL, unit VARCHAR(40) NOT NULL, unit_price DECIMAL(19,2) NOT NULL,
 quantity INT NOT NULL, line_total DECIMAL(19,2) NOT NULL, PRIMARY KEY(id), UNIQUE KEY uk_soko_order_item_uuid(uuid),
 KEY idx_soko_order_item_order(order_id), CONSTRAINT fk_soko_item_order FOREIGN KEY(order_id) REFERENCES pms_soko_order(id)
);

ALTER TABLE pms_invoice ADD KEY idx_invoice_billing_type (billing_type);
