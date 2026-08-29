ALTER TABLE pms_sp_booking
 ADD COLUMN quoted_amount DECIMAL(19,2) NULL,
 ADD COLUMN currency VARCHAR(3) NULL,
 ADD COLUMN pricing_unit VARCHAR(40) NULL,
 ADD KEY idx_sp_booking_customer_status (created_by,status,scheduled_at);

UPDATE pms_sp_booking b
JOIN pms_sp_service s ON s.id=b.service_id
SET b.quoted_amount=s.amount, b.currency=s.currency, b.pricing_unit=s.pricing_unit
WHERE b.quoted_amount IS NULL;
