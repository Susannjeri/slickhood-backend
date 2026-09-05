ALTER TABLE pms_payment
    ADD COLUMN provider_receipt VARCHAR(255) NULL;

UPDATE pms_payment payment
JOIN (
    SELECT channel, third_party_trans_id, MIN(id) AS canonical_id
    FROM pms_payment
    WHERE third_party_trans_id IS NOT NULL
      AND TRIM(third_party_trans_id) <> ''
      AND in_progress = 0
      AND (
          (category = 'STK' AND status = '0')
          OR (category = 'PAYMENT_PROCESSED' AND status = '1')
          OR (category = 'CARD_PAYMENT' AND status = 'successful')
          OR (category = 'MANUAL_RECORD' AND status = 'success')
      )
    GROUP BY channel, third_party_trans_id
) canonical ON canonical.canonical_id = payment.id
SET payment.provider_receipt = payment.third_party_trans_id;

CREATE UNIQUE INDEX uk_payment_channel_provider_receipt
    ON pms_payment (channel, provider_receipt);
