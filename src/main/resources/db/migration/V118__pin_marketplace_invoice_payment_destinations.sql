-- Marketplace checkout must settle only to the receiving account captured by its source transaction.
-- Correct only live receivables whose source, participants, account ownership and channel agree.
UPDATE pms_invoice i
JOIN pms_soko_order o ON o.invoice_ref = i.ref
JOIN pms_soko_store s ON s.id = o.store_id
JOIN pms_payment_account a ON a.id = o.payment_account_id
SET i.payment_account_id = o.payment_account_id
WHERE i.billing_type = 'SOKO'
  AND i.active = 1
  AND i.paid = 0
  AND COALESCE(i.pending_amount_decimal, CAST(i.pending_amount AS DECIMAL(19,2))) > 0.00
  AND i.payment_account_id IS NULL
  AND o.active = 1
  AND o.status = 'PENDING_PAYMENT'
  AND o.payment_status = 'UNPAID'
  AND o.reservation_expires_at > UTC_TIMESTAMP(6)
  AND o.payment_account_id IS NOT NULL
  AND o.customer_user_id = i.billed_user_id
  AND s.owner_user_id = i.pay_to_user_id
  AND a.category = 'MERCHANT'
  AND a.active = 1
  AND a.verified = 1
  AND a.created_by = i.pay_to_user_id
  AND a.channel IS NOT NULL
  AND (o.payment_channel IS NULL OR o.payment_channel = a.channel);

UPDATE pms_invoice i
JOIN pms_sp_booking b ON b.invoice_ref = i.ref
JOIN pms_sp_service s ON s.id = b.service_id
JOIN pms_sp_profile p ON p.id = s.profile_id
JOIN pms_payment_account a ON a.id = b.payment_account_id
SET i.payment_account_id = b.payment_account_id
WHERE i.billing_type = 'SERVICE_MARKETPLACE'
  AND i.active = 1
  AND i.paid = 0
  AND COALESCE(i.pending_amount_decimal, CAST(i.pending_amount AS DECIMAL(19,2))) > 0.00
  AND i.payment_account_id IS NULL
  AND b.active = 1
  AND b.status = 'AWAITING_PAYMENT'
  AND b.payment_status = 'UNPAID'
  AND b.payment_account_id IS NOT NULL
  AND b.created_by = i.billed_user_id
  AND p.user_id = i.pay_to_user_id
  AND a.category = 'MERCHANT'
  AND a.active = 1
  AND a.verified = 1
  AND a.created_by = i.pay_to_user_id
  AND a.channel IS NOT NULL
  AND (b.payment_channel IS NULL OR b.payment_channel = a.channel);

-- Rows in this view were deliberately not guessed or overwritten. It retains paid,
-- inactive, zero-balance and otherwise unprovable conflicts for explicit reconciliation.
CREATE OR REPLACE VIEW pms_marketplace_invoice_destination_reconciliation AS
SELECT 'SOKO' AS billing_type,
       i.id AS invoice_id,
       i.ref AS invoice_ref,
       i.active AS invoice_active,
       i.paid AS invoice_paid,
       COALESCE(i.pending_amount_decimal, CAST(i.pending_amount AS DECIMAL(19,2))) AS invoice_pending_amount,
       o.id AS source_record_id,
       o.active AS source_active,
       i.payment_account_id AS invoice_payment_account_id,
       o.payment_account_id AS source_payment_account_id,
       o.payment_channel AS source_payment_channel,
       a.channel AS account_channel,
       CASE
           WHEN o.id IS NULL THEN 'SOURCE_NOT_FOUND'
           WHEN o.payment_account_id IS NULL THEN 'SOURCE_DESTINATION_MISSING'
           WHEN o.active = 0 THEN 'SOURCE_INACTIVE'
           WHEN o.customer_user_id <> i.billed_user_id THEN 'SOURCE_CUSTOMER_MISMATCH'
           WHEN s.id IS NULL THEN 'SOURCE_STORE_NOT_FOUND'
           WHEN s.owner_user_id <> i.pay_to_user_id THEN 'SOURCE_PAYEE_MISMATCH'
           WHEN a.id IS NULL THEN 'SOURCE_ACCOUNT_NOT_FOUND'
           WHEN a.category IS NULL OR a.category <> 'MERCHANT' THEN 'SOURCE_ACCOUNT_NOT_MERCHANT'
           WHEN a.active = 0 THEN 'SOURCE_ACCOUNT_INACTIVE'
           WHEN a.verified = 0 THEN 'SOURCE_ACCOUNT_UNVERIFIED'
           WHEN a.created_by IS NULL OR a.created_by <> i.pay_to_user_id THEN 'SOURCE_ACCOUNT_OWNER_MISMATCH'
           WHEN a.channel IS NULL OR (o.payment_channel IS NOT NULL AND o.payment_channel <> a.channel)
               THEN 'SOURCE_CHANNEL_MISMATCH'
           WHEN i.active = 0 AND i.payment_account_id IS NULL THEN 'INACTIVE_INVOICE_DESTINATION_MISSING'
           WHEN i.active = 0 THEN 'INACTIVE_INVOICE_SOURCE_MISMATCH'
           WHEN i.paid = 1 AND i.payment_account_id IS NULL THEN 'PAID_INVOICE_DESTINATION_MISSING'
           WHEN i.paid = 1 THEN 'PAID_INVOICE_SOURCE_MISMATCH'
           WHEN COALESCE(i.pending_amount_decimal, CAST(i.pending_amount AS DECIMAL(19,2))) <= 0.00
                AND i.payment_account_id IS NULL THEN 'NON_POSITIVE_BALANCE_DESTINATION_MISSING'
           WHEN COALESCE(i.pending_amount_decimal, CAST(i.pending_amount AS DECIMAL(19,2))) <= 0.00
               THEN 'NON_POSITIVE_BALANCE_SOURCE_MISMATCH'
           WHEN i.payment_account_id IS NULL THEN 'INVOICE_DESTINATION_MISSING'
           ELSE 'INVOICE_SOURCE_MISMATCH'
       END AS issue_type
FROM pms_invoice i
LEFT JOIN pms_soko_order o ON o.invoice_ref = i.ref
LEFT JOIN pms_soko_store s ON s.id = o.store_id
LEFT JOIN pms_payment_account a ON a.id = o.payment_account_id
WHERE i.billing_type = 'SOKO'
  AND (i.payment_account_id IS NULL
       OR o.id IS NULL
       OR o.payment_account_id IS NULL
       OR o.active = 0
       OR o.customer_user_id <> i.billed_user_id
       OR s.id IS NULL
       OR s.owner_user_id <> i.pay_to_user_id
       OR a.id IS NULL
       OR a.category IS NULL OR a.category <> 'MERCHANT'
       OR a.active = 0
       OR a.verified = 0
       OR a.created_by IS NULL OR a.created_by <> i.pay_to_user_id
       OR a.channel IS NULL
       OR (o.payment_channel IS NOT NULL AND o.payment_channel <> a.channel)
       OR i.payment_account_id <> o.payment_account_id)
UNION ALL
SELECT 'SERVICE_MARKETPLACE' AS billing_type,
       i.id AS invoice_id,
       i.ref AS invoice_ref,
       i.active AS invoice_active,
       i.paid AS invoice_paid,
       COALESCE(i.pending_amount_decimal, CAST(i.pending_amount AS DECIMAL(19,2))) AS invoice_pending_amount,
       b.id AS source_record_id,
       b.active AS source_active,
       i.payment_account_id AS invoice_payment_account_id,
       b.payment_account_id AS source_payment_account_id,
       b.payment_channel AS source_payment_channel,
       a.channel AS account_channel,
       CASE
           WHEN b.id IS NULL THEN 'SOURCE_NOT_FOUND'
           WHEN b.payment_account_id IS NULL THEN 'SOURCE_DESTINATION_MISSING'
           WHEN b.active = 0 THEN 'SOURCE_INACTIVE'
           WHEN b.created_by <> i.billed_user_id THEN 'SOURCE_CUSTOMER_MISMATCH'
           WHEN s.id IS NULL THEN 'SOURCE_SERVICE_NOT_FOUND'
           WHEN p.id IS NULL THEN 'SOURCE_PROFILE_NOT_FOUND'
           WHEN p.user_id <> i.pay_to_user_id THEN 'SOURCE_PAYEE_MISMATCH'
           WHEN a.id IS NULL THEN 'SOURCE_ACCOUNT_NOT_FOUND'
           WHEN a.category IS NULL OR a.category <> 'MERCHANT' THEN 'SOURCE_ACCOUNT_NOT_MERCHANT'
           WHEN a.active = 0 THEN 'SOURCE_ACCOUNT_INACTIVE'
           WHEN a.verified = 0 THEN 'SOURCE_ACCOUNT_UNVERIFIED'
           WHEN a.created_by IS NULL OR a.created_by <> i.pay_to_user_id THEN 'SOURCE_ACCOUNT_OWNER_MISMATCH'
           WHEN a.channel IS NULL OR (b.payment_channel IS NOT NULL AND b.payment_channel <> a.channel)
               THEN 'SOURCE_CHANNEL_MISMATCH'
           WHEN i.active = 0 AND i.payment_account_id IS NULL THEN 'INACTIVE_INVOICE_DESTINATION_MISSING'
           WHEN i.active = 0 THEN 'INACTIVE_INVOICE_SOURCE_MISMATCH'
           WHEN i.paid = 1 AND i.payment_account_id IS NULL THEN 'PAID_INVOICE_DESTINATION_MISSING'
           WHEN i.paid = 1 THEN 'PAID_INVOICE_SOURCE_MISMATCH'
           WHEN COALESCE(i.pending_amount_decimal, CAST(i.pending_amount AS DECIMAL(19,2))) <= 0.00
                AND i.payment_account_id IS NULL THEN 'NON_POSITIVE_BALANCE_DESTINATION_MISSING'
           WHEN COALESCE(i.pending_amount_decimal, CAST(i.pending_amount AS DECIMAL(19,2))) <= 0.00
               THEN 'NON_POSITIVE_BALANCE_SOURCE_MISMATCH'
           WHEN i.payment_account_id IS NULL THEN 'INVOICE_DESTINATION_MISSING'
           ELSE 'INVOICE_SOURCE_MISMATCH'
       END AS issue_type
FROM pms_invoice i
LEFT JOIN pms_sp_booking b ON b.invoice_ref = i.ref
LEFT JOIN pms_sp_service s ON s.id = b.service_id
LEFT JOIN pms_sp_profile p ON p.id = s.profile_id
LEFT JOIN pms_payment_account a ON a.id = b.payment_account_id
WHERE i.billing_type = 'SERVICE_MARKETPLACE'
  AND (i.payment_account_id IS NULL
       OR b.id IS NULL
       OR b.payment_account_id IS NULL
       OR b.active = 0
       OR b.created_by <> i.billed_user_id
       OR s.id IS NULL
       OR p.id IS NULL
       OR p.user_id <> i.pay_to_user_id
       OR a.id IS NULL
       OR a.category IS NULL OR a.category <> 'MERCHANT'
       OR a.active = 0
       OR a.verified = 0
       OR a.created_by IS NULL OR a.created_by <> i.pay_to_user_id
       OR a.channel IS NULL
       OR (b.payment_channel IS NOT NULL AND b.payment_channel <> a.channel)
       OR i.payment_account_id <> b.payment_account_id);
