ALTER TABLE pms_users
    ADD COLUMN account_status VARCHAR(40) NOT NULL DEFAULT 'PENDING_EMAIL_VERIFICATION',
    ADD COLUMN phone_verified_at DATETIME(6) NULL;

UPDATE pms_users
SET phone_verified_at = COALESCE(last_modified_date, created_on)
WHERE phone_verified = 1 AND phone_verified_at IS NULL;

UPDATE pms_users
SET account_status = CASE
    WHEN verified = 1 THEN 'ACTIVE'
    WHEN email_verified = 1 THEN 'PENDING_KYC'
    ELSE 'PENDING_EMAIL_VERIFICATION'
END;

UPDATE pms_users u
SET u.account_status = 'ACTIVE'
WHERE EXISTS (
    SELECT 1
    FROM pms_user_role ur
    JOIN pms_role r ON r.id = ur.role_id
    WHERE ur.user_id = u.id
      AND r.name IN ('Superadmin', 'Finance', 'InsuranceAdviser', 'InsuranceManager', 'Guard', 'PropertyManager')
);

UPDATE pms_users u
JOIN pms_kyc_case k ON k.user_id = u.id AND k.active = 1
SET u.account_status = CASE
    WHEN k.status = 'APPROVED' THEN 'ACTIVE'
    WHEN k.status IN ('SUBMITTED', 'REVIEW_REQUIRED') THEN 'KYC_UNDER_REVIEW'
    WHEN k.status = 'REJECTED' THEN 'KYC_REJECTED'
    ELSE 'PENDING_KYC'
END
WHERE u.account_status <> 'ACTIVE';

CREATE INDEX idx_users_account_status ON pms_users(account_status);
