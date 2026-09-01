ALTER TABLE pms_affiliate_payout
 ADD COLUMN payout_account_name VARCHAR(160),
 ADD COLUMN payout_channel VARCHAR(50),
 ADD COLUMN processed_by_user_id BIGINT,
 ADD KEY idx_affiliate_payout_queue (status,active,requested_at);

UPDATE pms_affiliate_payout p
 JOIN pms_payment_account a ON a.id=p.payment_account_id
 SET p.payout_account_name=a.name,p.payout_channel=a.channel
 WHERE p.payout_account_name IS NULL;

ALTER TABLE pms_affiliate_commission
 ADD COLUMN available_at DATETIME(6),
 ADD COLUMN reversed_at DATETIME(6),
 ADD COLUMN reversal_reason VARCHAR(1000),
 ADD KEY idx_affiliate_commission_maturity (status,active,available_at);

UPDATE pms_affiliate_commission SET available_at=earned_at WHERE available_at IS NULL;
