package org.pms.silverocean.service.subscription.enums;

public enum SubscriptionEventType {
    ACTIVATION,
    UPGRADE,
    RENEWAL,
    CANCELLATION_REQUESTED,
    CANCELLATION_REVOKED,
    PLAN_CHANGE_REQUESTED,
    PLAN_CHANGE_REVOKED,
    AUTO_RENEW_UPDATED,
    EXPIRY,
    PAYMENT_FAILED,
    TRIAL_STARTED
}
