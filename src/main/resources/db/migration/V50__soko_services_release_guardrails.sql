ALTER TABLE pms_soko_order
 ADD COLUMN encrypted_delivery_code VARBINARY(512),
 ADD COLUMN checkout_idempotency_key VARCHAR(80),
 ADD UNIQUE KEY uk_soko_checkout_idempotency (customer_user_id, checkout_idempotency_key),
 ADD KEY idx_soko_reservation_expiry (active, status, stock_released, reservation_expires_at);
