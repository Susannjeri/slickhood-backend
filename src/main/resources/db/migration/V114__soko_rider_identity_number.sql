ALTER TABLE pms_soko_rider
    ADD COLUMN IF NOT EXISTS national_id_number VARCHAR(40);

CREATE INDEX IF NOT EXISTS idx_soko_rider_national_id
    ON pms_soko_rider (national_id_number);
