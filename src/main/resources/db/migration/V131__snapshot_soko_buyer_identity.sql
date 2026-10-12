-- Freeze the verified buyer identity that authorised checkout. Fulfilment and
-- invoice display must not later fall back to a shop owner or a mutable user
-- profile, and historical orders retain the identity used at purchase time.
ALTER TABLE pms_soko_order
    ADD COLUMN customer_name_snapshot VARCHAR(160) NULL AFTER delivery_longitude,
    ADD COLUMN customer_email_snapshot VARCHAR(255) NULL AFTER customer_name_snapshot;

-- Existing rows predate the snapshot. Backfill only from the order's buyer id;
-- never use the store owner as a fallback.
UPDATE pms_soko_order o
LEFT JOIN pms_users u ON u.id = o.customer_user_id
SET o.customer_name_snapshot = COALESCE(o.customer_name_snapshot,u.full_name),
    o.customer_email_snapshot = COALESCE(o.customer_email_snapshot,LOWER(u.email))
WHERE o.customer_name_snapshot IS NULL
   OR o.customer_email_snapshot IS NULL;

-- Public marketplace buyers are distinct from invite-bound property buyers and
-- from sellers. PermissionInit adds the least-privilege mappings on boot; this
-- idempotent seed ensures the role is available to registration immediately.
INSERT INTO pms_role (uuid, created_on, active, name, description, self_assignable)
SELECT UUID_TO_BIN(UUID()), UTC_TIMESTAMP(6), 1, 'MarketplaceBuyer',
       'Verified customer who can buy products and request marketplace services', 1
WHERE NOT EXISTS (SELECT 1 FROM pms_role WHERE name = 'MarketplaceBuyer');

UPDATE pms_role
SET active = 1,
    self_assignable = 1,
    description = 'Verified customer who can buy products and request marketplace services'
WHERE name = 'MarketplaceBuyer';

-- Product publishing is seller-owned. The retired approval workflow left
-- active listings in REJECTED, a state the current transition model cannot
-- publish. Restore those legacy listings as editable drafts; the seller must
-- still explicitly publish, and normal KYC/catalogue validation still runs.
UPDATE pms_soko_product
SET status = 'DRAFT'
WHERE active = 1
  AND UPPER(TRIM(status)) = 'REJECTED';
