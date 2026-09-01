ALTER TABLE pms_soko_order
 ADD COLUMN expected_arrival_at DATETIME(6),
 ADD COLUMN delivery_proof_content_type VARCHAR(80),
 ADD COLUMN delivery_proof_size BIGINT,
 ADD KEY idx_soko_order_delivery_status (delivery_method,status,dispatched_at);
