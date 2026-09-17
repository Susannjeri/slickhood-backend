ALTER TABLE pms_insurance_guest_access
 ADD COLUMN verified_channel VARCHAR(12) NULL AFTER verified_at;

UPDATE pms_insurance_guest_access
 SET verified_channel = delivery_channel
 WHERE verified_at IS NOT NULL AND verified_channel IS NULL;
