ALTER TABLE pms_soko_rider
    ADD COLUMN phone_confirmed BIT NOT NULL DEFAULT 0,
    ADD COLUMN phone_confirmation_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN phone_confirmation_otp VARBINARY(1024) NULL,
    ADD COLUMN phone_confirmation_expires_at DATETIME(6) NULL,
    ADD COLUMN phone_confirmation_requested_at DATETIME(6) NULL,
    ADD COLUMN phone_confirmation_confirmed_at DATETIME(6) NULL,
    ADD COLUMN phone_confirmation_window_started_at DATETIME(6) NULL,
    ADD COLUMN phone_confirmation_request_count INT NOT NULL DEFAULT 0,
    ADD COLUMN phone_confirmation_attempts INT NOT NULL DEFAULT 0;

-- Existing manually verified riders retain their assignment eligibility. New and
-- edited riders must complete the phone challenge introduced by this migration.
UPDATE pms_soko_rider
SET phone_confirmed=verified,
    phone_confirmation_status=CASE WHEN verified=1 THEN 'CONFIRMED' ELSE 'PENDING' END,
    phone_confirmation_confirmed_at=CASE WHEN verified=1 THEN COALESCE(verified_at,created_on) ELSE NULL END;
