CREATE TABLE pms_sp_finance_operation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    uuid BINARY(16) NOT NULL,
    created_on DATETIME(6),
    booking_id BIGINT NOT NULL,
    provider_user_id BIGINT NOT NULL,
    payment_channel VARCHAR(30) NOT NULL,
    operation_type VARCHAR(30) NOT NULL,
    operation_status VARCHAR(30) NOT NULL,
    provider_reference VARCHAR(120) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    currency VARCHAR(12) NOT NULL,
    initiated_by BIGINT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sp_finance_operation_uuid UNIQUE (uuid),
    CONSTRAINT uk_sp_finance_provider_channel_type_ref UNIQUE
        (provider_user_id, payment_channel, operation_type, provider_reference),
    KEY idx_sp_finance_booking_created (booking_id, created_on),
    CONSTRAINT fk_sp_finance_operation_booking FOREIGN KEY (booking_id) REFERENCES pms_sp_booking(id),
    CONSTRAINT chk_sp_finance_operation_type CHECK (operation_type IN ('REFUND', 'SETTLEMENT')),
    CONSTRAINT chk_sp_finance_operation_status CHECK (operation_status = 'CONFIRMED'),
    CONSTRAINT chk_sp_finance_operation_amount CHECK (amount > 0)
);

-- Preserve already-confirmed production records. INSERT IGNORE lets rollout
-- finish if legacy data already reused a reference; the unique key then stops
-- every new reuse and exposes a conflicting retry for manual reconciliation.
INSERT IGNORE INTO pms_sp_finance_operation
    (uuid, created_on, booking_id, provider_user_id, payment_channel,
     operation_type, operation_status, provider_reference, amount, currency, initiated_by)
SELECT UUID_TO_BIN(UUID()), UTC_TIMESTAMP(6), b.id, p.user_id,
       COALESCE(NULLIF(UPPER(TRIM(b.payment_channel)), ''), 'LEGACY'),
       'REFUND', 'CONFIRMED', TRIM(b.refund_reference), b.refunded_amount,
       COALESCE(NULLIF(UPPER(TRIM(b.currency)), ''), 'KES'), NULL
FROM pms_sp_booking b
JOIN pms_sp_service s ON s.id = b.service_id
JOIN pms_sp_profile p ON p.id = s.profile_id
WHERE b.refund_status = 'CONFIRMED'
  AND b.refunded_amount > 0
  AND NULLIF(TRIM(b.refund_reference), '') IS NOT NULL;

INSERT IGNORE INTO pms_sp_finance_operation
    (uuid, created_on, booking_id, provider_user_id, payment_channel,
     operation_type, operation_status, provider_reference, amount, currency, initiated_by)
SELECT UUID_TO_BIN(UUID()), UTC_TIMESTAMP(6), b.id, p.user_id,
       COALESCE(NULLIF(UPPER(TRIM(b.payment_channel)), ''), 'LEGACY'),
       'SETTLEMENT', 'CONFIRMED', TRIM(b.settlement_reference), b.settled_amount,
       COALESCE(NULLIF(UPPER(TRIM(b.currency)), ''), 'KES'), NULL
FROM pms_sp_booking b
JOIN pms_sp_service s ON s.id = b.service_id
JOIN pms_sp_profile p ON p.id = s.profile_id
WHERE b.settlement_status = 'CONFIRMED'
  AND b.settled_amount > 0
  AND NULLIF(TRIM(b.settlement_reference), '') IS NOT NULL;
