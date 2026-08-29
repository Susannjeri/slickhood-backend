CREATE TABLE pms_soko_rider (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), store_id BIGINT NOT NULL, rider_type VARCHAR(30) NOT NULL,
 display_name VARCHAR(150) NOT NULL, phone_number VARCHAR(30) NOT NULL, email VARCHAR(180),
 vehicle_type VARCHAR(60), vehicle_plate VARCHAR(20), availability VARCHAR(30) NOT NULL,
 status VARCHAR(30) NOT NULL, verified BIT NOT NULL, completed_deliveries INT NOT NULL,
 notes VARCHAR(1000), PRIMARY KEY(id), UNIQUE KEY uk_soko_rider_uuid(uuid),
 KEY idx_soko_rider_store(store_id,active), KEY idx_soko_rider_availability(store_id,availability,active),
 KEY idx_soko_rider_phone(store_id,phone_number),
 CONSTRAINT fk_soko_rider_store FOREIGN KEY(store_id) REFERENCES pms_soko_store(id)
);

ALTER TABLE pms_soko_order
 ADD COLUMN rider_id BIGINT NULL,
 ADD COLUMN courier_name VARCHAR(150) NULL,
 ADD COLUMN courier_phone VARCHAR(30) NULL,
 ADD COLUMN courier_vehicle_plate VARCHAR(20) NULL,
 ADD COLUMN delivery_code VARCHAR(6) NULL,
 ADD COLUMN delivery_code_verified BIT NOT NULL DEFAULT 0,
 ADD COLUMN delivery_code_attempts INT NOT NULL DEFAULT 0,
 ADD KEY idx_soko_order_rider (rider_id),
 ADD CONSTRAINT fk_soko_order_rider FOREIGN KEY(rider_id) REFERENCES pms_soko_rider(id);
