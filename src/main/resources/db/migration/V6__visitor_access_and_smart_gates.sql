ALTER TABLE pms_visitor
 ADD COLUMN visit_type VARCHAR(20) NULL,
 ADD COLUMN purpose VARCHAR(250) NULL,
 ADD COLUMN company_name VARCHAR(150) NULL,
 ADD COLUMN tracking_number VARCHAR(120) NULL,
 ADD COLUMN credential_hash VARCHAR(64) NULL,
 ADD COLUMN credential_hint VARCHAR(12) NULL,
 ADD COLUMN valid_from DATETIME(6) NULL,
 ADD COLUMN valid_until DATETIME(6) NULL,
 ADD COLUMN approved_at DATETIME(6) NULL,
 ADD COLUMN approved_by BIGINT NULL,
 ADD COLUMN host_user_id BIGINT NULL,
 ADD COLUMN checked_in_at DATETIME(6) NULL,
 ADD COLUMN checked_out_at DATETIME(6) NULL,
 ADD COLUMN entry_count INT NOT NULL DEFAULT 0,
 ADD COLUMN max_entries INT NOT NULL DEFAULT 1,
 ADD COLUMN requires_approval BIT NOT NULL DEFAULT 0,
 ADD UNIQUE KEY uk_visitor_credential_hash (credential_hash),
 ADD KEY idx_visitor_access_window (property_id, status, valid_from, valid_until);

UPDATE pms_visitor
SET visit_type = CASE
    WHEN category = 'DELIVERY' THEN 'DELIVERY'
    WHEN vehicle_plate IS NOT NULL AND TRIM(vehicle_plate) <> '' THEN 'DRIVE_IN'
    ELSE 'WALK_IN'
END,
valid_from = COALESCE(valid_from, DATE_SUB(expected_arrival_time, INTERVAL 2 HOUR)),
valid_until = COALESCE(valid_until, DATE_ADD(expected_arrival_time, INTERVAL 8 HOUR));

ALTER TABLE pms_visitor MODIFY visit_type VARCHAR(20) NOT NULL;

CREATE TABLE pms_gate_device (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), device_code VARCHAR(64) NOT NULL, property_id BIGINT NOT NULL,
 display_name VARCHAR(120) NOT NULL, gate_name VARCHAR(120), lane_name VARCHAR(120), public_key VARCHAR(800) NOT NULL,
 enabled BIT NOT NULL, last_seen_at DATETIME(6), PRIMARY KEY(id), UNIQUE KEY uk_gate_device_uuid(uuid),
 UNIQUE KEY uk_gate_device_code(device_code), KEY idx_gate_device_property(property_id,active)
);

CREATE TABLE pms_gate_request_nonce (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6),
 device_id BIGINT NOT NULL, nonce VARCHAR(100) NOT NULL, expires_at DATETIME(6) NOT NULL,
 PRIMARY KEY(id), UNIQUE KEY uk_gate_nonce_uuid(uuid), UNIQUE KEY uk_gate_nonce_device(device_id,nonce),
 KEY idx_gate_nonce_expiry(expires_at), CONSTRAINT fk_gate_nonce_device FOREIGN KEY(device_id) REFERENCES pms_gate_device(id)
);

CREATE TABLE pms_visitor_access_event (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), visitor_id BIGINT NULL, property_id BIGINT NOT NULL,
 device_id BIGINT NULL, source VARCHAR(30) NOT NULL, direction VARCHAR(10) NOT NULL, outcome VARCHAR(10) NOT NULL,
 reason_code VARCHAR(60) NOT NULL, correlation_id VARCHAR(64) NOT NULL, vehicle_plate VARCHAR(20), occurred_at DATETIME(6) NOT NULL,
 PRIMARY KEY(id), UNIQUE KEY uk_access_event_uuid(uuid), UNIQUE KEY idx_access_event_correlation(correlation_id),
 KEY idx_access_event_visitor(visitor_id,occurred_at), KEY idx_access_event_property(property_id,occurred_at),
 CONSTRAINT fk_access_event_visitor FOREIGN KEY(visitor_id) REFERENCES pms_visitor(id),
 CONSTRAINT fk_access_event_device FOREIGN KEY(device_id) REFERENCES pms_gate_device(id)
);
