ALTER TABLE pms_soko_order ADD COLUMN payment_account_id BIGINT NULL, ADD COLUMN payment_channel VARCHAR(40) NULL;
-- Historical destinations cannot always be reconstructed. Capture the currently configured account once.
UPDATE pms_soko_order o JOIN pms_soko_store s ON s.id=o.store_id
 SET o.payment_account_id=s.payment_account_id WHERE o.payment_account_id IS NULL;
