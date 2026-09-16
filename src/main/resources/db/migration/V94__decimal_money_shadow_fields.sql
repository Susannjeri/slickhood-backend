-- Preserve legacy columns while new writes are mirrored into exact DECIMAL shadows.
ALTER TABLE pms_invoice
    ADD COLUMN amount_decimal DECIMAL(19,2) NULL AFTER amount,
    ADD COLUMN pending_amount_decimal DECIMAL(19,2) NULL AFTER pending_amount;
UPDATE pms_invoice SET amount_decimal=ROUND(amount,2),pending_amount_decimal=ROUND(pending_amount,2)
WHERE amount_decimal IS NULL OR pending_amount_decimal IS NULL;

ALTER TABLE pms_payment
    ADD COLUMN amount_decimal DECIMAL(19,2) NULL AFTER amount,
    ADD COLUMN currency_code VARCHAR(3) NULL AFTER amount_decimal;
UPDATE pms_payment p LEFT JOIN pms_invoice i ON i.ref=p.bill_reference
SET p.amount_decimal=ROUND(p.amount,2),
    p.currency_code=CASE WHEN UPPER(i.currency) IN ('KES','USD') THEN UPPER(i.currency) ELSE NULL END
WHERE p.amount_decimal IS NULL OR p.currency_code IS NULL;

ALTER TABLE pms_unit ADD COLUMN price_decimal DECIMAL(19,2) NULL AFTER price;
UPDATE pms_unit SET price_decimal=ROUND(price,2) WHERE price_decimal IS NULL;

ALTER TABLE pms_lease
    ADD COLUMN price_decimal DECIMAL(19,2) NULL AFTER price,
    ADD COLUMN repair_threshold_decimal DECIMAL(19,2) NULL AFTER repair_threshold;
UPDATE pms_lease SET price_decimal=ROUND(price,2),
    repair_threshold_decimal=CASE WHEN repair_threshold IS NULL THEN NULL ELSE ROUND(repair_threshold,2) END
WHERE price_decimal IS NULL OR (repair_threshold IS NOT NULL AND repair_threshold_decimal IS NULL);

ALTER TABLE pms_unit_charge ADD COLUMN amount_decimal DECIMAL(19,2) NULL AFTER amount;
UPDATE pms_unit_charge SET amount_decimal=ROUND(amount,2) WHERE amount_decimal IS NULL;
ALTER TABLE pms_lease_charge ADD COLUMN amount_decimal DECIMAL(19,2) NULL AFTER amount;
UPDATE pms_lease_charge SET amount_decimal=ROUND(amount,2) WHERE amount_decimal IS NULL;

CREATE OR REPLACE VIEW pms_monetary_shadow_reconciliation AS
SELECT 'INVOICE_AMOUNT' field_name,id record_id,CAST(amount AS DECIMAL(19,2)) legacy_value,amount_decimal decimal_value FROM pms_invoice WHERE amount_decimal IS NULL OR ABS(CAST(amount AS DECIMAL(19,2))-amount_decimal)>0.00
UNION ALL SELECT 'INVOICE_PENDING',id,CAST(pending_amount AS DECIMAL(19,2)),pending_amount_decimal FROM pms_invoice WHERE pending_amount_decimal IS NULL OR ABS(CAST(pending_amount AS DECIMAL(19,2))-pending_amount_decimal)>0.00
UNION ALL SELECT 'PAYMENT_AMOUNT',id,CAST(amount AS DECIMAL(19,2)),amount_decimal FROM pms_payment WHERE (amount IS NULL AND amount_decimal IS NOT NULL) OR (amount IS NOT NULL AND (amount_decimal IS NULL OR ABS(CAST(amount AS DECIMAL(19,2))-amount_decimal)>0.00))
UNION ALL SELECT 'UNIT_PRICE',id,CAST(price AS DECIMAL(19,2)),price_decimal FROM pms_unit WHERE price_decimal IS NULL OR ABS(CAST(price AS DECIMAL(19,2))-price_decimal)>0.00
UNION ALL SELECT 'LEASE_PRICE',id,CAST(price AS DECIMAL(19,2)),price_decimal FROM pms_lease WHERE price_decimal IS NULL OR ABS(CAST(price AS DECIMAL(19,2))-price_decimal)>0.00
UNION ALL SELECT 'LEASE_REPAIR_THRESHOLD',id,CAST(repair_threshold AS DECIMAL(19,2)),repair_threshold_decimal FROM pms_lease WHERE (repair_threshold IS NULL AND repair_threshold_decimal IS NOT NULL) OR (repair_threshold IS NOT NULL AND (repair_threshold_decimal IS NULL OR ABS(CAST(repair_threshold AS DECIMAL(19,2))-repair_threshold_decimal)>0.00))
UNION ALL SELECT 'UNIT_CHARGE',id,CAST(amount AS DECIMAL(19,2)),amount_decimal FROM pms_unit_charge WHERE amount_decimal IS NULL OR ABS(CAST(amount AS DECIMAL(19,2))-amount_decimal)>0.00
UNION ALL SELECT 'LEASE_CHARGE',id,CAST(amount AS DECIMAL(19,2)),amount_decimal FROM pms_lease_charge WHERE amount_decimal IS NULL OR ABS(CAST(amount AS DECIMAL(19,2))-amount_decimal)>0.00;

CREATE OR REPLACE VIEW pms_transaction_currency_reconciliation AS
SELECT 'INVOICE' record_type,id record_id,currency FROM pms_invoice WHERE currency IS NULL OR UPPER(currency) NOT IN ('KES','USD')
UNION ALL SELECT 'PAYMENT',id,currency_code FROM pms_payment WHERE amount IS NOT NULL AND (currency_code IS NULL OR UPPER(currency_code) NOT IN ('KES','USD'))
UNION ALL SELECT 'UNIT',id,currency FROM pms_unit WHERE currency IS NULL OR UPPER(currency) NOT IN ('KES','USD')
UNION ALL SELECT 'LEASE',id,currency FROM pms_lease WHERE currency IS NULL OR UPPER(currency) NOT IN ('KES','USD');

CREATE OR REPLACE VIEW pms_financial_reconciliation AS
SELECT 'INVOICE_BALANCE_OUT_OF_RANGE' issue_type,CAST(id AS CHAR) record_reference,
       amount_decimal expected_value,pending_amount_decimal actual_value,currency
FROM pms_invoice
WHERE amount_decimal IS NOT NULL AND pending_amount_decimal IS NOT NULL
  AND (pending_amount_decimal < 0.00 OR pending_amount_decimal > amount_decimal)
UNION ALL
SELECT 'PAYMENT_OPERATIONS_EXCEED_PAYMENT',CAST(p.id AS CHAR),p.amount_decimal,SUM(o.amount),p.currency_code
FROM pms_payment p
JOIN pms_payment_operation o ON o.payment_id=p.id
WHERE o.status='CONFIRMED' AND o.operation_type IN ('REFUND','REVERSAL','CHARGEBACK')
GROUP BY p.id,p.amount_decimal,p.currency_code
HAVING p.amount_decimal IS NOT NULL AND SUM(o.amount)>p.amount_decimal
UNION ALL
SELECT 'AFFILIATE_COMMISSION_CURRENCY',CAST(c.id AS CHAR),c.qualifying_amount,c.commission_amount,c.currency
FROM pms_affiliate_commission c
WHERE c.currency IS NULL OR UPPER(c.currency) NOT IN ('KES','USD');
