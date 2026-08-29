-- Hibernate originally created pms_user_subscription.role as a MySQL ENUM.
-- Explicit personas added after the original schema (for example
-- ESTATE_MANAGER and SALES_AGENT) cannot be persisted into that frozen enum.
-- PMSRole remains the application-level validator; the database stores its
-- bounded string representation so future legitimate roles are deployable.
ALTER TABLE pms_user_subscription
    MODIFY COLUMN role VARCHAR(64) NULL;
