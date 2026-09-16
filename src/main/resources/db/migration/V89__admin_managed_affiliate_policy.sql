-- No seed values: effective deployment defaults remain until Superadmin saves a policy.
CREATE TABLE pms_affiliate_policy (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,uuid BINARY(16) NOT NULL UNIQUE,
 created_on DATETIME(6),active BIT NOT NULL,created_by BIGINT,last_modified_date DATETIME(6),
 policy_key VARCHAR(32) NOT NULL UNIQUE,
 commission_rate DECIMAL(8,2) NOT NULL,eligible_payment_count INT NOT NULL,
 minimum_payout DECIMAL(15,2) NOT NULL,hold_days INT NOT NULL,version BIGINT NOT NULL DEFAULT 0
);
