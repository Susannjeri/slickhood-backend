-- Freeze the customer-selected delivery pin and the shop collection point on
-- the order. Fulfilment must continue to use what the buyer confirmed even if
-- the merchant later edits the shop profile.
ALTER TABLE pms_soko_order
    ADD COLUMN delivery_latitude DOUBLE NULL AFTER delivery_address,
    ADD COLUMN delivery_longitude DOUBLE NULL AFTER delivery_latitude,
    ADD COLUMN store_name_snapshot VARCHAR(160) NULL AFTER payment_channel,
    ADD COLUMN store_address_snapshot VARCHAR(500) NULL AFTER store_name_snapshot,
    ADD COLUMN store_phone_snapshot VARCHAR(30) NULL AFTER store_address_snapshot,
    ADD COLUMN store_latitude_snapshot DOUBLE NULL AFTER store_phone_snapshot,
    ADD COLUMN store_longitude_snapshot DOUBLE NULL AFTER store_latitude_snapshot;

-- Existing orders cannot recover their historical shop profile, but freezing
-- the current value at migration time prevents subsequent edits from moving an
-- already-created pickup or changing its contact details again.
UPDATE pms_soko_order o
JOIN pms_soko_store s ON s.id = o.store_id
SET o.store_name_snapshot = COALESCE(o.store_name_snapshot,s.name),
    o.store_address_snapshot = COALESCE(o.store_address_snapshot,s.address),
    o.store_phone_snapshot = COALESCE(o.store_phone_snapshot,s.phone_number),
    o.store_latitude_snapshot = COALESCE(o.store_latitude_snapshot,s.latitude),
    o.store_longitude_snapshot = COALESCE(o.store_longitude_snapshot,s.longitude)
WHERE o.store_name_snapshot IS NULL
   OR o.store_address_snapshot IS NULL
   OR o.store_phone_snapshot IS NULL
   OR o.store_latitude_snapshot IS NULL
   OR o.store_longitude_snapshot IS NULL;
