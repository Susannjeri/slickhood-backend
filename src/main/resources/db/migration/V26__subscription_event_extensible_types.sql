-- Hibernate originally created event_type as a MySQL ENUM. New legitimate
-- lifecycle events such as TRIAL_STARTED must be deployable without a schema
-- failure. SubscriptionEventType remains the application-level validator.
ALTER TABLE pms_subscription_event
    MODIFY COLUMN event_type VARCHAR(64) NULL;
