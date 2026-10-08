ALTER TABLE pms_soko_rider
    ADD COLUMN national_id_number VARCHAR(40);

CREATE INDEX idx_soko_rider_national_id
    ON pms_soko_rider (national_id_number);
