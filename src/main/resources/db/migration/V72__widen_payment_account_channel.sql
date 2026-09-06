-- Payment channel values have historically been stored both as enum names
-- (for PaymentAccount) and human-readable provider labels (for legacy data).
-- Keep the column textual and wide enough for either representation so account
-- creation cannot fail with MySQL "Data truncated" when a display label is
-- received from an older client.
ALTER TABLE pms_payment_account
    MODIFY COLUMN channel VARCHAR(64) NOT NULL;
