CREATE TABLE pms_maintenance_work_order (
 id BIGINT NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, created_on DATETIME(6), active BIT NOT NULL,
 created_by BIGINT, last_modified_date DATETIME(6), work_order_number VARCHAR(40) NOT NULL,
 property_id BIGINT NOT NULL, unit_id BIGINT NOT NULL, requested_by_user_id BIGINT NOT NULL,
 assigned_provider_service_id BIGINT, title VARCHAR(120) NOT NULL, description VARCHAR(2000) NOT NULL,
 category VARCHAR(40) NOT NULL, priority VARCHAR(20) NOT NULL, status VARCHAR(30) NOT NULL,
 scheduled_at DATETIME(6), completed_at DATETIME(6), estimated_cost DECIMAL(19,2), actual_cost DECIMAL(19,2),
 currency VARCHAR(12), resolution_notes VARCHAR(1000), PRIMARY KEY(id),
 UNIQUE KEY uk_maintenance_uuid(uuid), UNIQUE KEY uk_maintenance_number(work_order_number),
 KEY idx_maintenance_unit_status(unit_id,status,active), KEY idx_maintenance_property_status(property_id,status,active),
 KEY idx_maintenance_requester(requested_by_user_id,created_on)
);
